package com.typing.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    // Data
    private var appData = JSONObject()
    private var currentContentId = ""
    private var practiceMode = "normal"
    private var userInput = ""
    private var isRunning = false
    private var isFinished = false
    private var isComposing = false
    private var startTime = 0L
    private var elapsed = 0
    private var editId: String? = null
    private var selectMode = false
    private var modalCallback: (() -> Unit)? = null

    // Timer & cursor
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var cursorVisible = true
    private val cursorRunnable = object : Runnable {
        override fun run() {
            cursorVisible = !cursorVisible
            updateTextDisplay(keepScroll = true)
            handler.postDelayed(this, 500)
        }
    }

    // Views - pages
    private lateinit var pageHome: View
    private lateinit var pagePractice: View
    private lateinit var pageContentList: View
    private lateinit var pageContentEdit: View
    private lateinit var pageContentDetail: View
    private lateinit var pageRecords: View
    private lateinit var pageChallenge: View
    private lateinit var modalOverlay: FrameLayout

    // Practice views
    private lateinit var statTime: TextView
    private lateinit var statProgress: TextView
    private lateinit var statSpeed: TextView
    private lateinit var statAcc: TextView
    private lateinit var practiceHint: TextView
    private lateinit var wubiHint: TextView
    private var wubiTable: HashMap<String, String>? = null
    private lateinit var typingScrollView: ScrollView
    private lateinit var typingTextView: TypingTextView
    private lateinit var hiddenInput: EditText

    // Content list
    private lateinit var contentListTitle: TextView
    private lateinit var contentListContainer: LinearLayout

    // Content edit
    private lateinit var editPageTitle: TextView
    private lateinit var editTitleInput: EditText
    private lateinit var editContentInput: EditText

    // Content detail
    private lateinit var detailPageTitle: TextView
    private lateinit var detailMeta: TextView
    private lateinit var detailContent: TextView

    // Records
    private lateinit var recordsContainer: LinearLayout

    // Challenge
    private lateinit var badgeIcon: TextView
    private lateinit var badgeTitle: TextView
    private lateinit var badgeSub: TextView
    private lateinit var rankProgress: ProgressBar
    private lateinit var rankInfo: TextView
    private lateinit var csGames: TextView
    private lateinit var csBestSpeed: TextView
    private lateinit var csBestAcc: TextView

    // Modal
    private lateinit var modalTitle: TextView
    private lateinit var modalBody: TextView
    private lateinit var modalBtn: Button

    // Nav items
    private lateinit var navChallenge: View
    private lateinit var navHome: View
    private lateinit var navRecords: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadData()
        bindViews()
        setupListeners()
        showPage("home")
    }

    private fun loadData() {
        val prefs = getSharedPreferences("typing_app_data", Context.MODE_PRIVATE)
        val json = prefs.getString("data", null)
        if (json != null) {
            try {
                appData = JSONObject(json)
            } catch (e: Exception) {
                appData = defaultData()
            }
        } else {
            appData = defaultData()
        }
        val contents = appData.optJSONArray("contents")
        if (contents != null && contents.length() > 0) {
            currentContentId = contents.getJSONObject(0).getString("id")
        }
    }

    private fun saveData() {
        val prefs = getSharedPreferences("typing_app_data", Context.MODE_PRIVATE)
        prefs.edit().putString("data", appData.toString()).apply()
    }

    private fun defaultData(): JSONObject {
        val now = System.currentTimeMillis()
        val data = JSONObject()
        val contents = JSONArray()

        val items = arrayOf(
            arrayOf("c1", "五课", "古云：不矜细行，终累大德。为山九仞，功亏一篑。此圣贤之深戒也。然今之士人，多务虚名而鲜实学，逐末忘本，舍近求远。是以所学非所用，所用非所学，终无成焉。"),
            arrayOf("c2", "正气歌", "天地有正气，杂然赋流形。下则为河岳，上则为日星。于人曰浩然，沛乎塞苍冥。皇路当清夷，含和吐明庭。时穷节乃见，一一垂丹青。"),
            arrayOf("c3", "说园", "园有静观、动观之分。小园以静观为主，大园以动观为主。静观者，如静坐斋中，平视远眺，景随人意；动观者，步移景换，如游画中。"),
            arrayOf("c4", "清静经", "大道无形，生育天地；大道无情，运行日月；大道无名，长养万物。吾不知其名，强名曰道。夫道者，有清有浊，有动有静。"),
            arrayOf("c5", "夏夜晚风", "夏夜的风，带着白天的余温，轻轻拂过脸颊。远处的蝉鸣此起彼伏，像是大自然的交响乐。星空下，一切都显得那么宁静而美好。"),
            arrayOf("c6", "千字文", "天地玄黄，宇宙洪荒。日月盈昃，辰宿列张。寒来暑往，秋收冬藏。闰余成岁，律吕调阳。云腾致雨，露结为霜。"),
            arrayOf("c7", "孙子兵法始计篇", "孙子曰：兵者，国之大事，死生之地，存亡之道，不可不察也。故经之以五事，校之以计，而索其情：一曰道，二曰天，三曰地，四曰将，五曰法。"),
            arrayOf("c8", "三字经", "人之初，性本善。性相近，习相远。苟不教，性乃迁。教之道，贵以专。昔孟母，择邻处。子不学，断机杼。窦燕山，有义方。教五子，名俱扬。"),
            arrayOf("c9", "般若波罗蜜多心经", "观自在菩萨，行深般若波罗蜜多时，照见五蕴皆空，度一切苦厄。舍利子，色不异空，空不异色，色即是空，空即是色。受想行识，亦复如是。")
        )

        for (item in items) {
            val obj = JSONObject()
            obj.put("id", item[0])
            obj.put("title", item[1])
            obj.put("content", item[2])
            obj.put("createdAt", now)
            contents.put(obj)
        }

        data.put("contents", contents)
        data.put("records", JSONArray())
        return data
    }

    private fun bindViews() {
        pageHome = findViewById(R.id.page_home)
        pagePractice = findViewById(R.id.page_practice)
        pageContentList = findViewById(R.id.page_content_list)
        pageContentEdit = findViewById(R.id.page_content_edit)
        pageContentDetail = findViewById(R.id.page_content_detail)
        pageRecords = findViewById(R.id.page_records)
        pageChallenge = findViewById(R.id.page_challenge)
        modalOverlay = findViewById(R.id.modal_overlay)

        statTime = findViewById(R.id.stat_time)
        statProgress = findViewById(R.id.stat_progress)
        statSpeed = findViewById(R.id.stat_speed)
        statAcc = findViewById(R.id.stat_acc)
        practiceHint = findViewById(R.id.practice_hint)
        wubiHint = findViewById(R.id.wubi_hint)
        typingScrollView = findViewById(R.id.typing_scroll_view)
        typingTextView = findViewById(R.id.typing_text_view)
        hiddenInput = findViewById(R.id.hidden_input)

        contentListTitle = findViewById(R.id.content_list_title)
        contentListContainer = findViewById(R.id.content_list_container)

        editPageTitle = findViewById(R.id.edit_page_title)
        editTitleInput = findViewById(R.id.edit_title_input)
        editContentInput = findViewById(R.id.edit_content_input)

        detailPageTitle = findViewById(R.id.detail_page_title)
        detailMeta = findViewById(R.id.detail_meta)
        detailContent = findViewById(R.id.detail_content)

        recordsContainer = findViewById(R.id.records_container)

        badgeIcon = findViewById(R.id.badge_icon)
        badgeTitle = findViewById(R.id.badge_title)
        badgeSub = findViewById(R.id.badge_sub)
        rankProgress = findViewById(R.id.rank_progress)
        rankInfo = findViewById(R.id.rank_info)
        csGames = findViewById(R.id.cs_games)
        csBestSpeed = findViewById(R.id.cs_best_speed)
        csBestAcc = findViewById(R.id.cs_best_acc)

        modalTitle = findViewById(R.id.modal_title)
        modalBody = findViewById(R.id.modal_body)
        modalBtn = findViewById(R.id.modal_btn)

        navChallenge = findViewById(R.id.nav_challenge)
        navHome = findViewById(R.id.nav_home)
        navRecords = findViewById(R.id.nav_records)
    }

    private fun setupListeners() {
        // Home buttons
        findViewById<Button>(R.id.btn_normal_practice).setOnClickListener {
            practiceMode = "normal"
            if (!hasContents()) {
                showModal(getString(R.string.title_cannot_empty), getString(R.string.msg_add_content_first))
                return@setOnClickListener
            }
            showPage("practice")
            initPractice()
        }

        findViewById<Button>(R.id.btn_timed_practice).setOnClickListener {
            practiceMode = "timed"
            if (!hasContents()) {
                showModal(getString(R.string.title_cannot_empty), getString(R.string.msg_add_content_first))
                return@setOnClickListener
            }
            showPage("practice")
            initPractice()
        }

        findViewById<Button>(R.id.btn_go_content_list).setOnClickListener {
            selectMode = false
            showPage("contentList")
            renderContentList()
        }

        // Practice buttons
        findViewById<Button>(R.id.btn_select_content).setOnClickListener {
            selectMode = true
            showPage("contentList")
            renderContentList()
        }

        findViewById<Button>(R.id.btn_restart).setOnClickListener {
            initPractice()
        }

        findViewById<Button>(R.id.btn_back_home).setOnClickListener {
            stopTimer()
            stopCursorBlink()
            showPage("home")
        }

        // Typing text view click -> focus hidden input
        typingTextView.setOnClickListener {
            if (!isFinished) {
                focusInput()
            }
        }

        // Hidden input text watcher
        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFinished || isComposing) return
                handleInput(s?.toString() ?: "")
            }
        })

        // Content list buttons
        findViewById<Button>(R.id.btn_add_content_item).setOnClickListener {
            openEdit(null)
        }

        // Content edit buttons
        findViewById<Button>(R.id.btn_cancel_edit).setOnClickListener {
            showPage("contentList")
            renderContentList()
        }

        findViewById<Button>(R.id.btn_save_content).setOnClickListener {
            saveContent()
        }

        // Content detail
        findViewById<Button>(R.id.btn_back_to_list).setOnClickListener {
            showPage("contentList")
            renderContentList()
        }

        // Records
        findViewById<Button>(R.id.btn_clear_records).setOnClickListener {
            clearRecords()
        }

        // Challenge
        findViewById<Button>(R.id.btn_start_challenge).setOnClickListener {
            startChallenge()
        }

        // Modal
        modalBtn.setOnClickListener {
            hideModal()
        }

        modalOverlay.setOnClickListener {
            hideModal()
        }

        // Bottom nav
        navHome.setOnClickListener { showPage("home") }
        navChallenge.setOnClickListener {
            showPage("challenge")
            updateChallengePage()
        }
        navRecords.setOnClickListener {
            showPage("records")
            renderRecords()
        }
    }

    private fun hasContents(): Boolean {
        val contents = appData.optJSONArray("contents")
        return contents != null && contents.length() > 0
    }

    // ===== Page Navigation =====

    private fun showPage(pageId: String) {
        pageHome.visibility = View.GONE
        pagePractice.visibility = View.GONE
        pageContentList.visibility = View.GONE
        pageContentEdit.visibility = View.GONE
        pageContentDetail.visibility = View.GONE
        pageRecords.visibility = View.GONE
        pageChallenge.visibility = View.GONE

        when (pageId) {
            "home" -> pageHome.visibility = View.VISIBLE
            "practice" -> pagePractice.visibility = View.VISIBLE
            "contentList" -> pageContentList.visibility = View.VISIBLE
            "contentEdit" -> pageContentEdit.visibility = View.VISIBLE
            "contentDetail" -> pageContentDetail.visibility = View.VISIBLE
            "records" -> pageRecords.visibility = View.VISIBLE
            "challenge" -> pageChallenge.visibility = View.VISIBLE
        }

        // Update nav active state
        updateNavActive(pageId)
    }

    private fun updateNavActive(pageId: String) {
        val activeColor = Color.parseColor("#E65C53")
        val inactiveColor = Color.parseColor("#59000000")

        val navMap = mapOf(
            "home" to navHome,
            "practice" to navHome,
            "challenge" to navChallenge,
            "records" to navRecords,
            "contentList" to navHome,
            "contentEdit" to navHome,
            "contentDetail" to navHome
        )

        val activeNav = navMap[pageId] ?: navHome

        listOf(navChallenge, navHome, navRecords).forEach { nav ->
            val labelView = (nav as LinearLayout).getChildAt(1) as TextView
            if (nav === activeNav) {
                labelView.setTextColor(activeColor)
                labelView.typeface = Typeface.DEFAULT_BOLD
            } else {
                labelView.setTextColor(inactiveColor)
                labelView.typeface = Typeface.DEFAULT
            }
        }
    }

    // ===== 86版五笔拆字提示 =====

    // 加载 assets/wubi86.txt：覆盖2013年《通用规范汉字表》全部8105字
    // 每行格式：字\t全码,简码…（完整码在前，简码逗号隔开跟后，如：仞\tWVYY,WVY）
    private fun loadWubiTable(): HashMap<String, String> {
        val map = HashMap<String, String>()
        try {
            assets.open("wubi86.txt").bufferedReader().useLines { lines ->
                for (line in lines) {
                    val t = line.trim()
                    if (t.isEmpty()) continue
                    val idx = t.indexOf('\t')
                    if (idx > 0) map[t.substring(0, idx)] = t.substring(idx + 1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    private fun getWubiCode(ch: Char): String {
        val table = wubiTable ?: loadWubiTable().also { wubiTable = it }
        return table[ch.toString()] ?: ""
    }

    // 在"正在输入…"同行最右侧，显示光标当前位置汉字的86版五笔拆字（完整码,简码）
    private fun updateWubiHint(text: String) {
        val idx = userInput.length
        var code = ""
        if (!isFinished && idx < text.length) {
            code = getWubiCode(text[idx])
        }
        if (wubiHint.text.toString() != code) {
            wubiHint.text = code
        }
    }

    // ===== Practice =====

    private fun initPractice() {
        val content = getContent(currentContentId)
        if (content == null) {
            showModal(getString(R.string.title_cannot_empty), getString(R.string.msg_select_content)) {
                selectMode = true
                showPage("contentList")
                renderContentList()
            }
            return
        }

        stopTimer()
        stopCursorBlink()
        isRunning = false
        isFinished = false
        elapsed = 0
        userInput = ""

        statTime.text = "00:00"
        statTime.setTextColor(Color.parseColor("#CC000000"))
        statProgress.text = "0%"
        statSpeed.text = "0"
        statAcc.text = "0%"
        practiceHint.text = getString(R.string.practice_hint)

        hiddenInput.setText("")
        cursorVisible = true

        val text = content.getString("content")
        typingTextView.setTextData(text, userInput, cursorVisible)
        updateWubiHint(text)
        startCursorBlink()

        // 蓝牙/物理键盘已连接：进入练习页自动聚焦，无需点屏幕即可直接打字
        if (hasHardKeyboard()) {
            hiddenInput.requestFocus()
        }
    }

    private fun updateTextDisplay(keepScroll: Boolean = false) {
        val content = getContent(currentContentId) ?: return
        val text = content.getString("content")
        typingTextView.setTextData(text, userInput, cursorVisible)
        updateWubiHint(text)
        // 仅在输入内容变化时自动滚动到当前行；光标闪烁(keepScroll=true)时保留用户手动滚动位置
        if (!keepScroll) {
            autoScrollToCurrent()
        }
    }

    private fun autoScrollToCurrent() {
        val inputLen = userInput.length
        val content = getContent(currentContentId) ?: return
        val text = content.getString("content")
        if (inputLen >= text.length) return

        // Calculate which row the cursor is on (each row has CHARS_PER_ROW characters)
        val currentRow = inputLen / TypingTextView.CHARS_PER_ROW
        val y = (currentRow * typingTextView.rowHeight).toInt()

        val scrollViewHeight = typingScrollView.height
        val scrollY = typingScrollView.scrollY

        // If the current row is below the visible area, scroll down
        if (y > scrollY + scrollViewHeight * 0.6) {
            typingScrollView.smoothScrollTo(0, y - scrollViewHeight / 3)
        } else if (y < scrollY) {
            typingScrollView.smoothScrollTo(0, y)
        }
    }

    private fun startCursorBlink() {
        handler.removeCallbacks(cursorRunnable)
        cursorVisible = true
        handler.post(cursorRunnable)
    }

    private fun stopCursorBlink() {
        handler.removeCallbacks(cursorRunnable)
    }

    private fun focusInput() {
        hiddenInput.requestFocus()
        if (hasHardKeyboard()) {
            // 蓝牙/物理键盘已连接：不弹软键盘，保持全屏干净，直接物理按键输入
            hideKeyboard()
        } else {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT)
        }
        if (!isFinished) {
            practiceHint.text = getString(R.string.inputting)
        }
    }

    private fun handleInput(text: String) {
        val content = getContent(currentContentId) ?: return
        val originalText = content.getString("content")

        if (!isRunning && text.isNotEmpty()) {
            isRunning = true
            startTime = System.currentTimeMillis()
            elapsed = 0
            practiceHint.text = getString(R.string.inputting)
            startTimer()
        }

        // Limit input length
        userInput = if (text.length > originalText.length) {
            val limited = text.substring(0, originalText.length)
            hiddenInput.setText(limited)
            limited
        } else {
            text
        }

        updateTextDisplay()
        updateStats(originalText)

        // Check completion
        if (userInput.length >= originalText.length && !isFinished) {
            isFinished = true
            stopTimer()
            stopCursorBlink()
            hideKeyboard()
            practiceHint.text = getString(R.string.completed)

            val stats = recalcStats(originalText)
            val secs = if (elapsed > 0) elapsed else 1
            val speed = Math.round(stats[0].toFloat() / secs * 60)
            val acc = if (stats[0] + stats[1] > 0) Math.round(stats[0].toFloat() / (stats[0] + stats[1]) * 100) else 0
            saveRecord(stats[0], acc, speed)

            showModal(
                getString(R.string.modal_complete),
                "速度：$speed 字/分\n正确率：$acc%\n正确：${stats[0]} 字\n错误：${stats[1]} 字\n用时：$secs 秒"
            ) {
                updateChallengePage()
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(hiddenInput.windowToken, 0)
    }

    private fun recalcStats(text: String): IntArray {
        var correct = 0
        var wrong = 0
        for (i in userInput.indices) {
            if (i < text.length) {
                if (userInput[i] == text[i]) correct++ else wrong++
            }
        }
        return intArrayOf(correct, wrong)
    }

    private fun updateStats(text: String) {
        val stats = recalcStats(text)
        val totalLen = text.length
        val prog = if (totalLen > 0) Math.min(100, Math.round(userInput.length.toFloat() / totalLen * 100)) else 0
        statProgress.text = "$prog%"

        val acc = if (stats[0] + stats[1] > 0) Math.round(stats[0].toFloat() / (stats[0] + stats[1]) * 100) else 0
        statAcc.text = "$acc%"

        val secs = if (elapsed > 0) elapsed else 1
        val speed = Math.round(stats[0].toFloat() / secs * 60)
        statSpeed.text = "$speed"

        if (practiceMode == "timed") {
            val remain = 60 - elapsed
            val min = remain / 60
            val sec = remain % 60
            val timeStr = String.format("%02d:%02d", min, sec)
            statTime.text = timeStr
            if (remain <= 10) {
                statTime.setTextColor(Color.parseColor("#E65C53"))
            } else {
                statTime.setTextColor(Color.parseColor("#CC000000"))
            }
        }
    }

    // ===== Timer =====

    private fun startTimer() {
        if (timerRunnable != null) return
        timerRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()

                if (practiceMode == "timed") {
                    if (elapsed >= 60) {
                        stopTimer()
                        finishTimedPractice()
                        return
                    }
                    val remain = 60 - elapsed
                    val min = remain / 60
                    val sec = remain % 60
                    statTime.text = String.format("%02d:%02d", min, sec)
                    if (remain <= 10) {
                        statTime.setTextColor(Color.parseColor("#E65C53"))
                    } else {
                        statTime.setTextColor(Color.parseColor("#CC000000"))
                    }
                } else {
                    val min = elapsed / 60
                    val sec = elapsed % 60
                    statTime.text = String.format("%02d:%02d", min, sec)
                }

                val content = getContent(currentContentId)
                if (content != null) {
                    updateStats(content.getString("content"))
                }

                handler.postDelayed(this, 200)
            }
        }
        handler.postDelayed(timerRunnable!!, 200)
    }

    private fun stopTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
    }

    private fun finishTimedPractice() {
        isRunning = false
        isFinished = true
        stopCursorBlink()
        hideKeyboard()
        practiceHint.text = getString(R.string.time_up)

        val content = getContent(currentContentId) ?: return
        val text = content.getString("content")
        userInput = hiddenInput.text.toString()
        if (userInput.length > text.length) {
            userInput = text
            hiddenInput.setText(userInput)
        }

        val stats = recalcStats(text)
        updateTextDisplay()

        val secs = 60
        val speed = Math.round(stats[0].toFloat() / secs * 60)
        val acc = if (stats[0] + stats[1] > 0) Math.round(stats[0].toFloat() / (stats[0] + stats[1]) * 100) else 0
        saveRecord(stats[0], acc, speed)
        updateStats(text)

        showModal(
            getString(R.string.modal_timeout),
            "速度：$speed 字/分\n正确率：$acc%\n正确：${stats[0]} 字\n错误：${stats[1]} 字"
        ) {
            updateChallengePage()
        }
    }

    // ===== Records =====

    private fun saveRecord(correct: Int, acc: Int, speed: Int) {
        val content = getContent(currentContentId)
        val stats = recalcStats(content?.optString("content", "") ?: "")

        val record = JSONObject()
        record.put("id", "r${System.currentTimeMillis()}")
        record.put("contentId", currentContentId)
        record.put("contentTitle", content?.optString("title") ?: "未知")
        record.put("mode", practiceMode)
        record.put("speed", speed)
        record.put("accuracy", acc)
        record.put("correctChars", correct)
        record.put("wrongChars", stats[1])
        record.put("totalChars", correct + stats[1])
        record.put("duration", elapsed)
        record.put("date", System.currentTimeMillis())

        val records = appData.optJSONArray("records") ?: JSONArray()
        records.put(record)
        appData.put("records", records)
        saveData()
    }

    private fun renderRecords() {
        recordsContainer.removeAllViews()
        val records = appData.optJSONArray("records") ?: JSONArray()

        if (records.length() == 0) {
            val emptyView = TextView(this).apply {
                text = getString(R.string.empty_records)
                setTextColor(Color.parseColor("#59000000"))
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 80, 0, 80)
            }
            recordsContainer.addView(emptyView)
            return
        }

        // Reverse order
        for (i in records.length() - 1 downTo 0) {
            val r = records.getJSONObject(i)
            val item = createRecordItemView(r)
            recordsContainer.addView(item)
        }
    }

    private fun createRecordItemView(r: JSONObject): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 24)
        }

        val leftLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleView = TextView(this).apply {
            text = r.optString("contentTitle", "未知")
            textSize = 15f
            setTextColor(Color.parseColor("#CC000000"))
            typeface = Typeface.DEFAULT_BOLD
        }

        val date = java.util.Date(r.optLong("date"))
        val cal = java.util.Calendar.getInstance()
        cal.time = date
        val dateStr = String.format(
            "%d月%d日 %02d:%02d",
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
        val modeLabel = if (r.optString("mode") == "timed") "限时" else "普通"
        val metaStr = "$dateStr · ${modeLabel}模式 · 正确率${r.optInt("accuracy")}% "

        val metaView = TextView(this).apply {
            text = metaStr
            textSize = 11f
            setTextColor(Color.parseColor("#80000000"))
        }

        leftLayout.addView(titleView)
        leftLayout.addView(metaView)

        val rightLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.CENTER_VERTICAL
        }

        val speedView = TextView(this).apply {
            text = "${r.optInt("speed")}"
            textSize = 16f
            setTextColor(Color.parseColor("#E65C53"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
        }

        val unitView = TextView(this).apply {
            text = getString(R.string.chars_per_min)
            textSize = 11f
            setTextColor(Color.parseColor("#80000000"))
            gravity = android.view.Gravity.CENTER
        }

        rightLayout.addView(speedView)
        rightLayout.addView(unitView)

        layout.addView(leftLayout)
        layout.addView(rightLayout)

        // Divider
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.setMargins(0, 0, 0, 0) }
            setBackgroundColor(Color.parseColor("#14000000"))
        }

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        wrapper.addView(layout)
        wrapper.addView(divider)

        return wrapper
    }

    private fun clearRecords() {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_clear_records))
            .setPositiveButton("确定") { _, _ ->
                appData.put("records", JSONArray())
                saveData()
                renderRecords()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ===== Content Management =====

    private fun getContent(id: String): JSONObject? {
        val contents = appData.optJSONArray("contents") ?: return null
        for (i in 0 until contents.length()) {
            val c = contents.getJSONObject(i)
            if (c.getString("id") == id) return c
        }
        return null
    }

    private fun getContentIndex(id: String): Int {
        val contents = appData.optJSONArray("contents") ?: return -1
        for (i in 0 until contents.length()) {
            if (contents.getJSONObject(i).getString("id") == id) return i
        }
        return -1
    }

    private fun genId(): String {
        return "c${System.currentTimeMillis()}_${(Math.random() * 10000).toInt().toString(36)}"
    }

    private fun renderContentList() {
        contentListContainer.removeAllViews()

        if (selectMode) {
            contentListTitle.text = getString(R.string.content_select_title)
        } else {
            contentListTitle.text = getString(R.string.content_list_title)
        }

        val contents = appData.optJSONArray("contents") ?: JSONArray()

        if (contents.length() == 0) {
            val emptyView = TextView(this).apply {
                text = getString(R.string.empty_content)
                setTextColor(Color.parseColor("#59000000"))
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 80, 0, 80)
            }
            contentListContainer.addView(emptyView)
            return
        }

        for (i in 0 until contents.length()) {
            val c = contents.getJSONObject(i)
            val itemView = createContentListItem(c)
            contentListContainer.addView(itemView)
        }
    }

    private fun createContentListItem(c: JSONObject): View {
        val id = c.getString("id")
        val title = c.optString("title", "")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 28, 0, 28)
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(Color.parseColor("#CC000000"))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        if (selectMode) {
            titleView.setOnClickListener {
                selectContentForPractice(id)
            }
        } else {
            titleView.setOnClickListener {
                viewContent(id)
            }
        }

        val actionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        if (!selectMode) {
            val editBtn = TextView(this).apply {
                text = "✏️"
                textSize = 16f
                setPadding(12, 8, 12, 8)
                setOnClickListener { openEdit(id) }
            }

            val deleteBtn = TextView(this).apply {
                text = "🗑️"
                textSize = 16f
                setPadding(12, 8, 12, 8)
                setOnClickListener { deleteContent(id) }
            }

            actionsLayout.addView(editBtn)
            actionsLayout.addView(deleteBtn)
        }

        layout.addView(titleView)
        layout.addView(actionsLayout)

        // Divider
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            )
            setBackgroundColor(Color.parseColor("#14000000"))
        }

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        wrapper.addView(layout)
        wrapper.addView(divider)

        return wrapper
    }

    private fun selectContentForPractice(id: String) {
        currentContentId = id
        showPage("practice")
        initPractice()
    }

    private fun viewContent(id: String) {
        val c = getContent(id) ?: return
        detailPageTitle.text = c.optString("title", "")
        detailMeta.text = getString(R.string.char_count, c.optString("content", "").length)
        detailContent.text = c.optString("content", "")
        showPage("contentDetail")
    }

    private fun openEdit(id: String?) {
        editId = id
        if (id != null) {
            val c = getContent(id)
            if (c != null) {
                editPageTitle.text = getString(R.string.edit_title_edit)
                editTitleInput.setText(c.optString("title", ""))
                editContentInput.setText(c.optString("content", ""))
            } else {
                editId = null
                editPageTitle.text = getString(R.string.edit_title)
                editTitleInput.setText("")
                editContentInput.setText("")
            }
        } else {
            editPageTitle.text = getString(R.string.edit_title)
            editTitleInput.setText("")
            editContentInput.setText("")
        }
        showPage("contentEdit")
    }

    private fun saveContent() {
        val title = editTitleInput.text.toString().trim()
        val content = editContentInput.text.toString().trim()
        if (title.isEmpty() || content.isEmpty()) {
            showModal(getString(R.string.title_cannot_empty), getString(R.string.msg_title_content_empty))
            return
        }

        if (editId != null) {
            val idx = getContentIndex(editId!!)
            if (idx >= 0) {
                val contents = appData.getJSONArray("contents")
                val c = contents.getJSONObject(idx)
                c.put("title", title)
                c.put("content", content)
            }
        } else {
            val newContent = JSONObject()
            newContent.put("id", genId())
            newContent.put("title", title)
            newContent.put("content", content)
            newContent.put("createdAt", System.currentTimeMillis())
            appData.getJSONArray("contents").put(newContent)
        }

        saveData()
        editId = null
        showPage("contentList")
        renderContentList()
    }

    private fun deleteContent(id: String) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_delete))
            .setPositiveButton("确定") { _, _ ->
                val idx = getContentIndex(id)
                if (idx >= 0) {
                    val contents = appData.getJSONArray("contents")
                    // Remove by creating new array
                    val newContents = JSONArray()
                    for (i in 0 until contents.length()) {
                        if (i != idx) {
                            newContents.put(contents.getJSONObject(i))
                        }
                    }
                    appData.put("contents", newContents)

                    if (currentContentId == id) {
                        currentContentId = if (newContents.length() > 0) {
                            newContents.getJSONObject(0).getString("id")
                        } else {
                            ""
                        }
                    }
                    saveData()
                    renderContentList()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ===== Challenge =====

    private fun updateChallengePage() {
        val records = appData.optJSONArray("records") ?: JSONArray()
        var totalGames = records.length()
        var bestSpeed = 0
        var bestAcc = 0
        var totalScore = 0

        for (i in 0 until records.length()) {
            val r = records.getJSONObject(i)
            val speed = r.optInt("speed")
            val accuracy = r.optInt("accuracy")
            if (speed > bestSpeed) bestSpeed = speed
            if (accuracy > bestAcc) bestAcc = accuracy
            totalScore += Math.round(speed.toFloat() * accuracy / 100)
        }

        val rank = getRank(totalScore)
        badgeIcon.text = rank.icon
        badgeTitle.text = "${rank.name} ${rank.star}"
        badgeSub.text = if (rank.next != null) {
            getString(R.string.rank_next_fmt, rank.next - totalScore)
        } else {
            getString(R.string.rank_max)
        }

        val pct = if (rank.next != null) {
            Math.min(100, Math.round(totalScore.toFloat() / rank.next * 100))
        } else {
            100
        }
        rankProgress.progress = pct
        rankInfo.text = "$totalScore/${rank.next ?: totalScore}"

        csGames.text = "$totalGames"
        csBestSpeed.text = "$bestSpeed"
        csBestAcc.text = "$bestAcc%"
    }

    private data class RankInfo(
        val name: String,
        val star: String,
        val icon: String,
        val next: Int?
    )

    private fun getRank(score: Int): RankInfo {
        return when {
            score >= 1000 -> RankInfo("大师", "", "🏆", null)
            score >= 600 -> RankInfo("钻石", "", "💎", 1000)
            score >= 300 -> RankInfo("黄金", "", "🥇", 600)
            score >= 100 -> RankInfo("白银", "", "🥈", 300)
            else -> {
                val star = Math.min(5, score / 20 + 1)
                RankInfo("青铜", "${star}星", "🥉", 100)
            }
        }
    }

    private fun startChallenge() {
        practiceMode = "timed"
        val contents = appData.optJSONArray("contents")
        if (contents == null || contents.length() == 0) {
            showModal(getString(R.string.title_cannot_empty), getString(R.string.msg_add_content_first))
            return
        }
        currentContentId = contents.getJSONObject(0).getString("id")
        showPage("practice")
        initPractice()
    }

    // ===== 蓝牙/物理键盘支持 =====

    // 是否接入了物理键盘（如蓝牙键盘）
    private fun hasHardKeyboard(): Boolean {
        return resources.configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
    }

    // 物理键盘直输兜底：焦点不在输入框时（如误触其他区域），直接接管按键，
    // 走与软键盘一致的 handleInput 流程；焦点在输入框时交给系统（物理键直接进 EditText / IME）
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isFinished && event.action == KeyEvent.ACTION_DOWN &&
            pagePractice.visibility == View.VISIBLE && !hiddenInput.hasFocus()
        ) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> {
                    if (userInput.isNotEmpty()) {
                        hiddenInput.setText(userInput.substring(0, userInput.length - 1))
                        hiddenInput.setSelection(hiddenInput.text.length)
                        return true
                    }
                }
                else -> {
                    val ch = event.unicodeChar
                    if (ch != 0 && !Character.isISOControl(ch)) {
                        hiddenInput.setText(userInput + ch.toChar())
                        hiddenInput.setSelection(hiddenInput.text.length)
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // 蓝牙键盘连接/断开时即时响应：收起软键盘、自动聚焦输入框
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (pagePractice.visibility == View.VISIBLE && !isFinished) {
            if (hasHardKeyboard()) {
                hideKeyboard()
                hiddenInput.requestFocus()
            }
        }
    }

    // ===== Modal =====

    private fun showModal(title: String, body: String, callback: (() -> Unit)? = null) {
        modalTitle.text = title
        modalBody.text = body
        modalCallback = callback
        modalOverlay.visibility = View.VISIBLE
    }

    private fun hideModal() {
        modalOverlay.visibility = View.GONE
        modalCallback?.invoke()
        modalCallback = null
    }

    override fun onBackPressed() {
        when {
            modalOverlay.visibility == View.VISIBLE -> hideModal()
            pagePractice.visibility == View.VISIBLE -> {
                stopTimer()
                stopCursorBlink()
                showPage("home")
            }
            pageContentList.visibility == View.VISIBLE -> showPage("home")
            pageContentEdit.visibility == View.VISIBLE -> {
                showPage("contentList")
                renderContentList()
            }
            pageContentDetail.visibility == View.VISIBLE -> {
                showPage("contentList")
                renderContentList()
            }
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        stopTimer()
        stopCursorBlink()
        super.onDestroy()
    }
}
