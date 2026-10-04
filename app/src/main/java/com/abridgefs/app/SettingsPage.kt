package com.abridgefs.app

import android.widget.*
import android.view.*

internal fun ApsActivity.settingsPage() {
    val content = LinearLayout(this@settingsPage).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(14))
    }
    content.addView(title("设置"))

    val sections = listOf("连接", "权限", "文件", "执行", "外观", "通知", "日志", "系统")
    sections.forEach { name ->
        val open = settingOpen.contains(name)

        content.addView(
            section(name, open) {
                if (open) settingOpen.remove(name) else settingOpen.add(name)
                render()
            },
            LinearLayout.LayoutParams(-1, dp(52)).apply {
                bottomMargin = dp(4)
            }
        )

        if (open) {
            content.addView(
                card().apply {
                    when (name) {
                        "连接" -> {
                            addView(label("全局连接"))
                            addView(value("连接资源集中管理；具体权限归属到对应模块。"))

                            addView(Button(this@settingsPage).apply {
                                text = "API Profiles"
                                setOnClickListener {
                                    toast("API Profiles：连接、模型与 API 内部权限")
                                }
                            }, LinearLayout.LayoutParams(-1, dp(44)).apply {
                                topMargin = dp(6)
                            })

                            val githubButton = Button(this@settingsPage)
                            githubButton.text = "GitHub"
                            githubButton.setOnClickListener {
                                toast("GitHub：账号、Repository、Branch 与 GitHub 内部权限")
                            }
                            addView(githubButton, LinearLayout.LayoutParams(-1, dp(44)).apply {
                                topMargin = dp(6)
                            })
                        }

                        "权限" -> {
                            addView(label("APS 系统级权限"))
                            addView(value("这里只管理没有明确归属到具体模块的系统访问能力。"))
                            addView(settingSwitch("本地文件访问", localFileAccess) {
                                localFileAccess = it
                            })
                            addView(settingSwitch("存储访问", storageAccess) {
                                storageAccess = it
                            })
                            addView(settingSwitch("其他 APS 系统访问", externalAccess) {
                                externalAccess = it
                            })
                        }

                        else -> {
                            addView(label("系统级 $name"))
                            addView(value("该分类已预留，具体设置将在对应功能接线时加入。"))
                        }
                    }
                },
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(6)
                }
            )
        }
    }

    host.addView(
        ScrollView(this@settingsPage).apply {
            addView(content)
        },
        LinearLayout.LayoutParams(-1, 0, 1f)
    )
}
