package com.abridgefs.app.ui

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

private fun settingSwitch(name: String, checked: Boolean, onChanged: (Boolean) -> Unit) =
        Switch(activity).apply {
            text = name
            isChecked = checked
            textSize = 13f
            setTextColor(c(R.color.bridgefs_text_primary))
            setPadding(0, dp(2), 0, dp(2))
            setOnCheckedChangeListener { _, value ->
                onChanged(value)
            }
        }
