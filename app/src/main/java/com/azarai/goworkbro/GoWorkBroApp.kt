package com.azarai.goworkbro

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.azarai.goworkbro.core.Store
import com.azarai.goworkbro.core.db.AppDatabase
import com.azarai.goworkbro.core.news.NewsRepo
import com.azarai.goworkbro.core.util.ErrorLog
import java.io.File

class GoWorkBroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        ErrorLog.install(this)
    }
}

/** Tiny service locator — the app is single-process and single-module. */
object Graph {
    lateinit var appContext: Context
        private set

    private var dbInstance: AppDatabase? = null
    private var storeInstance: Store? = null
    private var newsInstance: NewsRepo? = null
    private var avatarsInstance: AvatarStore? = null

    val db: AppDatabase
        get() = dbInstance ?: AppDatabase.get(appContext).also { dbInstance = it }
    val store: Store
        get() = storeInstance ?: Store(db.settingsDao(), Store.createBootPrefs(appContext))
            .also { storeInstance = it }
    val news: NewsRepo
        get() = newsInstance ?: NewsRepo(db.newsCacheDao(), store).also { newsInstance = it }
    val avatars: AvatarStore
        get() = avatarsInstance ?: AvatarStore(appContext).also { avatarsInstance = it }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Robolectric reuses the classloader across test methods — the previous
     *  method's instances point at a torn-down environment. Rebind to the
     *  current application and drop every cached singleton. */
    fun rebindForTesting(context: Context) {
        runCatching { dbInstance?.close() }
        dbInstance = null
        storeInstance = null
        newsInstance = null
        avatarsInstance = null
        AppDatabase.clearForTesting()
        init(context)
    }
}

/** Stores the picked avatar as a local file; path persisted via settings. */
class AvatarStore(private val context: Context) {

    fun file(): File = File(context.filesDir, "avatar.jpg")

    fun exists(): Boolean = file().exists()

    fun save(uri: android.net.Uri): Boolean = runCatching {
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return false
        file().outputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        true
    }.getOrDefault(false)

    fun load(): Bitmap? = runCatching { BitmapFactory.decodeFile(file().absolutePath) }.getOrNull()

    fun delete() {
        file().delete()
    }
}
