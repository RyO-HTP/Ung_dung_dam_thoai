package com.example.damthoai.ui.livecue

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LiveCueViewModel : ViewModel() {

    // Trạng thái lưu trữ nội dung hoặc trạng thái gửi LiveCue
    private val _uiState = MutableStateFlow<String?>(null)
    val uiState: StateFlow<String?> = _uiState.asStateFlow()

    /**
     * Hàm xử lý logic khi người dùng bấm gửi LiveCue từ màn hình soạn thảo.
     * @param cueContent Nội dung hoặc dữ liệu của Cue được truyền lên từ UI.
     */
    fun sendLiveCue(cueContent: String) {
        // TODO: Viết logic xử lý gửi dữ liệu LiveCue thực tế ở đây
        // (Ví dụ: gọi API, gửi qua WebSocket hoặc lưu vào Repository)
        _uiState.value = cueContent
    }
}