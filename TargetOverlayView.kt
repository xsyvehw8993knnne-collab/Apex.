package com.example.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View

/**
 * Full-screen, 100% touch-pass-through (`FLAG_NOT_TOUCHABLE`) overlay Canvas rendering:
 * - Corner-bracketed Red Magic tracking boxes & pulsing target rings for "Pro Assistant"
 * - Precision Headshot Lock reticle & dynamic aim-alignment guide onto the detected Head Landmark
 *   using `VisionTargetTracker.computeHeadLockGuidance()` (turns Emerald when aligned on head).
 */
class TargetOverlayView(context: Context) : View(context) {

    var proAssistantEnabled: Boolean = false
    var headshotAssistEnabled: Boolean = false
    var headLockLabel: String = "HEAD LOCK"
    private var targets: List<DetectedScreenTarget> = emptyList()

    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1E3C")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.SQUARE
    }

    private val boxOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FF1E3C")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4D67")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        pathEffect = DashPathEffect(floatArrayOf(16f, 10f), 0f)
    }

    private val headshotReticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val headshotLockedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val headshotInnerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1E3C")
        style = Paint.Style.FILL
    }

    private val guideLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC00E5FF")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        textSize = 26f
        typeface = Typeface.MONOSPACE
    }

    fun updateTargets(newTargets: List<DetectedScreenTarget>) {
        targets = newTargets
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!proAssistantEnabled && !headshotAssistEnabled) return

        val screenCx = width / 2f
        val screenCy = height / 2f

        // 1. Pro Assistant: sharp corner-bracketed tracking frames + circle on moving/humanoid targets
        if (proAssistantEnabled) {
            for (target in targets) {
                val r = target.bounds
                canvas.drawRect(r, boxOutlinePaint)

                val cornerLen = (minOf(r.width(), r.height()) * 0.24f).coerceIn(14f, 40f)
                // Top-left bracket
                canvas.drawLine(r.left, r.top, r.left + cornerLen, r.top, bracketPaint)
                canvas.drawLine(r.left, r.top, r.left, r.top + cornerLen, bracketPaint)
                // Top-right bracket
                canvas.drawLine(r.right - cornerLen, r.top, r.right, r.top, bracketPaint)
                canvas.drawLine(r.right, r.top, r.right, r.top + cornerLen, bracketPaint)
                // Bottom-left bracket
                canvas.drawLine(r.left, r.bottom - cornerLen, r.left, r.bottom, bracketPaint)
                canvas.drawLine(r.left, r.bottom, r.left + cornerLen, r.bottom, bracketPaint)
                // Bottom-right bracket
                canvas.drawLine(r.right - cornerLen, r.bottom, r.right, r.bottom, bracketPaint)
                canvas.drawLine(r.right, r.bottom - cornerLen, r.right, r.bottom, bracketPaint)

                val radius = (minOf(r.width(), r.height()) * 0.40f).coerceIn(24f, 100f)
                canvas.drawCircle(r.centerX(), r.centerY(), radius, circlePaint)
            }
        }

        // 2. Headshot Vision Lock Reticle + Aim Alignment Vector
        if (headshotAssistEnabled) {
            val guidance = VisionTargetTracker.computeHeadLockGuidance(
                targets = targets,
                crosshairX = screenCx,
                crosshairY = screenCy
            )

            canvas.drawCircle(screenCx, screenCy, 14f, guideLinePaint)

            if (guidance != null) {
                val head = guidance.headPoint
                val activePaint = if (guidance.isLockedOnHead) headshotLockedPaint else headshotReticlePaint

                if (!guidance.isLockedOnHead) {
                    canvas.drawLine(screenCx, screenCy, head.x, head.y, guideLinePaint)
                }

                canvas.drawCircle(head.x, head.y, 26f, activePaint)
                canvas.drawCircle(head.x, head.y, 7f, headshotInnerDotPaint)

                canvas.drawLine(head.x - 38f, head.y, head.x - 18f, head.y, activePaint)
                canvas.drawLine(head.x + 18f, head.y, head.x + 38f, head.y, activePaint)
                canvas.drawLine(head.x, head.y - 38f, head.x, head.y - 18f, activePaint)
                canvas.drawLine(head.x, head.y + 18f, head.x, head.y + 38f, activePaint)

                labelPaint.color = if (guidance.isLockedOnHead) {
                    Color.parseColor("#00E676")
                } else {
                    Color.parseColor("#00E5FF")
                }
                canvas.drawText(headLockLabel, head.x + 32f, head.y - 24f, labelPaint)
            }
        }
    }
}
