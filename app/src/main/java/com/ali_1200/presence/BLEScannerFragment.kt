package com.ali_1200.presence

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.ali_1200.presence.databinding.FragStudentBinding
import java.util.*

class BLEScannerFragment : Fragment() {
    lateinit var binding: FragStudentBinding
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var dataAlreadySent = false
    private lateinit var bleScanner: BluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false
    private var bluetoothGatt: BluetoothGatt? = null
    private val SCAN_PERIOD: Long = 10000 // 10 ثانیه
    private val TARGET_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = FragStudentBinding.inflate(layoutInflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bluetoothManager =
            requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bleScanner = bluetoothAdapter.bluetoothLeScanner
        dataAlreadySent = false
        val btnStartScan = binding.start
        val txtScanResult = binding.result

        btnStartScan.setOnClickListener {
            checkPermissionsAndStartScan(txtScanResult)
        }
    }

    private fun checkPermissionsAndStartScan(txtScanResult: TextView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ),
                    1002
                )
                return
            }
        }
        startScan(txtScanResult)
    }

    @SuppressLint("MissingPermission")
    private fun startScan(txtScanResult: TextView) {
        if (scanning) return
        if (dataAlreadySent) {
            txtScanResult.text = "داده قبلاً ارسال شده است"
            return
        }

        scanning = true
        txtScanResult.text = "در حال اسکن BLE..."

        handler.postDelayed({
            scanning = false
            bleScanner.stopScan(scanCallback)
            txtScanResult.text = "اسکن متوقف شد"
        }, SCAN_PERIOD)

        bleScanner.startScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            result.scanRecord?.serviceUuids?.forEach {
                if (it.uuid == TARGET_UUID) {
                    Log.d("BLEScanner", "Beacon معلم پیدا شد: ${result.device.address}")
                    Toast.makeText(
                        requireContext(),
                        "Beacon معلم پیدا شد: ${result.device.address}",
                        Toast.LENGTH_LONG
                    ).show()

                    // اتصال به GATT Server معلم
                    handler.postDelayed({
                        connectToGattServer(result.device)
                    }, 1000) // یک ثانیه تاخیر
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("BLEScanner", "خطا در اسکن BLE: $errorCode")
            Toast.makeText(requireContext(), "خطا : ${errorCode}", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToGattServer(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(requireContext(), false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d("GATTClient", "onConnectionStateChange: status=$status, newState=$newState")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("GATTClient", "متصل شد به GATT Server")
                    if (ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.d("GATTClient", "permigen")
                        return
                    }
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("GATTClient", "اتصال قطع شد")
                    gatt.close()
                }
            } else {
                Log.e("GATTClient", "خطا در اتصال: $status")
                disconnectGattServer()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val characteristic = gatt.getService(TARGET_UUID)?.getCharacteristic(
                    UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
                )
                characteristic?.let {
                    // فقط اگر قبلاً ارسال نشده باشد، ارسال کن
                    if (!dataAlreadySent) {
                        sendAttendanceData(gatt, it)
                        dataAlreadySent = true // تنظیم وضعیت به ارسال شده
                    } else {
                        Log.d(
                            "GATTClient",
                            "داده قبلاً ارسال شده است، از ارسال مجدد خودداری می‌شود"
                        )
                        // پس از اینکه دیگر نیازی به اتصال نیست، آن را قطع کنید
                        disconnectGattServer()
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendAttendanceData(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        val studentData = "Ali,123456,Android123".toByteArray(Charsets.UTF_8) // نمونه اطلاعات
        characteristic.value = studentData
        gatt.writeCharacteristic(characteristic)
        Log.d("GATTClient", "داده حضور ارسال شد: ${String(studentData)}")

        // پس از ارسال، اتصال را با تأخیر قطع کنید
        handler.postDelayed({
            disconnectGattServer()
            // اسکن را نیز متوقف کنید اگر هنوز در حال اسکن هستید
            if (scanning) {
                scanning = false
                bleScanner.stopScan(scanCallback)
            }
        }, 1000) // یک ثانیه صبر کنید تا داده ارسال شود
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGattServer() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        Log.d("GATTClient", "اتصال GATT بسته شد")
    }

}