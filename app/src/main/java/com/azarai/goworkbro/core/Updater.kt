package com.azarai.goworkbro.core

import android.app.PendingIntent
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.azarai.goworkbro.Graph
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * GitHub-release auto-updater — the ONE thing this otherwise offline app
 * uses the network for. Checks `releases/latest` via the public API, lets
 * DownloadManager fetch the APK into the app's private external Download
 * dir (no storage permission needed), then hands it to a PackageInstaller
 * session from the home banner (the system shows the confirm UI).
 */
object Updater {

    private const val REPO = "AzarAI-TOP/GoWorkBro"
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    private const val APK_PREFIX = "GoWorkBro-update-"
    private const val APK_SUFFIX = ".apk"

    /** Latest release as reported by GitHub. */
    data class ReleaseInfo(val tag: String, val version: String, val apkUrl: String)

    /** Version of a downloaded-but-not-yet-installed APK, for the home banner. */
    val pendingInstallVersion = MutableStateFlow<String?>(null)

    private var enqueuedDownloadId: Long = -1

    /** Version of the currently installed build, e.g. "3.1.0". */
    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName!! }
            .getOrDefault("0.0.0")

    /** "v3.1.0" → "3.1.0". */
    fun normalizeVersion(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")

    /** Numeric segment compare; unreadable versions never count as newer. */
    fun isNewer(remote: String, current: String): Boolean {
        val r = normalizeVersion(remote).split('.').map { it.toIntOrNull() ?: return false }
        val c = normalizeVersion(current).split('.').map { it.toIntOrNull() ?: return false }
        val n = maxOf(r.size, c.size)
        for (i in 0 until n) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    /** Pulls tag + APK asset URL out of a GitHub release JSON payload. */
    fun parseRelease(json: String): ReleaseInfo? = runCatching {
        val obj = JSONObject(json)
        val tag = obj.optString("tag_name")
        val asset = obj.optJSONArray("assets")
            ?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
            ?.firstOrNull { it.optString("name").endsWith(".apk") }
        if (tag.isEmpty() || asset == null) null
        else ReleaseInfo(tag, normalizeVersion(tag), asset.optString("browser_download_url"))
    }.getOrNull()

    /** Fetches releases/latest; null on any failure (offline is normal). */
    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(API_LATEST).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "GoWorkBro-Updater") // GitHub requires a UA
                if (conn.responseCode != 200) return@runCatching null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()?.let { parseRelease(it) }
    }

    /**
     * Auto path (app start / foreground): throttled to one check per day and
     * only when the 自动更新 switch is on. Finds something newer → starts the
     * download quietly. Never runs under Robolectric so unit tests stay
     * hermetic (the JVM would happily hit the real API).
     */
    suspend fun maybeAutoCheck(store: Store, context: Context) {
        if (Graph.testing) return
        // pick up an APK that finished downloading after the process died;
        // stale ones (<= the running version) are deleted
        withContext(Dispatchers.IO) { refreshPendingApk(context) }
        if (store.get(Store.Keys.AUTO_UPDATE) == "false") return
        val last = store.get(Store.Keys.LAST_UPDATE_CHECK)?.toLongOrNull() ?: 0L
        val now = System.currentTimeMillis()
        if (now - last < CHECK_INTERVAL_MS) return
        store.set(Store.Keys.LAST_UPDATE_CHECK, now.toString())
        val release = fetchLatest() ?: return
        if (!isNewer(release.version, currentVersion(context))) return
        download(context, release)
    }

    /** Manual path (the settings row): immediate check with toast feedback. */
    suspend fun checkNow(context: Context) {
        val current = currentVersion(context)
        val release = fetchLatest()
        when {
            release == null ->
                Toast.makeText(context, "检查更新失败，网络不好？稍后再试", Toast.LENGTH_SHORT).show()
            !isNewer(release.version, current) ->
                Toast.makeText(context, "已经是最新版本 $current 啦", Toast.LENGTH_SHORT).show()
            else -> {
                Toast.makeText(context, "发现新版本 v${release.version}，开始下载", Toast.LENGTH_SHORT).show()
                download(context, release)
            }
        }
    }

    private fun download(context: Context, release: ReleaseInfo) {
        if (enqueuedDownloadId != -1L) return // one download at a time
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("GoWorkBro v${release.version}")
            .setDescription("正在下载更新")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, apkName(release.version))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        enqueuedDownloadId = context.getSystemService(DownloadManager::class.java).enqueue(request)
    }

    fun apkName(version: String): String = "$APK_PREFIX$version$APK_SUFFIX"

    fun updatesDir(context: Context): File? =
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

    /** Registers the download-complete listener. The APK dir scan runs from
     *  [maybeAutoCheck] so no disk I/O lands on the main thread at startup. */
    fun install(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != enqueuedDownloadId) return
                enqueuedDownloadId = -1
                refreshPendingApk(ctx)
                if (pendingInstallVersion.value != null) {
                    Toast.makeText(ctx, "新版本已下载，回主页点击横幅安装", Toast.LENGTH_LONG).show()
                }
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED,
        )
    }

    private fun refreshPendingApk(context: Context) {
        val dir = updatesDir(context) ?: return
        val current = currentVersion(context)
        var best: File? = null
        dir.listFiles { f -> f.name.startsWith(APK_PREFIX) && f.name.endsWith(APK_SUFFIX) }
            ?.forEach { file ->
                val v = versionOfApk(file)
                when {
                    isNewer(v, current) ->
                        if (best == null || isNewer(v, versionOfApk(best!!))) best = file
                    else -> file.delete() // stale: we're already on it (or past it)
                }
            }
        pendingInstallVersion.value = best?.let { versionOfApk(it) }
    }

    private fun versionOfApk(file: File): String =
        file.name.removePrefix(APK_PREFIX).removeSuffix(APK_SUFFIX)

    /** Fires the system install-confirm UI for a downloaded APK via a
     *  PackageInstaller session (the deterministic path on modern Android:
     *  ACTION_VIEW+apk resolution is unreliable); asks for the
     *  install-unknown-apps toggle first when it hasn't been granted. */
    suspend fun startInstall(context: Context) {
        val version = pendingInstallVersion.value ?: return
        val file = File(updatesDir(context), apkName(version))
        if (!file.exists()) {
            pendingInstallVersion.value = null
            return
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(context, "请先允许 GoWorkBro 安装未知应用，回来再点一次安装", Toast.LENGTH_LONG).show()
            val settings = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(settings) }
                .onFailure { Toast.makeText(context, "打不开安装设置：${it.message}", Toast.LENGTH_LONG).show() }
            return
        }
        runCatching {
            val installer = context.packageManager.packageInstaller
            val session = installer.openSession(
                installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)),
            )
            // multi-MB stream copy: keep it off the main thread
            withContext(Dispatchers.IO) {
                session.use { s ->
                    s.openWrite("update", 0, -1).use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                    val status = Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName)
                    val pi = PendingIntent.getBroadcast(
                        context,
                        0,
                        status,
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    s.commit(pi.intentSender)
                }
            }
        }.onFailure {
            Toast.makeText(context, "安装启动失败：${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private const val ACTION_INSTALL_STATUS = "com.azarai.goworkbro.UPDATE_STATUS"
}
