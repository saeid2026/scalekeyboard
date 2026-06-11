package com.example.scalekeyboard

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.concurrent.Executors

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }
        
        val infoText = TextView(this).apply {
            text = "۱. ابتدا دکمه شناور را فعال کنید.\n۲. کابل دستگاه Mecmesin را وصل کنید.\n۳. روی مربع قرمز روی صفحه کلیک کنید تا اتصال برقرار شود."
            textSize = 18f
        }
        
        val btnOverlay = Button(this).apply {
            text = "فعال‌سازی دکمه شناور"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(intent)
                } else {
                    startService(Intent(this@MainActivity, MecmesinFloatingService::class.java))
                }
            }
        }

        layout.addView(infoText)
        layout.addView(btnOverlay)
        setContentView(layout)
    }
}

class MecmesinFloatingService : Service(), SerialInputOutputManager.Listener {
    private var windowManager: WindowManager? = null
    private var floatingButton: Button? = null
    private var usbPort: UsbSerialPort? = null
    private var usbIoManager: SerialInputOutputManager? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var lastData: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        floatingButton = Button(this).apply {
            text = "دستگاه قطع (کلیک جهت اتصال)"
            setBackgroundColor(0xFFFF0000.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            
            // اضافه شدن قابلیت کلیک روی دکمه قرمز برای اسکن مجدد پورت USB
            setOnClickListener {
                startUsbConnection()
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 100
        }

        windowManager?.addView(floatingButton, params)
        startUsbConnection()
    }

    fun startUsbConnection() {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        
        if (availableDrivers.isEmpty()) {
            Toast.makeText(this, "هیچ کابل یا دستگاه USB شناسایی نشد! اتصالات را چک کنید.", Toast.LENGTH_SHORT).show()
            return
        }

        val driver = availableDrivers[0]
        val connection = manager.openDevice(driver.device) 
        
        if (connection == null) {
            Toast.makeText(this, "دستگاه وصل است اما مجوز اندروید صادر نشده است.", Toast.LENGTH_SHORT).show()
            return
        }

        usbPort = driver.ports[0]
        try {
            usbPort?.open(connection)
            usbPort?.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            usbIoManager = SerialInputOutputManager(usbPort, this)
            executor.submit(usbIoManager)
            
            floatingButton?.post {
                floatingButton?.text = "آماده دریافت عدد"
                floatingButton?.setBackgroundColor(0xFF00AA00.toInt())
                // بعد از سبز شدن، وظیفه دکمه تبدیل به کپی کردن متن می‌شود
                floatingButton?.setOnClickListener {
                    copyToClipboard()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "خطا در باز کردن پورت: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard() {
        if (lastData.isNotEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Mecmesin", lastData)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "عدد $lastData کپی شد!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "هنوز دیتایی از دستگاه ارسال نشده است.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewData(data: ByteArray?) {
        if (data == null || data.isEmpty()) return
        lastData = String(data, Charsets.UTF_8).trim()
        
        floatingButton?.post {
            floatingButton?.text = "دیتا: $lastData (کلیک جهت کپی)"
        }
    }

    override fun onRunError(e: java.lang.Exception?) {}

    override fun onDestroy() {
        super.onDestroy()
        usbIoManager?.stop()
        usbPort?.close()
        if (floatingButton != null) windowManager?.removeView(floatingButton)
    }
}
