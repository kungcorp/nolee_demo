package ai.nolee.demo

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat

/** Public, no-root camera-lid scroll bridge exposed by Nolee Launcher. */
class LidScrollBridge(
    private val context: Context,
    private val onStep: (Int) -> Unit,
) {
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getIntExtra("schema", -1) != 1) return
            when (intent.getStringExtra("direction")) {
                "step_up" -> onStep(1)
                "step_down" -> onStep(-1)
            }
        }
    }

    fun start(): String? {
        if (registered) return null
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val callback = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val result = runCatching {
            context.contentResolver.call(
                STATE_URI,
                "register_lid_scroll",
                null,
                Bundle().apply { putParcelable("callback", callback) },
            )
        }.getOrNull()
        registered = result?.getBoolean("ok") == true
        if (!registered) {
            runCatching { context.unregisterReceiver(receiver) }
            return result?.getString("reason") ?: "Nolee Launcher did not answer."
        }
        return null
    }

    fun stop() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        runCatching {
            context.contentResolver.call(STATE_URI, "unregister_lid_scroll", null, null)
        }
        registered = false
    }

    private companion object {
        val STATE_URI: Uri = Uri.parse("content://io.kungcorp.nolee.launcher.state")
        const val ACTION = "ai.nolee.demo.LID_STEP"
    }
}

