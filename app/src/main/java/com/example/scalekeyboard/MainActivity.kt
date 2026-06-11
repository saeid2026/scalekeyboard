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
            text = "مراحل فعال‌سازی اتوماتیک:\n۱. دکمه زیر را بزنید و در صفحه باز شده، نام برنامه (Mecmesin Wedge) را پیدا کرده و آن را روشن (On) کنید.\n۲. کابل دستگاه را وصل کنید.\n۳. حالا وارد کروم یا نوت شوید؛ با زدن دکمه دستگاه، عدد خودکار تایپ می‌شود."
            textSize = 18f
        }
        
        val btnAccessibility = android.widget.Button(this).apply {
            text = "فعال‌سازی سرویس تایپ خودکار"
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
        // وقتی کابل وصل می‌شود و سرویس زنده است، پورت را باز نگه می‌دارد
        if (!isConnected) {
            startUsbConnection()
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        Toast.makeText(this, "سرویس تایپ خودکار فعال شد", Toast.LENGTH_SHORT).show()
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
            
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(this, "دستگاه گشتاورسنج متصل و آماده است! 🟢", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewData(data: ByteArray?) {
        if (data == null || data.isEmpty()) return
        val lastData = String(data, Charsets.UTF_8).trim()
        
        // پیدا کردن کادر متنی که فوکوس (نشانگر چشمک‌زن) روی آن است و تایپ خودکار عدد
        val rootNode = rootInActiveWindow ?: return
        val focusedNode = findFocusedNode(rootNode)
        
        if (focusedNode != null) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, lastData)
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
