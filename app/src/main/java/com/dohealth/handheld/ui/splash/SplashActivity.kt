package com.dohealth.handheld.ui.splash

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dohealth.handheld.R
import com.dohealth.handheld.ui.connection.ConnectionActivity
import com.dohealth.handheld.utils.DeterminateLoadingProgress
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val progress = findViewById<LinearProgressIndicator>(R.id.splashLinearProgress)
        val percentText = findViewById<TextView>(R.id.splashPercentText)

        lifecycleScope.launch {
            DeterminateLoadingProgress.startQuickRampThenHold(
                lifecycleScope,
                progress,
                percentText,
            ).join()
            delay(1400)
            DeterminateLoadingProgress.flashFullProgress(progress, percentText)
            startActivity(Intent(this@SplashActivity, ConnectionActivity::class.java))
            finish()
        }
    }
}
