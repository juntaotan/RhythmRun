package com.compx551.watchos.communication

import android.content.Intent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** Receives phone session commands without requiring the watch UI to be open. */
class WatchSessionCommandService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (!event.path.startsWith("/rhythmrun/v1/session/")) return

        sendBroadcast(Intent(ACTION_SESSION_COMMAND).apply {
            putExtra(EXTRA_PATH, event.path)
            putExtra(EXTRA_SESSION_ID, String(event.data, Charsets.UTF_8))
        })
    }

    companion object {
        const val ACTION_SESSION_COMMAND =
            "com.compx551.watchos.SESSION_COMMAND"
        const val EXTRA_PATH = "path"
        const val EXTRA_SESSION_ID = "session_id"
    }
}
