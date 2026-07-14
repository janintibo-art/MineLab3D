package com.minelab.game

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager

class MainActivity : Activity() {

    private lateinit var view: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Filet de securite : toute erreur fatale est memorisee et sera
        // affichee au prochain lancement (ecran rouge dans le jeu).
        val prefs = getSharedPreferences("minelab", MODE_PRIVATE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val log = e.toString() + "\n" +
                        e.stackTrace.take(14).joinToString("\n") { "  $it" }
                prefs.edit().putString("lastcrash", log).commit()
            } catch (t: Throwable) { }
            previous?.uncaughtException(thread, e)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = GameView(this)
        @Suppress("DEPRECATION")
        view.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        setContentView(view)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
    }

    override fun onPause() {
        super.onPause()
        view.onPauseAudio()
    }

    override fun onResume() {
        super.onResume()
        view.onResumeAudio()
    }

    override fun onDestroy() {
        view.onDestroyAudio()
        super.onDestroy()
    }
}
