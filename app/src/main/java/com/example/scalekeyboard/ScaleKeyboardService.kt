package com.example.scalekeyboard

import android.content.Context
import android.hardware.usb.UsbManager
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException
import java.util.concurrent.Executors

class ScaleKeyboardService : InputMethodService(), SerialInputOutputManager.Listener {

    private var usbIoManager: SerialInputOutputManager? = null
    private var usbPort: UsbSerialPort? = null

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
            usbPort?.setParameters(9600, 8, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            usbIoManager = SerialInputOutputManager(usbPort, this)
            Executors.newSingleThreadExecutor().submit(usbIoManager)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onNewData(data: ByteArray?) {
        if (data == null || data.isEmpty()) return

        val rawData = String(data, Charsets.UTF_8)
        val cleanWeight = rawData.filter { it.isDigit() || it == '.' || it == '-' }

        if (cleanWeight.isNotEmpty()) {
            val ic: InputConnection = currentInputConnection ?: return
            ic.commitText(cleanWeight, 1)
            
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    override fun onRunError(e: Exception?) {
        startUsbConnection()
    }

    override fun onDestroy() {
        usbIoManager?.stop()
        try {
            usbPort?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}
