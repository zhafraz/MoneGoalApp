package com.example.monegoal.model

data class Submission(
    val id: String = "",
    val childName: String = "",
    val title: String = "",
    val amount: Long = 0,
    val status: String = "",
    val createdAt: Long = 0
)