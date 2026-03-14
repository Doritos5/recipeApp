package com.example.recipeapp.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.recipeapp.MainActivity
import com.example.recipeapp.R

class SplashScreenActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val launchRunnable = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Keep the splash visible briefly, then navigate to MainActivity.
        handler.postDelayed(launchRunnable, SPLASH_DELAY_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(launchRunnable)
        super.onDestroy()
    }

    private companion object {
        private const val SPLASH_DELAY_MS = 1500L
    }
}
