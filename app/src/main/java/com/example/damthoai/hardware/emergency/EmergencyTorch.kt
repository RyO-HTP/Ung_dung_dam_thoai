package com.example.damthoai.hardware.emergency

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.damthoai.R
import com.example.damthoai.permission.AppPermission
import com.example.damthoai.permission.PermissionManager
import com.example.damthoai.permission.PermissionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Nháy đèn flash phía sau trong thời gian cảnh báo.
 *
 * ĐIỂM NỐI: [EmergencyAlertManager] khởi chạy và dừng vòng lặp; trạng thái được trả về qua callback.
 */
internal object EmergencyTorch {

    private const val TAG = "EmergencyTorch"
    private const val FLASH_ON_MS = 400L
    private const val FLASH_OFF_MS = 400L
    private val lock = Any()
    private var activeSession: FlashSession? = null

    suspend fun flash(
        context: Context,
        onFlashingChanged: (Boolean) -> Unit,
        onUnavailable: (String) -> Unit
    ): String? {
        val appContext = context.applicationContext
        val permissionGranted = try {
            PermissionManager.checkPermissionState(
                appContext,
                AppPermission.CAMERA
            ) == PermissionState.GRANTED
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không thể kiểm tra quyền camera", exception)
            false
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Không thể kiểm tra trạng thái quyền camera", exception)
            false
        }
        if (!permissionGranted) {
            return appContext.getString(R.string.emergency_flash_reason_permission)
        }

        val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return appContext.getString(R.string.emergency_flash_reason_unavailable)

        val cameraId = try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    characteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (exception: CameraAccessException) {
            Log.e(TAG, "Không thể tìm camera có đèn flash", exception)
            return appContext.getString(R.string.emergency_flash_reason_camera_error)
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không có quyền truy cập camera", exception)
            return appContext.getString(R.string.emergency_flash_reason_security)
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Camera không thể truy cập ở trạng thái hiện tại", exception)
            return appContext.getString(R.string.emergency_flash_reason_camera_error)
        }
        if (cameraId == null) {
            return appContext.getString(R.string.emergency_flash_reason_unavailable)
        }

        val session = FlashSession(cameraManager, cameraId)
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeUnavailable(callbackCameraId: String) {
                if (callbackCameraId == cameraId && isActive(session)) {
                    val reason = appContext.getString(R.string.emergency_flash_reason_in_use)
                    session.unavailableReason = reason
                    onFlashingChanged(false)
                    onUnavailable(reason)
                }
            }
        }
        session.callback = callback
        synchronized(lock) {
            activeSession = session
        }

        try {
            cameraManager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))

            while (currentCoroutineContext().isActive && isActive(session)) {
                setTorchMode(session, enabled = true)
                onFlashingChanged(true)
                delay(FLASH_ON_MS)
                if (!isActive(session) || session.unavailableReason != null) break

                setTorchMode(session, enabled = false)
                onFlashingChanged(false)
                delay(FLASH_OFF_MS)
            }
            return session.unavailableReason
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: CameraAccessException) {
            val reason = appContext.getString(R.string.emergency_flash_reason_camera_error)
            Log.e(TAG, "Không thể điều khiển đèn flash", exception)
            session.unavailableReason = reason
            onUnavailable(reason)
            return reason
        } catch (exception: SecurityException) {
            val reason = appContext.getString(R.string.emergency_flash_reason_security)
            Log.e(TAG, "Không có quyền điều khiển đèn flash", exception)
            session.unavailableReason = reason
            onUnavailable(reason)
            return reason
        } catch (exception: IllegalStateException) {
            val reason = appContext.getString(R.string.emergency_flash_reason_camera_error)
            Log.e(TAG, "Đèn flash không thể điều khiển ở trạng thái hiện tại", exception)
            session.unavailableReason = reason
            onUnavailable(reason)
            return reason
        } catch (exception: IllegalArgumentException) {
            val reason = appContext.getString(R.string.emergency_flash_reason_camera_error)
            Log.e(TAG, "Camera flash không còn hợp lệ", exception)
            session.unavailableReason = reason
            onUnavailable(reason)
            return reason
        } finally {
            turnOff(session)
            unregisterCallback(session)
            onFlashingChanged(false)
            synchronized(lock) {
                if (activeSession === session) activeSession = null
            }
        }
    }

    fun stop() {
        val session = synchronized(lock) {
            activeSession.also { activeSession = null }
        } ?: return
        turnOff(session)
        unregisterCallback(session)
    }

    private fun setTorchMode(session: FlashSession, enabled: Boolean) {
        synchronized(lock) {
            if (activeSession === session) {
                session.cameraManager.setTorchMode(session.cameraId, enabled)
            }
        }
    }

    private fun turnOff(session: FlashSession) {
        try {
            session.cameraManager.setTorchMode(session.cameraId, false)
        } catch (exception: CameraAccessException) {
            Log.e(TAG, "Không thể tắt đèn flash", exception)
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không có quyền tắt đèn flash", exception)
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Đèn flash không thể tắt ở trạng thái hiện tại", exception)
        } catch (exception: IllegalArgumentException) {
            Log.e(TAG, "Camera flash không còn hợp lệ khi tắt", exception)
        }
    }

    private fun unregisterCallback(session: FlashSession) {
        val callback = synchronized(lock) {
            session.callback.also { session.callback = null }
        } ?: return
        try {
            session.cameraManager.unregisterTorchCallback(callback)
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không thể hủy đăng ký theo dõi đèn flash", exception)
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Không thể hủy theo dõi đèn flash ở trạng thái hiện tại", exception)
        } catch (exception: IllegalArgumentException) {
            Log.e(TAG, "Theo dõi đèn flash đã được hủy trước đó", exception)
        }
    }

    private fun isActive(session: FlashSession): Boolean =
        synchronized(lock) { activeSession === session }

    private class FlashSession(
        val cameraManager: CameraManager,
        val cameraId: String
    ) {
        @Volatile
        var unavailableReason: String? = null
        var callback: CameraManager.TorchCallback? = null
    }
}
