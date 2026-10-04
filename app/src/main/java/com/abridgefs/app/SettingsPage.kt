package com.abridgefs.app

import android.content.Context
import android.graphics.Typeface
import android.widget.*
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

internal fun ApsActivity.settingsPage() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(14))
        }
        content.addView(title("设置"))
        listOf("连接", "权限", "文件", "执行", "外观", "通知", "日志", "系统").forEach { name ->
            val open = settingOpen.contains(name)
            content.addView(section(name, open) {
                if (open) settingOpen.remove(name) else settingOpen.add(name)
                render()
            }, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(4) })
            if (open) {
                content.addView(card().apply {
                    addView(value(
                        if (name == "连接") "全局连接资源"
                        else if (name == "权限") "仅管理没有明确归属到具体模块的 APS 系统级权限"
                        else "系统级 " + name + " 设置"
                    ))
                    if (name == "连接") {
                        addView(Button(this).apply {
                            text = "API Profiles"
                            setOnClickListener { toast("API 连接及其内部权限将在接线阶段恢复") }
                        })
                        addView(Button(this).apply {
                            text = "GitHub"
                            setOnClickListener { toast("GitHub 连接及其内部权限将在接线阶段恢复") }
                        })
                    }
                    if (name == "权限") {
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
                })
            }
        }
        host.addView(ScrollView(this).apply { addView(content) },
            LinearLayout.LayoutParams(-1, 0, 1f))
    }
