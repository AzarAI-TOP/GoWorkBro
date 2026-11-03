package com.azarai.goworkbro.core.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.azarai.goworkbro.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The dev/adb transport for [RemoteApi] — same JSON, no radio:
 *
 *   adb shell am broadcast -a com.azarai.goworkbro.ADB_CMD \
 *     --es json '{"v":1,"token":"…","op":"todo.add","data":{"title":"写毕设"}}'
 *
 * The response is logged (tag `GWBCmd`) and set as the broadcast result.
 * Chinese text survives here, which `adb shell input text` cannot do.
 *
 * The receiver is exported so `shell` (uid 2000) can reach it, and cannot tell
 * its sender apart afterwards — so the token check inside [RemoteApi] is the
 * only gate, exactly like it is over Bluetooth. Keep 电脑协同 off by default.
 */
class AdbBridge : BroadcastReceiver() {

    companion object {
        const val ACTION = "com.azarai.goworkbro.ADB_CMD"
        const val EXTRA_JSON = "json"
        private const val TAG = "GWBCmd"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val request = intent.getStringExtra(EXTRA_JSON) ?: return
        // The database call outlives onReceive; goAsync keeps the app alive.
        val pending = goAsync()
        scope.launch {
            val response = runCatching { RemoteApi.handle(Graph.db, request) }.getOrElse { e ->
                JSONObject()
                    .put("ok", false)
                    .put("error", "internal")
                    .put("message", e.message ?: e.javaClass.simpleName)
                    .toString()
            }
            Log.i(TAG, response)
            runCatching { setResultData(response) }
            pending.finish()
        }
    }
}
