package com.typing.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import kotlin.math.ceil

class TypingTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val CHARS_PER_ROW = 17
    }

    private val colorCorrect = Color.parseColor("#40B43E")
    private val colorWrong = Color.parseColor("#E65C53")
    private val colorCurrent = Color.parseColor("#CC000000")
    private val colorPending = Color.parseColor("#59000000")
    private val colorCursor = Color.parseColor("#2196F3")
    private val colorSeparator = Color.parseColor("#80000000")
    private val colorInputText = Color.parseColor("#CC000000")

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 42f
        typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
    }

    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorCursor
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorSeparator
        strokeWidth = 2f
    }

    var originalText: String = ""
    var userInput: String = ""
    var cursorVisible: Boolean = true
    var rowHeight: Float = 0f
    var charWidth: Float = 0f
    var topPadding: Float = 0f

    private var needsLayout = true

    fun setTextData(original: String, input: String, showCursor: Boolean) {
        originalText = original
        userInput = input
        cursorVisible = showCursor
        needsLayout = true
        invalidate()
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val totalRows = ceil(originalText.length.toFloat() / CHARS_PER_ROW).toInt().coerceAtLeast(1)

        if (needsLayout || charWidth == 0f) {
            textPaint.textSize = width / (CHARS_PER_ROW + 1f)
            charWidth = textPaint.measureText("测")
            val fm = textPaint.fontMetrics

            // Each row needs enough height for:
            // - Original text (with ascent/descent)
            // - Gap
            // - Input text (with ascent/descent)
            // - Gap
            // - Separator line area
            val textH = fm.descent - fm.ascent
            rowHeight = textH * 3.5f
            topPadding = -fm.ascent
            needsLayout = false
        }

        val height = (totalRows * rowHeight + topPadding).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (originalText.isEmpty()) return

        val totalRows = ceil(originalText.length.toFloat() / CHARS_PER_ROW).toInt().coerceAtLeast(1)
        val paddingLeft = (width - charWidth * CHARS_PER_ROW) / 2f
        val fm = textPaint.fontMetrics

        for (row in 0 until totalRows) {
            val rowStart = row * CHARS_PER_ROW
            val rowEnd = minOf(rowStart + CHARS_PER_ROW, originalText.length)
            val rowChars = originalText.substring(rowStart, rowEnd)

            val rowTop = topPadding + row * rowHeight

            // Row layout using percentages of rowHeight:
            // 0%    - 25%:  Original text area
            // 25%   - 55%:  Gap
            // 55%   - 80%:  Input text area
            // 80%   - 90%:  Gap
            // 90%   - 100%: Separator line area

            // === Original text at 25% of row ===
            val originalBaseline = rowTop + rowHeight * 0.25f
            for (i in rowChars.indices) {
                val globalIndex = rowStart + i
                textPaint.color = getCharColor(globalIndex)
                val x = paddingLeft + i * charWidth
                canvas.drawText(rowChars[i].toString(), x, originalBaseline, textPaint)
            }

            // === User input at 55% of row ===
            val inputBaseline = rowTop + rowHeight * 0.55f
            val inputStart = rowStart
            val inputEnd = minOf(userInput.length, rowEnd)
            if (inputEnd > inputStart) {
                val inputChars = userInput.substring(inputStart, inputEnd)
                textPaint.color = colorInputText
                for (i in inputChars.indices) {
                    val x = paddingLeft + i * charWidth
                    canvas.drawText(inputChars[i].toString(), x, inputBaseline, textPaint)
                }
            }

            // === Separator line at 90% of row ===
            val lineY = rowTop + rowHeight * 0.90f
            canvas.drawLine(
                paddingLeft - 4f, lineY,
                paddingLeft + charWidth * CHARS_PER_ROW + 4f, lineY,
                separatorPaint
            )
        }

        // === Blue cursor on separator line ===
        if (cursorVisible && userInput.length <= originalText.length) {
            val cursorRow = userInput.length / CHARS_PER_ROW
            val cursorCol = userInput.length % CHARS_PER_ROW
            val cursorX = paddingLeft + cursorCol * charWidth

            val rowTop = topPadding + cursorRow * rowHeight
            val lineY = rowTop + rowHeight * 0.90f
            val cursorHeight = rowHeight * 0.15f
            val cursorTop = lineY - cursorHeight
            val cursorBottom = lineY

            canvas.drawLine(cursorX, cursorTop, cursorX, cursorBottom, cursorPaint)
        }
    }

    private fun getCharColor(index: Int): Int {
        if (index >= originalText.length) return colorPending
        if (index >= userInput.length) {
            return if (index == userInput.length) colorCurrent else colorPending
        }
        return if (userInput[index] == originalText[index]) colorCorrect else colorWrong
    }
}
