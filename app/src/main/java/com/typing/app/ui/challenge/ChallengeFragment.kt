package com.typing.app.ui.challenge

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.typing.app.R
import com.typing.app.data.AppDatabase
import com.typing.app.data.DataRepository
import com.typing.app.databinding.FragmentChallengeBinding
import kotlinx.coroutines.launch

data class RankInfo(
    val name: String,
    val star: String,
    val iconText: String,
    val className: String,
    val nextThreshold: Int?,
    val currentScore: Int
)

class ChallengeFragment : Fragment() {

    private var _binding: FragmentChallengeBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: DataRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChallengeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        repository = DataRepository(db.contentDao(), db.recordDao())

        binding.btnStartChallenge.setOnClickListener {
            startChallenge()
        }

        loadChallengeData()
    }

    private fun loadChallengeData() {
        lifecycleScope.launch {
            repository.allRecords.observe(viewLifecycleOwner) { records ->
                updateChallengeUI(records)
            }
        }
    }

    private fun updateChallengeUI(records: List<com.typing.app.data.Record>) {
        // Calculate stats
        val totalGames = records.size
        var bestSpeed = 0
        var bestAcc = 0
        var totalScore = 0

        for (r in records) {
            if (r.speed > bestSpeed) bestSpeed = r.speed
            if (r.accuracy > bestAcc) bestAcc = r.accuracy
            totalScore += r.speed * r.accuracy / 100
        }

        // Get rank info
        val rank = getRank(totalScore)
        val ctx = requireContext()

        // Update badge icon background color
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            when (rank.className) {
                "silver" -> setColors(
                    ContextCompat.getColor(ctx, R.color.silver_start),
                    ContextCompat.getColor(ctx, R.color.silver_end),
                    ContextCompat.getColor(ctx, R.color.silver_start)
                )
                "gold" -> setColors(
                    ContextCompat.getColor(ctx, R.color.gold_start),
                    ContextCompat.getColor(ctx, R.color.gold_end),
                    ContextCompat.getColor(ctx, R.color.gold_start)
                )
                "diamond" -> setColors(
                    ContextCompat.getColor(ctx, R.color.diamond_start),
                    ContextCompat.getColor(ctx, R.color.diamond_end),
                    ContextCompat.getColor(ctx, R.color.diamond_start)
                )
                "master" -> setColors(
                    ContextCompat.getColor(ctx, R.color.master_start),
                    ContextCompat.getColor(ctx, R.color.master_end),
                    ContextCompat.getColor(ctx, R.color.master_start)
                )
                else -> setColors(
                    ContextCompat.getColor(ctx, R.color.bronze_start),
                    ContextCompat.getColor(ctx, R.color.bronze_end),
                    ContextCompat.getColor(ctx, R.color.bronze_start)
                )
            }
        }
        binding.badgeIcon.background = drawable

        // Update text fields
        binding.badgeTitle.text = "${rank.name} ${rank.star}"
        if (rank.nextThreshold != null) {
            binding.badgeSub.text = getString(R.string.rank_next_fmt, rank.nextThreshold - rank.currentScore)
            val pct = minOf(100, (rank.currentScore * 100 / rank.nextThreshold))
            binding.rankFill.layoutParams.width = (280 * pct / 100).toInt()
            binding.rankInfo.text = "${rank.currentScore}/${rank.nextThreshold}"
        } else {
            binding.badgeSub.text = getString(R.string.rank_max)
            binding.rankFill.layoutParams.width = 280
            binding.rankInfo.text = "${rank.currentScore}/${rank.currentScore}"
        }
        binding.rankFill.requestLayout()

        binding.csGames.text = totalGames.toString()
        binding.csBestSpeed.text = bestSpeed.toString()
        binding.csBestAcc.text = "$bestAcc%"
    }

    private fun getRank(score: Int): RankInfo {
        return when {
            score >= 1000 -> RankInfo("大师", "", "🏆", "master", null, score)
            score >= 600 -> RankInfo("钻石", "", "💎", "diamond", 1000, score)
            score >= 300 -> RankInfo("黄金", "", "🥇", "gold", 600, score)
            score >= 100 -> RankInfo("白银", "", "🥈", "silver", 300, score)
            else -> {
                val star = minOf(5, score / 20 + 1)
                RankInfo("青铜", "${star}星", "🥉", "", 100, score)
            }
        }
    }

    private fun startChallenge() {
        lifecycleScope.launch {
            val contents = repository.getAllContentsSync()
            if (contents.isEmpty()) {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.title_cannot_empty))
                    .setMessage(getString(R.string.msg_add_content_first))
                    .setPositiveButton("好的", null)
                    .show()
                return@launch
            }

            // Navigate to practice with timed mode and first content
            val bundle = Bundle().apply {
                putString("mode", "timed")
                putString("contentId", contents.first().id)
            }
            findNavController().navigate(R.id.practice_fragment, bundle)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning from practice
        loadChallengeData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
