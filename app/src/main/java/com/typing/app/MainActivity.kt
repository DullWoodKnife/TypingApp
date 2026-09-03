package com.typing.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.JsonReader
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

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
    private lateinit var pageSettings: View
    private lateinit var modalOverlay: FrameLayout

    // Practice views
    private lateinit var statTime: TextView
    private lateinit var statProgress: TextView
    private lateinit var statSpeed: TextView
    private lateinit var statAcc: TextView
    private lateinit var practiceHint: TextView
    private lateinit var wubiHint: TextView
    private lateinit var dictHint: TextView
    private lateinit var dictHintScroll: ScrollView
    private var wubiTable: HashMap<String, String>? = null
    private var hanziDict: HashMap<String, HanziEntry>? = null
    private var enZhDict: HashMap<String, String>? = null
    private lateinit var typingScrollView: ScrollView
    private lateinit var typingTextView: TypingTextView
    private lateinit var hiddenInput: EditText

    // Settings views
    private lateinit var settingsVersionValue: TextView
    private lateinit var switchKeySound: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var switchBgMusic: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var btnPickMusic: Button
    private lateinit var btnImportHanzi: Button
    private lateinit var btnImportEn: Button
    private lateinit var btnExportZhWordbook: Button
    private lateinit var btnExportEnWordbook: Button

    // 音效 & 背景音乐 & 词典数据库
    private val settingsPrefs by lazy { getSharedPreferences("typing_settings", Context.MODE_PRIVATE) }
    private var soundPool: SoundPool? = null
    private var clickSoundId = 0
    private var errorSoundId = 0
    private var bgPlayer: MediaPlayer? = null
    private var dictDb: DictDbHelper? = null
    private var importJob: Job? = null

    // 生词本（SharedPreferences 存 JSONArray，中文/英文分开；每个 key 是一行条目：词\t拼音/音标\t释义）
    private val wordbookPrefs by lazy { getSharedPreferences("typing_wordbook", Context.MODE_PRIVATE) }

    // 长按选词弹出的 PopupWindow
    private var wordInfoPopup: PopupWindow? = null

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
        initSounds()
        // 后台预加载内置词典，避免练习中首次查询卡顿
        CoroutineScope(Dispatchers.IO).launch {
            if (hanziDict == null) hanziDict = loadHanziDict()
            if (enZhDict == null) enZhDict = loadEnZhDict()
        }
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
        pageSettings = findViewById(R.id.page_settings)
        modalOverlay = findViewById(R.id.modal_overlay)

        statTime = findViewById(R.id.stat_time)
        statProgress = findViewById(R.id.stat_progress)
        statSpeed = findViewById(R.id.stat_speed)
        statAcc = findViewById(R.id.stat_acc)
        practiceHint = findViewById(R.id.practice_hint)
        wubiHint = findViewById(R.id.wubi_hint)
        dictHint = findViewById(R.id.dict_hint)
        dictHintScroll = findViewById(R.id.dict_hint_scroll)
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

        settingsVersionValue = findViewById(R.id.settings_version_value)
        switchKeySound = findViewById(R.id.switch_key_sound)
        switchBgMusic = findViewById(R.id.switch_bg_music)
        btnPickMusic = findViewById(R.id.btn_pick_music)
        btnImportHanzi = findViewById(R.id.btn_import_hanzi)
        btnImportEn = findViewById(R.id.btn_import_en)
        btnExportZhWordbook = findViewById(R.id.btn_export_zh_wordbook)
        btnExportEnWordbook = findViewById(R.id.btn_export_en_wordbook)

        navChallenge = findViewById(R.id.nav_challenge)
        navHome = findViewById(R.id.nav_home)
        navRecords = findViewById(R.id.nav_records)
    }

    private fun setupListeners() {
        // Home buttons
        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            showPage("settings")
            renderSettings()
        }

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

        // 长按打字区：选中最近的汉字或英文单词，弹出查词 PopupWindow
        typingTextView.onCharLongPress = { idx ->
            if (isFinished || pagePractice.visibility != View.VISIBLE) return@onCharLongPress false
            val range = typingTextView.selectWordAt(idx)
            if (range.size != 2) return@onCharLongPress false
            val s = range[0]
            val e = range[1]
            val content = getContent(currentContentId) ?: return@onCharLongPress false
            val fullText = content.getString("content")
            val word = fullText.substring(s, e)
            if (word.isBlank()) return@onCharLongPress false
            showWordLookupPopup(word, isHanziChar(word[0]), typingTextView.lastTouchX().toInt(), typingTextView.lastTouchY().toInt())
            true
        }

        // Hidden input text watcher
        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFinished || isComposing) return
                handleInput(s?.toString() ?: "")
                // 始终把隐藏输入框的光标拉回末尾，避免左/右移光标后按删除键删错字符
                try { hiddenInput.setSelection(hiddenInput.text.length) } catch (_: Exception) {}
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

        // Settings
        findViewById<Button>(R.id.btn_back_from_settings).setOnClickListener {
            showPage("home")
        }

        btnPickMusic.setOnClickListener {
            pickFile(REQUEST_PICK_MUSIC, "audio/*")
        }

        btnImportHanzi.setOnClickListener {
            if (importJob?.isActive == true) return@setOnClickListener
            pickFile(REQUEST_IMPORT_HANZI, "*/*")
        }

        btnImportEn.setOnClickListener {
            if (importJob?.isActive == true) return@setOnClickListener
            pickFile(REQUEST_IMPORT_EN, "*/*")
        }

        btnExportZhWordbook.setOnClickListener {
            createWordbookFile("chinese_wordbook.txt", "text/plain", REQUEST_CREATE_ZH_TXT)
        }

        btnExportEnWordbook.setOnClickListener {
            createWordbookFile("english_wordbook.txt", "text/plain", REQUEST_CREATE_EN_TXT)
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
        pageSettings.visibility = View.GONE

        when (pageId) {
            "home" -> pageHome.visibility = View.VISIBLE
            "practice" -> pagePractice.visibility = View.VISIBLE
            "contentList" -> pageContentList.visibility = View.VISIBLE
            "contentEdit" -> pageContentEdit.visibility = View.VISIBLE
            "contentDetail" -> pageContentDetail.visibility = View.VISIBLE
            "records" -> pageRecords.visibility = View.VISIBLE
            "challenge" -> pageChallenge.visibility = View.VISIBLE
            "settings" -> {
                pageSettings.visibility = View.VISIBLE
                renderSettingsImportButtons()
                renderWordbookButtons()
            }
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
            "contentDetail" to navHome,
            "settings" to navHome
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

    // ===== 拼音/音标 + 释义数据 =====

    data class HanziEntry(val pinyin: String, val defs: String)

    // 加载 assets/hanzi_dict.txt：只收录 wubi86_码表.txt 中的 8105 个汉字
    // 格式：字\t拼音1;拼音2\t释义1;释义2
    private fun loadHanziDict(): HashMap<String, HanziEntry> {
        val map = HashMap<String, HanziEntry>()
        try {
            assets.open("hanzi_dict.txt").bufferedReader().useLines { lines ->
                for (line in lines) {
                    val t = line.trim()
                    if (t.isEmpty()) continue
                    val parts = t.split('\t')
                    if (parts.size >= 3) {
                        map[parts[0]] = HanziEntry(parts[1], parts[2])
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    // 加载 assets/en_zh_dict.txt：英文单词 -> 中文释义（从 cc-cedict 反向构建）
    // 格式：word\t中文1;中文2
    private fun loadEnZhDict(): HashMap<String, String> {
        val map = HashMap<String, String>()
        try {
            assets.open("en_zh_dict.txt").bufferedReader().useLines { lines ->
                for (line in lines) {
                    val t = line.trim()
                    if (t.isEmpty()) continue
                    val idx = t.indexOf('\t')
                    if (idx > 0) map[t.substring(0, idx).lowercase()] = t.substring(idx + 1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    private fun getHanziEntry(ch: Char): HanziEntry? {
        val dict = hanziDict ?: loadHanziDict().also { hanziDict = it }
        return dict[ch.toString()]
    }

    private fun getEnZh(word: String): String? {
        val dict = enZhDict ?: loadEnZhDict().also { enZhDict = it }
        return dict[word.lowercase()]
    }

    // ===== 词典数据库（用户导入的 SQLite，优先于内置数据）=====

    class DictDbHelper(context: Context) : SQLiteOpenHelper(context, "dicts.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS hanzi(word TEXT PRIMARY KEY, pinyin TEXT, def TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS enword(word TEXT PRIMARY KEY, phonetic TEXT, definition TEXT, translation TEXT)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }

    private fun getDictDb(): DictDbHelper {
        return dictDb ?: DictDbHelper(this).also { dictDb = it }
    }

    data class EnEntry(val phonetic: String, val definition: String, val translation: String)

    // 查询汉字：优先用户导入的 SQLite 词典，回退内置 hanzi_dict.txt
    private fun queryHanzi(ch: Char): HanziEntry? {
        try {
            getDictDb().readableDatabase.rawQuery(
                "SELECT pinyin, def FROM hanzi WHERE word = ?", arrayOf(ch.toString())
            ).use { c ->
                if (c.moveToFirst()) return HanziEntry(c.getString(0) ?: "", c.getString(1) ?: "")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return getHanziEntry(ch)
    }

    // 查询英文单词：优先用户导入的 SQLite 词典，回退内置 en_zh_dict.txt（仅中文释义）
    private fun queryEn(word: String): EnEntry? {
        val w = word.lowercase()
        try {
            getDictDb().readableDatabase.rawQuery(
                "SELECT phonetic, definition, translation FROM enword WHERE word = ?", arrayOf(w)
            ).use { c ->
                if (c.moveToFirst()) {
                    return EnEntry(
                        c.getString(0) ?: "",
                        c.getString(1) ?: "",
                        c.getString(2) ?: ""
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val zh = getEnZh(w) ?: return null
        return EnEntry("", "", zh)
    }

    // 在"正在输入…"同行最右侧，显示光标当前位置汉字的86版五笔拆字（完整码,简码）。
    // 跟随"当前字"：userInput.length>0 时取 text[userInput.length-1]（已显示在UI上的当前字），
    // 否则取 text[0]（准备输入的第一个字）。输入完成显示到UI后才切换到下一个字。
    private fun updateWubiHint(text: String) {
        val idx = currentHintIndex(text)
        val code = if (idx >= 0 && idx < text.length) getWubiCode(text[idx]) else ""
        if (wubiHint.text.toString() != code) {
            wubiHint.text = code
        }
    }

    // 计算当前应当显示提示的字符下标：已完成并显示在 UI 上的那个字
    private fun currentHintIndex(text: String): Int {
        return when {
            isFinished -> -1
            userInput.isEmpty() -> 0
            else -> (userInput.length - 1).coerceAtMost(text.length - 1)
        }
    }

    // 更新五笔码 + 拼音/音标释义区。**只自动显示五笔码**；拼音/音标释义由用户长按打字区触发填充。
    private fun updateHints(text: String) {
        if (isFinished || text.isEmpty()) {
            wubiHint.text = ""
            return
        }
        updateWubiHint(text)
    }

    private fun isHanziChar(ch: Char): Boolean {
        return ch.code in 0x4E00..0x9FA5
    }

    // 仅英文字母（排除汉字：Char.isLetter() 对汉字也返回 true）
    private fun isEnLetter(ch: Char): Boolean {
        return ch.isLetter() && !isHanziChar(ch)
    }

    // 是否为 ASCII 字母（A-Z / a-z）—— 用于识别 IME 组合中的临时字母
    private fun Char.isAsciiLetter(): Boolean {
        return this in 'A'..'Z' || this in 'a'..'z'
    }

    // 设置释义文本：内容变化后回滚到顶部，保证切换字/词后新释义从开头可见
    private fun setDictHintText(text: String) {
        if (dictHint.text.toString() == text) return
        dictHint.text = text
        dictHintScroll.scrollTo(0, 0)
    }

    // 清理释义文本：内部换行（含字面 \n 转义）统一替换为分号分隔
    private fun cleanDef(s: String): String {
        return s.replace("\\r", "")
            .replace("\\n", "; ")
            .replace("\r", "")
            .replace("\n", "; ")
            .trim()
    }

    // ===== 长按查词 / 生词本 =====

    private val NOT_FOUND_TXT = "该字词典中未收录"

    // 在"正在输入..."下方的 dict_hint 区显示释义；单词未收录显示提示文案
    private fun showWordInfoInDictHint(word: String, isHanzi: Boolean) {
        val display: String
        if (isHanzi) {
            val entry = queryHanzi(word[0])
            display = if (entry != null) "${entry.pinyin}  ${cleanDef(entry.defs)}" else NOT_FOUND_TXT
        } else {
            val entry = queryEn(word)
            display = if (entry != null) {
                val parts = mutableListOf<String>()
                if (entry.phonetic.isNotBlank()) parts.add("/${entry.phonetic.trim('/')}/")
                if (entry.definition.isNotBlank()) parts.add(cleanDef(entry.definition))
                if (entry.translation.isNotBlank()) parts.add(cleanDef(entry.translation))
                if (parts.isEmpty()) NOT_FOUND_TXT else parts.joinToString("; ")
            } else NOT_FOUND_TXT
        }
        setDictHintText(display)
    }

    // 弹出"添加到生词本"小卡片；长按位置附近
    private fun showWordLookupPopup(word: String, isHanzi: Boolean, anchorX: Int, anchorY: Int) {
        // 先填充释义
        showWordInfoInDictHint(word, isHanzi)
        // 弹窗
        wordInfoPopup?.dismiss()
        val view = LayoutInflater.from(this).inflate(R.layout.popup_wordbook, null)
        val tvWord = view.findViewById<TextView>(R.id.popup_word)
        val tvAdd = view.findViewById<TextView>(R.id.popup_add)
        tvWord.text = word
        tvAdd.text = "添加到生词本"
        val pw = PopupWindow(view, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        pw.setBackgroundDrawable(ColorDrawable(0xCCFFFFFF.toInt()))
        pw.elevation = 12f
        // 计算锚点：基于 TypingTextView 在窗口中的位置 + 触摸坐标
        val loc = IntArray(2)
        typingTextView.getLocationOnScreen(loc)
        val x = (loc[0] + anchorX).coerceAtLeast(8)
        // 在触摸点上方 80dp 弹出，避免被键盘或软键盘遮挡
        val y = (loc[1] + anchorY - (80 * resources.displayMetrics.density).toInt()).coerceAtLeast(loc[1] - 200)
        pw.showAtLocation(typingTextView, Gravity.NO_GRAVITY, x, y)
        // 点 PopupWindow 内部"添加到生词本"按钮
        tvAdd.setOnClickListener {
            addToWordbook(word, isHanzi)
            pw.dismiss()
        }
        // 点 PopupWindow 其它区域：点击事件已被 PopupWindow 拦截；外部触摸自动 dismiss（下面配置）
        pw.setTouchInterceptor { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_UP) {
                val dismiss = if (ev.x >= 0 && ev.x < view.width && ev.y >= 0 && ev.y < view.height) {
                    // 点在 PopupWindow 内（且没命中"添加到生词本"已单独处理）→ 不消失
                    !isClickOnView(tvAdd, ev.rawX.toInt(), ev.rawY.toInt())
                } else true
                if (dismiss) {
                    pw.dismiss()
                    true
                } else false
            } else false
        }
        pw.isOutsideTouchable = true
        pw.setOnDismissListener {
            wordInfoPopup = null
        }
        wordInfoPopup = pw
    }

    private fun isClickOnView(v: View, rawX: Int, rawY: Int): Boolean {
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return rawX >= loc[0] && rawX <= loc[0] + v.width && rawY >= loc[1] && rawY <= loc[1] + v.height
    }

    private fun addToWordbook(word: String, isHanzi: Boolean) {
        val key = if (isHanzi) "zh_wordbook" else "en_wordbook"
        val lines = wordbookPrefs.getStringSet(key, null)?.toMutableSet() ?: mutableSetOf()
        val entry = buildWordbookLine(word, isHanzi)
        if (lines.contains(entry)) {
            Toast.makeText(this, "已在生词本中", Toast.LENGTH_SHORT).show()
            return
        }
        lines.add(entry)
        wordbookPrefs.edit().putStringSet(key, lines).apply()
        Toast.makeText(this, "已加入生词本", Toast.LENGTH_SHORT).show()
    }

    private fun buildWordbookLine(word: String, isHanzi: Boolean): String {
        return if (isHanzi) {
            val entry = queryHanzi(word[0])
            val pinyin = entry?.pinyin ?: ""
            val def = entry?.let { cleanDef(it.defs) } ?: NOT_FOUND_TXT
            "$word\t$pinyin\t$def"
        } else {
            val entry = queryEn(word)
            val phonetic = entry?.phonetic ?: ""
            val definition = entry?.let { cleanDef(it.definition) } ?: ""
            val translation = entry?.let { cleanDef(it.translation) } ?: ""
            "$word\t$phonetic\t$definition; $translation"
        }
    }

    private fun getWordbookLines(isHanzi: Boolean): List<String> {
        val key = if (isHanzi) "zh_wordbook" else "en_wordbook"
        val set = wordbookPrefs.getStringSet(key, null) ?: return emptyList()
        return set.toSortedSet().toList()
    }

    private fun createWordbookFile(defaultName: String, mime: String, requestCode: Int) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = mime
        intent.putExtra(Intent.EXTRA_TITLE, defaultName)
        try {
            startActivityForResult(intent, requestCode)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "无法创建文件", Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeWordbookToUri(uri: Uri, isHanzi: Boolean) {
        val lines = getWordbookLines(isHanzi)
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(("\uFEFF").toByteArray(Charsets.UTF_8))
                for (l in lines) {
                    out.write((l + "\n").toByteArray(Charsets.UTF_8))
                }
            }
            Toast.makeText(this, "已导出 ${lines.size} 条", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show()
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
        updateHints(text)
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
        updateHints(text)
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

        // 光标所在行：中文按 17 字网格，英文按实际排版行（每行字符数不固定）
        val currentRow = typingTextView.rowOfIndex(inputLen)
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
        val prevLen = userInput.length

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

// 键盘音效：每个新增字符都按对/错播放一次敲击音/错误音。
        // 中文 IME 会一次性 commit 多个汉字，需逐字符触发。
        // 中文练习中 IME（拼音/五笔等）在组合时也会 commit 字母到 hiddenInput
        // （如图中打 d g h 选中汉字前），此时不该判为错误——只要原文对应位置是汉字，
        // 新字符是 ASCII 字母就视作 IME 正在组成汉字（无效输入），不计数、不播错误音。
        if (userInput.length > prevLen && originalText.isNotEmpty()) {
            val end = minOf(userInput.length, originalText.length)
            for (idx in prevLen until end) {
                val expectC = originalText[idx]
                val gotC = userInput[idx]
                if (isHanziChar(expectC) && gotC.isAsciiLetter()) {
                    // IME 组合中的字母：清掉，不计
                    hiddenInput.setText(userInput.substring(0, idx))
                    hiddenInput.setSelection(idx)
                } else {
                    playKeySound(gotC == expectC)
                }
            }
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
            updateHints(originalText)
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
        // 练习页中，方向键/左右键一律把隐藏输入框光标拉回末尾，不允许移动光标，
        // 否则界面光标不动、但按删除键会删掉“移动后位置”的字符（错位删除）。
        if (!isFinished && pagePractice.visibility == View.VISIBLE &&
            event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
             event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
             event.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
             event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
             event.keyCode == KeyEvent.KEYCODE_MOVE_END ||
             event.keyCode == KeyEvent.KEYCODE_MOVE_HOME)
        ) {
            try { hiddenInput.setSelection(hiddenInput.text.length) } catch (_: Exception) {}
            return true
        }
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
            pageSettings.visibility == View.VISIBLE -> showPage("home")
            else -> super.onBackPressed()
        }
    }

    // ===== 设置：键盘音效 / 背景音乐 / 词典导入 =====

    companion object {
        private const val REQUEST_PICK_MUSIC = 1001
        private const val REQUEST_IMPORT_HANZI = 1002
        private const val REQUEST_IMPORT_EN = 1003
        private const val REQUEST_CREATE_ZH_TXT = 1004
        private const val REQUEST_CREATE_EN_TXT = 1005
    }

    private var soundsReady = 0

    private fun initSounds() {
        try {
            // 使用 USAGE_GAME：跟随媒体音量通道。
            // 之前用的 USAGE_ASSISTANCE_SONIFICATION 在部分机型上被映射到
            // "辅助功能"音量（常为 0 或被系统静音），导致听不到键盘音。
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            soundPool?.release()
            soundPool = SoundPool.Builder()
                .setMaxStreams(8)   // 快速连打时不丢音
                .setAudioAttributes(attrs)
                .build()
            soundPool?.setOnLoadCompleteListener { _, _, status ->
                if (status == 0) soundsReady++
            }
            clickSoundId = soundPool?.load(this, R.raw.key_click, 1) ?: 0
            errorSoundId = soundPool?.load(this, R.raw.error, 1) ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 机械键盘敲击音（对）/ 错误提示音（错）
    private fun playKeySound(correct: Boolean) {
        if (!settingsPrefs.getBoolean("key_sound_enabled", true)) return
        val sp = soundPool ?: run { initSounds(); soundPool } ?: return
        val id = if (correct) clickSoundId else errorSoundId
        if (id == 0) return
        try {
            // 不等待加载完成：未就绪时 play 直接返回 0（静默），
            // 避免因加载回调未触发而永久无声
            if (correct) sp.play(id, 1.0f, 1.0f, 1, 0, 1f)
            else sp.play(id, 1.0f, 1.0f, 1, 0, 1f)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 背景音乐：循环播放用户选择的本地音频
    private fun startBgMusic() {
        val uriStr = settingsPrefs.getString("bg_music_uri", null) ?: return
        stopBgMusic()
        try {
            val player = MediaPlayer()
            player.setDataSource(this, Uri.parse(uriStr))
            player.isLooping = true
            player.setVolume(0.5f, 0.5f)
            player.prepare()
            player.start()
            bgPlayer = player
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopBgMusic() {
        bgPlayer?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            try {
                it.release()
            } catch (_: Exception) {
            }
        }
        bgPlayer = null
    }

    private fun renderSettings() {
        settingsVersionValue.text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        // 回显开关状态时暂时移除监听，避免触发播放逻辑
        switchKeySound.setOnCheckedChangeListener(null)
        switchBgMusic.setOnCheckedChangeListener(null)
        switchKeySound.isChecked = settingsPrefs.getBoolean("key_sound_enabled", true)
        switchBgMusic.isChecked = settingsPrefs.getBoolean("bg_music_enabled", false)
        switchKeySound.setOnCheckedChangeListener { _, checked ->
            settingsPrefs.edit().putBoolean("key_sound_enabled", checked).apply()
            // 打开时立即试听一次，便于确认音效是否正常
            if (checked) playKeySound(true)
        }
        switchBgMusic.setOnCheckedChangeListener { _, checked ->
            settingsPrefs.edit().putBoolean("bg_music_enabled", checked).apply()
            if (checked) startBgMusic() else stopBgMusic()
        }
        renderSettingsImportButtons()
        renderWordbookButtons()
    }

    // 导入按钮文字：已导入时追加"（已导入"）
    private fun renderSettingsImportButtons() {
        val hzImported = settingsPrefs.getBoolean("imported_hanzi", false)
        val enImported = settingsPrefs.getBoolean("imported_en", false)
        btnImportHanzi.text = getString(R.string.settings_import_hanzi) + if (hzImported) getString(R.string.settings_imported_suffix) else ""
        btnImportEn.text = getString(R.string.settings_import_en) + if (enImported) getString(R.string.settings_imported_suffix) else ""
    }

    // 生词本按钮：空时禁用
    private fun renderWordbookButtons() {
        val zhCount = getWordbookLines(true).size
        val enCount = getWordbookLines(false).size
        btnExportZhWordbook.isEnabled = zhCount > 0
        btnExportEnWordbook.isEnabled = enCount > 0
    }

    private fun pickFile(requestCode: Int, mime: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = mime
        try {
            startActivityForResult(intent, requestCode)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun queryFileName(uri: Uri): String {
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return c.getString(idx) ?: ""
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return uri.lastPathSegment ?: ""
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        when (requestCode) {
            REQUEST_PICK_MUSIC -> {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                settingsPrefs.edit().putString("bg_music_uri", uri.toString()).apply()
                Toast.makeText(this, getString(R.string.settings_file_chosen, queryFileName(uri)), Toast.LENGTH_SHORT).show()
                if (settingsPrefs.getBoolean("bg_music_enabled", false)) startBgMusic()
            }
            REQUEST_IMPORT_HANZI -> importHanziJson(uri)
            REQUEST_IMPORT_EN -> importEnCsv(uri)
            REQUEST_CREATE_ZH_TXT -> writeWordbookToUri(uri, true)
            REQUEST_CREATE_EN_TXT -> writeWordbookToUri(uri, false)
        }
    }

    // 导入中文字典（chinese-xinhua dict.json 格式：word/pinyin/explanation）。
    // **流式解析**：android.util.JsonReader 不把整文件读入内存，可安全处理 26M+ JSON。
    // 26M JSON 之前用 JSONArray(content) 会 OOM 崩溃，故此处改用 JsonReader。
    private fun importHanziJson(uri: Uri) {
        btnImportHanzi.text = getString(R.string.settings_importing)
        btnImportHanzi.isEnabled = false
        importJob = CoroutineScope(Dispatchers.IO).launch {
            var count = 0
            var ok = false
            try {
                val db = getDictDb().writableDatabase
                val stmt = db.compileStatement("INSERT OR REPLACE INTO hanzi(word, pinyin, def) VALUES(?,?,?)")
                db.beginTransaction()
                try {
                    contentResolver.openInputStream(uri)?.use { ins ->
                        InputStreamReader(ins, Charsets.UTF_8).use { isr ->
                            JsonReader(isr).use { jr ->
                                jr.beginArray()
                                while (jr.hasNext()) {
                                    jr.beginObject()
                                    var word = ""
                                    var pinyin = ""
                                    var explanation = ""
                                    while (jr.hasNext()) {
                                        val name = jr.nextName()
                                        when (name) {
                                            "word" -> word = jr.nextString()
                                            "pinyin" -> pinyin = jr.nextString()
                                            "explanation" -> explanation = jr.nextString()
                                            else -> jr.skipValue()
                                        }
                                    }
                                    jr.endObject()
                                    if (word.length == 1) {
                                        stmt.bindString(1, word)
                                        stmt.bindString(2, pinyin)
                                        stmt.bindString(3, explanation)
                                        stmt.executeInsert()
                                        count++
                                        // 每 5000 条提交一次事务，减少 IO 抖动
                                        if (count % 5000 == 0) {
                                            db.setTransactionSuccessful()
                                            db.endTransaction()
                                            db.beginTransaction()
                                        }
                                    }
                                }
                                jr.endArray()
                            }
                        }
                    }
                    db.setTransactionSuccessful()
                    ok = true
                } finally {
                    try { db.endTransaction() } catch (_: Exception) {}
                }
                if (ok) {
                    settingsPrefs.edit().putBoolean("imported_hanzi", true).apply()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val cnt = count
            val success = ok
            withContext(Dispatchers.Main) {
                btnImportHanzi.isEnabled = true
                renderSettingsImportButtons()
                if (success) {
                    Toast.makeText(this@MainActivity, getString(R.string.settings_import_done, cnt), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.settings_import_fail), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 导入英文词典（ECDICT CSV 格式：word,phonetic,definition,translation,pos,collins,oxford,tag,bnc,frq,…）
    // 流式解析（支持引号内逗号/换行），仅导入有词频或考试标注的常用词条
    private fun importEnCsv(uri: Uri) {
        btnImportEn.text = getString(R.string.settings_importing)
        btnImportEn.isEnabled = false
        importJob = CoroutineScope(Dispatchers.IO).launch {
            var count = 0
            var ok = false
            try {
                val reader = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                if (reader != null) {
                    val db = getDictDb().writableDatabase
                    val stmt = db.compileStatement("INSERT OR REPLACE INTO enword(word, phonetic, definition, translation) VALUES(?,?,?,?)")
                    val fields = ArrayList<String>(13)
                    val sb = StringBuilder()
                    var inQuotes = false
                    var isHeader = true
                    var txOpen = false
                    var done = false
                    fun processRecord(fs: List<String>) {
                        if (isHeader) {
                            isHeader = false
                            if (fs.isNotEmpty() && fs[0].lowercase() == "word") return
                        }
                        if (fs.size < 4) return
                        val word = fs[0]
                        if (word.isBlank() || word.length > 40) return
                        val collins = fs.getOrNull(5)?.toIntOrNull() ?: 0
                        val oxford = fs.getOrNull(6)?.toIntOrNull() ?: 0
                        val tag = fs.getOrNull(7) ?: ""
                        val bnc = fs.getOrNull(8)?.toIntOrNull() ?: 0
                        val frq = fs.getOrNull(9)?.toIntOrNull() ?: 0
                        val keep = collins > 0 || oxford > 0 || tag.isNotBlank() || bnc > 0 || frq > 0
                        if (!keep) return
                        stmt.bindString(1, word.lowercase())
                        stmt.bindString(2, fs[1])
                        stmt.bindString(3, fs[2])
                        stmt.bindString(4, fs[3])
                        stmt.executeInsert()
                        count++
                        if (count % 5000 == 0 && txOpen) {
                            db.setTransactionSuccessful()
                            db.endTransaction()
                            db.beginTransaction()
                        }
                    }

                    db.beginTransaction()
                    txOpen = true
                    try {
                        val buf = CharArray(65536)
                        while (!done) {
                            val n = reader.read(buf)
                            if (n < 0) break
                            for (k in 0 until n) {
                                val c = buf[k]
                                if (inQuotes) {
                                    if (c == '"') inQuotes = false
                                    else sb.append(c)
                                } else {
                                    when (c) {
                                        '"' -> inQuotes = true
                                        ',' -> {
                                            fields.add(sb.toString()); sb.setLength(0)
                                        }
                                        '\n' -> {
                                            fields.add(sb.toString()); sb.setLength(0)
                                            processRecord(fields)
                                            fields.clear()
                                        }
                                        '\r' -> { /* 跳过 CR */ }
                                        else -> sb.append(c)
                                    }
                                }
                            }
                        }
                        // 文件末尾不足一行的残余记录
                        if (sb.isNotEmpty() || fields.isNotEmpty()) {
                            fields.add(sb.toString())
                            processRecord(fields)
                        }
                        db.setTransactionSuccessful()
                        txOpen = false
                        done = true
                    } finally {
                        if (txOpen) {
                            try {
                                db.endTransaction()
                            } catch (_: Exception) {
                            }
                        }
                    }
                    reader.close()
                    ok = true
                }
                if (ok) {
                    settingsPrefs.edit().putBoolean("imported_en", true).apply()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val cnt = count
            val success = ok
            withContext(Dispatchers.Main) {
                btnImportEn.isEnabled = true
                renderSettingsImportButtons()
                if (success) {
                    Toast.makeText(this@MainActivity, getString(R.string.settings_import_done, cnt), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.settings_import_fail), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        wordInfoPopup?.dismiss()
        wordInfoPopup = null
        try {
            bgPlayer?.takeIf { it.isPlaying }?.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        if (settingsPrefs.getBoolean("bg_music_enabled", false)) {
            if (bgPlayer == null) {
                startBgMusic()
            } else {
                try {
                    if (!bgPlayer!!.isPlaying) bgPlayer!!.start()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onDestroy() {
        stopTimer()
        stopCursorBlink()
        stopBgMusic()
        try {
            soundPool?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        soundPool = null
        super.onDestroy()
    }
}
