package com.abridgefs.app

import android.content.Context
import android.graphics.Typeface
import android.widget.*
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

internal fun ApsActivity.projectPage() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
        }
        val project = currentProject
        val conversation = project?.activeConversation()
        content.addView(title(project?.name ?: "默认项目"))
        content.addView(TextView(this).apply {
            text = "Project Address\nLocal：${project?.localAddress ?: "未设置"}\nGitHub：${project?.githubAddress?.repository ?: "未设置"}"
            textSize = 12f
            setTextColor(c(R.color.bridgefs_text_secondary))
            setPadding(0, dp(3), 0, dp(6))
        })
        content.addView(TextView(this).apply {
            text = if (displayOpen) "↓  收起内容区" else "↑  展开内容区"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(c(R.color.bridgefs_text_secondary))
            background = round(c(R.color.bridgefs_input_surface), dp(10))
            setPadding(0, dp(9), 0, dp(9))
            setOnClickListener { displayOpen = !displayOpen; render() }
        }, LinearLayout.LayoutParams(-1, dp(38)).apply { bottomMargin = dp(6) })
        content.addView(section("项目配置", projectConfigOpen) {
            projectConfigOpen = !projectConfigOpen
            render()
        })
        if (projectConfigOpen) {
            content.addView(card().apply {
                addView(label("项目名称"))
                addView(EditText(this).apply {
                    hint = "默认项目"
                    maxLines = 1
                })
                addView(label("Project Address"))
                addView(value("Local Project Address：${project?.localAddress ?: "未设置"}"))
                addView(value("GitHub Project Address：${project?.githubAddress?.repository ?: "未设置"}"))
                addView(Button(this).apply {
                    text = "配置 Project Address"
                    setOnClickListener {
                        val p = currentProject ?: return@setOnClickListener
                        val local = EditText(this@projectPage).apply { hint = "Local Project Address"; setText(p.localAddress.orEmpty()) }
                        val github = EditText(this@projectPage).apply { hint = "GitHub Repository（owner/repo）"; setText(p.githubAddress.repository.orEmpty()) }
                        val branch = EditText(this@projectPage).apply { hint = "GitHub Branch"; setText(p.githubAddress.branch.orEmpty()) }
                        val box = LinearLayout(this@projectPage).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(20), dp(4), dp(20), 0)
                            addView(local); addView(github); addView(branch)
                        }
                        AlertDialog.Builder(this@projectPage).setTitle("Project Address").setView(box)
                            .setPositiveButton("保存") { _, _ ->
                                p.localAddress = local.text.toString().trim().ifBlank { null }
                                p.githubAddress.repository = github.text.toString().trim().ifBlank { null }
                                p.githubAddress.branch = branch.text.toString().trim().ifBlank { null }
                                projectStore.save(projects); render()
                            }.setNegativeButton("取消", null).show()
                    }
                })
                addView(label("API"))
                val member = project?.defaultMemberId?.let { id -> project.aiMembers.firstOrNull { it.id == id } }
                val profile = member?.apiProfileId?.let { ApiProfileStore(this@projectPage).find(it) }
                addView(value("Default API：${profile?.name ?: "未绑定"}"))
                addView(label("项目级配置仅在这里维护"))
                addView(Button(this).apply {
                    text = "选择 API Profile"
                    setOnClickListener {
                        val p = currentProject ?: return@setOnClickListener
                        val profiles = apiProfilesStore.list()
                        if (profiles.isEmpty()) {
                            toast("暂无 API Profile，请先在设置 → 连接 → API Profiles 配置")
                            return@setOnClickListener
                        }
                        val names = profiles.map { it.name }.toTypedArray()
                        val currentId = p.defaultMemberId?.let { id -> p.aiMembers.firstOrNull { it.id == id }?.apiProfileId }
                        val checked = profiles.indexOfFirst { it.id == currentId }
                        AlertDialog.Builder(this@projectPage)
                            .setTitle("选择 Default API")
                            .setSingleChoiceItems(names, checked) { dialog, which ->
                                val profile = profiles[which]
                                val member = p.defaultMemberId?.let { id -> p.aiMembers.firstOrNull { it.id == id } }
                                    ?: BridgeAiMember(java.util.UUID.randomUUID().toString(), profile.name, profile.id).also {
                                        p.aiMembers += it
                                        p.defaultMemberId = it.id
                                    }
                                member.name = profile.name
                                member.apiProfileId = profile.id
                                p.activeConversation().apiId = profile.id
                                projectStore.save(projects)
                                dialog.dismiss()
                                render()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                })
            })
        }
        if (displayOpen) content.addView(card().apply {
            addView(label("待处理任务"))
            listOf("UI 输入框问题", "构建问题", "签名冲突").forEach { task ->
                addView(CheckBox(this).apply {
                    text = task
                    textSize = 13f
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) {
                            pendingTaskMentions.add("@" + task)
                            syncProjectInput()
                            toast("@" + task + " 已加入当前输入目标")
                        } else {
                            pendingTaskMentions.remove("@" + task)
                            syncProjectInput()
                        }
                    }
                })
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        if (displayOpen) content.addView(section("项目主要对话", projectChatOpen) {
            projectChatOpen = !projectChatOpen
            render()
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        if (displayOpen && projectChatOpen) {
            content.addView(card().apply {
                if (conversation?.messages.isNullOrEmpty()) {
                    addView(value("当前还没有项目对话。"))
                } else {
                    conversation?.messages?.takeLast(8)?.forEach { message ->
                        addView(messageBubble(if (message.role == "user") "你" else "AI", message.content))
                    }
                }
            })
        }
        if (displayOpen) {
            content.addView(Button(this).apply {
                text = "Request AI Assistance"
                setOnClickListener { toast("协助链将在项目对话闭环后接入") }
            }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
        }
        host.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        inputBar("输入工作目标……")
    }
