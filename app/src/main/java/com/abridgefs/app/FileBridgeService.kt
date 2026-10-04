package com.abridgefs.app
import android.animation.ValueAnimator
import android.app.*
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.*
import android.text.InputType
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FileBridgeService:Service(){
private lateinit var wm:WindowManager
private lateinit var overlayRoot:FrameLayout
private lateinit var bottom_bar:LinearLayout
private lateinit var ball:PillOrbView
private lateinit var bottomBarBrand:TextView
private var panel:LinearLayout?=null
private var isRenderingBrowser=false
private var isRenderingMainPanel=false
private var isUpdatingBottomBarLayout=false
private var isUpdatingPanelLayout=false
private var isClosingPanel=false
private var bottomBarEdgeHidden=0
private var bottomBarRevealTargetX:Int?=null
private var bottomBarSnapAnimator:ValueAnimator?=null
private var bottomBarMoveAnimator:ValueAnimator?=null
private var floatingRestoreX:Int?=null
private var floatingRestoreY:Int?=null
private var hasInitialFloatingPosition=false
private var bottomBarPanelStartX=0
private var bottomBarPanelStartY=0
private var commandInput:EditText?=null
private lateinit var root:File
private var workspaceReady=false
private val commandExecutor=java.util.concurrent.Executors.newSingleThreadExecutor()
private lateinit var bottomBarLp:WindowManager.LayoutParams
private lateinit var panelLpRef:WindowManager.LayoutParams
private val handler=Handler(Looper.getMainLooper())
companion object{@Volatile var running=false;@Volatile var clipboardCallback:((String)->Unit)?=null}

override fun onCreate(){
super.onCreate();running=true
val configuredRoot=getSharedPreferences("bridgefs",0).getString("root_path","").orEmpty()
root=File(getExternalFilesDir(null),"unconfigured-workspace")
workspaceReady=runCatching{configuredRoot.isNotBlank()&&File(configuredRoot).isDirectory&&!isProtectedWorkspace(configuredRoot)}.getOrDefault(false)
if(workspaceReady)root=File(configuredRoot).canonicalFile
channel()
startForeground(1,Notification.Builder(this,"filebridge").setContentTitle("FileBridge").setContentText("悬浮文件桥运行中").setSmallIcon(android.R.drawable.ic_menu_manage).build())
wm=getSystemService(WINDOW_SERVICE)as WindowManager
log("Service","onCreate");showBall()
}
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
if (intent?.hasExtra("bridgefs_external_command") == true) {
val text=intent.getStringExtra("bridgefs_external_command").orEmpty()
val requestedRoot=intent.getStringExtra("bridgefs_root").orEmpty()
val projectId=intent.getStringExtra("projectId")
val conversationId=intent.getStringExtra("conversationId")
val standaloneConversationId=intent.getStringExtra("standaloneConversationId")
commandExecutor.submit {
val result=executeExternalCommand(requestedRoot,text,projectId,conversationId,standaloneConversationId)
broadcastReceipt(result.first,result.second,result.third,projectId,conversationId,standaloneConversationId)
}
}
return START_NOT_STICKY
}

private fun executeExternalCommand(requestedRoot:String,text:String,projectId:String?,conversationId:String?,standaloneConversationId:String?):Triple<String,String,String>{
if(rootPath.isBlank()||!rootFile.isDirectory||isProtectedWorkspace(rootPath)){
return Triple("FAILED",text,"工作目录无效或属于受保护区域："+rootPath)
}
val commands=CommandParser.parse(text)
if(commands.isEmpty()){
return Triple("FAILED",text,CommandParser.lastError ?: "未识别到可执行指令")
}
val prefs = getSharedPreferences("bridgefs", 0)
val limit = prefs.getInt("command_limit", 3).coerceIn(1, 20)
if (commands.size > limit) {
    AppLogger.log(this, "EXECUTION_DENIED", "reason=command_limit count=" + commands.size + " limit=" + limit)
    return Triple("DENIED", text, "本轮指令数量 " + commands.size + " 超过限制 " + limit + "，未执行。")
}
val project = projectId?.let { id -> BridgeProjectStore(this).load().firstOrNull { it.id == id } }
val projectConversation = project?.let { current -> conversationId?.let { id -> current.conversations.firstOrNull { it.id == id } } ?: current.activeConversation() }
val standaloneConversation = standaloneConversationId?.let { id ->
    BridgeConversationStore(this).load().firstOrNull { it.id == id }
}
val auth = PermissionPolicy.authorization(this, project, projectConversation ?: standaloneConversation)
val rootPath = auth.root.ifBlank { requestedRoot }
val rootFile=File(rootPath)
if(rootPath.isBlank() || !rootFile.isDirectory || isProtectedWorkspace(rootPath)) {
    return Triple("FAILED", text, "Project Local Address 无效或属于受保护区域：" + rootPath)
}
if (project != null && requestedRoot.isNotBlank() && File(requestedRoot).canonicalPath != rootFile.canonicalPath) {
    return Triple("DENIED", text, "请求的 Local Address 与当前 Project Address 不一致")
}
val denied = commands.firstOrNull { PermissionPolicy.check(it, auth) == Decision.DENY }
if (denied != null) {
    AppLogger.log(this, "EXECUTION_DENIED", "reason=permission command=" + denied)
    return Triple("DENIED", denied.toString(), "当前权限设置禁止该操作")
}
val confirm = commands.firstOrNull { PermissionPolicy.check(it, auth) == Decision.CONFIRM }
if (confirm != null) {
    AppLogger.log(this, "EXECUTION_DENIED", "reason=confirmation_required command=" + confirm)
    return Triple("DENIED", confirm.toString(), "该操作需要用户确认，Service 不允许绕过确认直接执行")
}
return try{
val results=commands.map{CommandExecutor(rootFile,this).execute(it)}
val message=results.joinToString("\n\n")
val status=if(results.any{it.contains("✗")})"FAILED" else "SUCCEEDED"
Triple(status,commands.joinToString(" | "){it.toString()},message)
}catch(e:Exception){
Triple("FAILED",text,"执行异常："+(e.message ?: "未知错误"))
}
}

private fun broadcastReceipt(status:String,command:String,message:String,projectId:String?,conversationId:String?,standaloneConversationId:String?){
val now=System.currentTimeMillis()
val pending=org.json.JSONObject()
    .put("receiptId",java.util.UUID.randomUUID().toString())
    .put("status",status)
    .put("command",command)
    .put("message",message)
    .put("time",now)
    .put("projectId",projectId ?: "").put("conversationId",conversationId ?: "")
    .put("standaloneConversationId",standaloneConversationId ?: "")
val prefs = getSharedPreferences("bridgefs",0)
val queue = org.json.JSONArray(prefs.getString("pending_receipts","[]") ?: "[]")
queue.put(pending)
prefs.edit()
    .putString("pending_receipts",queue.toString())
    .remove("pending_receipt")
    .apply()
val intent=Intent("com.bridgefs.RESULT").setPackage(packageName)
.putExtra("receiptId",pending.optString("receiptId"))
.putExtra("status",status)
.putExtra("command",command)
.putExtra("message",message)
.putExtra("projectId",projectId)
.putExtra("conversationId",conversationId)
.putExtra("standaloneConversationId",standaloneConversationId)
.putExtra("time",now)
sendBroadcast(intent)
}
private fun channel(){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("filebridge","FileBridge",NotificationManager.IMPORTANCE_LOW))}
private fun lp(w:Int,h:Int)=WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,-3)
private fun panelLp(w:Int,h:Int)=WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,-3)
private fun mainPanelLp(w:Int,h:Int)=WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,-3)

private fun attachPanelToOverlay(box:LinearLayout,x:Int,y:Int){
try{
panel=box
panelLpRef=bottomBarLp
bottomBarLp.width=resources.getDimensionPixelSize(R.dimen.panel_width)
bottomBarLp.height=WindowManager.LayoutParams.WRAP_CONTENT
bottomBarLp.gravity=Gravity.TOP or Gravity.LEFT
bottomBarLp.flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
bottomBarLp.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
bottomBarLp.x=x.coerceAtLeast(0);bottomBarLp.y=y.coerceAtLeast(0)
if(box.parent!==overlayRoot)overlayRoot.addView(box,0,FrameLayout.LayoutParams(-1,-2,Gravity.TOP or Gravity.LEFT))
overlayRoot.setOnTouchListener{_,event->if(panel!=null&&event.actionMasked==MotionEvent.ACTION_OUTSIDE){closePanel();true}else false}
box.visibility=View.VISIBLE;ensureRobotState(true);updateBottomBarWindow()
}catch(e:Exception){log("Error","attach panel："+e.message);runCatching{(box.parent as? ViewGroup)?.removeView(box)};panel=null}
}
private fun showBall(){
try{
bottomBarMoveAnimator?.cancel();bottomBarMoveAnimator=null
bottomBarSnapAnimator?.cancel();bottomBarSnapAnimator=null;bottomBarRevealTargetX=null
val previousHiddenEdge=bottomBarEdgeHidden
if(!::overlayRoot.isInitialized)overlayRoot=FrameLayout(this)
if(!::bottom_bar.isInitialized){
ball=PillOrbView(this)
bottom_bar=LinearLayout(this).apply{
orientation=LinearLayout.HORIZONTAL
gravity=Gravity.CENTER_VERTICAL or Gravity.RIGHT
elevation=dp(12).toFloat()
isClickable=true
setPadding(dp(10),0,dp(10),0)
bottomBarBrand=TextView(this@FileBridgeService).apply{text="BridgeFS";textSize=11f;setTextColor(resources.getColor(R.color.bridgefs_text_secondary));gravity=Gravity.BOTTOM or Gravity.START;visibility=View.GONE}
addView(bottomBarBrand,LinearLayout.LayoutParams(-2,-2).apply{gravity=Gravity.BOTTOM;bottomMargin=dp(4)})
addView(ball,LinearLayout.LayoutParams(dp(32),resources.getDimensionPixelSize(R.dimen.bridgefs_bottom_bar_height)))
setOnTouchListener{v,e->bottomBarTouchHandler(v,e)}
}
overlayRoot.addView(bottom_bar,FrameLayout.LayoutParams(-2,resources.getDimensionPixelSize(R.dimen.bridgefs_bottom_bar_height),Gravity.TOP or Gravity.LEFT))
bottomBarLp=lp(WindowManager.LayoutParams.WRAP_CONTENT,resources.getDimensionPixelSize(R.dimen.bridgefs_bottom_bar_height))
}
bottom_bar.visibility=View.VISIBLE;bottom_bar.alpha=1f;bottomBarBrand.visibility=View.GONE;ball.visibility=View.VISIBLE;bottom_bar.setPadding(0,0,0,0)
(bottom_bar.layoutParams as? FrameLayout.LayoutParams)?.let{it.width=WindowManager.LayoutParams.WRAP_CONTENT;it.height=resources.getDimensionPixelSize(R.dimen.bridgefs_bottom_bar_height);it.gravity=Gravity.TOP or Gravity.LEFT;it.leftMargin=0;it.topMargin=0;bottom_bar.layoutParams=it}
(ball.layoutParams as? LinearLayout.LayoutParams)?.let{it.marginStart=0;ball.layoutParams=it}
ball.alpha=1f;ball.translationX=0f;ball.translationY=0f
bottomBarLp.width=WindowManager.LayoutParams.WRAP_CONTENT;bottomBarLp.height=resources.getDimensionPixelSize(R.dimen.bridgefs_bottom_bar_height)
bottomBarLp.gravity=Gravity.TOP or Gravity.START;bottomBarLp.flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
bottomBarLp.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
bottom_bar.requestLayout();bottom_bar.measure(View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED),View.MeasureSpec.makeMeasureSpec(bottomBarLp.height,View.MeasureSpec.EXACTLY))
val screenWidth=resources.displayMetrics.widthPixels
val screenHeight=resources.displayMetrics.heightPixels
val maxFloatingX=(screenWidth-bottom_bar.measuredWidth).coerceAtLeast(0)
val maxFloatingY=(screenHeight-bottom_bar.measuredHeight).coerceAtLeast(0)
val restoreX=floatingRestoreX;val restoreY=floatingRestoreY
val isInitialPlacement=!hasInitialFloatingPosition
bottomBarLp.x=when{
restoreX!=null->restoreX.coerceIn(0,maxFloatingX)
previousHiddenEdge<0->0
previousHiddenEdge>0->maxFloatingX
isInitialPlacement->(screenWidth-dp(32)-dp(16)).coerceIn(0,maxFloatingX)
else->bottomBarLp.x.coerceIn(0,maxFloatingX)
}
bottomBarLp.y=when{
restoreY!=null->restoreY.coerceIn(0,maxFloatingY)
isInitialPlacement->((screenHeight-bottomBarLp.height)/2).coerceIn(0,maxFloatingY)
else->bottomBarLp.y.coerceIn(0,maxFloatingY)
}
hasInitialFloatingPosition=true
bottomBarEdgeHidden=0;floatingRestoreX=null;floatingRestoreY=null
bottomBarBrand.invalidate();ball.invalidate();bottom_bar.invalidate();overlayRoot.invalidate()
overlayRoot.setOnTouchListener{_,event->if(panel!=null&&event.actionMasked==MotionEvent.ACTION_OUTSIDE){closePanel();true}else false}
if(overlayRoot.isAttachedToWindow)updateBottomBarWindow()else wm.addView(overlayRoot,bottomBarLp)
ensureRobotState(false)
}catch(e:Exception){log("Error","showBall："+e.message);runCatching{if(::bottom_bar.isInitialized){bottom_bar.visibility=View.VISIBLE;ball.visibility=View.VISIBLE;ball.invalidate();bottom_bar.invalidate()}}}
}

private fun ensureRobotState(expanded:Boolean){
if(!::bottom_bar.isInitialized)return
try{
bottom_bar.visibility=View.VISIBLE
bottom_bar.setPadding(if(expanded)dp(10) else 0,0,if(expanded)dp(10) else 0,0)
bottomBarBrand.visibility=if(expanded)View.VISIBLE else View.GONE
(ball.layoutParams as? LinearLayout.LayoutParams)?.let{it.marginStart=if(expanded)dp(8) else 0;ball.layoutParams=it}
ball.visibility=View.VISIBLE
if(!::bottomBarLp.isInitialized)return
if(panel==null){
bottomBarLp.width=WindowManager.LayoutParams.WRAP_CONTENT
bottomBarLp.height=resources.getDimensionPixelSize(R.dimen.bridgefs_bottom_bar_height)
(bottom_bar.layoutParams as? FrameLayout.LayoutParams)?.let{it.leftMargin=0;it.topMargin=0;bottom_bar.layoutParams=it}
}else{
bottomBarLp.width=resources.getDimensionPixelSize(R.dimen.panel_width)
bottomBarLp.height=WindowManager.LayoutParams.WRAP_CONTENT
}
bottom_bar.requestLayout()
bottom_bar.measure(View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED),View.MeasureSpec.makeMeasureSpec(resources.getDimensionPixelSize(R.dimen.bridgefs_bottom_bar_height),View.MeasureSpec.EXACTLY))
bottomBarBrand.invalidate();ball.invalidate();bottom_bar.invalidate();overlayRoot.invalidate()
if(overlayRoot.isAttachedToWindow)updateBottomBarWindow()
}catch(e:Exception){log("Error","ensureRobotState："+e.message);runCatching{ball.visibility=View.VISIBLE;ball.invalidate()}}
}

private fun showPanel(){
if(!workspaceReady){toast("请先在 BridgeFS 主页面设置并激活项目目录");return}
if(isRenderingMainPanel)return
isRenderingMainPanel=true
try{
bottomBarRevealTargetX?.let{target->bottomBarSnapAnimator?.cancel();bottomBarSnapAnimator=null;bottomBarLp.x=target;bottomBarRevealTargetX=null;updateBottomBarWindow()}
if(panel==null){floatingRestoreX=bottomBarLp.x;floatingRestoreY=bottomBarLp.y}
val origin=IntArray(2).also{bottom_bar.getLocationOnScreen(it)}
clearPanel()
orbTransitionOrigin=origin
bottomBarMoveAnimator?.cancel();bottomBarMoveAnimator=null
log("UI","showPanel")
val box=LinearLayout(this).apply{
orientation=LinearLayout.VERTICAL
setPadding(dp(8),dp(8),dp(8),dp(8))
background=bg("#FFFFFF",16,"#E0E0E0")
elevation=dp(8).toFloat()
}
val address=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
val dir=TextView(this).apply{
text=root.name.ifBlank{root.absolutePath}
textSize=15f;setTextColor(resources.getColor(R.color.bridgefs_text_primary));setSingleLine(true);contentDescription=root.absolutePath
setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_folder,0,0,0);compoundDrawablePadding=dp(6)
ellipsize=android.text.TextUtils.TruncateAt.END
setOnClickListener{showBrowser()}
setOnLongClickListener{copyText("BridgeFS路径",root.absolutePath);toast("已复制路径");true}
}
address.addView(dir,LinearLayout.LayoutParams(-1,dp(36)))
box.addView(address,LinearLayout.LayoutParams(-1,dp(36)))
// Main panel movement is bound only to the bottom bar.
val input=EditText(this).apply{
setText("")
hint="粘贴 AI 指令到这里..."
textSize=13f;gravity=Gravity.TOP
inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
setPadding(dp(10),dp(8),dp(42),dp(8));background=bg("#F5F6F8",10,"#E0E0E0")
isFocusable=false;isFocusableInTouchMode=false;showSoftInputOnFocus=false
setOnClickListener{showCommandInputDialog(this)}
}
commandInput=input
clipboardCallback={text->handler.post{commandInput?.let{it.clearFocus();it.setText(text)}}}
val inputFrame=FrameLayout(this).apply{
addView(input,FrameLayout.LayoutParams(-1,-1))
addView(TextView(this@FileBridgeService).apply{
text="!";textSize=14f;setTypeface(null,1);gravity=Gravity.CENTER;setTextColor(Color.WHITE)
contentDescription="指令帮助";background=bg("#FF7A00",14,null)
setOnClickListener{showHelpDialog()}
},FrameLayout.LayoutParams(dp(32),dp(32),Gravity.TOP or Gravity.RIGHT).also{it.topMargin=dp(4);it.rightMargin=dp(4)})
}
val paste=mainButton("粘贴"){val intent=Intent(this,ClipboardReaderActivity::class.java).apply{addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)};startActivity(intent)}
var runButton:Button?=null
val run=mainButton("执行"){
val raw=input.text.toString();log("Command","收到："+raw.replace("\n","\\n").take(500))
val cs=CommandParser.parse(raw)
if(cs.isEmpty()){
    val message=CommandParser.lastError ?: "未发现可执行指令"
    findReceipt(box)?.let{it.text=message;it.setTextColor(Color.DKGRAY)}
    broadcastReceipt("FAILED",raw,message,null,null,null,null)
}else if(CommandParser.lastError!=null){
    val message=CommandParser.lastError!!
    findReceipt(box)?.let{it.text=message;it.setTextColor(Color.DKGRAY)}
    broadcastReceipt("FAILED",raw,message,null,null,null,null)
}else{
    runButton?.isEnabled=false
    commandExecutor.submit{
        val results=cs.map{CommandExecutor(root,this).execute(it)}
        val message=results.joinToString("\n\n")
        val status=if(results.any{it.contains("✗")})"FAILED" else "SUCCEEDED"
        handler.post{
            findReceipt(box)?.let{it.text=message;it.setTextColor(Color.DKGRAY)}
            runButton?.isEnabled=true
            log("Command","执行 "+cs.size+" 条指令："+if(status=="SUCCEEDED")"成功" else "失败")
        }
        broadcastReceipt(status,cs.joinToString(" | "){it.toString()},message,null,null,null,null)
    }
}
}
runButton=run
val inputSide=LinearLayout(this).apply{
orientation=LinearLayout.VERTICAL
addView(paste,LinearLayout.LayoutParams(dp(56),dp(40)))
addView(run,LinearLayout.LayoutParams(dp(56),dp(40)).also{it.topMargin=dp(5)})
}
val inputRow=LinearLayout(this).apply{
gravity=Gravity.BOTTOM
addView(inputFrame,LinearLayout.LayoutParams(0,dp(85),1f))
addView(inputSide,LinearLayout.LayoutParams(dp(56),dp(85)).also{it.marginStart=dp(8)})
}
box.addView(inputRow,LinearLayout.LayoutParams(-1,dp(85)).also{it.topMargin=dp(8)})

val receipt=TextView(this).apply{
text="执行结果会显示在这里";textSize=11f;typeface=android.graphics.Typeface.MONOSPACE
setPadding(dp(10),dp(8),dp(10),dp(8));setTextColor(resources.getColor(R.color.bridgefs_text_secondary));background=resourceBg(R.color.bridgefs_input_surface,R.dimen.dialog_input_corner_radius);minHeight=dp(40)
}
val resultScroll=object:ScrollView(this){
override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
super.onMeasure(widthMeasureSpec,View.MeasureSpec.makeMeasureSpec(dp(100),View.MeasureSpec.AT_MOST))
}
}.apply{
isFillViewport=false
isVerticalScrollBarEnabled=true;scrollBarSize=dp(2)
addView(receipt,FrameLayout.LayoutParams(-1,-2))
}
val copyReceipt=mainButton("复制"){copyText("BridgeFS回执",receipt.text.toString())}
val receiptRow=LinearLayout(this).apply{
gravity=Gravity.TOP
addView(resultScroll,LinearLayout.LayoutParams(0,-2,1f))
addView(copyReceipt,LinearLayout.LayoutParams(dp(56),dp(40)).also{it.marginStart=dp(8)})
}
box.addView(receiptRow,LinearLayout.LayoutParams(-1,-2).also{it.topMargin=dp(8)})

addPanelFooter(box,dp(8))
check(overlayRoot.isAttachedToWindow&&bottom_bar.parent===overlayRoot&&bottomBarBrand.parent===bottom_bar&&ball.parent===bottom_bar){"persistent overlay host is not attached"}
box.setPadding(box.paddingLeft,dp(8),box.paddingRight,dp(8))
ensureRobotState(true)
val sw=resources.displayMetrics.widthPixels
val panelWidth=resources.getDimensionPixelSize(R.dimen.panel_width)
val barCenter=bottomBarLp.x+bottom_bar.width/2
val targetX=if(barCenter<sw/2)dp(8) else (sw-panelWidth-dp(8)).coerceAtLeast(0)
attachPanelToOverlay(box,targetX,dp(24))
}catch(e:Exception){log("Error","showPanel："+e.message);runCatching{clearPanel();showBall()};toast("打开面板失败："+e.message)}finally{isRenderingMainPanel=false}
}

private var inputDialog:Dialog?=null
private fun releaseInputFocus(){
runCatching{inputDialog?.dismiss();inputDialog=null}
}
private fun toast(msg:String){handler.post{Toast.makeText(this,msg,Toast.LENGTH_SHORT).show()}}
private fun showCommandInputDialog(target:EditText){
if(inputDialog?.isShowing==true)return
val dialog=Dialog(this)
inputDialog=dialog
dialog.setOnDismissListener{inputDialog=null}
val box=LinearLayout(this).apply{
orientation=LinearLayout.VERTICAL
setPadding(dimen(R.dimen.directory_dialog_padding),dimen(R.dimen.directory_dialog_padding),dimen(R.dimen.directory_dialog_padding),dimen(R.dimen.directory_dialog_padding))
background=resourceBg(R.color.bridgefs_surface,R.dimen.dialog_corner_radius,R.color.bridgefs_border);elevation=dp(8).toFloat()
}
val title=TextView(this).apply{text="输入指令";textSize=resources.getDimension(R.dimen.dialog_title_text_size)/resources.displayMetrics.scaledDensity;setTextColor(resources.getColor(R.color.bridgefs_text_primary));setTypeface(null,1)}
box.addView(title,LinearLayout.LayoutParams(-1,-2).also{it.bottomMargin=dp(8)})
val edit=EditText(this).apply{
setText(target.text);setSelection(text.length);hint="输入 AI 指令..."
textSize=resources.getDimension(R.dimen.dialog_input_text_size)/resources.displayMetrics.scaledDensity
gravity=Gravity.TOP
setPadding(dp(10),dp(8),dp(10),dp(8))
background=bg("#F5F6F8",10,null)
minLines=4;maxLines=8
inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
}
box.addView(edit,LinearLayout.LayoutParams(-1,dimen(R.dimen.command_dialog_input_height)))
val actions=LinearLayout(this).apply{gravity=Gravity.END}
val cancel=mainButton("取消"){dialog.dismiss()}.apply{background=rippleBg(resourceBg(R.color.bridgefs_button_bg,R.dimen.dialog_button_corner_radius),R.color.bridgefs_ripple_orange)}
val confirm=mainButton("确定"){target.setText(edit.text.toString());target.setSelection(target.text.length);dialog.dismiss()}.apply{background=rippleBg(bg("#F2F3F5",12,null),R.color.bridgefs_ripple_orange)}
actions.addView(cancel,LinearLayout.LayoutParams(dimen(R.dimen.dialog_button_width),dimen(R.dimen.dialog_button_height)))
actions.addView(confirm,LinearLayout.LayoutParams(dimen(R.dimen.dialog_button_width),dimen(R.dimen.dialog_button_height)).also{it.marginStart=dp(8)})
box.addView(actions,LinearLayout.LayoutParams(-1,dimen(R.dimen.dialog_button_height)).also{it.topMargin=dp(8)})
dialog.setContentView(box)
dialog.window?.let{it.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);it.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)}
dialog.show()
dialog.window?.let{it.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);it.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);it.setLayout(resources.getDimensionPixelSize(R.dimen.panel_width),WindowManager.LayoutParams.WRAP_CONTENT)}
edit.requestFocus()
edit.post{(getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).showSoftInput(edit,InputMethodManager.SHOW_IMPLICIT)}
}

private fun clearPanel(){
val current=panel
panel=null
runCatching{
current?.let{container->
runCatching{(container.parent as? ViewGroup)?.removeView(container)}
runCatching{container.removeAllViews()}
}

}
log("UI","clearPanel")
}
private fun closePanel(){if(isClosingPanel)return;isClosingPanel=true;try{log("UI","closePanel");releaseInputFocus();clipboardCallback=null;commandInput=null;clearPanel();handler.removeCallbacksAndMessages(null);showBall();ensureRobotState(false)}catch(e:Exception){log("Error","closePanel："+e.message);toast("关闭面板失败："+e.message)}finally{isClosingPanel=false}}

private var orbTransitionOrigin:IntArray?=null
private fun addPanelFooter(box:LinearLayout,topGap:Int=0){
if(Looper.myLooper()!=Looper.getMainLooper()){handler.post{addPanelFooter(box,topGap)};return}
try{
val height=resources.getDimensionPixelSize(R.dimen.bridgefs_bottom_bar_height)
box.setPadding(box.paddingLeft,box.paddingTop,box.paddingRight,0)
if(::bottom_bar.isInitialized){
bottom_bar.visibility=View.INVISIBLE
bottomBarBrand.visibility=View.GONE
ball.visibility=View.INVISIBLE
}
val slot=View(this)
slot.isClickable=true
slot.isFocusable=false
slot.setOnTouchListener{_,event->panelFooterTouchHandler(event)}
box.addView(slot,LinearLayout.LayoutParams(-1,height).also{it.topMargin=topGap})
slot.post{runCatching{positionBottomBarAtSlot(box,slot)}.onFailure{log("Error","position footer bar："+it.message)}}
}catch(e:Exception){log("Error","addPanelFooter："+e.message);runCatching{ensureRobotState(true)}}
}
private var footerDownRawX=0f
private var footerDownRawY=0f
private var footerStartX=0
private var footerStartY=0
private var footerMoved=false

private fun panelFooterTouchHandler(e:MotionEvent):Boolean{
return try{
when(e.actionMasked){
MotionEvent.ACTION_DOWN->{
bottomBarMoveAnimator?.cancel();bottomBarMoveAnimator=null
footerDownRawX=e.rawX;footerDownRawY=e.rawY
footerStartX=panelLpRef.x;footerStartY=panelLpRef.y
footerMoved=false
true
}
MotionEvent.ACTION_MOVE->{
val dx=e.rawX-footerDownRawX;val dy=e.rawY-footerDownRawY
if(kotlin.math.abs(dx)>dp(5)||kotlin.math.abs(dy)>dp(5))footerMoved=true
if(footerMoved&&panel!=null){
val current=panel!!
panelLpRef.x=(footerStartX+dx).toInt().coerceIn(0,(resources.displayMetrics.widthPixels-current.width).coerceAtLeast(0))
panelLpRef.y=(footerStartY+dy).toInt().coerceIn(0,(resources.displayMetrics.heightPixels-current.height).coerceAtLeast(0))
updatePanelWindow(current)
}
true
}
MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{
if(footerMoved){
val current=panel
if(current!=null){
panelLpRef.x=panelLpRef.x.coerceIn(0,(resources.displayMetrics.widthPixels-current.width).coerceAtLeast(0))
panelLpRef.y=panelLpRef.y.coerceIn(0,(resources.displayMetrics.heightPixels-current.height).coerceAtLeast(0))
updatePanelWindow(current)
}
}
true
}
else->false
}
}catch(e:Exception){log("Error","panel footer touch："+e.message);true}
}

private fun positionBottomBarAtSlot(box:LinearLayout,slot:View){
if(panel!==box||!box.isAttachedToWindow||slot.parent!==box||!::overlayRoot.isInitialized)return
try{
ensureRobotState(true)
val slotLocation=IntArray(2);val hostLocation=IntArray(2);val boxLocation=IntArray(2)
slot.getLocationOnScreen(slotLocation);overlayRoot.getLocationOnScreen(hostLocation);box.getLocationOnScreen(boxLocation)
val p=(bottom_bar.layoutParams as? FrameLayout.LayoutParams)?:FrameLayout.LayoutParams(-2,resources.getDimensionPixelSize(R.dimen.bridgefs_bottom_bar_height))
p.width=WindowManager.LayoutParams.WRAP_CONTENT;p.height=resources.getDimensionPixelSize(R.dimen.bridgefs_bottom_bar_height)
p.gravity=Gravity.TOP or Gravity.LEFT
p.leftMargin=(boxLocation[0]-hostLocation[0]+box.width-bottom_bar.measuredWidth-dp(8)).coerceAtLeast(0)
p.topMargin=(slotLocation[1]-hostLocation[1]).coerceAtLeast(0)
p.rightMargin=0;p.bottomMargin=0
bottom_bar.layoutParams=p;bottom_bar.visibility=View.VISIBLE;ensureRobotState(true);ball.visibility=View.VISIBLE
bottom_bar.invalidate();ball.invalidate();updateBottomBarWindow()
orbTransitionOrigin=null
}catch(e:Exception){log("Error","position footer bar："+e.message);runCatching{ensureRobotState(true)}}
}

private fun animateBottomBarToPosition(targetX:Int,targetY:Int){
bottomBarMoveAnimator?.cancel()
bottomBarSnapAnimator?.cancel();bottomBarSnapAnimator=null
val startX=bottomBarLp.x;val startY=bottomBarLp.y
if(startX==targetX&&startY==targetY){bottomBarLp.x=targetX;bottomBarLp.y=targetY;updateBottomBarWindow();return}
bottomBarMoveAnimator=ValueAnimator.ofFloat(0f,1f).apply{
duration=200L
addUpdateListener{animator->
val f=animator.animatedValue as Float
bottomBarLp.x=(startX+(targetX-startX)*f).toInt()
bottomBarLp.y=(startY+(targetY-startY)*f).toInt()
updateBottomBarWindow()
}
start()
}
}
private fun updateBottomBarWindow(){
if(isUpdatingBottomBarLayout||!::overlayRoot.isInitialized||!overlayRoot.isAttachedToWindow)return
isUpdatingBottomBarLayout=true
try{wm.updateViewLayout(overlayRoot,bottomBarLp)}catch(e:Exception){log("Error","update bottom bar："+e.message)}finally{isUpdatingBottomBarLayout=false}
}
private fun updatePanelWindow(current:LinearLayout){
if(isUpdatingPanelLayout||panel!==current||!::overlayRoot.isInitialized||!overlayRoot.isAttachedToWindow)return
isUpdatingPanelLayout=true
try{wm.updateViewLayout(overlayRoot,bottomBarLp)}catch(e:Exception){log("Error","update panel："+e.message)}finally{isUpdatingPanelLayout=false}
}
private fun animateBottomBarToX(targetX:Int){
if(!::bottom_bar.isInitialized)return
bottomBarSnapAnimator?.cancel()
val startX=bottomBarLp.x
bottomBarLp.gravity=Gravity.TOP or Gravity.LEFT
if(startX==targetX){bottomBarLp.x=targetX;updateBottomBarWindow();return}
bottomBarSnapAnimator=ValueAnimator.ofInt(startX,targetX).apply{
duration=150L
addUpdateListener{animator->
bottomBarLp.gravity=Gravity.TOP or Gravity.LEFT
bottomBarLp.x=animator.animatedValue as Int
updateBottomBarWindow()
}
start()
}
}
private fun bottomBarTouchHandler(@Suppress("UNUSED_PARAMETER") view:View,e:MotionEvent):Boolean{
return try{
when(e.actionMasked){
MotionEvent.ACTION_DOWN->{
bottomBarMoveAnimator?.cancel();bottomBarMoveAnimator=null
ball.animate().scaleX(.95f).scaleY(.95f).setDuration(80L).start()
if(panel==null&&bottomBarEdgeHidden!=0){
val wasHidden=bottomBarEdgeHidden
bottomBarSnapAnimator?.cancel();bottomBarSnapAnimator=null
bottomBarEdgeHidden=0
val fullWidthX=if(wasHidden<0)0 else (resources.displayMetrics.widthPixels-bottom_bar.width).coerceAtLeast(0)
bottomBarRevealTargetX=fullWidthX
animateBottomBarToX(fullWidthX)
}
bottomBarDownRawX=e.rawX;bottomBarDownRawY=e.rawY
bottomBarStartX=if(panel!=null)panelLpRef.x else (bottomBarRevealTargetX?:bottomBarLp.x)
bottomBarStartY=if(panel!=null)panelLpRef.y else bottomBarLp.y
bottomBarDragInPanel=panel!=null;bottomBarMoved=false
true
}
MotionEvent.ACTION_MOVE->{
val dx=e.rawX-bottomBarDownRawX;val dy=e.rawY-bottomBarDownRawY
if(kotlin.math.abs(dx)>dp(5)||kotlin.math.abs(dy)>dp(5))bottomBarMoved=true
if(bottomBarMoved){
if(bottomBarDragInPanel){
val current=panel
if(current!=null){
panelLpRef.x=(bottomBarStartX+dx).toInt().coerceIn(0,(resources.displayMetrics.widthPixels-current.width).coerceAtLeast(0))
panelLpRef.y=(bottomBarStartY+dy).toInt().coerceIn(0,(resources.displayMetrics.heightPixels-current.height).coerceAtLeast(0))
updatePanelWindow(current)
}
}else{
bottomBarSnapAnimator?.cancel();bottomBarSnapAnimator=null;bottomBarRevealTargetX=null
bottomBarLp.x=(bottomBarStartX+dx).toInt().coerceIn(0,(resources.displayMetrics.widthPixels-bottom_bar.width).coerceAtLeast(0))
bottomBarLp.y=(bottomBarStartY+dy).toInt().coerceIn(0,(resources.displayMetrics.heightPixels-bottom_bar.height).coerceAtLeast(0))
updateBottomBarWindow()
}
}
true
}
MotionEvent.ACTION_UP->{
ball.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
if(!bottomBarMoved){if(panel==null)showPanel()else closePanel();return true}
if(bottomBarDragInPanel){
val current=panel
if(current!=null){
val maxX=(resources.displayMetrics.widthPixels-current.width).coerceAtLeast(0)
panelLpRef.x=panelLpRef.x.coerceIn(0,maxX)
panelLpRef.y=panelLpRef.y.coerceIn(0,(resources.displayMetrics.heightPixels-current.height).coerceAtLeast(0))
updatePanelWindow(current)
}
}else{
val sw=resources.displayMetrics.widthPixels
val x=bottomBarLp.x.coerceIn(0,(sw-bottom_bar.width).coerceAtLeast(0))
bottomBarLp.x=x
val snapDistance=dp(48)
val maxX=(sw-bottom_bar.width).coerceAtLeast(0)
when{
x<=snapDistance->{bottomBarEdgeHidden=-1;animateBottomBarToX(0)}
maxX-x<=snapDistance->{bottomBarEdgeHidden=1;animateBottomBarToX(maxX)}
else->{bottomBarEdgeHidden=0;bottomBarRevealTargetX=null;updateBottomBarWindow()}
}
}
true
}
MotionEvent.ACTION_CANCEL->{ball.animate().scaleX(1f).scaleY(1f).setDuration(100L).start();true}
else->false
}
}catch(e:Exception){log("Error","bottom bar touch："+e.message);runCatching{ensureRobotState(panel!=null)};false}
}

private var bottomBarDownRawX=0f
private var bottomBarDownRawY=0f
private var bottomBarStartX=0
private var bottomBarStartY=0
private var bottomBarDragInPanel=false
private var bottomBarMoved=false

private fun findReceipt(v:View):TextView?{if(v is TextView&&v.text.toString()=="执行结果会显示在这里")return v;if(v is ViewGroup)for(i in 0 until v.childCount){val r=findReceipt(v.getChildAt(i));if(r!=null)return r};return null}
private fun smallButton(label:String,onClick:()->Unit)=Button(this).apply{text=label;textSize=13f;setTextColor(resources.getColor(R.color.bridgefs_button_text));minWidth=0;minimumWidth=0;setPadding(0,0,0,0);background=rippleBg(bg("#F2F3F5",10,null),R.color.bridgefs_ripple_orange);setOnClickListener{onClick()}}
private fun mainButton(label:String,onClick:()->Unit)=Button(this).apply{text=label;textSize=13f;setTextColor(resources.getColor(R.color.bridgefs_button_text));minWidth=0;minimumWidth=0;minimumHeight=0;setPadding(0,0,0,0);background=rippleBg(bg("#F2F3F5",10,null),R.color.bridgefs_ripple_orange);setOnClickListener{onClick()}}
private fun showHelpDialog(){
try{
log("UI","showHelpDialog")
val message=BridgeCommandSpec.documentation
val box=LinearLayout(this).apply{
orientation=LinearLayout.VERTICAL
setPadding(dp(12),dp(8),dp(12),dp(12))
background=bg("#FFFFFF",16,null)
elevation=dp(8).toFloat()
}
val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
val back=smallButton("←"){showPanel()}
val title=TextView(this).apply{
text="A-BridgeFS 指令规范 " + BridgeCommandSpec.version
textSize=15f
setTypeface(null,1)
setTextColor(resources.getColor(R.color.bridgefs_text_primary))
gravity=Gravity.CENTER_VERTICAL
}
val copy=smallButton("复制"){copyText("A-BridgeFS 指令规范",message);toast("已复制指令规范")}.apply{
textSize=11f
setSingleLine(true)
maxLines=1
contentDescription="复制全部指令规范"
}
top.addView(back,LinearLayout.LayoutParams(dp(48),dp(40)))
top.addView(title,LinearLayout.LayoutParams(0,dp(40),1f).also{it.marginStart=dp(4)})
top.addView(copy,LinearLayout.LayoutParams(dp(56),dp(40)).also{it.marginStart=dp(6)})
box.addView(top,LinearLayout.LayoutParams(-1,dp(48)))
val manualText=TextView(this).apply{
text=message
textSize=12f
typeface=android.graphics.Typeface.MONOSPACE
setTextColor(resources.getColor(R.color.bridgefs_text_primary))
setLineSpacing(dp(3).toFloat(),1f)
setPadding(dp(8),dp(6),dp(8),dp(6))
}
val scroll=object:ScrollView(this){
override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
super.onMeasure(widthMeasureSpec,View.MeasureSpec.makeMeasureSpec(dimen(R.dimen.help_scroll_height),View.MeasureSpec.EXACTLY))
}
}.apply{
isFillViewport=false
isVerticalScrollBarEnabled=true
scrollBarSize=dp(2)
addView(manualText,ViewGroup.LayoutParams(-1,-2))
}
box.addView(scroll,LinearLayout.LayoutParams(-1,dimen(R.dimen.help_scroll_height)).apply{topMargin=dp(2)})
addPanelFooter(box)
box.setPadding(box.paddingLeft,box.paddingTop,box.paddingRight,0)
clearPanel()
panel=box
val sw=resources.displayMetrics.widthPixels
val panelWidth=resources.getDimensionPixelSize(R.dimen.panel_width)
val barCenter=bottomBarLp.x+bottom_bar.width/2
val targetX=if(barCenter<sw/2)dp(8) else (sw-panelWidth-dp(8)).coerceAtLeast(0)
attachPanelToOverlay(box,targetX,dp(24))
}catch(e:Exception){
log("Error","showHelpDialog："+e.message)
runCatching{clearPanel();showBall()}
toast("打开指令规范失败："+e.message)
}
}
private fun copyText(label:String,text:String){val cm=getSystemService(CLIPBOARD_SERVICE)as ClipboardManager;cm.setPrimaryClip(ClipData.newPlainText(label,text))}
private fun copyInstructions(){copyText("A-BridgeFS指令规范",BridgeCommandSpec.documentation);toast("已复制指令规范")}
private fun showBrowser(){try{browserCurrent=root;log("UI","showBrowser");renderBrowser()}catch(e:Exception){log("Error","showBrowser："+e.message);toast("打开浏览器失败："+e.message)}}
private var browserCurrent:File?=null
private fun renderBrowser(){
if(Looper.myLooper()!=Looper.getMainLooper()){
handler.post{renderBrowser()}
return
}
if(isRenderingBrowser)return
isRenderingBrowser=true
try{
clearPanel();log("UI","renderBrowser")
val current=browserCurrent?:root
val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(12));background=bg("#FFFFFF",16,null)}
val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
val back=smallButton("←"){if(current.absolutePath==root.absolutePath)showPanel()else{browserCurrent=current.parentFile?:root;renderBrowser()}}
val rootBtn=TextView(this).apply{text=current.name.ifBlank{current.absolutePath};contentDescription=current.absolutePath;textSize=13f;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),0,dp(4),0);setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END;setTextColor(resources.getColor(R.color.bridgefs_text_primary));setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_folder,0,0,0);compoundDrawablePadding=dp(6);background=RippleDrawable(ColorStateList.valueOf(resources.getColor(R.color.bridgefs_ripple_gray)),GradientDrawable().apply{setColor(resources.getColor(R.color.bridgefs_surface));cornerRadius=resources.getDimension(R.dimen.directory_row_corner_radius)},null);setOnClickListener{showRootList()};setOnLongClickListener{copyText("BridgeFS路径",current.absolutePath);toast("已复制路径");true}}
val copy=smallButton("复制"){copyText("BridgeFS路径",current.absolutePath);toast("已复制："+current.absolutePath)}.apply{textSize=11f;setSingleLine(true);maxLines=1;contentDescription="复制当前层"}
top.addView(back,LinearLayout.LayoutParams(dp(48),dp(40)));top.addView(rootBtn,LinearLayout.LayoutParams(0,dp(40),1f));top.addView(copy,LinearLayout.LayoutParams(dp(56),dp(40)).also{it.marginStart=dp(6)})
box.addView(top,LinearLayout.LayoutParams(-1,dp(48)))
val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
val children=current.listFiles()?.sortedWith(compareBy<File>{!it.isDirectory}.thenBy{it.name.lowercase(Locale.getDefault())}).orEmpty()
val first=children.take(50)
first.forEach{f->val row=TextView(this).apply{text=f.name;textSize=14f;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(4),dp(8),dp(4));setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END;if(f.isDirectory){setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_folder,0,0,0);compoundDrawablePadding=dp(6)};setTextColor(resources.getColor(R.color.bridgefs_text_primary));background=rippleBg(resourceBg(R.color.bridgefs_surface,R.dimen.directory_row_corner_radius),R.color.bridgefs_ripple_gray);setOnClickListener{if(f.isDirectory){browserCurrent=f;renderBrowser()}else{copyText("BridgeFS路径",f.relativeTo(root).path);toast("已复制相对路径")}}};list.addView(row,LinearLayout.LayoutParams(-1,dp(48)))}
if(children.size>50)list.addView(TextView(this).apply{text="…等 "+(children.size-50)+" 项";textSize=13f;setTextColor(Color.GRAY);setPadding(dp(8),dp(8),dp(8),dp(8))})
box.addView(ScrollView(this).apply{isVerticalScrollBarEnabled=true;scrollBarSize=dp(2);addView(list)},LinearLayout.LayoutParams(-1,dimen(R.dimen.directory_list_max_height)))
addPanelFooter(box);panel=box
val sw=resources.displayMetrics.widthPixels
val panelWidth=resources.getDimensionPixelSize(R.dimen.panel_width)
val barCenter=bottomBarLp.x+bottom_bar.width/2
val targetX=if(barCenter<sw/2)dp(8) else (sw-panelWidth-dp(8)).coerceAtLeast(0)
attachPanelToOverlay(box,targetX,dp(24))
}catch(e:Exception){
log("Error","renderBrowser："+e.message)
runCatching{clearPanel();showBall()}
toast("打开目录失败："+e.message)
}finally{
isRenderingBrowser=false
}
}
private fun browserListing(current:File):String{val entries=current.listFiles()?.sortedWith(compareBy<File>{!it.isDirectory}.thenBy{it.name.lowercase(Locale.getDefault())}).orEmpty();val first=entries.take(50);val files=entries.count{!it.isDirectory};val dirs=entries.count{it.isDirectory};return current.relativeToOrSelf(root).path+"/\n含 "+files+" 个文件、"+dirs+" 个文件夹：\n"+first.joinToString("\n"){f->"  "+(if(f.isDirectory)"文件夹" else "文件")+" "+f.name}+(if(entries.size>50)"\n…等 "+(entries.size-50)+" 项" else "")}
private fun isProtectedWorkspace(path:String):Boolean{
val candidate=runCatching{File(path).canonicalPath.trimEnd('/')}.getOrElse{File(path).absolutePath.trimEnd('/')}
    .replace('\\','/').lowercase(Locale.ROOT)
val storageRoot=runCatching{Environment.getExternalStorageDirectory().canonicalPath.trimEnd('/')}.getOrElse{Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')}
    .replace('\\','/').lowercase(Locale.ROOT)
if(candidate==storageRoot)return true
val segments=candidate.split('/').filter{it.isNotEmpty()}
return segments.any{it=="android"}||listOf("/android/data","/android/obb","/android/media").any{candidate.contains(it)}
}
private fun activateWorkspace(path:String,onConfirm:()->Unit){
if(isProtectedWorkspace(path)){toast("此目录属于系统受保护区域，无法作为工作区");return}
if(File(path).name.equals("Download",true)){
val prefs=getSharedPreferences("bridgefs",0)
if(prefs.getBoolean("download_workspace_confirmed",false))onConfirm()
else showDownloadWorkspaceConfirm(path,onConfirm)
}else onConfirm()
}
private fun showDownloadWorkspaceConfirm(path:String,onConfirm:()->Unit){
try{
val box=LinearLayout(this).apply{
orientation=LinearLayout.VERTICAL
setPadding(dp(12),dp(8),dp(12),0)
background=bg("#FFFFFF",16,null)
elevation=dp(8).toFloat()
}
val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
val back=smallButton("←"){showRootList()}
val title=TextView(this).apply{
text="确认工作区"
textSize=15f
setTypeface(null,1)
setTextColor(resources.getColor(R.color.bridgefs_text_primary))
gravity=Gravity.CENTER_VERTICAL
}
top.addView(back,LinearLayout.LayoutParams(dp(48),dp(40)))
top.addView(title,LinearLayout.LayoutParams(0,dp(40),1f).also{it.marginStart=dp(4)})
box.addView(top,LinearLayout.LayoutParams(-1,dp(48)))
val message=TextView(this).apply{
text="下载目录可能同时包含系统下载文件。\\n\\n确认将：\\n${path}\\n\\n设为 BridgeFS 当前工作区？"
textSize=13f
setTextColor(resources.getColor(R.color.bridgefs_text_primary))
setPadding(dp(8),dp(12),dp(8),dp(8))
}
box.addView(message,LinearLayout.LayoutParams(-1,0,1f))
val actions=LinearLayout(this).apply{gravity=Gravity.END or Gravity.CENTER_VERTICAL}
val cancel=smallButton("取消"){showRootList()}
val confirm=mainButton("确认"){
getSharedPreferences("bridgefs",0).edit().putBoolean("download_workspace_confirmed",true).apply()
onConfirm()
}
actions.addView(cancel,LinearLayout.LayoutParams(dp(64),dp(40)))
actions.addView(confirm,LinearLayout.LayoutParams(dp(64),dp(40)).also{it.marginStart=dp(8)})
box.addView(actions,LinearLayout.LayoutParams(-1,dp(48)))
addPanelFooter(box)
clearPanel()
panel=box
val sw=resources.displayMetrics.widthPixels
val panelWidth=resources.getDimensionPixelSize(R.dimen.panel_width)
val barCenter=bottomBarLp.x+bottom_bar.width/2
val targetX=if(barCenter<sw/2)dp(8) else (sw-panelWidth-dp(8)).coerceAtLeast(0)
attachPanelToOverlay(box,targetX,dp(24))
}catch(e:Exception){
log("Error","showDownloadWorkspaceConfirm："+e.message)
runCatching{showRootList()}
}
}
private fun showRootList(){
try{
clearPanel();log("UI","showRootList")
val prefs=getSharedPreferences("bridgefs",0)
val roots=((prefs.getStringSet("root_paths",emptySet<String>())?:emptySet<String>())+listOfNotNull(prefs.getString("root_path",null))).distinct().toList()
val box=LinearLayout(this).apply{
orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(12))
background=bg("#FFFFFF",16,"#E0E0E0");elevation=dp(8).toFloat()
}
val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
val back=smallButton("←"){try{clearPanel();renderBrowser()}catch(e:Exception){log("Error","返回目录浏览失败："+e.message);toast("返回目录浏览失败："+e.message)}}
val title=TextView(this).apply{text="选择根目录";textSize=15f;setTypeface(null,1);setTextColor(resources.getColor(R.color.bridgefs_text_primary))}
top.addView(back,LinearLayout.LayoutParams(dp(48),dp(40)))
top.addView(title,LinearLayout.LayoutParams(0,dp(40),1f).also{it.marginStart=dp(4)})
box.addView(top,LinearLayout.LayoutParams(-1,dp(48)))
val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
roots.forEach{path->
val row=LinearLayout(this).apply{
orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
setPadding(dimen(R.dimen.directory_row_padding),0,dimen(R.dimen.directory_row_padding),0)
background=rippleBg(bg("#FFFFFF",8,null),R.color.bridgefs_ripple_gray)
val icon=ImageView(this@FileBridgeService).apply{setImageResource(R.drawable.ic_folder)}
addView(icon,LinearLayout.LayoutParams(dimen(R.dimen.directory_icon_size),dimen(R.dimen.directory_icon_size)).also{it.marginEnd=dimen(R.dimen.directory_icon_gap)})
val name=TextView(this@FileBridgeService).apply{
text=File(path).name.ifBlank{path};textSize=resources.getDimension(R.dimen.directory_item_text_size)/resources.displayMetrics.scaledDensity
setTextColor(resources.getColor(R.color.bridgefs_text_primary));setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END;gravity=Gravity.CENTER_VERTICAL
}
addView(name,LinearLayout.LayoutParams(0,-1,1f))
setOnClickListener{
activateWorkspace(path){
root=File(path)
getSharedPreferences("bridgefs",0).edit().putString("root_path",root.absolutePath).apply()
clearPanel();browserCurrent=root;renderBrowser()
}
}
}
list.addView(row,LinearLayout.LayoutParams(-1,dimen(R.dimen.directory_row_height)))
}
if(roots.isEmpty())list.addView(TextView(this).apply{text="暂无已保存的根目录";textSize=13f;setTextColor(resources.getColor(R.color.bridgefs_text_secondary));gravity=Gravity.CENTER_VERTICAL;setPadding(dimen(R.dimen.directory_row_padding),0,dimen(R.dimen.directory_row_padding),0)},LinearLayout.LayoutParams(-1,dimen(R.dimen.directory_row_height)))
val rowHeight=dimen(R.dimen.directory_row_height)
val listHeight=dimen(R.dimen.directory_list_max_height)
val boundedList=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
val scroller=ScrollView(this).apply{isVerticalScrollBarEnabled=true;scrollBarSize=dp(2);addView(list,ViewGroup.LayoutParams(-1,-2))}
boundedList.addView(scroller,LinearLayout.LayoutParams(-1,0,1f))
box.addView(boundedList,LinearLayout.LayoutParams(-1,listHeight))
addPanelFooter(box);panel=box
val panelWidth=resources.getDimensionPixelSize(R.dimen.panel_width)
val screenWidth=resources.displayMetrics.widthPixels
val barCenter=bottomBarLp.x+bottom_bar.measuredWidth/2
val targetX=if(barCenter<screenWidth/2)dp(8) else (screenWidth-panelWidth-dp(8)).coerceAtLeast(0)
attachPanelToOverlay(box,targetX,dp(24))
}catch(e:Exception){log("Error","showRootList："+e.message);runCatching{clearPanel();showBall()};toast("打开根目录列表失败："+e.message)}
}
private fun runLogFile():File{val dir=File("/sdcard/BridgeFS/logs");return if(dir.exists()||dir.mkdirs())File(dir,"run.log")else File(getExternalFilesDir(null),"logs").apply{mkdirs()}.resolve("run.log")}
private fun log(module:String,message:String){if(!getSharedPreferences("bridgefs",0).getBoolean("run_log_enabled",true))return;val line=SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(Date())+" ["+module+"] "+message.replace("\n","\\n")+"\n";runCatching{val f=runLogFile();val old=if(f.isFile)f.readLines().takeLast(499)else emptyList();f.parentFile?.mkdirs();f.writeText((old+line).joinToString(""))}.onFailure{android.util.Log.e("BridgeFS","run log failed",it)}}
private fun rippleBg(content:GradientDrawable,colorRes:Int)=RippleDrawable(ColorStateList.valueOf(resources.getColor(colorRes)),content,null)
private fun resourceBg(fillRes:Int,radiusRes:Int,strokeRes:Int?=null)=GradientDrawable().apply{setColor(resources.getColor(fillRes));cornerRadius=resources.getDimension(radiusRes);if(strokeRes!=null)setStroke(dp(1),resources.getColor(strokeRes))}
private fun bg(fill:String,r:Int,stroke:String?)=GradientDrawable().apply{setColor(Color.parseColor(fill));cornerRadius=dp(r).toFloat();if(stroke!=null)setStroke(dp(1),Color.parseColor(stroke))}
private fun dimen(id:Int)=resources.getDimensionPixelSize(id)
private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
override fun onBind(i:Intent?)=null
override fun onDestroy(){clipboardCallback=null;commandInput=null;handler.removeCallbacksAndMessages(null);commandExecutor.shutdownNow();log("Service","onDestroy");clearPanel();if(::overlayRoot.isInitialized&&overlayRoot.isAttachedToWindow)runCatching{wm.removeView(overlayRoot)};running=false;super.onDestroy()}
}

class ClipboardReaderActivity:Activity(){
private var consumed=false
override fun onCreate(savedInstanceState:Bundle?){
super.onCreate(savedInstanceState)
overridePendingTransition(0,0)
window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
window.statusBarColor=Color.TRANSPARENT
window.navigationBarColor=Color.TRANSPARENT
window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
window.setLayout(1,1)
window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
}
override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(!hasFocus||consumed)return;consumed=true;val text=runCatching{val cm=getSystemService(CLIPBOARD_SERVICE)as android.content.ClipboardManager;cm.primaryClip?.let{if(it.itemCount>0)it.getItemAt(0).coerceToText(this).toString()else""}?:""}.getOrDefault("");FileBridgeService.clipboardCallback?.invoke(text);overridePendingTransition(0,0);finish()}
override fun onDestroy(){overridePendingTransition(0,0);super.onDestroy()}
}