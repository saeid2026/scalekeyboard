package com.example.scalekeyboard

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.hardware.usb.UsbManager
import android.content.Context
import android.os.Bundle
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.concurrent.Executors

class MecmesinWedgeService : AccessibilityService(), SerialInputOutputManager.Listener {

    companion object {
        var instance: MecmesinWedgeService? = null
    }

    private var usbPort: UsbSerialPort? = null
    private var usbIoManager: SerialInputOutputManager? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewData(data: ByteArray?) {
        if (data == null || data.isEmpty()) return
        val rawData = String(data, Charsets.UTF_8).trim()

        if (rawData.isNotEmpty()) {
            // پیدا کردن کادر متنی که در حال حاضر کاربر رویش کلیک کرده است
            val rootNode = rootInActiveWindow ?: return
            val focusedNode = findFocusedNode(rootNode)
            
            focusedNode?.let { node ->
                val arguments = Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, rawData)
                // تزریق مستقیم متن گشتاورسنج به داخل کادر (مثل اکسل)
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
        }
    }

    private fun findFocusedNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isFocused && node.className == "android.widget.EditText") return node
        for (i in 0 until node.childCount) {
            val focused = findFocusedNode(node.getChild(i))
            if (focused != null) return focused
        }
        return null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onRunError(e: Exception?) { e?.printStackTrace() }

    override fun onDestroy() {
        usbIoManager?.stop()
        usbPort?.close()
        instance = null
        super.onDestroy()
    }
}
