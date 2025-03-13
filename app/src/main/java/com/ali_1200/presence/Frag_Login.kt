package com.ali_1200.presence

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
}