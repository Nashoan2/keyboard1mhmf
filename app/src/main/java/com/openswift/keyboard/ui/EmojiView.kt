package com.openswift.keyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import com.openswift.keyboard.R
import com.openswift.keyboard.theme.Themes

/**
 * Dedicated Shortcut Emojis panel:
 * - Features the full top shortcuts toolbar (Hub, Emoji [active], Voice, Cursor, Clipboard, Hide)
 * - Exclusively presents the shortcut emojis (EmojiCatalog.shortcutValues) in a clean, high-contrast grid
 * - Bottom action row with [ ABC ] to return to keyboard, Space, and Backspace [ ⌫ ]
 */
class EmojiView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : View(ctx, attrs) {

    var onEmojiSelected: ((String) -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onOpenHub: (() -> Unit)? = null
    var onOpenVoice: (() -> Unit)? = null
    var onOpenTextEditing: (() -> Unit)? = null
    var onOpenClipboard: (() -> Unit)? = null
    var onHide: (() -> Unit)? = null
    var onBackspace: (() -> Unit)? = null

    private val theme = Themes.Amoled
    private val density = resources.displayMetrics.density

    // Top toolbar Drawables & bounds - Pure bright white icons matching main keyboard shortcuts toolbar
    private val hubIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_hub_monogram)?.mutate()?.apply {
        setTint(0xFFFFFFFF.toInt())
    }
    private val emojiIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_sentiment_satisfied)?.mutate()?.apply {
        setTint(0xFFFFFFFF.toInt())
    }
    private val micIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_mic)?.mutate()?.apply {
        setTint(0xFFFFFFFF.toInt())
    }
    private val cursorIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_text_cursor)?.mutate()?.apply {
        setTint(0xFFFFFFFF.toInt())
    }
    private val clipboardIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_content_paste)?.mutate()?.apply {
        setTint(0xFFFFFFFF.toInt())
    }
    private val hideIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_keyboard_arrow_down)?.mutate()?.apply {
        setTint(0xFFFFFFFF.toInt())
    }
    private val backspaceIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_backspace)?.mutate()?.apply {
        setTint(0xFFFFFFFF.toInt())
    }
    private val closeIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_close)?.mutate()?.apply {
        setTint(0xFFFFFFFF.toInt())
    }
    private val starIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_star)?.mutate()?.apply {
        setTint(theme.keyAccent)
    }

    // Paints matching KeyboardView shortcuts toolbar exactly
    private val bgPaint = Paint().apply { color = 0xFF000000.toInt() }
    private val toolbarBarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF050B14.toInt()
        style = Paint.Style.FILL
    }
    private val toolbarBarBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt() // Neon cyan border
        style = Paint.Style.STROKE
    }
    private val toolbarDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt() // Solid neon cyan vertical divider matching keyboard
        strokeCap = Paint.Cap.ROUND
    }
    private val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.keyBackground
        style = Paint.Style.FILL
    }
    private val cardPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.keyAccent
        style = Paint.Style.FILL
    }
    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x2200E5FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val emojiTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val bottomBarTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 15f * density
    }
    private val bottomBarSubtlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD8DCE5.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 14f * density
    }

    // Touch hit test regions
    private val toolbarBounds = mutableMapOf<String, RectF>()
    private val toolbarBarRect = RectF()
    private val emojiCardBounds = mutableMapOf<String, RectF>()
    private val bottomAbcRect = RectF()
    private val bottomSpaceRect = RectF()
    private val bottomBackspaceRect = RectF()

    private var pressedKey: String? = null

    // Dimensions matching main keyboard
    private val toolbarHeight = 42f * density
    private val bottomBarHeight = 44f * density

    // Shortcut emojis exclusively
    private val shortcutEmojis = EmojiCatalog.shortcutValues

    var explicitHeightPx: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = if (explicitHeightPx > 0) explicitHeightPx else (360f * density).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Screen background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 2. Top Shortcuts Toolbar
        drawTopToolbar(canvas, w)

        // 3. Bottom Action Row (ABC, Space, Backspace)
        val bottomY = h - bottomBarHeight
        drawBottomBar(canvas, w, bottomY, bottomBarHeight)

        // 4. Shortcut Emojis Grid
        val gridTop = toolbarHeight + (6f * density)
        val gridBottom = bottomY - (6f * density)
        drawEmojiGrid(canvas, w, gridTop, gridBottom)
    }

    private fun drawTopToolbar(canvas: Canvas, w: Float) {
        toolbarBounds.clear()
        val barMarginH = 4f * density
        val barTop = 2f * density
        val barBottom = toolbarHeight - (2f * density)
        val barRadius = 12f * density
        toolbarBarRect.set(barMarginH, barTop, w - barMarginH, barBottom)

        // Toolbar background (dark midnight)
        canvas.drawRoundRect(toolbarBarRect, barRadius, barRadius, toolbarBarBgPaint)

        // Neon cyan border
        toolbarBarBorderPaint.strokeWidth = 2.2f * density
        canvas.drawRoundRect(toolbarBarRect, barRadius, barRadius, toolbarBarBorderPaint)

        val icons = listOf(
            "hub" to hubIcon,
            "emoji" to emojiIcon,
            "voice" to micIcon,
            "cursor" to cursorIcon,
            "clipboard" to clipboardIcon,
            "hide" to hideIcon
        )
        val iconCount = icons.size
        val slotWidth = toolbarBarRect.width() / iconCount.toFloat()

        // Solid neon cyan divider lines between slots
        toolbarDividerPaint.strokeWidth = 1.5f * density
        val dividerHalfH = 10.5f * density
        val dividerCenterY = toolbarBarRect.centerY()
        for (i in 1 until iconCount) {
            val divX = toolbarBarRect.left + (i * slotWidth)
            canvas.drawLine(divX, dividerCenterY - dividerHalfH, divX, dividerCenterY + dividerHalfH, toolbarDividerPaint)
        }

        icons.forEachIndexed { index, (action, drawable) ->
            val centerX = toolbarBarRect.left + (index * slotWidth) + (slotWidth / 2f)
            val centerY = toolbarBarRect.centerY()

            val hitRect = RectF(
                toolbarBarRect.left + (index * slotWidth),
                barTop,
                toolbarBarRect.left + ((index + 1) * slotWidth),
                barBottom
            )
            toolbarBounds[action] = hitRect

            if (drawable != null) {
                val iconSize = when (action) {
                    "cursor", "hide" -> (25f * density).toInt()
                    else -> (22f * density).toInt()
                }
                val iconLeft = (centerX - (iconSize / 2f)).toInt()
                val iconTop = (centerY - (iconSize / 2f)).toInt()
                drawable.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                drawable.draw(canvas)
            }
        }
    }

    private fun drawEmojiGrid(canvas: Canvas, w: Float, top: Float, bottom: Float) {
        emojiCardBounds.clear()
        val availableHeight = (bottom - top).coerceAtLeast(10f)
        val columns = 6
        val rows = 2
        val gap = 6f * density
        val maxKeyWidth = 48f * density
        val keyWidth = minOf(maxKeyWidth, (w - (16f * density) - (gap * (columns - 1))) / columns.toFloat())
        val keyHeight = 39f * density
        val emojiFontSize = 21f * density
        emojiTextPaint.textSize = emojiFontSize

        val totalGridWidth = (keyWidth * columns) + (gap * (columns - 1))
        val startX = (w - totalGridWidth) / 2f
        val totalGridHeight = (keyHeight * rows) + gap
        val startY = top + ((availableHeight - totalGridHeight) / 2f).coerceAtLeast(0f)

        shortcutEmojis.forEachIndexed { index, emoji ->
            val col = index % columns
            val row = index / columns
            if (row >= rows) return@forEachIndexed

            val left = startX + (col * (keyWidth + gap))
            val cardTop = startY + (row * (keyHeight + gap))
            val rect = RectF(left, cardTop, left + keyWidth, cardTop + keyHeight)
            emojiCardBounds[emoji] = rect

            val isPressed = pressedKey == emoji
            val paint = if (isPressed) cardPressedPaint else cardBgPaint

            // Draw rounded standard keyboard key
            val radius = 8f * density
            canvas.drawRoundRect(rect, radius, radius, paint)
            canvas.drawRoundRect(rect, radius, radius, cardBorderPaint)

            // Draw emoji glyph centered
            val fontMetrics = emojiTextPaint.fontMetrics
            val baselineY = rect.centerY() - ((fontMetrics.ascent + fontMetrics.descent) / 2f)
            canvas.drawText(emoji, rect.centerX(), baselineY, emojiTextPaint)
        }
    }

    private fun drawBottomBar(canvas: Canvas, w: Float, top: Float, height: Float) {
        val marginH = 10f * density
        val btnHeight = height - (8f * density)
        val btnTop = top + (4f * density)
        val btnBottom = btnTop + btnHeight
        val radius = 8f * density

        // 1. ABC Button on left
        val abcWidth = 72f * density
        bottomAbcRect.set(marginH, btnTop, marginH + abcWidth, btnBottom)
        val isAbcPressed = pressedKey == "ABC"
        canvas.drawRoundRect(bottomAbcRect, radius, radius, if (isAbcPressed) cardPressedPaint else cardBgPaint)
        canvas.drawRoundRect(bottomAbcRect, radius, radius, cardBorderPaint)
        val abcFontMetrics = bottomBarTextPaint.fontMetrics
        val abcTextY = bottomAbcRect.centerY() - ((abcFontMetrics.ascent + abcFontMetrics.descent) / 2f)
        canvas.drawText("ABC", bottomAbcRect.centerX(), abcTextY, bottomBarTextPaint)

        // 2. Backspace Button on right
        val backspaceWidth = 62f * density
        val backspaceLeft = w - marginH - backspaceWidth
        bottomBackspaceRect.set(backspaceLeft, btnTop, w - marginH, btnBottom)
        val isBkPressed = pressedKey == "BK"
        canvas.drawRoundRect(bottomBackspaceRect, radius, radius, if (isBkPressed) cardPressedPaint else cardBgPaint)
        canvas.drawRoundRect(bottomBackspaceRect, radius, radius, cardBorderPaint)
        if (backspaceIcon != null) {
            val iconSize = (22f * density).toInt()
            val left = (bottomBackspaceRect.centerX() - iconSize / 2f).toInt()
            val topIcon = (bottomBackspaceRect.centerY() - iconSize / 2f).toInt()
            backspaceIcon.setBounds(left, topIcon, left + iconSize, topIcon + iconSize)
            backspaceIcon.draw(canvas)
        }

        // 3. Space button in center
        val spaceLeft = bottomAbcRect.right + (8f * density)
        val spaceRight = bottomBackspaceRect.left - (8f * density)
        bottomSpaceRect.set(spaceLeft, btnTop, spaceRight, btnBottom)
        val isSpacePressed = pressedKey == "SPACE"
        canvas.drawRoundRect(bottomSpaceRect, radius, radius, if (isSpacePressed) cardPressedPaint else cardBgPaint)
        canvas.drawRoundRect(bottomSpaceRect, radius, radius, cardBorderPaint)
        val spaceFontMetrics = bottomBarSubtlePaint.fontMetrics
        val spaceTextY = bottomSpaceRect.centerY() - ((spaceFontMetrics.ascent + spaceFontMetrics.descent) / 2f)
        canvas.drawText("مسافة", bottomSpaceRect.centerX(), spaceTextY, bottomBarSubtlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressedKey = findKeyAt(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val target = pressedKey
                pressedKey = null
                invalidate()

                if (target != null && target == findKeyAt(x, y)) {
                    handleKeyAction(target)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedKey = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findKeyAt(x: Float, y: Float): String? {
        // 1. Toolbar items
        for ((action, rect) in toolbarBounds) {
            if (rect.contains(x, y)) return "TB:$action"
        }
        // 2. Bottom items
        if (bottomAbcRect.contains(x, y)) return "ABC"
        if (bottomBackspaceRect.contains(x, y)) return "BK"
        if (bottomSpaceRect.contains(x, y)) return "SPACE"

        // 3. Emojis
        for ((emoji, rect) in emojiCardBounds) {
            if (rect.contains(x, y)) return emoji
        }
        return null
    }

    private fun handleKeyAction(key: String) {
        when {
            key.startsWith("TB:") -> {
                val action = key.removePrefix("TB:")
                when (action) {
                    "hub" -> onOpenHub?.invoke() ?: onClose?.invoke()
                    "emoji" -> onClose?.invoke() // Re-tapping emoji returns to keyboard
                    "voice" -> onOpenVoice?.invoke() ?: onClose?.invoke()
                    "cursor" -> onOpenTextEditing?.invoke() ?: onClose?.invoke()
                    "clipboard" -> onOpenClipboard?.invoke() ?: onClose?.invoke()
                    "hide" -> onHide?.invoke()
                }
            }
            key == "ABC" -> onClose?.invoke()
            key == "BK" -> onBackspace?.invoke()
            key == "SPACE" -> onEmojiSelected?.invoke(" ")
            else -> onEmojiSelected?.invoke(key)
        }
    }
}
