package com.example.monegoal.utils

import com.example.monegoal.models.CampaignItem
import kotlin.random.Random

object DummyDataGenerator {

    private val campaignTitles = listOf(
        "Festival Sains SMA IT Nur Hidayah",
        "Proyek Penelitian Air Bersih",
        "Kegiatan Donasi Buku Sekolah",
        "Workshop Robotika Pelajar",
        "Inovasi Energi Terbarukan Siswa"
    )

    private val goalTitles = listOf(
        "Olimpiade Sains Internasional",
        "Kompetisi Inovasi Teknologi ASEAN",
        "Lomba Matematika Nasional",
        "Hackathon Pelajar 2025"
    )

    private val programTitles = listOf(
        "Olimpiade Sains Nasional",
        "Festival Film Pendek Siswa",
        "Program Aksi Sosial Sekolah",
        "Pelatihan Digital Kreatif"
    )

    private val descriptions = listOf(
        "Program unggulan untuk meningkatkan prestasi akademik.",
        "Event kolaborasi antar siswa lintas sekolah.",
        "Kegiatan pengembangan kreativitas dan inovasi.",
        "Ajang bergengsi tingkat nasional bagi pelajar."
    )

    fun generateRandomList(category: String, count: Int = 5): List<CampaignItem> {
        val titles = when (category) {
            "Campaign" -> campaignTitles
            "Goal" -> goalTitles
            "Program" -> programTitles
            else -> emptyList()
        }

        val keyword = when (category) {
            "Campaign" -> "science,festival,students"
            "Goal" -> "competition,education,students"
            "Program" -> "school,project,learning"
            else -> "education"
        }

        return List(count) {
            val title = titles.random()
            val desc = descriptions.random()
            val progress = Random.nextInt(30, 100)
            val imageUrl = "https://source.unsplash.com/random/800x600/?$keyword"

            CampaignItem(
                title = title,
                description = desc,
                category = category,
                progress = progress,
                imageUrl = imageUrl
            )
        }
    }
}