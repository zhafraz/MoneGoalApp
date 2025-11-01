package com.example.monegoal.models

data class CampaignItem(
    val title: String,
    val description: String,
    val category: String,
    val progress: Int,
    val imageResId: Int? = null,
    val imageUrl: String? = null
)