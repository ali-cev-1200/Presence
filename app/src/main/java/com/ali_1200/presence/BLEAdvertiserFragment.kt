package com.ali_1200.presence

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
import com.ali_1200.presence.databinding.BleadvertiserfragmentBinding
import java.util.*

class BLEAdvertiserFragment : Fragment() {
    lateinit var binding: BleadvertiserfragmentBinding
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private val SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = BleadvertiserfragmentBinding.inflate(layoutInflater,container,false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {


        bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser

        val btnStart =  binding.start
        val btnStop = binding.stop

        btnStart.setOnClickListener {
            checkPermissionsAndStartAdvertising()
            startAdvertising() }
        btnStop.setOnClickListener { stopAdvertising() }
    }

    private fun checkPermissionsAndStartAdvertising() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // اندروید ۱۲ به بعد
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ),
                    1001
                )
                return
            }
        }
        startAdvertising() // اگر مجوزها داده شده بود، تبلیغات BLE را شروع کن
    }


    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (advertiser == null || !bluetoothAdapter.isEnabled) {
            Log.e("BLEAdvertiser", "بلوتوث خاموش است یا پشتیبانی نمی‌شود")
            Toast.makeText(requireContext(),"بلوتوث خاموش است",Toast.LENGTH_LONG).show()
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
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
            Toast.makeText(requireContext(),"تبلیغات BLE با موفقیت شروع شد", Toast.LENGTH_LONG).show()
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e("BLEAdvertiser", "خطا در شروع تبلیغات BLE: $errorCode")
            Toast.makeText(requireContext(),"خطا در شروع تبلیغات BLE: $errorCode",Toast.LENGTH_LONG).show()
        }
    }
}
