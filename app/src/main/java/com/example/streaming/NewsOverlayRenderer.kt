package com.example.streaming

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.data.NewsDeskPreset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

/**
 * Renders a crisp 1280x720 broadcast-grade TV News Graphics Overlay onto a transparent ARGB Bitmap:
 * - Top-Left: Uploaded custom channel logo + Channel Name & Location bug
 * - Top-Right: Pulsing LIVE indicator + Real-time digital clock + Presenter voice equalizer
 * - Lower-Third: Breaking News category pill + Main Headline bar
 * - Bottom Edge: Smooth continuous scrolling news ticker (Marquee) with fixed NEWS/শিরোনাম badge
 */
class NewsOverlayRenderer(
    private val width: Int = 1280,
    private val height: Int = 720
) {
    val overlayBitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(overlayBitmap)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 21f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val headlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val tickerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F8FAFC")
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var cachedTickerText = ""
    private var cachedTickerWidth = 600f

    fun renderFrame(
        preset: NewsDeskPreset,
        logoBitmap: Bitmap?,
        elapsedMs: Long,
        isPresenterSpeaking: Boolean
    ): Bitmap {
        // Clear to full transparency so underlying video footage shows through cleanly
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // 1. Top-Left Channel Logo & Branding Bug
        if (preset.showLogo) {
            drawTopChannelBug(preset, logoBitmap)
            drawTopRightLiveClock(elapsedMs, isPresenterSpeaking)
        }

        // 2. Lower-Third Breaking News Headline Bar
        val tickerHeight = if (preset.showTicker) 56f else 0f
        if (preset.showHeadline && preset.headline.isNotBlank()) {
            drawLowerThirdHeadline(preset, bottomY = height - tickerHeight - 14f)
        }

        // 3. Bottom Continuous Scrolling News Ticker
        if (preset.showTicker && preset.tickerText.isNotBlank()) {
            drawBottomScrollingTicker(preset, elapsedMs, tickerHeight)
        }

        return overlayBitmap
    }

    private fun drawTopChannelBug(preset: NewsDeskPreset, logoBitmap: Bitmap?) {
        val left = 28f
        val top = 24f
        val boxHeight = 64f
        val channelName = preset.channelName.ifBlank { "StreamNews 24" }
        val location = preset.locationTag.trim()

        val nameWidth = titlePaint.measureText(channelName)
        val locWidth = if (location.isNotEmpty()) subtitlePaint.measureText(location) else 0f
        val logoSlot = if (logoBitmap != null) 60f else 18f
        val boxWidth = (logoSlot + maxOf(nameWidth, locWidth) + 34f).coerceAtMost(width * 0.48f)

        val rect = RectF(left, top, left + boxWidth, top + boxHeight)
        bgPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            Color.parseColor("#E60B1324"),
            Color.parseColor("#CC162442"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, 14f, 14f, bgPaint)
        bgPaint.shader = null

        // Left crimson vertical brand bar
        bgPaint.color = Color.parseColor("#FF2E4C")
        canvas.drawRoundRect(RectF(left, top, left + 8f, top + boxHeight), 4f, 4f, bgPaint)

        // Subtle luminous border
        strokePaint.color = Color.parseColor("#5538BDF8")
        canvas.drawRoundRect(rect, 14f, 14f, strokePaint)

        var textX = left + 20f
        if (logoBitmap != null && !logoBitmap.isRecycled) {
            val logoDst = RectF(left + 16f, top + 8f, left + 64f, top + 56f)
            canvas.drawBitmap(logoBitmap, null, logoDst, null)
            textX = left + 74f
        }

        if (location.isNotEmpty()) {
            canvas.drawText(channelName, textX, top + 34f, titlePaint)
            canvas.drawText(location, textX, top + 55f, subtitlePaint)
        } else {
            canvas.drawText(channelName, textX, top + 43f, titlePaint)
        }
    }

    private fun drawTopRightLiveClock(elapsedMs: Long, isSpeaking: Boolean) {
        val timeText = timeFormat.format(Date())
        val right = width - 28f
        val top = 24f
        val pillHeight = 46f
        val pillWidth = if (isSpeaking) 268f else 224f
        val left = right - pillWidth

        val rect = RectF(left, top, right, top + pillHeight)
        bgPaint.color = Color.parseColor("#D9080E1A")
        canvas.drawRoundRect(rect, 23f, 23f, bgPaint)

        // Red LIVE pill segment on left
        val liveRect = RectF(left, top, left + 98f, top + pillHeight)
        bgPaint.color = Color.parseColor("#E11D48")
        canvas.drawRoundRect(liveRect, 23f, 23f, bgPaint)

        // Pulsing dot
        val pulseAlpha = ((0.55 + 0.45 * sin(elapsedMs / 240.0)) * 255).toInt().coerceIn(80, 255)
        bgPaint.color = Color.argb(pulseAlpha, 255, 255, 255)
        canvas.drawCircle(left + 22f, top + pillHeight / 2f, 6f, bgPaint)
        canvas.drawText("LIVE", left + 35f, top + 30f, badgePaint)

        // Real-time clock
        canvas.drawText(timeText, left + 110f, top + 31f, clockPaint)

        // Animated audio bars when TTS News Presenter is actively narrating
        if (isSpeaking) {
            bgPaint.color = Color.parseColor("#10B981")
            val barBaseX = right - 40f
            val centerY = top + pillHeight / 2f
            for (i in 0 until 4) {
                val barH = (8f + 12f * kotlin.math.abs(sin(elapsedMs / 120.0 + i * 1.1))).toFloat()
                canvas.drawRoundRect(
                    RectF(barBaseX + i * 7f, centerY - barH / 2f, barBaseX + i * 7f + 4f, centerY + barH / 2f),
                    2f, 2f, bgPaint
                )
            }
        }
    }

    private fun drawLowerThirdHeadline(preset: NewsDeskPreset, bottomY: Float) {
        val left = 28f
        val right = width - 28f
        val barHeight = 64f
        val topY = bottomY - barHeight

        // Main dark obsidian lower-third plate
        val plateRect = RectF(left, topY, right, bottomY)
        bgPaint.shader = LinearGradient(
            left, topY, right, bottomY,
            Color.parseColor("#EB0F172A"),
            Color.parseColor("#D91E293B"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(plateRect, 12f, 12f, bgPaint)
        bgPaint.shader = null

        // Top accent hairline
        bgPaint.color = Color.parseColor("#FF2E4C")
        canvas.drawRect(left + 12f, topY, right - 12f, topY + 3f, bgPaint)

        // Category Tag Box (e.g. ব্রেকিং নিউজ)
        val tagText = preset.breakingTag.ifBlank { "ব্রেকিং নিউজ" }
        val tagWidth = (badgePaint.measureText(tagText) + 34f).coerceIn(130f, 260f)
        val tagRect = RectF(left, topY, left + tagWidth, bottomY)
        bgPaint.shader = LinearGradient(
            tagRect.left, tagRect.top, tagRect.right, tagRect.bottom,
            Color.parseColor("#DC2626"),
            Color.parseColor("#991B1B"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(tagRect, 12f, 12f, bgPaint)
        bgPaint.shader = null

        val tagTextX = left + (tagWidth - badgePaint.measureText(tagText)) / 2f
        canvas.drawText(tagText, tagTextX, topY + 40f, badgePaint)

        // Headline Text clipped inside the right side of the plate
        val textLeft = left + tagWidth + 20f
        canvas.save()
        canvas.clipRect(textLeft, topY, right - 16f, bottomY)
        canvas.drawText(preset.headline, textLeft, topY + 43f, headlinePaint)
        canvas.restore()
    }

    private fun drawBottomScrollingTicker(
        preset: NewsDeskPreset,
        elapsedMs: Long,
        tickerHeight: Float
    ) {
        val topY = height - tickerHeight
        val bottomY = height.toFloat()

        // Full-width bottom broadcast ticker strip
        bgPaint.color = Color.parseColor("#F2090D16")
        canvas.drawRect(0f, topY, width.toFloat(), bottomY, bgPaint)

        // Subtle gold/amber top separator line
        bgPaint.color = Color.parseColor("#F59E0B")
        canvas.drawRect(0f, topY, width.toFloat(), topY + 3f, bgPaint)

        // Fixed left badge ("শিরোনাম")
        val badgeWidth = 152f
        val normalizedTicker = preset.tickerText
            .replace("\n", "   •   ")
            .trim()
        val loopSegment = "$normalizedTicker      ★      "
        if (loopSegment != cachedTickerText) {
            cachedTickerText = loopSegment
            cachedTickerWidth = tickerPaint.measureText(loopSegment).coerceAtLeast(320f)
        }

        val speedPxPerSec = preset.tickerSpeedEnum.pxPerSecond
        val offset = ((elapsedMs / 1000.0) * speedPxPerSec % cachedTickerWidth).toFloat()

        // Draw smoothly scrolling text seamlessly tiled behind the left badge
        canvas.save()
        canvas.clipRect(badgeWidth, topY, width.toFloat(), bottomY)
        var drawX = width - offset
        // Ensure continuous coverage from left edge to right edge
        while (drawX > badgeWidth) {
            drawX -= cachedTickerWidth
        }
        while (drawX < width + cachedTickerWidth) {
            canvas.drawText(loopSegment, drawX, topY + 38f, tickerPaint)
            drawX += cachedTickerWidth
        }
        canvas.restore()

        // Draw left fixed NEWS badge on top
        bgPaint.shader = LinearGradient(
            0f, topY, badgeWidth, bottomY,
            Color.parseColor("#F59E0B"),
            Color.parseColor("#D97706"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, topY, badgeWidth, bottomY, bgPaint)
        bgPaint.shader = null

        val badgeLabel = "সর্বশেষ"
        val prevColor = badgePaint.color
        badgePaint.color = Color.parseColor("#0F172A")
        val labelX = (badgeWidth - badgePaint.measureText(badgeLabel)) / 2f
        canvas.drawText(badgeLabel, labelX, topY + 36f, badgePaint)
        badgePaint.color = prevColor
    }

    fun release() {
        if (!overlayBitmap.isRecycled) {
            overlayBitmap.recycle()
        }
    }
}
