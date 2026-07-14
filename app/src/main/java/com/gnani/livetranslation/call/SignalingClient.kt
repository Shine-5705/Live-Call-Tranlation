package com.gnani.livetranslation.call

import android.util.Log
import com.gnani.livetranslation.data.BackendConfig
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI

class SignalingClient(
    private val backendHost: String,
    private val token: String,
    private val roomId: String,
    private val peerId: String,
    private val onMessage: (JSONObject) -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit
) {
    private var webSocket: WebSocketClient? = null

    fun connect() {
        val base = BackendConfig.wsBaseUrl(backendHost)
        val url = "$base/signaling?token=$token&roomId=$roomId&peerId=$peerId"
        webSocket = object : WebSocketClient(URI(url)) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                send(JSONObject().put("type", "join").toString())
                onConnected()
            }

            override fun onMessage(message: String?) {
                message ?: return
                onMessage(JSONObject(message))
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                onDisconnected()
            }

            override fun onError(ex: Exception?) {
                Log.e(TAG, "Signaling error", ex)
            }
        }
        webSocket?.connect()
    }

    fun sendSignal(type: String, payload: JSONObject) {
        val msg = JSONObject()
            .put("type", type)
            .put("payload", payload)
        webSocket?.send(msg.toString())
    }

    fun disconnect() {
        webSocket?.close()
        webSocket = null
    }

    companion object {
        private const val TAG = "SignalingClient"
    }
}
