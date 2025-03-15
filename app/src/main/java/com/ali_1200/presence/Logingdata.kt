package com.ali_1200.presence

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "Login")
data class Logingdata(
    @PrimaryKey val id: Int = 1,
    val studentID: String,
    val name: String,
    val deviceID: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "Respons")
data class dateResponse(
    val studentID: String,
    val name: String,
    @PrimaryKey val deviceID: String,
//    val timestamp: Long = System.currentTimeMillis()
)