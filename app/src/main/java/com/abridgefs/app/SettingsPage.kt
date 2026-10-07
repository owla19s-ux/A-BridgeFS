package com.abridgefs.app

import android.content.Intent
import android.view.*
import android.widget.*

internal fun ApsActivity.settingsPage() {
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(14))
    }
    content.addView(title("设置"))
    content.addView(value("连接、权限与系统行为集中管理"))

    val sections = listOf(
        "连接" to "API 与 GitHub",
        "权限" to "访问与施工权限",
        "文件" to "本地文件能力",
        "执行" to "命令执行与安全边界",
        "外观" to "显示与界面",
        "通知" to "提醒与后台状态",
        "日志" to "诊断与运行记录",
        "系统" to "版本与系统信息"
    )

    sections.forEach { pair ->
        val name = pair.first
        val subtitle = pair.second
        val open = settingOpen.contains(name)

        content.addView(
            section(name + "  ·  " + subtitle, open) {
                if (open) settingOpen.remove(name) else settingOpen.add(name)
                render()
            },
            LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(5) }
        )

        if (open) {
            content.addView(card().apply {
                when (name) {
                    "连接" -> {
                        addView(label("全局连接资源"))
                        addView(value("连接页只负责进入 API / GitHub 等具体资源配置；模块内部维护各自的连接权限。"))
                        addView(secondaryButton("API Profiles") {
                            startActivity(Intent(this@settingsPage, ApiSettingsActivity::class.java))
                        }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(5) })
                        addView(secondaryButton("GitHub 连接") {
                            startActivity(Intent(this@settingsPage, GitHubActivity::class.java))
                        }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(5) })
                    }
                    "权限" -> {
                        addView(label("APS 系统级权限"))
                        addView(value("这里只管理没有明确归属到 API、GitHub 或 Project 的系统级访问能力。"))
                        addView(settingSwitch("API 全局访问", AccessPolicy.isApiEnabled(this@settingsPage)) {
                            AccessPolicy.setApiEnabled(this@settingsPage, it)
                        })
                        addView(settingSwitch("GitHub 全局访问", AccessPolicy.isGithubEnabled(this@settingsPage)) {
                            AccessPolicy.setGithubEnabled(this@settingsPage, it)
                        })
                    }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(5) })
                    }
                    else -> {
                        addView(label(name))
                        addView(value(subtitle + " 暂未接入具体配置项。这里保留分类，不制造假开关。"))
                    }
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
    }

    host.addView(ScrollView(this).apply {
        isFillViewport = true
        addView(content)
    }, LinearLayout.LayoutParams(-1, 0, 1f))
}