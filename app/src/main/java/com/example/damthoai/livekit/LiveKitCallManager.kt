package com.example.damthoai.livekit

import android.content.Context
import android.util.Log
import com.example.damthoai.hardware.PttController
import com.example.damthoai.hardware.PttEvent
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Trạng thái kết nối phòng thoại LiveKit SFU
 */
sealed class LiveKitConnectionState {
    object Disconnected : LiveKitConnectionState()
    data class FetchingToken(val roomName: String) : LiveKitConnectionState()
    data class Connecting(val serverUrl: String) : LiveKitConnectionState()
    data class Connected(val roomName: String, val identity: String) : LiveKitConnectionState()
    data class Error(val message: String, val throwable: Throwable? = null) : LiveKitConnectionState()
}

/**
 * =================================================================================
 * TRÌNH ĐIỀU PHỐI ĐÀM THOẠI SFU QUA LIVEKIT CLOUD (LiveKitCallManager)
 * VAI TRÒ: Thành viên 2 (Client Network & Audio Streaming) - Tuần 2
 * =================================================================================
 *
 * Nhiệm vụ chính:
 * 1. Xin JWT Token từ API Server (api.js).
 * 2. Kết nối tới LiveKit Cloud SFU (wss://ung-dung-dam-thoai-pze2v7u9.livekit.cloud).
 * 3. Tự động tham gia phòng thoại theo Kênh (vd: "Kenh_Hau_Can").
 * 4. TÍCH HỢP VỚI PHÍM VOLUME PTT (TV4): Tự động bật micro khi bấm giữ phím Volume,
 *    và ngắt micro khi thả phím.
 * 5. Giám sát thành viên trong phòng và danh sách người đang nói (Active Speakers).
 */
class LiveKitCallManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LiveKitCallManager"

        @Volatile
        private var instance: LiveKitCallManager? = null

        fun getInstance(context: Context): LiveKitCallManager {
            return instance ?: synchronized(this) {
                instance ?: LiveKitCallManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val tokenService = LiveKitTokenService()

    private var room: Room? = null
    private var pttObserverJob: Job? = null
    private var roomEventsJob: Job? = null

    // Trạng thái kết nối
    private val _connectionState = MutableStateFlow<LiveKitConnectionState>(LiveKitConnectionState.Disconnected)
    val connectionState: StateFlow<LiveKitConnectionState> = _connectionState.asStateFlow()

    // Trạng thái giữ phím PTT (Đang nói / Đang nghe)
    private val _isPttPressed = MutableStateFlow(false)
    val isPttPressed: StateFlow<Boolean> = _isPttPressed.asStateFlow()

    // Danh sách thành viên đang nói trong kênh
    private val _activeSpeakers = MutableStateFlow<List<String>>(emptyList())
    val activeSpeakers: StateFlow<List<String>> = _activeSpeakers.asStateFlow()

    // Danh sách tất cả người dùng trong phòng hiện tại
    private val _roomParticipants = MutableStateFlow<List<String>>(emptyList())
    val roomParticipants: StateFlow<List<String>> = _roomParticipants.asStateFlow()

    // Tên kênh hiện tại
    private val _currentRoomName = MutableStateFlow<String?>(null)
    val currentRoomName: StateFlow<String?> = _currentRoomName.asStateFlow()

    init {
        // Tự động lắng nghe sự kiện phím Volume PTT từ TV4
        observeHardwarePttEvents()
    }

    /**
     * Lắng nghe phím cứng Volume từ PttController (TV4)
     */
    private fun observeHardwarePttEvents() {
        pttObserverJob?.cancel()
        pttObserverJob = scope.launch {
            PttController.events.collect { event ->
                when (event) {
                    is PttEvent.PttPressed -> {
                        Log.d(TAG, "[PTT Hardware] Phím Volume được nhấn -> BẬT Micro LiveKit")
                        setPushToTalkPressed(true)
                    }
                    is PttEvent.PttReleased -> {
                        Log.d(TAG, "[PTT Hardware] Phím Volume được thả -> TẮT Micro LiveKit")
                        setPushToTalkPressed(false)
                    }
                }
            }
        }
    }

    /**
     * Tham gia vào kênh đàm thoại LiveKit
     * @param apiBaseUrl Địa chỉ máy chủ api.js (vd: http://10.0.2.2:3000 hoặc http://192.168.1.x:3000)
     * @param roomName Tên phòng/kênh (vd: "Kenh_Hau_Can")
     * @param identity Tên người dùng (vd: "Tong_Dao_Dien")
     */
    fun joinChannel(
        apiBaseUrl: String = LiveKitTokenService.DEFAULT_EMULATOR_HOST,
        roomName: String = "Kenh_Hau_Can",
        identity: String = "Tong_Dao_Dien"
    ) {
        scope.launch {
            try {
                _connectionState.value = LiveKitConnectionState.FetchingToken(roomName)
                _currentRoomName.value = roomName

                // 1. Gọi API lấy Token JWT từ máy chủ của TV1 (api.js)
                val tokenResult = tokenService.fetchToken(
                    baseUrl = apiBaseUrl,
                    roomName = roomName,
                    identity = identity
                )

                if (tokenResult.isFailure) {
                    val error = tokenResult.exceptionOrNull()?.message ?: "Lấy token thất bại"
                    _connectionState.value = LiveKitConnectionState.Error(error)
                    return@launch
                }

                val tokenData = tokenResult.getOrThrow()
                Log.d(TAG, "Lấy Token thành công. Tiến hành kết nối LiveKit SFU: ${tokenData.serverUrl}")

                _connectionState.value = LiveKitConnectionState.Connecting(tokenData.serverUrl)

                // 2. Khởi tạo đối tượng Room của LiveKit
                if (room == null) {
                    room = LiveKit.create(context)
                }

                val currentRoom = room!!
                setupRoomEvents(currentRoom)

                // 3. Kết nối vào LiveKit Cloud
                currentRoom.connect(
                    url = tokenData.serverUrl,
                    token = tokenData.token
                )

                // Mặc định ban đầu: Tắt mic để chỉ nghe, khi người dùng giữ PTT mới mở mic
                currentRoom.localParticipant.setMicrophoneEnabled(false)

                _connectionState.value = LiveKitConnectionState.Connected(roomName, identity)
                updateParticipantsList(currentRoom)
                Log.d(TAG, "Đã kết nối thành công vào phòng $roomName với danh xưng $identity")

            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi kết nối LiveKit SFU: ${e.message}", e)
                _connectionState.value = LiveKitConnectionState.Error(e.message ?: "Lỗi kết nối phòng thoại", e)
            }
        }
    }

    /**
     * Lắng nghe các sự kiện âm thanh và thành viên từ LiveKit Room
     */
    private fun setupRoomEvents(targetRoom: Room) {
        roomEventsJob?.cancel()
        roomEventsJob = scope.launch {
            targetRoom.events.collect { event ->
                when (event) {
                    is RoomEvent.ActiveSpeakersChanged -> {
                        val speakerIdentities = event.speakers.mapNotNull { it.identity?.value }
                        _activeSpeakers.value = speakerIdentities
                        Log.d(TAG, "Thành viên đang nói: $speakerIdentities")
                    }

                    is RoomEvent.ParticipantConnected -> {
                        Log.d(TAG, "Thành viên mới tham gia: ${event.participant.identity?.value}")
                        updateParticipantsList(targetRoom)
                    }

                    is RoomEvent.ParticipantDisconnected -> {
                        Log.d(TAG, "Thành viên rời phòng: ${event.participant.identity?.value}")
                        updateParticipantsList(targetRoom)
                    }

                    is RoomEvent.Disconnected -> {
                        Log.d(TAG, "Đã ngắt kết nối phòng thoại LiveKit")
                        _connectionState.value = LiveKitConnectionState.Disconnected
                        _activeSpeakers.value = emptyList()
                        _roomParticipants.value = emptyList()
                    }

                    else -> {}
                }
            }
        }
    }

    private fun updateParticipantsList(targetRoom: Room) {
        val list = mutableListOf<String>()
        targetRoom.localParticipant.identity?.value?.let { list.add("$it (Tôi)") }
        targetRoom.remoteParticipants.values.forEach { p ->
            p.identity?.value?.let { list.add(it) }
        }
        _roomParticipants.value = list
    }

    /**
     * Xử lý hành động Bấm giữ phím PTT (Nói) hoặc Thả phím (Nghe)
     * Dùng cho cả Nút bấm Compose UI và Phím cứng Volume PTT.
     */
    fun setPushToTalkPressed(isPressed: Boolean) {
        _isPttPressed.value = isPressed
        val currentRoom = room
        if (currentRoom != null && _connectionState.value is LiveKitConnectionState.Connected) {
            scope.launch {
                try {
                    currentRoom.localParticipant.setMicrophoneEnabled(isPressed)
                    Log.d(TAG, "Microphone LiveKit: ${if (isPressed) "MỞ (ĐANG NÓI)" else "TẮT (CHẾ ĐỘ NGHE)"}")
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi bật/tắt Micro: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Rời phòng và ngắt kết nối
     */
    fun leaveChannel() {
        scope.launch {
            try {
                room?.localParticipant?.setMicrophoneEnabled(false)
                room?.disconnect()
                _isPttPressed.value = false
                _activeSpeakers.value = emptyList()
                _roomParticipants.value = emptyList()
                _currentRoomName.value = null
                _connectionState.value = LiveKitConnectionState.Disconnected
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi rời phòng: ${e.message}", e)
            }
        }
    }

    /**
     * Giải phóng tài nguyên
     */
    fun destroy() {
        leaveChannel()
        pttObserverJob?.cancel()
        roomEventsJob?.cancel()
        room?.release()
        room = null
    }
}
