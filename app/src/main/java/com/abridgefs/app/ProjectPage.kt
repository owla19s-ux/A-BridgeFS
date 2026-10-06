package com.abridgefs.app

import android.app.AlertDialog
import android.widget.*
import android.view.*

internal fun ApsActivity.projectPage() {
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(10))
    }
    val project = currentProject
    val conversation = project?.activeConversation()

    content.addView(title(project?.name ?: "默认项目"))
    content.addView(value("项目工作区 · " + (project?.githubAddress?.repository ?: "GitHub 未连接")))

    content.addView(card().apply {
        addView(label("项目状态"))
        addView(value("Local：" + (project?.localAddress ?: "未设置")))
        addView(value("GitHub：" + (project?.githubAddress?.repository ?: "未设置")))
        val apiName = project?.defaultMemberId
            ?.let { id -> project.aiMembers.firstOrNull { it.id == id }?.apiProfileId }
            ?.let { id -> apiProfilesStore.find(id)?.name }
            ?: "未绑定"
        addView(value("API：" + apiName))
        addView(secondaryButton("配置 Project Address") {
            val p = currentProject ?: return@secondaryButton
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
                    projectStore.save(projects)
                    render()
                }.setNegativeButton("取消", null).show()
        })
    })

    content.addView(section("项目配置", projectConfigOpen) {
        projectConfigOpen = !projectConfigOpen
        render()
    }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

    if (projectConfigOpen) {
        content.addView(card().apply {
            addView(label("项目名称"))
            val nameInput = EditText(this@projectPage).apply {
                hint = "默认项目"
                maxLines = 1
                setText(project?.name.orEmpty())
            }
            addView(nameInput)
            addView(primaryButton("保存项目名称") {
                val p = currentProject ?: return@primaryButton
                p.name = nameInput.text.toString().trim().ifBlank { "默认项目" }
                projectStore.save(projects)
                toast("项目名称已保存")
                render()
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })

            addView(label("Default API"))
            val member = project?.defaultMemberId?.let { id -> project.aiMembers.firstOrNull { it.id == id } }
            val profile = member?.apiProfileId?.let { ApiProfileStore(this@projectPage).find(it) }
            addView(value(profile?.name ?: "未绑定"))
            addView(secondaryButton("选择 API Profile") {
                val p = currentProject ?: return@secondaryButton
                val profiles = apiProfilesStore.list()
                if (profiles.isEmpty()) {
                    toast("暂无 API Profile，请先在设置 → 连接 → API Profiles 配置")
                    return@secondaryButton
                }
                val names = profiles.map { it.name }.toTypedArray()
                val currentId = p.defaultMemberId?.let { id -> p.aiMembers.firstOrNull { it.id == id }?.apiProfileId }
                val checked = profiles.indexOfFirst { it.id == currentId }
                AlertDialog.Builder(this@projectPage)
                    .setTitle("选择 Default API")
                    .setSingleChoiceItems(names, checked) { dialog, which ->
                        val selected = profiles[which]
                        val aiMember = p.defaultMemberId?.let { id -> p.aiMembers.firstOrNull { it.id == id } }
                            ?: BridgeAiMember(java.util.UUID.randomUUID().toString(), selected.name, selected.id).also {
                                p.aiMembers += it
                                p.defaultMemberId = it.id
                            }
                        aiMember.name = selected.name
                        aiMember.apiProfileId = selected.id
                        p.activeConversation().apiId = selected.id
                        projectStore.save(projects)
                        dialog.dismiss()
                        render()
                    }.setNegativeButton("取消", null).show()
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
    }

    content.addView(section("工作区内容", displayOpen && projectChatOpen) {
        if (!displayOpen) displayOpen = true
        projectChatOpen = !projectChatOpen
        render()
    }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

    if (displayOpen && projectChatOpen) {
        content.addView(card().apply {
            addView(label("Task"))
            addView(value("当前版本尚未接入正式 Task 数据源。"))
            addView(label("Conversation"))
            if (conversation?.messages.isNullOrEmpty()) {
                addView(value("当前还没有项目对话。"))
            } else {
                conversation?.messages?.takeLast(6)?.forEach { message ->
                    addView(messageBubble(
                        if (message.role == "user") "你" else if (message.role == "assistant") message.apiName ?: "AI" else "系统",
                        message.content
                    ))
                }
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
    }

    content.addView(TextView(this).apply {
        text = if (displayOpen) "⌃  收起工作区内容" else "⌄  展开工作区内容"
        gravity = Gravity.CENTER
        textSize = 12f
        setTextColor(c(R.color.bridgefs_text_secondary))
        setPadding(0, dp(10), 0, dp(6))
        setOnClickListener { displayOpen = !displayOpen; render() }
    })

    if (displayOpen) {
        content.addView(primaryButton("Request AI Assistance") { requestProjectAssistance() },
            LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(2) })
    }

    host.addView(ScrollView(this).apply {
        isFillViewport = true
        addView(content)
    }, LinearLayout.LayoutParams(-1, 0, 1f))
    inputBar("输入工作目标……")
}