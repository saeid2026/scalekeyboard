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
            setPadding(60, 60, 60, 60)
            background = android.graphics.drawable.ColorDrawable(0xFFF8FAFC.toInt()) // تم روشن و مدرن
        }
        
        val infoText = android.widget.TextView(this).apply {
            text = "Mecmesin Wedge Active!\n\n" +
                   "• The system is running successfully.\n" +
                   "• Data format has been calibrated to decimals (e.g., 0.08).\n\n" +
                   "How to use:\n" +
                   "1. Ensure the Accessibility Service is turned ON.\n" +
                   "2. Connect the gauge via OTG cable.\n" +
                   "3. Open Chrome, Excel, or Notes.\n" +
                   "4. Press Print on the device to type automatically."
            textSize = 16f
            setTextColor(0xFF1E293B.toInt())
            setLineSpacing(0f, 1.3f)
        }
        
        val btnAccessibility = android.widget.Button(this).apply {
            text = "Accessibility Settings"
            setBackgroundColor(0xFF2563EB.toInt()) // رنگ آبی رسمی و صنعتی
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setPadding(20, 30, 20, 30)
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 50
            }
            layoutParams = params
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
            
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(this, "Mecmesin Gauge Connected! 🟢", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewData(data: ByteArray?) {
        if (data == null || data.isEmpty()) return
        val rawData = String(data, Charsets.UTF_8).trim()
        
        var cleanNumber = rawData.replace(Regex("[a-zA-Z.\\s]+"), "")
        
        if (cleanNumber.length >= 2) {
            val numAsDouble = cleanNumber.toDoubleOrNull()
            if (numAsDouble != null) {
                cleanNumber = String.format(java.util.Locale.US, "%.2f", numAsDouble / 100.0)
            }
        } else if (cleanNumber.isNotEmpty()) {
            cleanNumber = "0.0$cleanNumber"
        }

        if (cleanNumber.isEmpty()) return

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
