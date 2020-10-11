package com.richarddewan.camerax_sample

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.richarddewan.camerax.ui.MainActivityHolder
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnCamera.setOnClickListener {
            val intent = Intent(applicationContext,MainActivityHolder::class.java)
            startActivity(intent)
        }
    }
}