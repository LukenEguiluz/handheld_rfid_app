package com.dohealth.handheld.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.dohealth.handheld.R
import com.dohealth.handheld.ui.connection.ConnectionActivity

class SplashActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        // Esperar 2 segundos y luego ir a la pantalla de conexión
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, ConnectionActivity::class.java))
            finish()
        }, 2000)
    }
}


