package com.childguardian

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make window transparent or just finish quickly?
        // For now, set a simple layout (we'll create it)
        setContentView(R.layout.activity_main)
        Timber.d("MainActivity started - this is the setup UI")
        // Later, we'll handle permissions here and then finish()
    }
}