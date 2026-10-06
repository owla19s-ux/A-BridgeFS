package com.abridgefs.app

import android.app.AlertDialog
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*

internal fun ApsActivity.projectPage() {
    val project = currentProject
    val conversation = project?.activeConversation()
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(10))
    }

    content.addView(title(project?.name ?: "默认项目"))
    content.addView(value("项目工作面 · " + (project?.githubAddress?.repository ?: "GitHub 未连接")))

    content.addView(card().apply {
        addView(label("项目信息"))
        addView(value("Local：" + (project?.localAddress ?: "未设置")))
        addView(value("GitHub：" + (project?.githubAddress?.repository ?: "未设置")))
        val apiName = project?.defaultMemberId
            ?.let { id -> project.aiMembers.firstOrNull { it.id == id }?.apiProfileId }
            ?.let { id -> apiProfilesStore.find(id)?.name }
            ?: "未绑定"
        addView(value("Default AI：" + apiName))
    })

    content.addView(section("项目配置", projectConfigOpen) {
        projectConfigOpen = !projectConfigOpen
        render()
    }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

    if (projectConfigOpen && project != null) {
        content.addView(card().apply {
            addView(label("项目名称"))
            addView(EditText(this@projectPage).apply {
                hint = "项目名称"
                maxLines = 1
                setText(project.name)
                tag = "projectNameInput"
            })
            addView(primaryButton("保存项目名称") {
                val nameInput = (getChildAt(1) as? EditText) ?: return@primaryButton
                ProjectConfigurationService(this@projectPage).rename(project.id, nameInput.text.toString())
                toast("项目名称已保存")
                render()
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            addView(secondaryButton("配置 Project Address") { showProjectAddressDialog(project) },
                LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            addView(label("Default API"))
            val member = project.defaultMemberId?.let { id -> project.aiMembers.firstOrNull { it.id == id } }
            val profile = member?.apiProfileId?.let { apiProfilesStore.find(it) }
            addView(value(profile?.name ?: "未绑定"))
            addView(secondaryButton("选择 API Profile") { chooseProjectApi(project) },
                LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(5) })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
    }

    content.addView(section("待处理任务", true) {}, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
    if (project != null) {
        content.addView(card().apply {
            val activeTaskId = project.taskState.activeTaskId
            addView(value(
                "当前施工： " + (
                    project.tasks.firstOrNull { it.id == activeTaskId }?.title
                        ?: "无"
                ) + " · " + project.taskState.status
            ))
            if (project.tasks.isEmpty()) {
                addView(value("当前没有待处理任务。"))
            } else {
                project.tasks.forEach { task ->
                    val row = LinearLayout(this@projectPage).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    val check = CheckBox(this@projectPage).apply {
                        isChecked = task.completed
                        isEnabled = task.id != activeTaskId || task.status != "RUNNING"
                        setOnCheckedChangeListener { _, checked ->
                            runCatching {
                                ProjectTaskStateService(this@projectPage).setCompleted(project.id, task.id, checked)
                            }.onFailure {
                                check.setOnCheckedChangeListener(null)
                                check.isChecked = task.completed
                                toast(it.message ?: "任务状态更新失败")
                                return@setOnCheckedChangeListener
                            }
                            val mention = "@" + task.title.trim()
                            if (checked) pendingTaskMentions += mention else pendingTaskMentions.remove(mention)
                            projects = projectStore.load()
                            currentProject = projects.firstOrNull { it.id == project.id }
                            syncProjectInput()
                            render()
                        }
                    }
                    row.addView(check, LinearLayout.LayoutParams(dp(48), dp(48)))
                    row.addView(TextView(this@projectPage).apply {
                        text = task.title + "  [" + task.status + "]"
                        textSize = 13f
                        typeface = if (task.completed) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
                        setTextColor(c(R.color.bridgefs_text_primary))
                    }, LinearLayout.LayoutParams(0, dp(48), 1f))
                    addView(row)

                    if (!task.completed) {
                        val actions = LinearLayout(this@projectPage).apply {
                            orientation = LinearLayout.HORIZONTAL
                        }
                        if (activeTaskId != task.id || project.taskState.status != "RUNNING") {
                            addView(primaryButton("开始施工") {
                                ProjectTaskStateService(this@projectPage).start(project.id, task.id)
                                projects = projectStore.load()
                                currentProject = projects.firstOrNull { it.id == project.id }
                                render()
                            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(4) })
                        } else {
                            addView(secondaryButton("暂停") {
                                ProjectTaskStateService(this@projectPage).pause(project.id)
                                projects = projectStore.load()
                                currentProject = projects.firstOrNull { it.id == project.id }
                                render()
                            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(4) })
                        }
                        addView(secondaryButton("完成") {
                            ProjectTaskStateService(this@projectPage).complete(project.id)
                            projects = projectStore.load()
                            currentProject = projects.firstOrNull { it.id == project.id }
                            render()
                        }, LinearLayout.LayoutParams(0, dp(40), 1f))
                        addView(actions, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
                    }
                }
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
    }

    content.addView(section("项目内历史对话", true) {}, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
    content.addView(card().apply {
        if (project == null || project.conversations.isEmpty()) {
            addView(value("当前没有项目对话。"))
        } else {
            project.conversations.forEach { item ->
                val last = item.messages.lastOrNull()?.content?.replace("\n", " ")?.take(42) ?: "暂无消息"
                addView(secondaryButton(
                    if (item.id == conversation?.id) "●  " + item.name + "  ·  " + last else item.name + "  ·  " + last
                ) {
                    project.activeConversationId = item.id
                    projectStore.save(projects)
                    render()
                }, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(5) })
            }
        }
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

    content.addView(section("协助", false) {
        requestProjectAssistance()
    }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

    host.addView(ScrollView(this).apply {
        isFillViewport = true
        addView(content)
    }, LinearLayout.LayoutParams(-1, 0, 1f))

    if (displayOpen) inputBar("输入工作目标……")
}

private fun ApsActivity.showProjectAddressDialog(project: BridgeProject) {
    val local = EditText(this).apply { hint = "Local Project Address"; setText(project.localAddress.orEmpty()) }
    val github = EditText(this).apply { hint = "GitHub Repository（owner/repo）"; setText(project.githubAddress.repository.orEmpty()) }
    val branch = EditText(this).apply { hint = "GitHub Branch"; setText(project.githubAddress.branch.orEmpty()) }
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(4), dp(20), 0)
        addView(local); addView(github); addView(branch)
    }
    AlertDialog.Builder(this).setTitle("Project Address").setView(box)
        .setPositiveButton("保存") { _, _ ->
            ProjectConfigurationService(this@showProjectAddressDialog).updateAddress(
                project.id,
                local.text.toString(),
                github.text.toString(),
                branch.text.toString()
            )
            render()
        }.setNegativeButton("取消", null).show()
}

private fun ApsActivity.chooseProjectApi(project: BridgeProject) {
    val profiles = apiProfilesStore.list()
    if (profiles.isEmpty()) {
        toast("暂无 API Profile，请先在设置 → 连接 → API Profiles 配置")
        return
    }
    val currentId = project.defaultMemberId?.let { id -> project.aiMembers.firstOrNull { it.id == id }?.apiProfileId }
    AlertDialog.Builder(this)
        .setTitle("选择 Default API")
        .setSingleChoiceItems(profiles.map { it.name }.toTypedArray(), profiles.indexOfFirst { it.id == currentId }) { dialog, which ->
            val selected = profiles[which]
            ProjectConfigurationService(this@chooseProjectApi).bindDefaultApi(project.id, selected)
            dialog.dismiss()
            render()
        }.setNegativeButton("取消", null).show()
}