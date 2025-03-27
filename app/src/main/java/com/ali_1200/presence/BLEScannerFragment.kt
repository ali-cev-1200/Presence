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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.ali_1200.presence.databinding.FragStudentBinding
import java.text.SimpleDateFormat
import java.util.*

class BLEScannerFragment : Fragment() {
    private lateinit var binding: FragStudentBinding
    private lateinit var loginViewModel: LoginRepository

    private val bluetoothManager by lazy {
        requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter by lazy { bluetoothManager.adapter }
    private val bleScanner by lazy { bluetoothAdapter.bluetoothLeScanner }
    private val handler = Handler(Looper.getMainLooper())

    private var teacherFound = false
    private var scanning = false
    private var bluetoothGatt: BluetoothGatt? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startBLEScan()
        } else {
            showPermissionDeniedToast()
        }
    }

    companion object {
        private const val SCAN_PERIOD: Long = 10_000 // 10 seconds
        private val TARGET_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
        private val TARGET_CHARACTERISTIC_UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragStudentBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loginViewModel = LoginRepository(requireContext())
        // بررسی وجود timestamp قبل از صدا زدن
        loginViewModel.getSavedLoginData()?.timestamp?.let { timestamp ->
            getRelativeTime(timestamp)
        }
        setupUI()
    }

    private fun setupUI() {
        // اگر داده قبلاً ارسال شده، نمایش وضعیت
        if (loginViewModel.getIsSent()) {
            binding.result.text = "داده قبلاً ارسال شده است"
        }

        binding.start.setOnClickListener {
            checkAndRequestPermissions()
        }
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = getRequiredPermissions()

        val notGrantedPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isNotEmpty()) {
            permissionLauncher.launch(notGrantedPermissions.toTypedArray())
        } else {
            startBLEScan()
        }
    }

    private fun getRequiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun showPermissionDeniedToast() {
        Toast.makeText(
            requireContext(),
            "مجوزهای لازم تایید نشدند",
            Toast.LENGTH_LONG
        ).show()
    }

    @SuppressLint("MissingPermission")
    private fun startBLEScan() {
        getRelativeTime(loginViewModel.getSavedLoginData()?.timestamp!!)
        if (scanning || loginViewModel.getIsSent()) return

        scanning = true
        binding.result.text = "در حال اسکن BLE..."

        handler.postDelayed(::stopScanning, SCAN_PERIOD)
        bleScanner.startScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        scanning = false
        bleScanner.stopScan(scanCallback)
        binding.result.text = "اسکن متوقف شد"
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!loginViewModel.getIsSent() && !teacherFound) {
                result.scanRecord?.serviceUuids?.forEach { uuid ->
                    if (uuid.uuid == TARGET_SERVICE_UUID) {
                        handleTeacherBeaconFound(result.device)
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            handleScanError(errorCode)
        }
    }

    private fun handleTeacherBeaconFound(device: BluetoothDevice) {
        teacherFound = true
        Log.d("BLEScanner", "Beacon معلم پیدا شد: ${device.address}")

        handler.post {
            Toast.makeText(
                requireContext(),
                "Beacon معلم پیدا شد",
                Toast.LENGTH_SHORT
            ).show()
        }

        handler.postDelayed({
            connectToGattServer(device)
        }, 1000)
    }

    private fun handleScanError(errorCode: Int) {
        Log.e("BLEScanner", "خطا در اسکن BLE: $errorCode")
        handler.post {
            Toast.makeText(
                requireContext(),
                "خطا در اسکن، تلاش مجدد...",
                Toast.LENGTH_SHORT
            ).show()
        }

        handler.postDelayed({ startBLEScan() }, 3000)
    }

    @SuppressLint("MissingPermission")
    private fun connectToGattServer(device: BluetoothDevice) {
        if (!hasBluetoothPermission()) {
            showBluetoothPermissionError()
            return
        }

        bluetoothGatt = device.connectGatt(requireContext(), false, gattCallback)
    }

    private fun hasBluetoothPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }

        return ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showBluetoothPermissionError() {
        Log.d("BLEScanner", "مجوز بلوتوث داده نشده است")
        Toast.makeText(
            requireContext(),
            "لطفاً مجوز بلوتوث را فعال کنید",
            Toast.LENGTH_LONG
        ).show()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d("BLEScanner", "متصل شد به GATT Server")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d("BLEScanner", "اتصال قطع شد")
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val characteristic = gatt.getService(TARGET_SERVICE_UUID)
                    ?.getCharacteristic(TARGET_CHARACTERISTIC_UUID)

                characteristic?.let {
                    if (!loginViewModel.getIsSent()) {
                        sendAttendanceData(gatt, it)
                    } else {
                        Log.d("BLEScanner", "داده قبلاً ارسال شده است")
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
        val data = loginViewModel.getSavedLoginData()

        data?.let { loginData ->
            val studentData = "${loginData.name} ${loginData.studentID} ${loginData.deviceID}"
                .toByteArray(Charsets.UTF_8)

            characteristic.value = studentData
            gatt.writeCharacteristic(characteristic)

            Log.d("BLEScanner", "داده حضور ارسال شد: $studentData")

            updateAttendanceStatus()

            handler.postDelayed({
                disconnectGattServer()
                stopScanningIfActive()
            }, 1000)
        } ?: run {
            Log.e("BLEScanner", "خطا در دیتابیس!")
        }
    }

    private fun updateAttendanceStatus() {
        teacherFound = false
        loginViewModel.sendStatus(true)
        loginViewModel.setTime(System.currentTimeMillis())
    }

    @SuppressLint("MissingPermission")
    private fun stopScanningIfActive() {
        if (scanning) {
            scanning = false
            bleScanner.stopScan(scanCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGattServer() {
        if (!hasBluetoothPermission()) {
            showBluetoothPermissionError()
            return
        }

        bluetoothGatt?.let {
            it.disconnect()
            it.close()
        }
        bluetoothGatt = null

        Log.d("BLEScanner", "اتصال GATT بسته شد")
    }

    private fun getRelativeTime(timestamp: Long) {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        if (diff > 24 /** 60 * 60_000*/) { // 24 ساعت
            loginViewModel.sendStatus(false)
        }
    }
}