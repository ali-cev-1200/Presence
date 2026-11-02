package com.ali_1200.presence

import android.annotation.SuppressLint
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
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

// سمت استاد: پخش سیگنال BLE و GATT Server که چالش می‌دهد و حضور دانشجوها را می‌گیرد
// در کل برنامه یک نمونه دارد (get) تا با عوض شدن صفحه‌ها یا بسته شدن صفحه برنامه قطع نشود؛
// AttendanceService هم تا وقتی روشن است برنامه را در پس‌زمینه زنده نگه می‌دارد
class AttendanceServer(
    context: Context,
    private val responseRepository: ResponseRepository,
    private val scope: CoroutineScope
) {
    companion object {
        @Volatile
        private var instance: AttendanceServer? = null

        fun get(context: Context): AttendanceServer =
            instance ?: synchronized(this) {
                instance ?: context.applicationContext.let { app ->
                    AttendanceServer(app, ResponseRepository(app), MainScope())
                }.also { instance = it }
            }
    }

    sealed class State {
        object Stopped : State()
        object Starting : State()
        data class Running(val course: Course) : State()
        data class Failed(val message: String) : State()
    }

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null

    private val _state = MutableLiveData<State>(State.Stopped)
    val state: LiveData<State> = _state

    // کالبک‌های بلوتوث ممکن است روی رشته دیگری اجرا شوند؛ LiveData فقط روی رشته اصلی setValue می‌پذیرد
    private fun setState(newState: State) {
        if (Looper.myLooper() == Looper.getMainLooper()) _state.value = newState else _state.postValue(newState)
    }

    // درسی که الان برایش حضور و غیاب پخش می‌شود؛ کالبک‌های GATT روی رشته بلوتوث آن را می‌خوانند
    private class ActiveSession(val course: Course, val verifier: AttendanceVerifier)

    @Volatile
    private var session: ActiveSession? = null

    // تکه‌های نوشتن طولانی (Long Write) هر دستگاه تا رسیدن onExecuteWrite
    private val pendingWrites = ConcurrentHashMap<String, ByteArrayOutputStream>()

    val isActive: Boolean get() = session != null

    @SuppressLint("MissingPermission")
    fun start(course: Course) {
        if (session != null) return

        // advertiser را هر بار از نو می‌گیریم؛ اگر بلوتوث خاموش باشد null است
        advertiser = bluetoothManager.adapter?.takeIf { it.isEnabled }?.bluetoothLeAdvertiser
        if (advertiser == null) {
            setState(State.Failed("بلوتوث خاموش است یا پشتیبانی نمی‌شود"))
            return
        }

        session = ActiveSession(course, AttendanceVerifier(
                course.classCode, TeacherKeyStore(appContext).getOrCreate(), SessionSettings(appContext)::policy
            ))
        setState(State.Starting)
        advertiser?.startAdvertising(createAdvertiseSettings(), createAdvertiseData(), advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (session != null) {
            runCatching { advertiser?.stopAdvertising(advertiseCallback) }
        }
        runCatching { gattServer?.close() }
        gattServer = null
        pendingWrites.clear()
        session = null
        setState(State.Stopped)
    }

    private fun createAdvertiseSettings(): AdvertiseSettings =
        AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

    // بسته تبلیغ حداکثر ۳۱ بایت است؛ UUID صد و بیست و هشت بیتی ۱۸ بایت می‌گیرد پس نام دستگاه جا نمی‌شود
    private fun createAdvertiseData(): AdvertiseData =
        AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build()

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d("AttendanceServer", "تبلیغات BLE با موفقیت شروع شد")
            val active = session ?: return
            if (startGattServer()) {
                setState(State.Running(active.course))
            } else {
                stop()
                setState(State.Failed("راه‌اندازی سرور بلوتوث ناموفق بود"))
            }
        }

        override fun onStartFailure(errorCode: Int) {
            session = null
            setState(State.Failed("شروع پخش بلوتوث ناموفق بود (خطای $errorCode)"))
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGattServer(): Boolean {
        if (gattServer != null) return true

        gattServer = bluetoothManager.openGattServer(appContext, gattServerCallback) ?: return false

        val service = BluetoothGattService(BleConstants.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                BleConstants.CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                BleConstants.CHALLENGE_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
        gattServer?.addService(service)
        return true
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        // دانشجو اول چالش را می‌خواند: nonce تازه + اثبات اینکه این گوشی استاد واقعی کلاس است
        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (device == null) return
            val response = session?.verifier
                ?.takeIf { characteristic?.uuid == BleConstants.CHALLENGE_UUID }
                ?.challengeFor(device.address, offset, System.currentTimeMillis())

            when {
                response == null ->
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                offset > response.size ->
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                else ->
                    gattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                        response.copyOfRange(offset, response.size)
                    )
            }
        }

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
            if (device == null) return
            if (characteristic?.uuid != BleConstants.CHARACTERISTIC_UUID || value == null) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                }
                return
            }

            if (preparedWrite) {
                // داده بزرگ‌تر از MTU تکه‌تکه می‌رسد؛ پردازش در onExecuteWrite انجام می‌شود
                pendingWrites.getOrPut(device.address) { ByteArrayOutputStream() }.write(value)
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
                return
            }

            handleAttendance(device, requestId, responseNeeded, value)
        }

        @SuppressLint("MissingPermission")
        override fun onExecuteWrite(device: BluetoothDevice?, requestId: Int, execute: Boolean) {
            if (device == null) return
            val buffer = pendingWrites.remove(device.address)

            if (!execute || buffer == null) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                return
            }
            handleAttendance(device, requestId, true, buffer.toByteArray())
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleAttendance(
        device: BluetoothDevice,
        requestId: Int,
        responseNeeded: Boolean,
        value: ByteArray
    ) {
        val active = session
        val payload = AttendancePayload.fromBytes(value)
        if (active == null || payload == null) {
            Log.w("AttendanceServer", "داده نامعتبر دریافت شد")
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
            return
        }

        // کد کلاس اشتباه، nonce منقضی، پیام تکراری (replay) یا بیرون از محدوده کلاس ← رد می‌شود
        val rejection = when (active.verifier.verify(device.address, payload, System.currentTimeMillis())) {
            AttendanceVerifier.Result.ACCEPTED -> null
            AttendanceVerifier.Result.AUTH_FAILED -> BleConstants.STATUS_AUTH_FAILED
            AttendanceVerifier.Result.TOO_FAR -> BleConstants.STATUS_TOO_FAR
        }
        if (rejection != null) {
            Log.w("AttendanceServer", "حضور ${payload.studentId} رد شد (کد $rejection)")
            if (responseNeeded) gattServer?.sendResponse(device, requestId, rejection, 0, null)
            return
        }

        scope.launch {
            val result = responseRepository.saveAttendance(active.course.id, payload, System.currentTimeMillis())
            Log.d("AttendanceServer", "نتیجه ثبت ${payload.studentId}: $result")

            // نتیجه به گوشی دانشجو برمی‌گردد تا بداند حضورش واقعاً ثبت شده یا نه
            val status = if (result == ResponseRepository.SaveResult.CONFLICT) {
                BleConstants.STATUS_CONFLICT
            } else {
                BluetoothGatt.GATT_SUCCESS
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, status, 0, null)
            }
        }
    }
}
