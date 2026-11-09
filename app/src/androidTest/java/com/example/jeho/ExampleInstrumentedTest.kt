package com.example.jeho

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            text = "길일기 시작!"
            textSize = 24f
            setPadding(48, 48, 48, 48)
        }
        setContentView(textView)
    }
}