package com.mongodb.tasktracker.model

import java.io.Serializable

data class AttendanceInfo(
    val date: Long, // Giả sử là Unix timestamp
    val status: String
) : Serializable