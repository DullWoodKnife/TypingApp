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

    // Colors
    private val colorCorrect = Color.parseColor("#40B43E")
    private val colorWrong = Color.parseColor("#E65C53")
    private val colorCurrent = Color.parseColor("#CC000000")
    private val colorPending = Color.parseColor("#59000000")
    private val colorCursor = Color.parseColor("#2196F3")
    private val colorSeparator = Color.parseColor("#80000000")
    private val colorInputText = Color.parseColor("#CC000000")

    // Paints
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 42f
        typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
    }

    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorCursor
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorSeparator
        strokeWidth = 2f
    }

    // Data
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
            val lineHeight = fm.descent - fm.ascent
            rowHeight = lineHeight * 2.8f
            topPadding = -fm.ascent
            needsLayout = false
        }

        val height = (totalRows * rowHeight + topPadding * 2).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (originalText.isEmpty()) return

        val totalRows = ceil(originalText.length.toFloat() / CHARS_PER_ROW).toInt().coerceAtLeast(1)
        val paddingLeft = (width - charWidth * CHARS_PER_ROW) / 2f

        for (row in 0 until totalRows) {
            val rowStart = row * CHARS_PER_ROW
            val rowEnd = minOf(rowStart + CHARS_PER_ROW, originalText.length)
            val rowChars = originalText.substring(rowStart, rowEnd)

            val yBase = topPadding + row * rowHeight

            // Draw original text (top line)
            for (i in rowChars.indices) {
                val globalIndex = rowStart + i
                val color = getCharColor(globalIndex)
                textPaint.color = color
                val x = paddingLeft + i * charWidth
                canvas.drawText(rowChars[i].toString(), x, yBase, textPaint)
            }

            // Draw user input (bottom line)
            val inputStart = rowStart
            val inputEnd = minOf(userInput.length, rowEnd)
            if (inputEnd > inputStart) {
                val inputChars = userInput.substring(inputStart, inputEnd)
                textPaint.color = colorInputText
                for (i in inputChars.indices) {
                    val x = paddingLeft + i * charWidth
                    canvas.drawText(inputChars[i].toString(), x, yBase + rowHeight * 0.55f, textPaint)
                }
            }

            // Draw separator line
            val lineY = yBase + rowHeight * 0.82f
            canvas.drawLine(paddingLeft - 4f, lineY, paddingLeft + charWidth * CHARS_PER_ROW + 4f, lineY, separatorPaint)
        }

        // Draw cursor
        if (cursorVisible && userInput.length <= originalText.length) {
            val cursorRow = userInput.length / CHARS_PER_ROW
            val cursorCol = userInput.length % CHARS_PER_ROW
            val cursorX = paddingLeft + cursorCol * charWidth
            val cursorYTop = topPadding + cursorRow * rowHeight + rowHeight * 0.55f - textPaint.fontMetrics.ascent
            val cursorYBottom = cursorYTop + textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent

            cursorPaint.strokeWidth = 3f
            canvas.drawLine(cursorX, cursorYTop - 4f, cursorX, cursorYBottom + 2f, cursorPaint)
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
