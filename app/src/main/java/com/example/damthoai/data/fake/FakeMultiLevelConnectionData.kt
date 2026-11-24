package com.example.damthoai.data.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MultiLevelConnectionUiState(
    val internetEnabled: Boolean = true,
    val localNetworkEnabled: Boolean = true,
    val bluetoothDirectEnabled: Boolean = false
)

object FakeMultiLevelConnectionData {
    private val _uiState = MutableStateFlow(MultiLevelConnectionUiState())
    val uiState: StateFlow<MultiLevelConnectionUiState> = _uiState.asStateFlow()

    fun setInternetEnabled(enabled: Boolean) {
        _uiState.update { it.copy(internetEnabled = enabled) }
    }

    fun setLocalNetworkEnabled(enabled: Boolean) {
        _uiState.update { it.copy(localNetworkEnabled = enabled) }
    }

    fun setBluetoothDirectEnabled(enabled: Boolean) {
        _uiState.update { it.copy(bluetoothDirectEnabled = enabled) }
    }
}
