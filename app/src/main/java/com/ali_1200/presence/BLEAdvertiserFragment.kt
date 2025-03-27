package com.ali_1200.presence

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ali_1200.presence.databinding.BleadvertiserfragmentBinding
import java.text.SimpleDateFormat
import java.util.*

class BLEAdvertiserFragment : Fragment() {
    private lateinit var binding: BleadvertiserfragmentBinding
    private lateinit var responseRepository: ResponseRepository
    private lateinit var adapter: AttendanceAdapter

    private val bluetoothManager by lazy {
        requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter by lazy { bluetoothManager.adapter }
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            startAdvertising()
        } else {
            Toast.makeText(
                requireContext(),
                "مجوزهای لازم تایید نشدند",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        private val SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BleadvertiserfragmentBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        responseRepository = ResponseRepository(requireContext())
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser

        setupRecyclerView()
        setupButtonListeners()
        observeAttendanceData()
    }

    private fun setupRecyclerView() {
        adapter = AttendanceAdapter()
        binding.recyclerAttendance.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@BLEAdvertiserFragment.adapter
        }
    }

    private fun setupButtonListeners() {
        binding.start.setOnClickListener {
            checkAndRequestPermissions()
        }

        binding.stop.setOnClickListener {
            stopAdvertising()
        }
    }

    private fun observeAttendanceData() {
        responseRepository.date.observe(viewLifecycleOwner) { data ->
            val sortedList = data.sortedByDescending { it.timestamp }
            adapter.submitList(sortedList)
            binding.recyclerAttendance.scrollToPosition(0)
        }
    }
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = PermissionUtils.getRequiredPermissions(isAdvertiser = true)
        val notGrantedPermissions = PermissionUtils.checkPermissions(requireContext(), permissionsToRequest)

        if (notGrantedPermissions.isNotEmpty()) {
            permissionLauncher.launch(notGrantedPermissions.toTypedArray())
        } else {
            startAdvertising()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (advertiser == null || !bluetoothAdapter.isEnabled) {
            showAdvertisingError("بلوتوث خاموش است یا پشتیبانی نمی‌شود")
            return
        }

        val settings = createAdvertiseSettings()
        val data = createAdvertiseData()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private fun createAdvertiseSettings(): AdvertiseSettings =
        AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

    private fun createAdvertiseData(): AdvertiseData =
        AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(true)
            .build()

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
    }

    private fun showAdvertisingError(message: String) {
        Log.e("BLEAdvertiser", message)
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d("BLEAdvertiser", "تبلیغات BLE با موفقیت شروع شد")
            Toast.makeText(
                requireContext(),
                "تبلیغات BLE با موفقیت شروع شد",
                Toast.LENGTH_LONG
            ).show()
            startGattServer()
        }

        override fun onStartFailure(errorCode: Int) {
            showAdvertisingError("خطا در شروع تبلیغات BLE: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        gattServer = bluetoothManager.openGattServer(requireContext(), gattServerCallback)
        if (gattServer == null) {
            Log.e("dataat", "راه‌اندازی GATT Server با خطا مواجه شد")
            return
        }

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB"),
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        service.addCharacteristic(characteristic)
        gattServer?.addService(service)
        Log.d("dataat", "GATT Server با موفقیت شروع شد")
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val receivedData = value?.toString(Charsets.UTF_8)
            Log.d("dataat", "داده دریافت شد: $receivedData")

            // پردازش داده‌ها
            if (receivedData != null) {
                saveReceivedData(receivedData)
            }

            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)

        }
    }

    private fun saveReceivedData(data: String) {
        val parts = data.split(" ")
        if (parts.size == 3) {
            val studentName = parts[0]
            val studentId = parts[1]
            val androidId = parts[2]

            Log.d(
                "dataat",
                "📝 دانشجو: $studentName | کد دانشجویی: $studentId | Android ID: $androidId"
            )
            Log.v("dataat", data)
//            Toast.makeText(requireContext(), data, Toast.LENGTH_LONG).show()
            val now = System.currentTimeMillis()
            val data = dateResponse(
                studentId,
                studentName,
                androidId + now.toString(),
                now,
                formatTimestamp(now)
            )
            responseRepository.insertdata(data)
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss -- yyyy/MM/dd", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun stringToDate(dateString: String): Date? {
        val format = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        return format.parse(dateString) // خروجی Date
    }

//    fun convertToShamsi(dateString: Date): String {
//        try {
//            val persianDate = PersianDate(dateString) // ایجاد شی تاریخ شمسی
//            val outputFormatter = PersianDateFormat("Y/m/d") // فرمت دلخواه
//            return outputFormatter.format(persianDate) // خروجی مانند "1402/10/15"
//        } catch (e: Exception) {
//            e.printStackTrace()
//            return "خطا در تبدیل تاریخ ⚠️"
//        }
//    }
}
