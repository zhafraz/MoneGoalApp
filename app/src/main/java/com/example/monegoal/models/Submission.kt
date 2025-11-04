package com.example.monegoal.models

data class Submission(
    val id: String = "",
    val childId: String? = null,
    val childName: String = "",
    val title: String = "",
    val amount: Long = 0L,
    val status: String = "",
    val createdAt: Long = 0L
)