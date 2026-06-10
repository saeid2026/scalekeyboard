package com.example.scalekeyboard

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.content.Intent
import android.provider.Settings

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState: Bundle?)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        val infoText = TextView(this).apply {
            text = "ابتدا از تنظیمات گوشی، خدمات دسترسی‌پذیری این برنامه را فعال کنید. سپس دستگاه را وصل کنید."
            textSize = 18f
        }
        
        val btnSetting = Button(this).apply {
            text = "باز کردن تنظیمات دسترسی‌پذیری گوشی"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val btnConnect = Button(this).apply {
            text = "اتصال مجدد به گشتاورسنج"
            setOnClickListener {
                MecmesinWedgeService.instance?.startUsbConnection()
            }
        }

        layout.addView(infoText)
        layout.addView(btnSetting)
        layout.addView(btnConnect)
        setContentView(layout)
    }
}
