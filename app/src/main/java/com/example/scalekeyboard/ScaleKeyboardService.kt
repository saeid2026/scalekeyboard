package com.example.scalekeyboard

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbDevice
import android.content.Context
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.concurrent.Executors

class ScaleKeyboardService : InputMethodService(), SerialInputOutputManager.Listener {

    private var usbPort: UsbSerialPort? = null
    private var usbIoManager: SerialInputOutputManager? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        startUsbConnection()
    }

    private fun startUsbConnection() {
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

        // تبدیل مستقیم بایت‌ها به متن بدون هیچ فیلتری (دقیقاً مثل برنامه ترمینال)
        val rawData = String(data, Charsets.UTF_8).trim()

        if (rawData.isNotEmpty()) {
            val ic: InputConnection = currentInputConnection ?: return
            
            // تایپ مستقیم متن ترازو
            ic.commitText(rawData, 1)
            
            // ارسال خودکار کلید اینتر
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    override fun onRunError(e: Exception?) {
        // هندل کردن خطاهای احتمالی اتصال
        e?.printStackTrace()
    }

    override fun onDestroy() {
        usbIoManager?.stop()
        try {
            usbPort?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}
