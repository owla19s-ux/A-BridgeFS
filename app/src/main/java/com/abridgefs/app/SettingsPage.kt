package com.abridgefs.app

import android.view.*
import android.widget.*

internal fun ApsActivity.settingsPage() {
    val activity = this
    val content = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(activity.dp(14), activity.dp(10), activity.dp(14), activity.dp(14))
    }
    content.addView(activity.title("设置"))

    val sections = listOf("连接", "权限", "文件", "执行", "外观", "通知", "日志", "系统")
    sections.forEach { name ->
        val open = activity.settingOpen.contains(name)

        content.addView(
            activity.section(name, open) {
                if (open) activity.settingOpen.remove(name) else activity.settingOpen.add(name)
                activity.render()
            },
            LinearLayout.LayoutParams(-1, activity.dp(52)).apply {
                bottomMargin = activity.dp(4)
            }
        )

        if (open) {
            val sectionCard = activity.card().apply {
                when (name) {
                    "连接" -> {
                        addView(activity.label("全局连接"))
                        addView(activity.value("连接资源集中管理；具体权限归属到对应模块。"))

                        addView(activity.settingSwitch(
                            "API 全局访问",
                            AccessPolicy.isApiEnabled(activity)
                        ) { enabled ->
                            AccessPolicy.setApiEnabled(activity, enabled)
                        })
                        addView(activity.settingSwitch(
                            "GitHub 全局访问",
                            AccessPolicy.isGithubEnabled(activity)
                        ) { enabled ->
                            AccessPolicy.setGithubEnabled(activity, enabled)
                        })
                        addView(Button(activity).apply {
                            text = "API Profiles"
                            setOnClickListener {
                                activity.startActivity(android.content.Intent(activity, ApiSettingsActivity::class.java))
                            }
                        }, LinearLayout.LayoutParams(-1, activity.dp(44)).apply {
                            topMargin = activity.dp(6)
                        })
                        addView(Button(activity).apply {
                            text = "GitHub 连接"
                            setOnClickListener {
                                activity.startActivity(android.content.Intent(activity, GitHubActivity::class.java))
                            }
                        }, LinearLayout.LayoutParams(-1, activity.dp(44)).apply {
                            topMargin = activity.dp(6)
                        })
                    }

                    "权限" -> {
                        addView(activity.label("APS 系统级权限"))
                        addView(activity.value("权限开关直接写入 APS 的持久化访问策略。具体 Project 施工权限仍由 Project 与执行权限共同决定。"))
                        addView(activity.settingSwitch(
                            "API 访问",
                            AccessPolicy.isApiEnabled(activity)
                        ) { AccessPolicy.setApiEnabled(activity, it) })
                        addView(activity.settingSwitch(
                            "GitHub 访问",
                            AccessPolicy.isGithubEnabled(activity)
                        ) { AccessPolicy.setGithubEnabled(activity, it) })
                        addView(Button(activity).apply {
                            text = "执行与文件权限"
                            setOnClickListener {
                                activity.startActivity(android.content.Intent(activity, SettingsCategoryActivity::class.java)
                                    .putExtra("category", "执行与权限"))
                            }
                        })
                    }

                    else -> {
                        addView(activity.label("系统级 $name"))
                        addView(activity.value("该分类已预留，具体设置将在对应功能接线时加入。"))
                    }
                }
            }
            content.addView(
                sectionCard,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = activity.dp(6)
                }
            )
        }
    }

    activity.host.addView(
        ScrollView(activity).apply {
            addView(content)
        },
        LinearLayout.LayoutParams(-1, 0, 1f)
    )
}
