package com.compx551.rhythmrun.communication

import android.content.Context
import com.google.android.gms.wearable.Wearable

/** Sends session lifecycle commands to the paired watch. */
class SessionCommandClient(context: Context) {
    private val nodeClient = Wearable.getNodeClient(context.applicationContext)
    private val messageClient = Wearable.getMessageClient(context.applicationContext)

    fun sendStart(sessionId: String) = send(RhythmProtocol.SESSION_START_PATH, sessionId)
    fun sendPause(sessionId: String) = send(RhythmProtocol.SESSION_PAUSE_PATH, sessionId)
    fun sendResume(sessionId: String) = send(RhythmProtocol.SESSION_RESUME_PATH, sessionId)
    fun sendStop(sessionId: String) = send(RhythmProtocol.SESSION_STOP_PATH, sessionId)

    fun connectedNodes() = nodeClient.connectedNodes

    private fun send(path: String, sessionId: String) {
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, path, sessionId.toByteArray())
            }
        }
    }
}
