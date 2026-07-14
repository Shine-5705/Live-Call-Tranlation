package com.gnani.livetranslation.twilio

import android.content.Context
import android.util.Log
import com.twilio.voice.Call
import com.twilio.voice.CallException
import com.twilio.voice.ConnectOptions
import com.twilio.voice.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TwilioCallState {
    IDLE, CONNECTING, CONNECTED, DISCONNECTED, ERROR
}

class TwilioCallManager(private val context: Context) {

    private var activeCall: Call? = null

    private val _state = MutableStateFlow(TwilioCallState.IDLE)
    val state: StateFlow<TwilioCallState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val callListener = object : Call.Listener {
        override fun onConnectFailure(call: Call, error: CallException) {
            Log.e(TAG, "Connect failure", error)
            _state.value = TwilioCallState.ERROR
            _error.value = error.message
            activeCall = null
        }

        override fun onRinging(call: Call) {
            _state.value = TwilioCallState.CONNECTING
        }

        override fun onConnected(call: Call) {
            _state.value = TwilioCallState.CONNECTED
            _error.value = null
        }

        override fun onReconnecting(call: Call, callException: CallException) {
            _state.value = TwilioCallState.CONNECTING
        }

        override fun onReconnected(call: Call) {
            _state.value = TwilioCallState.CONNECTED
        }

        override fun onDisconnected(call: Call, error: CallException?) {
            _state.value = TwilioCallState.DISCONNECTED
            if (error != null) {
                _error.value = error.message
            }
            activeCall = null
        }

        override fun onCallQualityWarningsChanged(
            call: Call,
            currentWarnings: MutableSet<Call.CallQualityWarning>,
            previousWarnings: MutableSet<Call.CallQualityWarning>
        ) {}
    }

    fun connect(accessToken: String, sessionId: String) {
        disconnect()
        _state.value = TwilioCallState.CONNECTING
        _error.value = null

        val options = ConnectOptions.Builder(accessToken)
            .params(mapOf("sessionId" to sessionId))
            .build()

        activeCall = Voice.connect(context, options, callListener)
    }

    fun setMuted(muted: Boolean) {
        activeCall?.mute(muted)
    }

    fun disconnect() {
        activeCall?.disconnect()
        activeCall = null
        _state.value = TwilioCallState.DISCONNECTED
    }

    fun reset() {
        activeCall = null
        _state.value = TwilioCallState.IDLE
        _error.value = null
    }

    companion object {
        private const val TAG = "TwilioCallManager"
    }
}
