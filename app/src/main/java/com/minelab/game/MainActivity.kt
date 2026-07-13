package com.minelab.game

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager

class MainActivity : Activity() {

    private lateinit var view: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
