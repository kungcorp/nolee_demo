package ai.nolee.demo

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NoleeKiosk {
    data class Result(val ok: Boolean, val reason: String?)

    suspend fun exit(context: Context): Result = withContext(Dispatchers.IO) {
        val response = runCatching {
            context.contentResolver.call(
                Uri.parse("content://io.kungcorp.nolee.launcher.state"),
                "exit_kiosk",
                null,
                null,
            )
        }.getOrNull()
        Result(
            ok = response?.getBoolean("ok") == true,
            reason = response?.getString("reason")
                ?: if (response == null) "Nolee Launcher did not answer." else null,
        )
    }
}

