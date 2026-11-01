package id.gamebooster

import android.app.Application

class GameApp : Application() {
    companion object {
        lateinit var instance: GameApp
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)
        Booster.active.value = Prefs.bool("active")
    }
}
