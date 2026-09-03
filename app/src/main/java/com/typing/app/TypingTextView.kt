package com.typing.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.ceil

class TypingTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val CHARS_PER_ROW = 17
        // 英文排版左右留白
        private const val EN_SIDE_PAD = 8f
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

    // 长按触点坐标（ACTION_DOWN 时记录，供宿主在长按回调里查询字符下标）
    private var downX = 0f
    private var downY = 0f

    // 外部设置的长按回调（参数：触点最近的字符下标；返回 true 表示消费）
    var onCharLongPress: ((index: Int) -> Boolean)? = null

    // 排版模式：true=英文（按字符自然宽度紧排、按单词换行），false=中文（17字固定网格）
    var isEnglishContent: Boolean = false
        private set

    private var needsLayout = true
    private var lastWidth = 0

    // 每个字符的绘制 x 坐标与所在行号（中文模式下与固定网格等价）
    private var charXs = FloatArray(0)
    private var charRows = IntArray(0)
    // 每行的起始字符下标 / 文本右边界
    private var rowStarts = IntArray(0)
    private var rowRights = FloatArray(0)
    private var layoutRows = 1
    private var gridPadding = 0f

    fun setTextData(original: String, input: String, showCursor: Boolean) {
        // 文本/输入无变化时（如光标闪烁刷新），只重绘光标，不做布局重算，
        // 避免 requestLayout 干扰父 ScrollView 的滚动位置
        if (original == originalText && input == userInput) {
            cursorVisible = showCursor
            invalidate()
            return
        }
        originalText = original
        userInput = input
        cursorVisible = showCursor
        isEnglishContent = detectEnglishContent(original)
        needsLayout = true
        invalidate()
        requestLayout()
    }

    // 内容判定：只要出现汉字/中文标点/全角字符就走中文网格排版，否则按英文紧排
    private fun detectEnglishContent(s: String): Boolean {
        if (s.isEmpty()) return false
        for (c in s) {
            val code = c.code
            if (code in 0x4E00..0x9FA5) return false      // 汉字
            if (code in 0x3000..0x303F) return false      // 中文标点
            if (code in 0xFF00..0xFFEF) return false      // 全角字符
        }
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)

        if (needsLayout || lastWidth != width || charWidth == 0f) {
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
            gridPadding = (width - charWidth * CHARS_PER_ROW) / 2f
            lastWidth = width
            computeLayout(width.toFloat())
            needsLayout = false
        }

        val height = (totalRows() * rowHeight + topPadding).toInt()
        setMeasuredDimension(width, height)
    }

    private fun totalRows(): Int {
        if (originalText.isEmpty()) return 1
        return if (isEnglishContent) layoutRows
        else ceil(originalText.length.toFloat() / CHARS_PER_ROW).toInt().coerceAtLeast(1)
    }

    // 计算每个字符的 x 坐标与行号，并汇总每行的起止下标与右边界
    private fun computeLayout(width: Float) {
        val n = originalText.length
        if (n == 0) {
            charXs = FloatArray(0)
            charRows = IntArray(0)
            rowStarts = IntArray(0)
            rowRights = FloatArray(0)
            layoutRows = 1
            return
        }

        charXs = FloatArray(n)
        charRows = IntArray(n)
        if (isEnglishContent) computeEnglishLayout(width, n) else computeChineseLayout(n)

        val starts = ArrayList<Int>()
        val rights = ArrayList<Float>()
        var curRow = 0
        var curRight = 0f
        starts.add(0)
        for (i in 0 until n) {
            if (charRows[i] != curRow) {
                rights.add(curRight)
                starts.add(i)
                curRow = charRows[i]
                curRight = 0f
            }
            val w = if (isEnglishContent) textPaint.measureText(originalText[i].toString()) else charWidth
            curRight = maxOf(curRight, charXs[i] + w)
        }
        rights.add(curRight)
        rowStarts = starts.toIntArray()
        rowRights = rights.toFloatArray()
        layoutRows = starts.size
    }

    // 中文：保持原有 17 字 / 行的固定网格，居中留白
    private fun computeChineseLayout(n: Int) {
        for (i in 0 until n) {
            charRows[i] = i / CHARS_PER_ROW
            charXs[i] = gridPadding + (i % CHARS_PER_ROW) * charWidth
        }
    }

    // 英文：字母按自然字宽紧挨排列，整词换行（超长词才强制断行）
    private fun computeEnglishLayout(width: Float, n: Int) {
        val left = EN_SIDE_PAD
        val right = width - EN_SIDE_PAD
        val spaceW = textPaint.measureText(" ")
        var x = left
        var row = 0
        var i = 0
        while (i < n) {
            if (originalText[i] == ' ') {
                charXs[i] = x
                charRows[i] = row
                x += spaceW
                i++
                continue
            }
            // 取一个“词”：到下一个空格为止（含尾随标点）
            var j = i
            while (j < n && originalText[j] != ' ') j++

            var tokenW = 0f
            for (k in i until j) tokenW += textPaint.measureText(originalText[k].toString())
            // 当前行放不下这个词就整体换行，避免单词被切断
            if (x + tokenW > right && x > left) {
                row++
                x = left
            }
            for (k in i until j) {
                val cw = textPaint.measureText(originalText[k].toString())
                if (x + cw > right && x > left) {   // 单个词仍然超宽时按字符断行
                    row++
                    x = left
                }
                charXs[k] = x
                charRows[k] = row
                x += cw
            }
            i = j
        }
    }

    // 供外部（自动滚动）查询某个字符下标所在的行
    fun rowOfIndex(index: Int): Int {
        if (index <= 0) return 0
        val i = index.coerceAtMost((originalText.length - 1).coerceAtLeast(0))
        return if (isEnglishContent && i < charRows.size) charRows[i] else i / CHARS_PER_ROW
    }

    // 暴露触点 x/y
    fun lastTouchX(): Float = downX
    fun lastTouchY(): Float = downY

    // 查找 (x, y) 最近的字符下标（必须在有效绘制区域内）。返回 -1 表示空文本。
    fun indexNear(x: Float, y: Float): Int {
        val n = originalText.length
        if (n == 0 || rowHeight <= 0f) return -1
        val rows = totalRows()
        val row = ((y - topPadding) / rowHeight).toInt().coerceIn(0, rows - 1)
        val rowStart = if (row < rowStarts.size) rowStarts[row] else n
        if (rowStart >= n) return n - 1
        val rowEnd = if (row + 1 < rowStarts.size) rowStarts[row + 1] else n
        var best = rowStart
        var bestDist = Float.MAX_VALUE
        for (i in rowStart until rowEnd) {
            val d = Math.abs(charXs[i] - x)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    // 给定下标，选中最近的"词"：汉字→返回该字；英文/数字→左右扩展到连续字母/数字串；
    // 标点/空格→向两侧找最近的汉字或英文词；空 → 返回 -1。
    fun selectWordAt(index: Int): IntArray {
        val n = originalText.length
        if (n == 0 || index < 0 || index >= n) return intArrayOf()
        val ch = originalText[index]
        if (isHanziChar(ch)) {
            return intArrayOf(index, index + 1)
        }
        if (ch.isLetterOrDigit()) {
            var s = index
            while (s > 0 && originalText[s - 1].isLetterOrDigit()) s--
            var e = index
            while (e < n && originalText[e].isLetterOrDigit()) e++
            // 如果全是 ASCII 字母/数字，归为"英文/数字词"
            return intArrayOf(s, e)
        }
        // 标点/空格：左右找最近的汉字或字母串
        var left = index - 1
        while (left >= 0 && !isHanziChar(originalText[left]) && !originalText[left].isLetterOrDigit()) left--
        var right = index + 1
        while (right < n && !isHanziChar(originalText[right]) && !originalText[right].isLetterOrDigit()) right++
        val leftDist = if (left >= 0) index - left else Int.MAX_VALUE
        val rightDist = if (right < n) right - index else Int.MAX_VALUE
        val pick = if (leftDist <= rightDist) left else right
        if (pick < 0 || pick >= n) return intArrayOf()
        return selectWordAt(pick)
    }

    private fun isHanziChar(c: Char): Boolean = c.code in 0x4E00..0x9FA5

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = event.x
            downY = event.y
        }
        return super.onTouchEvent(event)
    }

    override fun performLongClick(): Boolean {
        val cb = onCharLongPress ?: return super.performLongClick()
        val idx = indexNear(downX, downY)
        if (idx >= 0 && cb(idx)) return true
        return super.performLongClick()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = originalText.length
        if (n == 0) return

        val totalRows = totalRows()
        val fm = textPaint.fontMetrics

        for (row in 0 until totalRows) {
            val rowStart = if (row < rowStarts.size) rowStarts[row] else n
            val rowEnd = if (row + 1 < rowStarts.size) rowStarts[row + 1] else n
            if (rowStart >= rowEnd) continue

            val rowTop = topPadding + row * rowHeight

            // Row layout using percentages of rowHeight:
            // 0%    - 25%:  Original text area
            // 25%   - 55%:  Gap
            // 55%   - 75%:  Input text area
            // 75%   - 80%:  Small gap
            // 80%   - 82%:  Separator line
            // 82%   - 100%: Gap to next row

            // === Original text at 25% of row ===
            val originalBaseline = rowTop + rowHeight * 0.25f
            for (i in rowStart until rowEnd) {
                textPaint.color = getCharColor(i)
                canvas.drawText(originalText[i].toString(), charXs[i], originalBaseline, textPaint)
            }

            // === User input at 55% of row ===
            val inputBaseline = rowTop + rowHeight * 0.55f
            val inputEnd = minOf(userInput.length, rowEnd)
            if (inputEnd > rowStart) {
                textPaint.color = colorInputText
                for (i in rowStart until inputEnd) {
                    canvas.drawText(userInput[i].toString(), charXs[i], inputBaseline, textPaint)
                }
            }

            // === Separator line (right below input text descent) ===
            val lineY = inputBaseline + fm.descent + 4f
            val lineStart: Float
            val lineEnd: Float
            if (isEnglishContent) {
                // 英文：分隔线占满整个可用宽度，各行长度一致
                lineStart = 8f
                lineEnd = width - 8f
            } else {
                lineStart = gridPadding - 4f
                lineEnd = gridPadding + charWidth * CHARS_PER_ROW + 4f
            }
            canvas.drawLine(lineStart, lineY, lineEnd, lineY, separatorPaint)
        }

        // === Blue cursor on user input line ===
        if (cursorVisible && userInput.length <= n) {
            val idx = if (userInput.length < n) userInput.length else n - 1
            val row = charRows[idx]
            var cursorX = charXs[idx]
            if (userInput.length >= n) {
                // 已输完：光标停在最后一个字符右侧
                cursorX += if (isEnglishContent) textPaint.measureText(originalText[idx].toString()) else charWidth
            }

            val rowTop = topPadding + row * rowHeight
            val inputBaseline = rowTop + rowHeight * 0.55f
            val cursorTop = inputBaseline + fm.ascent - 4f
            val cursorBottom = inputBaseline + fm.descent + 2f

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
