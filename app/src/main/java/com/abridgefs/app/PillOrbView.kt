package com.abridgefs.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Outline
import android.graphics.RectF
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator

class PillOrbView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var eyeScaleY = 1f
    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.width / 2f)
            }
        }
        elevation = 2f * density
    }
    private val blinkAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 9_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { animator ->
            val fraction = animator.animatedValue as Float
            val flashStart = 1f - 150f / 9_000f
            eyeScaleY = when {
                fraction < flashStart -> 1f
                fraction < flashStart + 75f / 9_000f ->
                    1f - .9f * ((fraction - flashStart) / (75f / 9_000f))
                else -> .1f + .9f * ((fraction - flashStart - 75f / 9_000f) / (75f / 9_000f))
            }.coerceIn(.1f, 1f)
            invalidate()
        }
    }

    // Reserved edge-hide baseline: 32dp wide exposes 16dp; 56dp high exposes 28dp when half-clipped.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize((40 * density).toInt(), widthMeasureSpec),
            resolveSize((56 * density).toInt(), heightMeasureSpec)
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!blinkAnimator.isStarted) blinkAnimator.start()
    }

    override fun onDetachedFromWindow() {
        blinkAnimator.cancel()
        eyeScaleY = 1f
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save()
        canvas.scale(width / (40f * density), height / (56f * density))

        // 42dp orange body with rounded lower corners.
        paint.color = Color.rgb(255, 128, 0)
        val body = Path().apply {
            moveTo(0f, 14f * density)
            lineTo(40f * density, 14f * density)
            lineTo(40f * density, 36f * density)
            cubicTo(40f * density, 47.05f * density, 31.05f * density, 56f * density, 20f * density, 56f * density)
            cubicTo(8.95f * density, 56f * density, 0f, 47.05f * density, 0f, 36f * density)
            close()
        }
        canvas.drawPath(body, paint)

        // 14dp black head.
        paint.color = Color.BLACK
        val head = Path().apply {
            moveTo(20f * density, 0f)
            cubicTo(9f * density, 0f, 0f, 6f * density, 0f, 14f * density)
            lineTo(40f * density, 14f * density)
            cubicTo(40f * density, 6f * density, 31f * density, 0f, 20f * density, 0f)
            close()
        }
        canvas.drawPath(head, paint)

        // Two 4x5dp blue eyes, 8dp center-to-center and 4dp below the top.
        canvas.save()
        canvas.scale(1f, eyeScaleY, 0f, 6.5f * density)
        paint.color = Color.BLUE
        val radius = 1f * density
        canvas.drawRoundRect(RectF(14f * density, 4f * density, 18f * density, 9f * density), radius, radius, paint)
        canvas.drawRoundRect(RectF(22f * density, 4f * density, 26f * density, 9f * density), radius, radius, paint)
        canvas.restore()
        canvas.restoreToCount(save)
    }
}
