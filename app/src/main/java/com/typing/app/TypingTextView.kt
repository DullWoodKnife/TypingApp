package com.typing.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class TypingTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B00FF")
        style = Paint.Style.FILL
    }

    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40000000")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    var cursorPosition: Int = -1
    var showCursor: Boolean = true

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layout = layout ?: return
        val textStr = text.toString()

        // Draw paragraph separators (after \n\n or at end of paragraphs)
        for (i in textStr.indices) {
            if (textStr[i] == '\n') {
                val lineNum = layout.getLineForOffset(i)
                val y = layout.getLineBottom(lineNum).toFloat() + 8f
                canvas.drawLine(
                    paddingLeft.toFloat(),
                    y,
                    (width - paddingRight).toFloat(),
                    y,
                    separatorPaint
                )
            }
        }

        // Draw purple vertical cursor bar
        if (cursorPosition >= 0 && cursorPosition <= textStr.length && showCursor) {
            val pos = cursorPosition.coerceAtMost(textStr.length - 1)
            val lineNum = layout.getLineForOffset(pos)
            val x = layout.getPrimaryHorizontal(pos)
            val yTop = layout.getLineTop(lineNum).toFloat()
            val yBottom = layout.getLineBottom(lineNum).toFloat()
            cursorPaint.strokeWidth = 3f
            canvas.drawLine(x, yTop, x, yBottom, cursorPaint)
        }
    }
}
