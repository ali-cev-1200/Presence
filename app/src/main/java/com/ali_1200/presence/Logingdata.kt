package com.ali_1200.presence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Login")
data class Logingdata(
   @PrimaryKey val id:Int = 1,
    val studentID :String,
    val name :String,
    val deviceID :String
)