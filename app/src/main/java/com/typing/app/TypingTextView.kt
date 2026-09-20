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

    init {
        // 必须显式启用长按，否则系统不会调度 performLongClick()，导致长按选词失效
        isLongClickable = true
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

    // 单字最大字号（sp）：横屏下控件宽度很大，据此限制每行高度，避免行高过高
    private val MAX_TEXT_SIZE_SP = 20f

    // 长按触点坐标（ACTION_DOWN 时记录，供宿主在长按回调里查询字符下标）
    private var downX = 0f
    private var downY = 0f

    // 外部设置的长按回调（参数：触点最近的字符下标；返回 true 表示消费）
    var onCharLongPress: ((index: Int) -> Boolean)? = null

    // ===== 选中/高亮/前后拖动选择 =====
    // 选择范围（字符下标，右开区间），-1 表示未选择
    private var selStart = -1
    private var selEnd = -1
    // 拖动手柄状态：0=无, 1=起点手柄, 2=终点手柄
    private var draggingHandle = 0
    private val handleRadius = 18f
    private val handleHitRadius = 46f
    private var startHandleX = 0f
    private var startHandleY = 0f
    private var endHandleX = 0f
    private var endHandleY = 0f

    // 选择变化回调：拖动前后手柄时通知宿主刷新查词
    var onSelectionChanged: ((start: Int, end: Int) -> Unit)? = null
    var onSelectionDismissed: (() -> Unit)? = null

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF")
        style = Paint.Style.FILL
    }

    // 黄色高亮（长按弹窗"高亮"按钮写入的持久高亮区域）
    private val yellowHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF176")
        style = Paint.Style.FILL
    }
    // 已高亮区域（字符下标，右开区间），按内容切换清空
    private val highlightedRanges = ArrayList<IntArray>()
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00BCD4")
        style = Paint.Style.FILL
    }

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
    // originalText 中非空白字符的下标，用于 userInput 与 originalText 对齐
    private var visibleIndices = IntArray(0)

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
        // 切换内容时清除长按选中状态与已高亮区域，避免残留到其它文章
        clearSelectionInternal()
        highlightedRanges.clear()
        needsLayout = true
        invalidate()
        requestLayout()
    }

    // 内容判定：按汉字占比决定排版模式。
    // 有效字符（非空白）中汉字/中文标点/全角字符占比 >= 30% 时走中文固定网格，否则按英文自然排版。
    // 这样混合文本（少量英文术语+大量中文）仍走中文网格；英文占主体的文本则按英文排版，避免单词被拆开。
    private fun detectEnglishContent(s: String): Boolean {
        if (s.isEmpty()) return false
        var cjkCount = 0
        var totalCount = 0
        for (c in s) {
            if (c.isWhitespace()) continue
            totalCount++
            val code = c.code
            if (code in 0x4E00..0x9FA5 || code in 0x3000..0x303F || code in 0xFF00..0xFFEF) {
                cjkCount++
            }
        }
        return if (totalCount == 0) false else cjkCount.toFloat() / totalCount < 0.30f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)

        if (needsLayout || lastWidth != width || charWidth == 0f) {
            // 横屏时控件宽度很大，若 textSize 完全按宽度计算，会导致每行字符过大、行高过高。
            // 对字号设上限，使每行高度在横竖屏下都保持合理（竖屏通常已小于该上限，不受影响）。
            val maxTextSize = MAX_TEXT_SIZE_SP * resources.displayMetrics.scaledDensity
            textPaint.textSize = minOf(width / (CHARS_PER_ROW + 1f), maxTextSize)
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

    // 供外部（自动滚动）查询内容总行数
    fun totalRows(): Int {
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
            visibleIndices = IntArray(0)
            layoutRows = 1
            return
        }

        charXs = FloatArray(n)
        charRows = IntArray(n)
        if (isEnglishContent) computeEnglishLayout(width, n) else computeChineseLayout(n)

        val starts = ArrayList<Int>()
        val rights = ArrayList<Float>()
        val vis = ArrayList<Int>()
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
            if (!originalText[i].isWhitespace()) vis.add(i)
        }
        rights.add(curRight)
        rowStarts = starts.toIntArray()
        rowRights = rights.toFloatArray()
        visibleIndices = vis.toIntArray()
        layoutRows = starts.size
    }

    // 中文：固定网格，但空格/窄空格宽度缩小，避免成语/歇后语间隔过大
    private fun computeChineseLayout(n: Int) {
        val spaceW = charWidth * 0.3f
        var row = 0
        var x = gridPadding
        var count = 0
        for (i in 0 until n) {
            val c = originalText[i]
            val w = if (c.isWhitespace()) spaceW else charWidth
            if (x + w > gridPadding + charWidth * CHARS_PER_ROW && count > 0) {
                row++
                x = gridPadding
                count = 0
            }
            charRows[i] = row
            charXs[i] = x
            x += w
            count++
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
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                // 若已有选择，判断是否点在某个手柄上，进入拖动手柄状态（前后扩展选择）
                if (selStart in 0 until n_() && selEnd in 0..n_()) {
                    val ds = dist(startHandleX, startHandleY, event.x, event.y)
                    val de = dist(endHandleX, endHandleY, event.x, event.y)
                    if (ds <= handleHitRadius && ds <= de) {
                        draggingHandle = 1
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    } else if (de <= handleHitRadius) {
                        draggingHandle = 2
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    } else {
                        // 点击选中区域以外的地方 → 取消选中并通知宿主关闭弹窗
                        clearSelection()
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingHandle != 0) {
                    val idx = indexNear(event.x, event.y)
                    if (idx >= 0) {
                        if (draggingHandle == 1) {
                            val newStart = if (idx <= selEnd) idx else selEnd
                            if (newStart != selStart) {
                                selStart = newStart
                                updateHandlePositions()
                                onSelectionChanged?.invoke(selStart, selEnd)
                                invalidate()
                            }
                        } else {
                            val newEnd = if (idx >= selStart) idx + 1 else selStart + 1
                            val capped = newEnd.coerceAtMost(n_())
                            if (capped != selEnd) {
                                selEnd = capped
                                updateHandlePositions()
                                onSelectionChanged?.invoke(selStart, selEnd)
                                invalidate()
                            }
                        }
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (draggingHandle != 0) {
                    draggingHandle = 0
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun n_(): Int = originalText.length

    // 清除长按选中状态（高亮+手柄），并通知宿主（用于关闭查词弹窗）
    fun clearSelection() {
        if (selStart == -1 && selEnd == -1) return
        clearSelectionInternal()
        invalidate()
        onSelectionDismissed?.invoke()
    }

    // 静默清除选中状态（不回调），供内容切换等内部场景使用
    private fun clearSelectionInternal() {
        selStart = -1
        selEnd = -1
        draggingHandle = 0
    }

    // 当前是否处于选中状态
    fun hasSelection(): Boolean = selStart in 0 until n_() && selEnd in 0..n_() && selEnd > selStart

    // 当前选中文本
    fun selectedText(): String {
        if (!hasSelection()) return ""
        return originalText.substring(selStart, selEnd)
    }

    // 将当前选中区域加入黄色高亮
    fun addHighlightFromSelection() {
        if (!hasSelection()) return
        highlightedRanges.add(intArrayOf(selStart, selEnd))
        clearSelectionInternal()
        invalidate()
        onSelectionDismissed?.invoke()
    }

    fun clearHighlights() {
        highlightedRanges.clear()
        invalidate()
    }

    // 静默清除选中（不触发回调），供弹窗因外部点击消失时同步清除高亮，避免回调递归
    fun clearSelectionSilently() {
        if (selStart == -1 && selEnd == -1) return
        clearSelectionInternal()
        invalidate()
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2; val dy = y1 - y2
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    // 依据当前选择边界，在屏幕上定位起止手柄的坐标（并缓存供触摸检测与绘制使用）
    private fun updateHandlePositions() {
        if (selStart in 0 until n_()) {
            val r1 = charRows[selStart]
            startHandleX = charXs[selStart]
            startHandleY = topPadding + r1 * rowHeight + rowHeight * 0.25f
        }
        if (selEnd in 1..n_()) {
            val last = selEnd - 1
            val r2 = charRows[last]
            var xx = charXs[last]
            if (isEnglishContent) xx += textPaint.measureText(originalText[last].toString())
            else xx += charWidth
            endHandleX = xx
            endHandleY = topPadding + r2 * rowHeight + rowHeight * 0.25f
        }
    }

    override fun performLongClick(): Boolean {
        val cb = onCharLongPress ?: return super.performLongClick()
        val idx = indexNear(downX, downY)
        if (idx >= 0 && cb(idx)) {
            // 长按成功：记录选择范围并绘制高亮与手柄
            val range = selectWordAt(idx)
            if (range.size == 2) {
                selStart = range[0]
                selEnd = range[1]
                updateHandlePositions()
                // 说明：这里不再回调 onSelectionChanged。长按回调 onCharLongPress 已经弹出了查词弹窗，
                // 若再次触发会重复弹出第二个弹窗，而第二个弹窗 show 前会 dismiss 第一个弹窗，
                // 触发其 OnDismissListener 清空刚刚建立的选中（selStart/selEnd），导致蓝色选区和手柄立即消失。
                invalidate()
            }
            return true
        }
        return super.performLongClick()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = originalText.length
        if (n == 0) return

        val totalRows = totalRows()
        val fm = textPaint.fontMetrics

        // userInput 与 visibleIndices 的跨行同步下标
        var ui = 0
        var vi = 0

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

            // === 已"高亮"的黄色背景块（长按弹窗"高亮"按钮写入）===
            for (hr in highlightedRanges) {
                val hs2 = maxOf(hr[0], rowStart)
                val he2 = minOf(hr[1], rowEnd)
                if (he2 > hs2) {
                    var hLeft2 = charXs[hs2]
                    var hRight2 = charXs[he2 - 1]
                    if (isEnglishContent) hRight2 += textPaint.measureText(originalText[he2 - 1].toString())
                    else hRight2 += charWidth
                    val yTop = originalBaseline + fm.ascent - 2f
                    val yBottom = originalBaseline + fm.descent + 2f
                    canvas.drawRect(hLeft2, yTop, hRight2, yBottom, yellowHighlightPaint)
                }
            }

            // === 选中词高亮背景（与字形实际高度一致的矩形色块）===
            if (selStart in 0..n && selEnd in selStart..n && selEnd > selStart) {
                val hs = maxOf(selStart, rowStart)
                val he = minOf(selEnd, rowEnd)
                if (he > hs) {
                    var hLeft = charXs[hs]
                    var hRight = charXs[he - 1]
                    if (isEnglishContent) hRight += textPaint.measureText(originalText[he - 1].toString())
                    else hRight += charWidth
                    // 依据字体的 ascent/descent 计算与字形实际高度匹配的矩形上下边界
                    val glyphTop = originalBaseline + fm.ascent - 2f
                    val glyphBottom = originalBaseline + fm.descent + 2f
                    canvas.drawRect(hLeft, glyphTop, hRight, glyphBottom, highlightPaint)
                }
            }

            for (i in rowStart until rowEnd) {
                textPaint.color = getCharColor(i)
                canvas.drawText(originalText[i].toString(), charXs[i], originalBaseline, textPaint)
            }

            // === User input at 55% of row ===
            // userInput 按 visibleIndices 对齐：跳过双方空格，将输入字符与 originalText 的非空白字符一一对应
            val inputBaseline = rowTop + rowHeight * 0.55f
            if (userInput.isNotEmpty() && visibleIndices.isNotEmpty()) {
                textPaint.color = colorInputText
                // 同步跳过本行之前的所有内容
                while (vi < visibleIndices.size && visibleIndices[vi] < rowStart && ui < userInput.length) {
                    if (!userInput[ui].isWhitespace()) vi++
                    ui++
                }
                // 画本行范围内的
                while (ui < userInput.length && vi < visibleIndices.size && visibleIndices[vi] < rowEnd) {
                    if (userInput[ui].isWhitespace()) { ui++; continue }
                    val origIdx = visibleIndices[vi]
                    canvas.drawText(userInput[ui].toString(), charXs[origIdx], inputBaseline, textPaint)
                    ui++; vi++
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
        if (cursorVisible && visibleIndices.isNotEmpty()) {
            // 统计 userInput 中已输入的非空格字符数，以此定位光标
            var matchedVisCount = 0
            var ui = 0
            var vi = 0
            while (ui < userInput.length && vi < visibleIndices.size) {
                if (userInput[ui].isWhitespace()) { ui++; continue }
                matchedVisCount++; ui++; vi++
            }
            val (row, cursorX) = if (matchedVisCount < visibleIndices.size) {
                val idx = visibleIndices[matchedVisCount]
                charRows[idx] to charXs[idx]
            } else {
                // 已输完：光标停在最后一个字符右侧
                val idx = visibleIndices.last()
                val cw = if (isEnglishContent) textPaint.measureText(originalText[idx].toString()) else charWidth
                charRows[idx] to (charXs[idx] + cw)
            }

            val rowTop = topPadding + row * rowHeight
            val inputBaseline = rowTop + rowHeight * 0.55f
            val cursorTop = inputBaseline + fm.ascent - 4f
            val cursorBottom = inputBaseline + fm.descent + 2f

            canvas.drawLine(cursorX, cursorTop, cursorX, cursorBottom, cursorPaint)
        }

        // === 选中词两侧的拖动手柄（前后扩展选择）===
        if (selStart in 0 until n && selEnd in (selStart + 1)..n) {
            canvas.drawCircle(startHandleX, startHandleY, handleRadius, handlePaint)
            canvas.drawCircle(endHandleX, endHandleY, handleRadius, handlePaint)
        }
    }

    private fun getCharColor(index: Int): Int {
        if (index >= originalText.length) return colorPending
        val c = originalText[index]
        if (c.isWhitespace()) return colorPending
        // 如果用户还没输入任何内容，所有非空格字符都显示为 pending
        if (userInput.isEmpty()) return colorPending
        // 统计 originalText[0..index] 中的非空格字符数
        var origVis = 0
        for (i in 0..index) {
            if (!originalText[i].isWhitespace()) origVis++
        }
        // 在 userInput 中找到第 origVis 个非空格字符的位置
        var ui = 0
        var uiVis = 0
        while (ui < userInput.length && uiVis < origVis) {
            if (!userInput[ui].isWhitespace()) uiVis++
            ui++
        }
        // 如果 userInput 的非空格字符数不足以覆盖到当前位置，显示 pending（黑色）
        if (uiVis < origVis) return colorPending
        // 此时 ui-1 就是对应的 userInput 字符
        val match = ui > 0 && !userInput[ui - 1].isWhitespace() && userInput[ui - 1] == c
        return if (match) colorCorrect else colorWrong
    }
}
