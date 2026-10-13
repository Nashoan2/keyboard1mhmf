package com.openswift.keyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import com.openswift.keyboard.R

/**
 * Quick Actions / Tools Hub View matching the user's reference image (الصورة رقم 91):
 * - Top toolbar with 5 icons, where the Monogram hub icon is actively highlighted.
 * - 2 columns of rounded capsule pill buttons:
 *   Right Column: [تغيير الحجم], [< المظهر], [الصوت], [ترجمة], [اللغة التالية]
 *   Left Column:  [< الإعدادات], [< ملف GIF], [< ملصق], [عائم], [< رموز الإيموجي]
 * - Bottom row: Edit button (pencil icon) on the left and pagination dots in the center.
 */
class ToolsHubView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : View(ctx, attrs) {

    enum class ToolId {
        RESIZE,
        THEME,
        VOICE,
        TRANSLATE,
        NEXT_LANGUAGE,
        SETTINGS,
        GIF,
        STICKER,
        FLOATING,
        EMOJI,
        CUSTOMIZE
    }

    var onOpenEmoji: (() -> Unit)? = null
    var onOpenVoice: (() -> Unit)? = null
    var onOpenTextEditing: (() -> Unit)? = null
    var onOpenClipboard: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onToolSelected: ((ToolId) -> Unit)? = null

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

    // Colors matching screenshot #91
    private val colorBg = 0xFF14171E.toInt()
    private val colorPillBg = 0xFF282D37.toInt()
    private val colorToolbarIcon = 0xFFD8DCE5.toInt()
    private val colorHighlightBg = 0xFF1E3F42.toInt()
    private val colorTextWhite = 0xFFFFFFFF.toInt()
    private val colorChevron = 0xFF9EACB8.toInt()

    // Paints
    private val bgPaint = Paint().apply {
        color = colorBg
        style = Paint.Style.FILL
    }
    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPillBg
        style = Paint.Style.FILL
    }
    private val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = 14.5f * density
        color = colorTextWhite
        isFakeBoldText = true
    }
    private val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = 16f * density
        color = colorChevron
    }
    private val highlightBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorHighlightBg
        style = Paint.Style.FILL
    }
    private val bottomDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val editPillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }

    // Top toolbar Drawables - Pure bright white matching KeyboardView
    private val hubIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_hub_monogram)?.mutate()?.apply {
        setTint(0xFFFFFFFF.toInt())
    }
    private val emojiIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_sentiment_satisfied)?.mutate()?.apply {
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

    // Tool Icons
    private val resizeIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_resize)?.mutate()?.apply { setTint(colorToolbarIcon) }
    private val themeIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_palette)?.mutate()?.apply { setTint(colorToolbarIcon) }
    private val micIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_mic)?.mutate()?.apply { setTint(0xFFFFFFFF.toInt()) }
    private val translateIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_translate)?.mutate()?.apply { setTint(colorToolbarIcon) }
    private val globeIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_language)?.mutate()?.apply { setTint(colorToolbarIcon) }

    private val settingsIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_settings)?.mutate()?.apply { setTint(colorToolbarIcon) }
    private val gifIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_gif)?.mutate()?.apply { setTint(colorToolbarIcon) }
    private val stickerIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_sticker)?.mutate()?.apply { setTint(colorToolbarIcon) }
    private val floatingIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_floating)?.mutate()?.apply { setTint(colorToolbarIcon) }
    private val smileyFaceIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_sentiment_satisfied)?.mutate()?.apply { setTint(colorToolbarIcon) }
    private val pencilIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_edit_pencil)?.mutate()?.apply { setTint(colorToolbarIcon) }

    // Hit test bounds
    private val toolbarBarRect = RectF()
    private val toolbarBounds = mutableMapOf<String, RectF>()
    private val toolBounds = mutableMapOf<ToolId, RectF>()

    private val toolbarBarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF050B14.toInt()
        style = Paint.Style.FILL
    }
    private val toolbarBarBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt() // Neon cyan border matching keyboard shortcuts bar
        style = Paint.Style.STROKE
    }
    private val toolbarDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        strokeCap = Paint.Cap.ROUND
    }

    // Dimensions
    private val toolbarHeight = 42f * density
    private val horizontalMargin = 12f * density
    private val colGap = 10f * density
    private val rowGap = 7f * density
    private val pillHeight = 36f * density
    private val bottomBarHeight = 32f * density

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

        // 3. Pill Grid
        drawPillGrid(canvas, w)

        // 4. Bottom Row (pencil pill + pagination dots)
        drawBottomBar(canvas, w, h)
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
        val numIcons = icons.size
        val slotWidth = toolbarBarRect.width() / numIcons.toFloat()

        // Divider lines between slots
        toolbarDividerPaint.strokeWidth = 1.5f * density
        val dividerHalfH = 10.5f * density
        val dividerCenterY = toolbarBarRect.centerY()
        for (i in 1 until numIcons) {
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

    private data class PillItem(
        val id: ToolId,
        val label: String,
        val icon: Drawable?,
        val hasChevron: Boolean
    )

    private fun drawPillGrid(canvas: Canvas, w: Float) {
        toolBounds.clear()
        val gridTop = toolbarHeight + (6f * density)
        val totalAvailableWidth = w - (horizontalMargin * 2f) - colGap
        val colWidth = totalAvailableWidth / 2f

        // Right column items (RTL: right side)
        val rightItems = listOf(
            PillItem(ToolId.RESIZE, "تغيير الحجم", resizeIcon, hasChevron = false),
            PillItem(ToolId.THEME, "المظهر", themeIcon, hasChevron = true),
            PillItem(ToolId.VOICE, "الصوت", micIcon, hasChevron = false),
            PillItem(ToolId.TRANSLATE, "ترجمة", translateIcon, hasChevron = false),
            PillItem(ToolId.NEXT_LANGUAGE, "اللغة التالية", globeIcon, hasChevron = false)
        )

        // Left column items (RTL: left side)
        val leftItems = listOf(
            PillItem(ToolId.SETTINGS, "الإعدادات", settingsIcon, hasChevron = true),
            PillItem(ToolId.GIF, "ملف GIF", gifIcon, hasChevron = true),
            PillItem(ToolId.STICKER, "ملصق", stickerIcon, hasChevron = true),
            PillItem(ToolId.FLOATING, "عائم", floatingIcon, hasChevron = false),
            PillItem(ToolId.EMOJI, "رموز الإيموجي", smileyFaceIcon, hasChevron = true)
        )

        val numRows = 5
        for (row in 0 until numRows) {
            val y = gridTop + row * (pillHeight + rowGap)

            // Draw Right Pill
            val rightItem = rightItems[row]
            val rightX = w - horizontalMargin - colWidth
            val rightRect = RectF(rightX, y, rightX + colWidth, y + pillHeight)
            toolBounds[rightItem.id] = rightRect
            drawPill(canvas, rightRect, rightItem)

            // Draw Left Pill
            val leftItem = leftItems[row]
            val leftX = horizontalMargin
            val leftRect = RectF(leftX, y, leftX + colWidth, y + pillHeight)
            toolBounds[leftItem.id] = leftRect
            drawPill(canvas, leftRect, leftItem)
        }
    }

    private fun drawPill(canvas: Canvas, rect: RectF, item: PillItem) {
        val radius = rect.height() / 2f
        canvas.drawRoundRect(rect, radius, radius, pillBgPaint)

        val centerY = rect.centerY()
        val iconSize = (19f * density).toInt()

        // 1. Icon on the right side of the pill
        val iconRight = rect.right - (12f * density)
        val iconLeft = iconRight - iconSize
        val iconTop = (centerY - iconSize / 2f).toInt()
        val iconBottom = iconTop + iconSize
        item.icon?.setBounds(iconLeft.toInt(), iconTop, iconRight.toInt(), iconBottom)
        item.icon?.draw(canvas)

        // 2. Text in the middle/aligned right
        val textRight = iconLeft - (8f * density)
        val textY = centerY - ((pillTextPaint.ascent() + pillTextPaint.descent()) / 2f)
        canvas.drawText(item.label, textRight, textY, pillTextPaint)

        // 3. Optional left chevron `<` on the far left
        if (item.hasChevron) {
            val chevronX = rect.left + (12f * density)
            val chevronY = centerY - ((chevronPaint.ascent() + chevronPaint.descent()) / 2f)
            canvas.drawText("‹", chevronX, chevronY, chevronPaint)
        }
    }

    private fun drawBottomBar(canvas: Canvas, w: Float, h: Float) {
        val bottomY = h - bottomBarHeight - (6f * density)

        // Edit button (pill with pencil) on the left
        val editWidth = 52f * density
        val editHeight = 24f * density
        val editRect = RectF(horizontalMargin, bottomY, horizontalMargin + editWidth, bottomY + editHeight)
        toolBounds[ToolId.CUSTOMIZE] = editRect

        canvas.drawRoundRect(editRect, editHeight / 2f, editHeight / 2f, pillBgPaint)
        canvas.drawRoundRect(editRect, editHeight / 2f, editHeight / 2f, editPillBorderPaint)

        val pencilSize = (14f * density).toInt()
        val pLeft = (editRect.centerX() - pencilSize / 2f).toInt()
        val pTop = (editRect.centerY() - pencilSize / 2f).toInt()
        pencilIcon?.setBounds(pLeft, pTop, pLeft + pencilSize, pTop + pencilSize)
        pencilIcon?.draw(canvas)

        // Pagination Dots in the center: dot 1 (gray), dot 2 (white)
        val dotRadius = 3f * density
        val dotGap = 6f * density
        val dotsCenterX = w / 2f
        val dotsCenterY = editRect.centerY()

        // Dot 1 (inactive gray)
        bottomDotPaint.color = 0xFF58606E.toInt()
        canvas.drawCircle(dotsCenterX - dotRadius - (dotGap / 2f), dotsCenterY, dotRadius, bottomDotPaint)

        // Dot 2 (active white)
        bottomDotPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(dotsCenterX + dotRadius + (dotGap / 2f), dotsCenterY, dotRadius, bottomDotPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            // Check Toolbar clicks
            for ((id, rect) in toolbarBounds) {
                if (rect.contains(event.x, event.y)) {
                    when (id) {
                        "hub" -> onClose?.invoke() // Re-tapping hub returns to keyboard
                        "emoji" -> onOpenEmoji?.invoke()
                        "voice" -> onOpenVoice?.invoke()
                        "cursor" -> onOpenTextEditing?.invoke()
                        "clipboard" -> onOpenClipboard?.invoke()
                        "hide" -> onClose?.invoke()
                    }
                    return true
                }
            }

            // Check Tool clicks
            for ((id, rect) in toolBounds) {
                if (rect.contains(event.x, event.y)) {
                    when (id) {
                        ToolId.EMOJI -> onOpenEmoji?.invoke()
                        else -> onToolSelected?.invoke(id)
                    }
                    return true
                }
            }
        }
        return true
    }
}
