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
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ali_1200.presence.databinding.BleadvertiserfragmentBinding
import java.text.SimpleDateFormat
import java.util.*

class BLEAdvertiserFragment : Fragment() {
    lateinit var binding: BleadvertiserfragmentBinding
    lateinit var ResponseRepository: ResponseRepository
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var adapter: AttendanceAdapter
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private val SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = BleadvertiserfragmentBinding.inflate(layoutInflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {


        bluetoothManager =
            requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        ResponseRepository = ResponseRepository(requireContext())
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser

        val btnStart = binding.start
        val btnStop = binding.stop

        btnStart.setOnClickListener {
            checkPermissionsAndStartAdvertising()
            startAdvertising()
        }
        btnStop.setOnClickListener { stopAdvertising() }

        // تنظیم RecyclerView
        adapter = AttendanceAdapter()
        binding.recyclerAttendance.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@BLEAdvertiserFragment.adapter
        }

        // ✅ مشاهده تغییرات `LiveData` و آپدیت خودکار `RecyclerView`
        ResponseRepository.date.observe(viewLifecycleOwner) { data ->
            val sortedList = data.sortedByDescending { it.timestamp } // آیتم جدید رو اول بذار
            adapter.submitList(sortedList)
            binding.recyclerAttendance.scrollToPosition(0)
        }
    }

    private fun checkPermissionsAndStartAdvertising() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // اندروید 12 به بالا
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else { // اندروید 11 و پایین‌تر
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            }
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_ADMIN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                permissionsToRequest.toTypedArray(),
                1001
            )
            return
        }

        startAdvertising() // اگر مجوزها داده شده بود، تبلیغات BLE را شروع کن
    }


    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (advertiser == null || !bluetoothAdapter.isEnabled) {
            Log.e("BLEAdvertiser", "بلوتوث خاموش است یا پشتیبانی نمی‌شود")
            Toast.makeText(requireContext(), "بلوتوث خاموش است", Toast.LENGTH_LONG).show()
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(true)
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            Log.d("BLEAdvertiser", "تبلیغات BLE با موفقیت شروع شد")
            Toast.makeText(requireContext(), "تبلیغات BLE با موفقیت شروع شد", Toast.LENGTH_LONG)
                .show()
            startGattServer()
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e("BLEAdvertiser", "خطا در شروع تبلیغات BLE: $errorCode")
            Toast.makeText(
                requireContext(),
                "خطا در شروع تبلیغات BLE: $errorCode",
                Toast.LENGTH_LONG
            ).show()
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
            ResponseRepository.insertdata(data)
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
