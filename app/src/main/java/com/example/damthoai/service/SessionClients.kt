package com.example.damthoai.service

import android.content.Context
import com.example.damthoai.data.remote.EventSession
import com.example.damthoai.data.remote.FloorClient
import com.example.damthoai.data.remote.LiveCueClient
import com.example.damthoai.data.remote.RemoteEventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object SessionClients {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null

    fun init(context: Context) {
        EventSession.init(context)
        scope.launch {
            EventSession.sessionState.collectLatest { session ->
                refreshJob?.cancel()
                if (session != null) {
                    val dept = when (session.primaryChannelId) {
                        "ch_02" -> "STAGE"
                        "ch_03" -> "TECHNICAL"
                        "ch_04" -> "LOGISTICS_SEC"
                        "ch_05" -> "RECEPTION"
                        else -> "ALL_EVENT"
                    }
                    LiveCueClient.start(session.userId, session.eventId, dept)
                    FloorClient.start(session.userId, session.eventId)

                    val callManager = DamThoaiCallManager.getInstance(context.applicationContext)
                    if (!session.signalingUrl.isNullOrBlank()) {
                        callManager.connect(session.signalingUrl, session.userId, session.signalingToken)
                    }

                    refreshJob = scope.launch {
                        while (isActive) {
                            delay(10000L)
                            RemoteEventRepository.refreshEvent()
                        }
                    }
                } else {
                    LiveCueClient.stop()
                    FloorClient.stop()
                    DamThoaiCallManager.getInstance(context.applicationContext).endCall()
                }
            }
        }
    }
}
