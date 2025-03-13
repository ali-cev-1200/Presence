package com.ali_1200.presence

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
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
    private lateinit var bleScanner: BluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false
    private val SCAN_PERIOD: Long = 10000 // 10 ثانیه
    private val TARGET_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = FragStudentBinding.inflate(layoutInflater,container,false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bleScanner = bluetoothAdapter.bluetoothLeScanner

        val btnStartScan = binding.start
        val txtScanResult = binding.result

        btnStartScan.setOnClickListener {
            checkPermissionsAndStartScan(txtScanResult)
        }
    }

    private fun checkPermissionsAndStartScan(txtScanResult: TextView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
                    Toast.makeText(requireContext(),"Beacon معلم پیدا شد: ${result.device.address}",Toast.LENGTH_LONG).show()
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("BLEScanner", "خطا در اسکن BLE: $errorCode")
            Toast.makeText(requireContext(),"خطا : ${errorCode}",Toast.LENGTH_LONG).show()
        }
    }
}