package com.typing.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.typing.app.R
import com.typing.app.data.AppDatabase
import com.typing.app.data.DataRepository
import com.typing.app.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: DataRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        repository = DataRepository(db.contentDao(), db.recordDao())

        // Initialize default data if needed
        lifecycleScope.launch {
            val contents = repository.getAllContentsSync()
            if (contents.isEmpty()) {
                initDefaultData()
            }
        }

        binding.btnNormalPractice.setOnClickListener {
            navigateToPractice("normal")
        }

        binding.btnTimedPractice.setOnClickListener {
            navigateToPractice("timed")
        }

        binding.btnAddContent.setOnClickListener {
            findNavController().navigate(R.id.content_list_fragment)
        }
    }

    private fun navigateToPractice(mode: String) {
        // Store mode in shared preferences or arguments
        val bundle = Bundle().apply { putString("mode", mode) }
        findNavController().navigate(R.id.practice_fragment, bundle)
    }

    private suspend fun initDefaultData() {
        val now = System.currentTimeMillis()
        val defaultContents = listOf(
            Content("c1", "五课", "古云：不矜细行，终累大德。为山九仞，功亏一篑。此圣贤之深戒也。然今之士人，多务虚名而鲜实学，逐末忘本，舍近求远。是以所学非所用，所用非所学，终无成焉。", now),
            Content("c2", "正气歌", "天地有正气，杂然赋流形。下则为河岳，上则为日星。于人曰浩然，沛乎塞苍冥。皇路当清夷，含和吐明庭。时穷节乃见，一一垂丹青。", now),
            Content("c3", "说园", "园有静观、动观之分。小园以静观为主，大园以动观为主。静观者，如静坐斋中，平视远眺，景随人意；动观者，步移景换，如游画中。", now),
            Content("c4", "清静经", "大道无形，生育天地；大道无情，运行日月；大道无名，长养万物。吾不知其名，强名曰道。夫道者，有清有浊，有动有静。", now),
            Content("c5", "夏夜晚风", "夏夜的风，带着白天的余温，轻轻拂过脸颊。远处的蝉鸣此起彼伏，像是大自然的交响乐。星空下，一切都显得那么宁静而美好。", now),
            Content("c6", "千字文", "天地玄黄，宇宙洪荒。日月盈昃，辰宿列张。寒来暑往，秋收冬藏。闰余成岁，律吕调阳。云腾致雨，露结为霜。", now),
            Content("c7", "孙子兵法始计篇", "孙子曰：兵者，国之大事，死生之地，存亡之道，不可不察也。故经之以五事，校之以计，而索其情：一曰道，二曰天，三曰地，四曰将，五曰法。", now),
            Content("c8", "三字经", "人之初，性本善。性相近，习相远。苟不教，性乃迁。教之道，贵以专。昔孟母，择邻处。子不学，断机杼。窦燕山，有义方。教五子，名俱扬。", now),
            Content("c9", "般若波罗蜜多心经", "观自在菩萨，行深般若波罗蜜多时，照见五蕴皆空，度一切苦厄。舍利子，色不异空，空异色，色即是空，空即是色。受想行识，亦复如是。", now)
        )
        defaultContents.forEach { repository.insertContent(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
