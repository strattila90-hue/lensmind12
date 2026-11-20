package hu.lensmind

import android.app.Application
import android.util.Log

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                getSharedPreferences("dbg", MODE_PRIVATE).edit()
                    .putString("crash", Log.getStackTraceString(e) + "\n--- napló ---\n" + Logger.dump())
                    .commit()
            }
            prev?.uncaughtException(thread, e)
        }
        Logger.d("app", "indul ${BuildConfig.VERSION_NAME}")
    }
}
