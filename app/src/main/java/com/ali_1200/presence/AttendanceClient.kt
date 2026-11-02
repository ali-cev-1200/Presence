package com.ali_1200.presence

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

// سمت دانشجو: پیدا کردن گوشی استاد، بررسی اثبات استاد و فرستادن حضور امضاشده
// گوشی دانشجو خودش تشخیص می‌دهد گوشی استاد مال کدام درس است (با کد کلاس‌هایی که دارد)
class AttendanceClient(context: Context) {
    sealed class State {
        object Idle : State()
        object Searching : State()
        object Connecting : State()
        data class Sending(val courseName: String) : State()
        data class Success(val courseName: String, val alreadyRegistered: Boolean = false) : State()
        data class Failed(val message: String) : State()
    }

    // اطلاعاتی که برای یک تلاش ثبت حضور لازم است
    class Request(
        val login: Logingdata,
        val courses: List<Course>,
        val isRegisteredToday: (Long) -> Boolean,
        val onRegistered: (Course) -> Unit
    )

    companion object {
        private const val SCAN_PERIOD: Long = 10_000 // 10 seconds
        private const val TAG = "AttendanceClient"
    }

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableLiveData<State>(State.Idle)
    val state: LiveData<State> = _state

    // کالبک‌های بلوتوث ممکن است روی رشته دیگری اجرا شوند؛ LiveData فقط روی رشته اصلی setValue می‌پذیرد
    private fun setState(newState: State) {
        if (Looper.myLooper() == Looper.getMainLooper()) _state.value = newState else _state.postValue(newState)
    }

    // این مقادیر در کالبک‌های GATT (رشته بلوتوث) هم خوانده می‌شوند
    @Volatile
    private var request: Request? = null
    @Volatile
    private var currentCourse: Course? = null

    private var bleScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var scanning = false
    private var teacherFound = false

    // گوشی‌هایی که اثبات استادشان با هیچ‌کدام از کد کلاس‌های ما نخواند (مثلاً کلاس بغلی)
    private val rejectedTeachers = mutableSetOf<String>()

    val isBusy: Boolean get() = scanning || teacherFound

    @SuppressLint("MissingPermission")
    fun start(newRequest: Request) {
        if (isBusy) return
        request = newRequest

        // اگر بلوتوث خاموش باشد اسکنر null است
        bleScanner = bluetoothManager.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        val scanner = bleScanner
        if (scanner == null) {
            setState(State.Failed("لطفاً بلوتوث گوشی را روشن کنید"))
            return
        }

        scanning = true
        setState(State.Searching)

        // فقط دستگاه‌هایی که سرویس این برنامه را پخش می‌کنند گزارش می‌شوند
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        handler.postDelayed(::onScanTimeout, SCAN_PERIOD)
        scanner.startScan(filters, settings, scanCallback)
    }

    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        stopScanningIfActive()
        disconnectGattServer()
        teacherFound = false
        currentCourse = null
    }

    // بعد از نمایش نتیجه، صفحه می‌تواند وضعیت را به حالت عادی برگرداند
    fun reset() {
        if (!isBusy) setState(State.Idle)
    }

    private fun onScanTimeout() {
        stopScanningIfActive()
        if (!teacherFound) {
            setState(State.Failed("گوشی استاد پیدا نشد؛ نزدیک‌تر شوید و دوباره تلاش کنید"))
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (teacherFound) return
            if (result.device.address in rejectedTeachers) return
            val hasService = result.scanRecord?.serviceUuids?.any {
                it.uuid == BleConstants.SERVICE_UUID
            } == true
            if (hasService) {
                handleTeacherBeaconFound(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "خطا در اسکن BLE: $errorCode")
            scanning = false
            handler.removeCallbacksAndMessages(null)
            setState(State.Failed("خطا در جستجوی بلوتوث (کد $errorCode)؛ دوباره تلاش کنید"))
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleTeacherBeaconFound(device: BluetoothDevice) {
        teacherFound = true
        handler.removeCallbacksAndMessages(null)
        stopScanningIfActive()
        setState(State.Connecting)

        bluetoothGatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    // کالبک‌های GATT روی رشته بلوتوث اجرا می‌شوند؛ تغییر وضعیت با handler روی رشته اصلی انجام می‌شود
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                // اگر درخواست MTU پذیرفته نشود، مستقیم سراغ پیدا کردن سرویس‌ها می‌رویم
                if (!gatt.requestMtu(BleConstants.REQUESTED_MTU)) {
                    gatt.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "اتصال قطع شد (status=$status)")
                handler.post {
                    if (teacherFound) {
                        finishAttempt(State.Failed("ارتباط با گوشی استاد قطع شد؛ دوباره تلاش کنید"))
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val challenge = gatt.getService(BleConstants.SERVICE_UUID)
                ?.getCharacteristic(BleConstants.CHALLENGE_UUID)

            if (status != BluetoothGatt.GATT_SUCCESS || challenge == null) {
                handler.post { finishAttempt(State.Failed("سرویس حضور روی گوشی استاد پیدا نشد")) }
                return
            }
            // قبل از ارسال، چالش استاد خوانده می‌شود
            if (!gatt.readCharacteristic(challenge)) {
                handler.post { finishAttempt(State.Failed("خواندن چالش از گوشی استاد ناموفق بود")) }
            }
        }

        // اندروید ۱۳ به بعد
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            handleChallenge(gatt, value, status)
        }

        // اندروید ۱۲ و قبل‌تر
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                handleChallenge(gatt, characteristic.value ?: ByteArray(0), status)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            // فقط وقتی استاد تایید کرد، حضور ثبت‌شده حساب می‌شود
            handler.post {
                val course = currentCourse
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        if (course != null) request?.onRegistered?.invoke(course)
                        finishAttempt(State.Success(course?.name.orEmpty()))
                    }
                    BleConstants.STATUS_CONFLICT -> finishAttempt(
                        State.Failed("این کد دانشجویی یا این گوشی امروز در این درس با مشخصات دیگری ثبت شده است")
                    )
                    BleConstants.STATUS_AUTH_FAILED -> finishAttempt(
                        State.Failed("استاد درخواست را نپذیرفت (کد کلاس اشتباه یا درخواست منقضی شده)")
                    )
                    else -> finishAttempt(State.Failed("ثبت حضور ناموفق بود (خطای $status)؛ دوباره تلاش کنید"))
                }
            }
        }
    }

    // پاسخ چالش = nonce (۱۶ بایت) + اثبات استاد (۱۶ بایت)
    private fun handleChallenge(gatt: BluetoothGatt, value: ByteArray, status: Int) {
        val req = request
        val expectedSize = AttendanceCrypto.NONCE_SIZE + AttendanceCrypto.PROOF_SIZE
        if (status != BluetoothGatt.GATT_SUCCESS || value.size != expectedSize || req == null) {
            handler.post { finishAttempt(State.Failed("خواندن چالش از گوشی استاد ناموفق بود")) }
            return
        }

        val nonce = value.copyOfRange(0, AttendanceCrypto.NONCE_SIZE)
        val teacherProof = value.copyOfRange(AttendanceCrypto.NONCE_SIZE, value.size)

        // درسی که کد کلاسش با اثبات استاد جور است؛ اگر هیچ‌کدام نبود، این گوشی استاد ما نیست
        val course = req.courses.firstOrNull { AttendanceCrypto.verifyTeacherProof(it.classCode, nonce, teacherProof) }
        if (course == null) {
            Log.w(TAG, "اثبات استاد نامعتبر: ${gatt.device.address}")
            handler.post { rejectTeacher(gatt.device.address) }
            return
        }
        if (req.isRegisteredToday(course.id)) {
            handler.post { finishAttempt(State.Success(course.name, alreadyRegistered = true)) }
            return
        }

        val characteristic = gatt.getService(BleConstants.SERVICE_UUID)
            ?.getCharacteristic(BleConstants.CHARACTERISTIC_UUID)
        if (characteristic == null) {
            handler.post { finishAttempt(State.Failed("سرویس حضور روی گوشی استاد پیدا نشد")) }
            return
        }
        currentCourse = course
        handler.post { setState(State.Sending(course.name)) }
        sendAttendanceData(gatt, characteristic, req.login, course, nonce)
    }

    // این گوشی کنار گذاشته می‌شود و جستجو برای استاد درست ادامه پیدا می‌کند
    private fun rejectTeacher(address: String) {
        rejectedTeachers.add(address)
        disconnectGattServer()
        teacherFound = false
        request?.let(::start)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun sendAttendanceData(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        login: Logingdata,
        course: Course,
        nonce: ByteArray
    ) {
        val proof = AttendanceCrypto.studentProof(course.classCode, nonce, login.name, login.studentID, login.deviceID)
        val studentData = AttendancePayload(login.name, login.studentID, login.deviceID, proof).toBytes()
        val writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT // با تایید از طرف استاد

        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, studentData, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.writeType = writeType
            characteristic.value = studentData
            gatt.writeCharacteristic(characteristic)
        }

        if (!started) {
            handler.post { finishAttempt(State.Failed("ارسال داده ناموفق بود؛ دوباره تلاش کنید")) }
        }
    }

    // پایان یک تلاش (موفق یا ناموفق): نمایش نتیجه و بستن اتصال
    private fun finishAttempt(result: State) {
        teacherFound = false
        currentCourse = null
        disconnectGattServer()
        setState(result)
    }

    @SuppressLint("MissingPermission")
    private fun stopScanningIfActive() {
        if (scanning) {
            scanning = false
            runCatching { bleScanner?.stopScan(scanCallback) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGattServer() {
        bluetoothGatt?.let {
            it.disconnect()
            it.close()
        }
        bluetoothGatt = null
    }
}
