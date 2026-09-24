package com.azarai.goworkbro

import android.app.Application
import android.content.Context
import com.azarai.goworkbro.core.Store
import com.azarai.goworkbro.core.db.AppDatabase
import com.azarai.goworkbro.core.util.ErrorLog

class GoWorkBroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        ErrorLog.install(this)
    }
}

/** Tiny service locator — the app is single-process, single-module, offline. */
object Graph {
    lateinit var appContext: Context
        private set

    private var dbInstance: AppDatabase? = null
    private var storeInstance: Store? = null

    val db: AppDatabase
        get() = dbInstance ?: AppDatabase.get(appContext).also { dbInstance = it }
    val store: Store
        get() = storeInstance ?: Store(db.settingsDao()).also { storeInstance = it }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Robolectric reuses the classloader across test methods — rebind every
     *  cached singleton to the fresh application under test. The previous
     *  database is deliberately NOT closed: still-active Room flows from the
     *  torn-down UI would crash against a closed connection pool. */
    fun rebindForTesting(context: Context) {
        dbInstance = null
        storeInstance = null
        AppDatabase.clearForTesting()
        init(context)
    }
}
