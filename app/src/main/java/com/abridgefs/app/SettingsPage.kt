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

                        addView(Button(activity).apply {
                            text = "API Profiles"
                            setOnClickListener {
                                activity.toast("API Profiles：连接、模型与 API 内部权限")
                            }
                        }, LinearLayout.LayoutParams(-1, activity.dp(44)).apply {
                            topMargin = activity.dp(6)
                        })

                        addView(Button(activity).apply {
                            text = "GitHub"
                            setOnClickListener {
                                activity.toast("GitHub：账号、Repository、Branch 与 GitHub 内部权限")
                            }
                        }, LinearLayout.LayoutParams(-1, activity.dp(44)).apply {
                            topMargin = activity.dp(6)
                        })
                    }

                    "权限" -> {
                        addView(activity.label("APS 系统级权限"))
                        addView(activity.value("这里只管理没有明确归属到具体模块的系统访问能力。"))
                        addView(activity.settingSwitch("本地文件访问", activity.localFileAccess) {
                            activity.localFileAccess = it
                        })
                        addView(activity.settingSwitch("存储访问", activity.storageAccess) {
                            activity.storageAccess = it
                        })
                        addView(activity.settingSwitch("其他 APS 系统访问", activity.externalAccess) {
                            activity.externalAccess = it
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
