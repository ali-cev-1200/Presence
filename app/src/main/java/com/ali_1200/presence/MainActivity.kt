package com.ali_1200.presence

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {
    lateinit var repository: LoginRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        repository = LoginRepository(this)
        val data = repository.getSavedLoginData()
        if (data != null) {
            if (data.studentID == "1") {
                replace_Fragment(BLEAdvertiserFragment())
            } else {
                replace_Fragment(BLEScannerFragment())
            }
        } else {
            replace_Fragment(Frag_Login())
        }
    }

    fun replace_Fragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.frag_main, fragment)
        transaction.commit()
    }
}