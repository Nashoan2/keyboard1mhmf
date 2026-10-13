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

/**
 * Text Editing and Cursor Navigation View matching the user's reference image (الصورة رقم 90):
 * - Top toolbar with 5 icons, where the <|> cursor icon is actively highlighted with a teal rounded box.
 * - 3 rows x 5 columns grid of keypad buttons:
 *   Row 1: [ حافظة النصوص ]  [  ^  ]  [ الكل ]  [ نسخ ]  [ ⌫ (Delete) ]
 *   Row 2: [      <       ]  [تحديد]  [  >  ]  [ قص  ]  [ — (Space)  ]
 *   Row 3: [الصفحة الرئيسية]  [  v  ]  [إنهاء]  [ لصق ]  [ ↶ (Undo)   ]
 */
class TextEditingView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : View(ctx, attrs) {

    enum class Action {
        CLIPBOARD,
        UP,
        SELECT_ALL,
        COPY,
        DELETE,
        LEFT,
        SELECT_TOGGLE,
        RIGHT,
        CUT,
        SPACE,
        HOME,
        DOWN,
        END,
        PASTE,
        UNDO
    }

    var onOpenHub: (() -> Unit)? = null
    var onOpenEmoji: (() -> Unit)? = null
    var onOpenVoice: (() -> Unit)? = null
    var onOpenClipboard: (() -> Unit)? = null
    var onHide: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onAction: ((Action) -> Unit)? = null

    var isSelectionModeActive = false
        set(value) {
            field = value
            invalidate()
        }

    var keyHeightDp = 78
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var explicitHeightPx: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    private val density = resources.displayMetrics.density

    // Colors matching keyboard shortcuts bar and Amoled theme
    private val colorBg = 0xFF000000.toInt() // Dark Amoled keyboard background
    private val colorKeyBg = 0xFF242831.toInt() // Dark slate key background
    private val colorKeyActiveBg = 0xFF1D3B3E.toInt() // Key active/selected
    private val colorTeal = 0xFF2DD4BF.toInt() // Vibrant cyan/teal
    private val colorTextWhite = 0xFFFFFFFF.toInt()

    // Paints
    private val bgPaint = Paint().apply {
        color = colorBg
        style = Paint.Style.FILL
    }
    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorKeyBg
        style = Paint.Style.FILL
    }
    private val keyActiveBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorKeyActiveBg
        style = Paint.Style.FILL
    }
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 16f * density
        color = colorTextWhite
        isFakeBoldText = true
    }
    private val smallMultiLineTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 13.5f * density
        color = colorTextWhite
        isFakeBoldText = true
    }
    private val arrowGlyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 22f * density
        color = colorTeal
        isFakeBoldText = true
    }
    private val toolbarBarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF050B14.toInt()
        style = Paint.Style.FILL
    }
    private val toolbarBarBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt() // Neon cyan border matching keyboard shortcuts bar
        style = Paint.Style.STROKE
    }
    private val toolbarDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt() // Solid neon cyan vertical divider matching keyboard
        strokeCap = Paint.Cap.ROUND
    }

    // Top toolbar Drawables - Pure bright white matching KeyboardView
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

    // Keypad Action Drawables (Cyan/Teal)
    private val deleteIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_edit_backspace)?.mutate()
    private val spaceIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_edit_space)?.mutate()
    private val undoIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_undo)?.mutate()

    // Hit test regions
    private val toolbarBarRect = RectF()
    private val toolbarBounds = mutableMapOf<String, RectF>()
    private val keyBounds = mutableMapOf<Action, RectF>()

    // Dimensions
    private val toolbarHeight = 42f * density
    private val keyCornerRadius = 6f * density
    private val keyPadding = 4f * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val targetHeight = if (explicitHeightPx > 0) {
            explicitHeightPx
        } else {
            val keyHeightPx = keyHeightDp * density
            ((4 * keyHeightPx) + (2f * density * 3) + (keyHeightPx * 0.82f) + (8f * density)).toInt()
        }
        setMeasuredDimension(w, targetHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 2. Toolbar
        drawToolbar(canvas, w)

        // 3. 3x5 Keypad Grid
        drawKeypad(canvas, w, h)
    }

    private fun drawToolbar(canvas: Canvas, w: Float) {
        toolbarBounds.clear()
        val barMarginH = 4f * density
        val barTop = 2f * density
        val barBottom = toolbarHeight - (2f * density)
        val barRadius = 12f * density
        toolbarBarRect.set(barMarginH, barTop, w - barMarginH, barBottom)

        // Fill toolbar background (dark midnight)
        canvas.drawRoundRect(toolbarBarRect, barRadius, barRadius, toolbarBarBgPaint)

        // Neon cyan border outline
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

        // Divider lines between slots
        toolbarDividerPaint.strokeWidth = 1.5f * density
        val dividerHalfH = 10.5f * density
        val dividerCenterY = toolbarBarRect.centerY()
        for (i in 1 until iconCount) {
            val divX = toolbarBarRect.left + (i * slotWidth)
            canvas.drawLine(divX, dividerCenterY - dividerHalfH, divX, dividerCenterY + dividerHalfH, toolbarDividerPaint)
        }

        icons.forEachIndexed { i, (id, drawable) ->
            val centerX = toolbarBarRect.left + (i * slotWidth) + (slotWidth / 2f)
            val centerY = toolbarBarRect.centerY()

            val slotRect = RectF(
                toolbarBarRect.left + (i * slotWidth),
                barTop,
                toolbarBarRect.left + ((i + 1) * slotWidth),
                barBottom
            )
            toolbarBounds[id] = slotRect

            if (drawable != null) {
                val size = when (id) {
                    "cursor", "hide" -> (25f * density).toInt()
                    else -> (22f * density).toInt()
                }
                val left = (centerX - size / 2f).toInt()
                val top = (centerY - size / 2f).toInt()
                drawable.setBounds(left, top, left + size, top + size)
                drawable.draw(canvas)
            }
        }
    }

    private fun drawKeypad(canvas: Canvas, w: Float, h: Float) {
        keyBounds.clear()
        val keypadTop = toolbarHeight + (4f * density)
        val keypadBottom = h - (6f * density)
        val keypadHeight = keypadBottom - keypadTop

        val rows = 3
        val cols = 5
        val rowHeight = (keypadHeight - (keyPadding * (rows - 1))) / rows.toFloat()
        val colWidth = (w - (keyPadding * (cols + 1))) / cols.toFloat()

        // 3x5 layout definition
        val grid = listOf(
            listOf(
                KeyItem(Action.CLIPBOARD, "حافظة\nالنصوص", isMultiLine = true),
                KeyItem(Action.UP, "^", isArrow = true),
                KeyItem(Action.SELECT_ALL, "الكل"),
                KeyItem(Action.COPY, "نسخ"),
                KeyItem(Action.DELETE, "", icon = deleteIcon)
            ),
            listOf(
                KeyItem(Action.LEFT, "<", isArrow = true),
                KeyItem(Action.SELECT_TOGGLE, "تحديد"),
                KeyItem(Action.RIGHT, ">", isArrow = true),
                KeyItem(Action.CUT, "قص"),
                KeyItem(Action.SPACE, "", icon = spaceIcon)
            ),
            listOf(
                KeyItem(Action.HOME, "الصفحة\nالرئيسية", isMultiLine = true),
                KeyItem(Action.DOWN, "v", isArrow = true),
                KeyItem(Action.END, "إنهاء"),
                KeyItem(Action.PASTE, "لصق"),
                KeyItem(Action.UNDO, "", icon = undoIcon)
            )
        )

        grid.forEachIndexed { r, row ->
            val top = keypadTop + r * (rowHeight + keyPadding)
            val bottom = top + rowHeight

            row.forEachIndexed { c, item ->
                val left = keyPadding + c * (colWidth + keyPadding)
                val right = left + colWidth
                val rect = RectF(left, top, right, bottom)
                keyBounds[item.action] = rect

                // Background
                val isSelectionActive = (item.action == Action.SELECT_TOGGLE && isSelectionModeActive)
                val isPressed = (item.action == pressedAction)
                val paintToUse = if (isSelectionActive || isPressed) keyActiveBgPaint else keyBgPaint
                canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, paintToUse)

                // Content
                val centerX = rect.centerX()
                val centerY = rect.centerY()

                when {
                    item.icon != null -> {
                        val iconSize = (22f * density).toInt()
                        val iLeft = (centerX - iconSize / 2f).toInt()
                        val iTop = (centerY - iconSize / 2f).toInt()
                        item.icon.setBounds(iLeft, iTop, iLeft + iconSize, iTop + iconSize)
                        item.icon.draw(canvas)
                    }
                    item.isArrow -> {
                        val glyph = when (item.action) {
                            Action.UP -> "︿"
                            Action.DOWN -> "﹀"
                            Action.LEFT -> "〈"
                            Action.RIGHT -> "〉"
                            else -> item.label
                        }
                        val textY = centerY - ((arrowGlyphPaint.ascent() + arrowGlyphPaint.descent()) / 2f)
                        canvas.drawText(glyph, centerX, textY, arrowGlyphPaint)
                    }
                    item.isMultiLine -> {
                        val lines = item.label.split("\n")
                        val lineHeight = 16f * density
                        val totalHeight = (lines.size - 1) * lineHeight
                        val startY = centerY - (totalHeight / 2f) - ((smallMultiLineTextPaint.ascent() + smallMultiLineTextPaint.descent()) / 2f)
                        lines.forEachIndexed { idx, line ->
                            canvas.drawText(line, centerX, startY + (idx * lineHeight), smallMultiLineTextPaint)
                        }
                    }
                    else -> {
                        val textY = centerY - ((keyTextPaint.ascent() + keyTextPaint.descent()) / 2f)
                        canvas.drawText(item.label, centerX, textY, keyTextPaint)
                    }
                }
            }
        }
    }

    private data class KeyItem(
        val action: Action,
        val label: String,
        val isArrow: Boolean = false,
        val isMultiLine: Boolean = false,
        val icon: android.graphics.drawable.Drawable? = null
    )

    private var pressedAction: Action? = null
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var isDeleteActive = false
    private var deleteRepeatCount = 0
    private val deleteRepeatHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val deleteRepeatRunnable = object : Runnable {
        override fun run() {
            if (!isDeleteActive) return
            deleteRepeatCount++
            onAction?.invoke(Action.DELETE)
            try {
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            } catch (_: Exception) {}

            // Accelerating repeat rate for super fast continuous deletion
            val interval = when {
                deleteRepeatCount > 15 -> 14L // Ultra-fast continuous delete (~70/sec)
                deleteRepeatCount > 6 -> 28L  // Rapid delete (~35/sec)
                else -> 55L                   // Initial repetition
            }
            deleteRepeatHandler.postDelayed(this, interval)
        }
    }

    private fun stopDeleteRepeat() {
        isDeleteActive = false
        deleteRepeatHandler.removeCallbacks(deleteRepeatRunnable)
        deleteRepeatCount = 0
    }

    override fun onDetachedFromWindow() {
        stopDeleteRepeat()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = x
                touchDownY = y
                // Check Keypad clicks
                for ((action, rect) in keyBounds) {
                    if (rect.contains(x, y)) {
                        pressedAction = action
                        invalidate()
                        if (action == Action.DELETE) {
                            isDeleteActive = true
                            deleteRepeatCount = 0
                            // Instant deletion on touch down!
                            onAction?.invoke(Action.DELETE)
                            try {
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            } catch (_: Exception) {}
                            deleteRepeatHandler.postDelayed(deleteRepeatRunnable, 220L)
                            return true
                        }
                        break
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDeleteActive) {
                    val dist = kotlin.math.hypot(x - touchDownX, y - touchDownY)
                    if (dist > 32f * density) {
                        stopDeleteRepeat()
                        pressedAction = null
                        invalidate()
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                val wasDelete = isDeleteActive
                stopDeleteRepeat()
                pressedAction = null
                invalidate()

                if (wasDelete) {
                    return true
                }

                // Check Toolbar clicks
                for ((id, rect) in toolbarBounds) {
                    if (rect.contains(x, y)) {
                        when (id) {
                            "hub" -> onOpenHub?.invoke()
                            "emoji" -> onOpenEmoji?.invoke()
                            "voice" -> onOpenVoice?.invoke()
                            "cursor" -> onClose?.invoke() // Re-tapping cursor returns to keyboard
                            "clipboard" -> onOpenClipboard?.invoke()
                            "hide" -> onHide?.invoke() ?: onClose?.invoke()
                        }
                        return true
                    }
                }

                // Check Keypad clicks
                val clickedAction = keyBounds.entries.firstOrNull { it.value.contains(x, y) }?.key ?: pressedAction
                if (clickedAction != null) {
                    if (clickedAction == Action.CLIPBOARD) {
                        onOpenClipboard?.invoke()
                    } else if (clickedAction == Action.HOME) {
                        onClose?.invoke() // "الصفحة الرئيسية" returns directly to keyboard
                        onAction?.invoke(Action.HOME)
                    } else if (clickedAction != Action.DELETE) {
                        onAction?.invoke(clickedAction)
                    }
                    return true
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                stopDeleteRepeat()
                pressedAction = null
                invalidate()
                return true
            }
        }
        return true
    }
}
