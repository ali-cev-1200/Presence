package com.ali_1200.presence

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.ali_1200.presence.databinding.FragLoginBinding

class Frag_Login : Fragment() {
    lateinit var binding: FragLoginBinding
    lateinit var loginViewModel: LoginRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragLoginBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    @SuppressLint("HardwareIds")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loginViewModel = LoginRepository(requireContext())
        checkPermissionsAndStartAdvertising()
        binding.btnCont.setOnClickListener {
            val name = binding.tietName.text.toString()
            val studentid = binding.tietStudentId.text.toString()

            if (name.isNotEmpty() && studentid.isNotEmpty()) {

                val androidID = Settings.Secure.getString(
                    requireContext().contentResolver,
                    Settings.Secure.ANDROID_ID
                )
                val data = Logingdata(1, studentid, name, androidID)
                loginViewModel.insertdata(data)

                if (studentid == "1") {
                    replace_Fragment(BLEAdvertiserFragment())
                } else {
                    replace_Fragment(BLEScannerFragment())
                }
            }
        }
    }

    fun replace_Fragment(fragment: Fragment) {

        val Transaction = parentFragmentManager.beginTransaction()
        Transaction.replace(R.id.frag_main, fragment)
        Transaction.addToBackStack(null)
        Transaction.commit()
    }
    private fun checkPermissionsAndStartAdvertising() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // اندروید ۱۲ به بعد
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                    requireContext() as Activity,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ),
                    1001
                )
                return
            }
        } // اگر مجوزها داده شده بود، تبلیغات BLE را شروع کن
    }
}