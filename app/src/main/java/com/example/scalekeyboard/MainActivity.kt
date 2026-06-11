package com.example.scalekeyboard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.concurrent.Executors

class MainActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }
        
        val infoText = android.widget.TextView(this).apply {
            text = "برنامه با موفقیت فعال است.\nحالا خروجی اعداد به صورت اعشاری و تمیز اصلاح شده است."
            textSize = 18f
        }
        
        val btnAccessibility = android.widget.Button(this).apply {
            text = "تنظیمات دسترسی‌پذیری"
            setOnClickListener {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
        }

        layout.addView(infoText)
        layout.addView(btnAccessibility)
        setContentView(layout)
    }
}

class MecmesinAccessibilityService : AccessibilityService(), SerialInputOutputManager.Listener {
    private var usbPort: UsbSerialPort? = null
    private var usbIoManager: SerialInputOutputManager? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var isConnected = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isConnected) {
            startUsbConnection()
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        startUsbConnection()
    }

    fun startUsbConnection() {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) return

        val driver = availableDrivers[0]
        val connection = manager.openDevice(driver.device) ?: return

        usbPort = driver.ports[0]
        try {
            usbPort?.open(connection)
            usbPort?.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            usbIoManager = SerialInputOutputManager(usbPort, this)
            executor.submit(usbIoManager)
            isConnected = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewData(data: ByteArray?) {
        if (data == null || data.isEmpty()) return
        val rawData = String(data, Charsets.UTF_8).trim()
        
        // هوشمندسازی استخراج عدد:
        // ۱. حذف تمام حروف انگلیسی و واحدهای دستگاه (مثل ibf.in)
        var cleanNumber = rawData.replace(Regex("[a-zA-Z.\\s]+"), "")
        
        // ۲. تبدیل به فرمت اعشاری درست (اگر عدد مثلاً 08 بود، تبدیلش می‌کند به 0.08)
        if (cleanNumber.length >= 2) {
            val numAsDouble = cleanNumber.toDoubleOrNull()
            if (numAsDouble != null) {
                // تقسیم بر 100 برای ایجاد دو رقم اعشار دقیق مشابه روی دستگاه
                cleanNumber = String.format(java.util.Locale.US, "%.2f", numAsDouble / 100.0)
            }
        } else if (cleanNumber.isNotEmpty()) {
            cleanNumber = "0.0$cleanNumber"
        }

        if (cleanNumber.isEmpty()) return

        // تایپ خودکار عدد اصلاح شده
        val rootNode = rootInActiveWindow ?: return
        val focusedNode = findFocusedNode(rootNode)
        
        if (focusedNode != null) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, cleanNumber)
            }
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
    }

    private fun findFocusedNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedNode(child)
            if (result != null) return result
        }
        return null
    }

    override fun onRunError(e: java.lang.Exception?) {}

    override fun onDestroy() {
        super.onDestroy()
        usbIoManager?.stop()
        usbPort?.close()
    }
}
