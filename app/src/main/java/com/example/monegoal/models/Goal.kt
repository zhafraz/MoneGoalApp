package com.example.monegoal.models

data class Goal(
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var currentAmount: Long = 0L,   // optional (tidak dipakai jika kita gunakan user.balance)
    var targetAmount: Long = 0L,
    var points: Int = 0,
    var createdAt: Long = 0L
)