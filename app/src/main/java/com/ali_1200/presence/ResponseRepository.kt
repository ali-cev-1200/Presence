package com.ali_1200.presence

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Repository مسئولیت ارتباط با منابع داده (API و دیتابیس) را بر عهده دارد
class ResponseRepository(context: Context) {
    private val responseDao = MainDatabase.getDatabase(context).responseDao // دسترسی به DAO

    // ✅ دریافت LiveData مستقیم از DAO
    val date: LiveData<List<dateResponse>> = responseDao.getLoginData()

//    // ✅ تابعی برای درج داده در دیتابیس (باید در Background اجرا بشه)
//    fun insertData(data: dateResponse) {
//        Thread { responseDao.insertLoginData(data) }.start()
//    }
//}
    fun insertdata(data: dateResponse){
        responseDao.insertLoginData(data)
    }
}
