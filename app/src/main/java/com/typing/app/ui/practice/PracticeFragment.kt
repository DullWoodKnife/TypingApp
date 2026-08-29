package com.typing.app.ui.practice

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.typing.app.R
import com.typing.app.data.AppDatabase
import com.typing.app.data.Content
import com.typing.app.data.DataRepository
import com.typing.app.data.Record
import com.typing.app.databinding.FragmentPracticeBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PracticeFragment : Fragment() {

    private var _binding: FragmentPracticeBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: DataRepository
    private var currentContentId: String? = null
    private var practiceMode: String = "normal"

    // Practice state
    private var cursorPos = 0
    private var correctCount = 0
    private var wrongCount = 0
    private var isRunning = false
    private var isFinished = false
    private var startTime = 0L
    private var elapsed = 0
    private val charsPerLine = 15
    private var totalChars = 0

    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    companion object {
        const val COLOR_CORRECT = Color.parseColor("#40B43E")
        const val COLOR_WRONG = Color.parseColor("#E65C53")
        const val COLOR_CURRENT_BG = Color.parseColor("#1AE65C53")
        const val COLOR_PENDING = Color.parseColor("#59000000")
        const val COLOR_PRIMARY = Color.parseColor("#CC000000")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getString("mode")?.let { practiceMode = it }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPracticeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        repository = DataRepository(db.contentDao(), db.recordDao())

        binding.btnSelectContent.setOnClickListener {
            val bundle = Bundle().apply { putBoolean("selectMode", true) }
            findNavController().navigate(R.id.content_list_fragment, bundle)
        }

        binding.btnRestart.setOnClickListener { initPractice() }
        binding.btnBackHome.setOnClickListener {
            stopTimer()
            findNavController().popBackStack()
        }

        binding.inputField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                handleInput(s?.toString() ?: "")
            }
        })

        // Load first content by default
        lifecycleScope.launch {
            val contents = repository.getAllContentsSync()
            currentContentId = contents.firstOrNull()?.id
            initPractice()
        }
    }

    private fun initPractice() {
        stopTimer()
        cursorPos = 0
        correctCount = 0
        wrongCount = 0
        isRunning = false
        isFinished = false
        elapsed = 0

        binding.statTime.text = "00:00"
        binding.statTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        binding.statProgress.text = "0%"
        binding.statSpeed.text = "0"
        binding.statAccuracy.text = "0%"
        binding.practiceHint.text = getString(R.string.practice_hint)
        binding.inputField.setText("")
        binding.inputField.isEnabled = true

        lifecycleScope.launch {
            val content = currentContentId?.let { repository.getContent(it) } ?: return@launch
            renderBoard(content.content)
        }
    }

    private fun renderBoard(text: String) {
        binding.practiceBoard.removeAllViews()
        totalChars = text.length
        val lines = mutableListOf<String>()
        for (i in text.indices step charsPerLine) {
            lines.add(text.substring(i, minOf(i + charsPerLine, text.length)))
        }

        for (li in lines.indices) {
            val line = lines[li]
            val lineLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 4 }
            }

            // Reference row
            val refRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            for (ci in line.indices) {
                val globalIdx = li * charsPerLine + ci
                val tv = TextView(context).apply {
                    text = line[ci].toString()
                    textSize = 16f
                    setTextColor(if (globalIdx == 0) COLOR_PRIMARY else COLOR_PENDING)
                    width = dpToPx(24)
                    height = dpToPx(32)
                    gravity = android.view.Gravity.CENTER
                    if (globalIdx == 0) setBackgroundColor(COLOR_CURRENT_BG)
                    tag = globalIdx
                }
                refRow.addView(tv)
            }

            // Input row
            val inputRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            for (ci in line.indices) {
                val idx = li * charsPerLine + ci
                val tv = TextView(context).apply {
                    text = ""
                    textSize = 16f
                    setTextColor(Color.TRANSPARENT)
                    width = dpToPx(24)
                    height = dpToPx(29)
                    gravity = android.view.Gravity.CENTER
                    tag = "input_$idx"
                }
                inputRow.addView(tv)
            }

            lineLayout.addView(refRow)
            lineLayout.addView(inputRow)
            binding.practiceBoard.addView(lineLayout)
        }
    }

    private fun handleInput(inputText: String) {
        if (isFinished) return

        lifecycleScope.launch {
            val content = currentContentId?.let { repository.getContent(it) } ?: return@launch
            val text = content.content

            if (!isRunning && inputText.isNotEmpty()) {
                isRunning = true
                startTime = System.currentTimeMillis()
                elapsed = 0
                binding.practiceHint.text = getString(R.string.inputting)
                startTimer()
            }

            val newPos = inputText.length.coerceAtMost(text.length)

            // Recalculate correct/wrong counts
            var newCorrect = 0
            var newWrong = 0
            val wrongSet = mutableSetOf<Int>()
            for (i in 0 until newPos) {
                if (inputText[i] == text[i]) {
                    newCorrect++
                    cursorPos = i + 1
                } else {
                    newWrong++
                    wrongSet.add(i)
                }
            }
            correctCount = newCorrect
            wrongCount = newWrong

            // Update reference cells
            updateRefCells(text, newPos, wrongSet)
            // Update input cells
            updateInputCells(inputText, text, newPos, wrongSet)

            updateStats(text)

            // Check completion
            if (cursorPos >= text.length && !isFinished) {
                finishPractice(text, inputText)
            }
        }
    }

    private fun updateRefCells(text: String, newPos: Int, wrongSet: Set<Int>) {
        for (i in 0 until binding.practiceBoard.childCount) {
            val linePair = binding.practiceBoard.getChildAt(i) as? LinearLayout ?: continue
            val refRow = linePair.getChildAt(0) as? LinearLayout ?: continue
            for (j in 0 until refRow.childCount) {
                val cell = refRow.getChildAt(j) as? TextView ?: continue
                val idx = cell.tag as? Int ?: continue
                when {
                    idx < newPos -> {
                        cell.setTextColor(if (wrongSet.contains(idx)) COLOR_WRONG else COLOR_CORRECT)
                        cell.setBackgroundColor(Color.TRANSPARENT)
                    }
                    idx == cursorPos && idx < text.length -> {
                        cell.setTextColor(COLOR_PRIMARY)
                        cell.setBackgroundColor(COLOR_CURRENT_BG)
                    }
                    else -> {
                        cell.setTextColor(COLOR_PENDING)
                        cell.setBackgroundColor(Color.TRANSPARENT)
                    }
                }
            }
        }
    }

    private fun updateInputCells(inputText: String, text: String, newPos: Int, wrongSet: Set<Int>) {
        for (i in 0 until binding.practiceBoard.childCount) {
            val linePair = binding.practiceBoard.getChildAt(i) as? LinearLayout ?: continue
            val inputRow = linePair.getChildAt(1) as? LinearLayout ?: continue
            for (j in 0 until inputRow.childCount) {
                val cell = inputRow.getChildAt(j) as? TextView ?: continue
                val tag = cell.tag as? String ?: continue
                val idx = tag.removePrefix("input_").toIntOrNull() ?: continue
                when {
                    idx < newPos && idx < text.length -> {
                        cell.text = inputText[idx].toString()
                        cell.setTextColor(if (wrongSet.contains(idx)) COLOR_WRONG else COLOR_CORRECT)
                    }
                    idx == newPos && idx < text.length -> {
                        cell.text = ""
                        cell.setTextColor(COLOR_PRIMARY)
                    }
                    else -> {
                        cell.text = ""
                        cell.setTextColor(Color.TRANSPARENT)
                    }
                }
            }
        }
    }

    private fun updateStats(text: String) {
        val progress = if (totalChars > 0) {
            minOf(100, (cursorPos * 100 / totalChars))
        } else 0
        binding.statProgress.text = "$progress%"

        val accuracy = if ((correctCount + wrongCount) > 0) {
            correctCount * 100 / (correctCount + wrongCount)
        } else 0
        binding.statAccuracy.text = "$accuracy%"

        val secs = if (elapsed > 0) elapsed else 1
        val speed = correctCount * 60 / secs
        binding.statSpeed.text = speed.toString()

        if (practiceMode == "timed") {
            val remain = 60 - elapsed
            val min = remain / 60
            val sec = remain % 60
            binding.statTime.text = String.format("%02d:%02d", min, sec)
            if (remain <= 10) {
                binding.statTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.destructive))
            } else {
                binding.statTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            }
        } else {
            val min = elapsed / 60
            val sec = elapsed % 60
            binding.statTime.text = String.format("%02d:%02d", min, sec)
        }
    }

    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()

                if (practiceMode == "timed" && elapsed >= 60) {
                    stopTimer()
                    finishTimedPractice()
                    return
                }

                lifecycleScope.launch {
                    val content = currentContentId?.let { repository.getContent(it) } ?: return@launch
                    updateStats(content.content)
                }

                handler.postDelayed(this, 200)
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
    }

    private fun finishPractice(text: String, inputText: String) {
        isFinished = true
        stopTimer()
        binding.inputField.isEnabled = false
        binding.practiceHint.text = getString(R.string.completed)

        val secs = if (elapsed > 0) elapsed else 1
        val speed = correctCount * 60 / secs
        val accuracy = if ((correctCount + wrongCount) > 0) {
            correctCount * 100 / (correctCount + wrongCount)
        } else 0

        saveRecord(speed, accuracy)
        showCompleteDialog(speed, accuracy)
    }

    private fun finishTimedPractice() {
        isRunning = false
        isFinished = true
        binding.inputField.isEnabled = false
        binding.practiceHint.text = getString(R.string.time_up)

        val inputText = binding.inputField.text.toString()

        lifecycleScope.launch {
            val content = currentContentId?.let { repository.getContent(it) } ?: return@launch
            val text = content.content
            var correct = 0
            var wrong = 0
            for (i in 0 until minOf(inputText.length, text.length)) {
                if (inputText[i] == text[i]) correct++ else wrong++
            }
            correctCount = correct
            wrongCount = wrong
            cursorPos = inputText.length

            val speed = correct * 60 / 60
            val accuracy = if ((correct + wrong) > 0) correct * 100 / (correct + wrong) else 0

            saveRecord(speed, accuracy)
            updateStats(text)
            showCompleteDialog(speed, accuracy)
        }
    }

    private suspend fun saveRecord(speed: Int, accuracy: Int) {
        val content = currentContentId?.let { repository.getContent(it) } ?: return
        val record = Record(
            id = "r${System.currentTimeMillis()}",
            contentId = currentContentId!!,
            contentTitle = content.title,
            mode = practiceMode,
            speed = speed,
            accuracy = accuracy,
            correctChars = correctCount,
            wrongChars = wrongCount,
            totalChars = correctCount + wrongCount,
            duration = elapsed,
            date = System.currentTimeMillis()
        )
        repository.insertRecord(record)
    }

    private fun showCompleteDialog(speed: Int, accuracy: Int) {
        val message = """
            速度：$speed 字/分
            正确率：$accuracy%
            正确：$correctCount 字
            错误：$wrongCount 字
            用时：$elapsed 秒
        """.trimIndent()

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.modal_complete))
            .setMessage(message)
            .setPositiveButton("好的") { _, _ ->
                findNavController().popBackStack()
            }
            .setCancelable(false)
            .show()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onPause() {
        super.onPause()
        stopTimer()
    }

    override fun onResume() {
        super.onResume()
        if (isRunning && !isFinished) {
            startTimer()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopTimer()
        _binding = null
    }
}
