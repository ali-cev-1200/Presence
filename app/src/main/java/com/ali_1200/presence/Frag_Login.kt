package com.ali_1200.presence

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.ali_1200.presence.databinding.FragLoginBinding

class Frag_Login : Fragment() {
    private lateinit var binding: FragLoginBinding
    private lateinit var loginViewModel: LoginRepository

    // Permission launcher for multiple permissions
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            // Permissions granted, proceed with BLE setup
            proceedWithBLESetup()
        } else {
            Toast.makeText(
                requireContext(),
                "برای استفاده از برنامه، مجوزها باید تایید شوند",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragLoginBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    @SuppressLint("HardwareIds")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loginViewModel = LoginRepository(requireContext())

        // Check and request BLE permissions
        checkAndRequestBLEPermissions()

        binding.btnCont.setOnClickListener {
            handleLoginSubmission()
        }
    }

    private fun checkAndRequestBLEPermissions() {
        val permissionsToRequest = PermissionUtils.getRequiredPermissions()
        val notGrantedPermissions = PermissionUtils.checkPermissions(requireContext(), permissionsToRequest)

        if (notGrantedPermissions.isNotEmpty()) {
            permissionLauncher.launch(notGrantedPermissions.toTypedArray())
        } else {
            proceedWithBLESetup()
        }
    }

    private fun proceedWithBLESetup() {
        // Any initialization needed after permissions are granted
        Toast.makeText(
            requireContext(),
            "مجوزهای بلوتوث تایید شدند",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun handleLoginSubmission() {
        val name = binding.tietName.text.toString()
        val studentId = binding.tietStudentId.text.toString()

        if (name.isBlank() || studentId.isBlank()) {
            Toast.makeText(
                requireContext(),
                "لطفاً تمام فیلدها را پر کنید",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val androidID = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ANDROID_ID
        )

        val loginData = Logingdata(1, studentId, name, androidID)
        loginViewModel.insertdata(loginData)

        // Navigate to appropriate fragment based on student ID
        val targetFragment = when (studentId) {
            "1" -> BLEAdvertiserFragment()
            else -> BLEScannerFragment()
        }

        replaceFragment(targetFragment)
    }

    private fun replaceFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction().apply {
            replace(R.id.frag_main, fragment)
            addToBackStack(null)
            commit()
        }
    }
}
