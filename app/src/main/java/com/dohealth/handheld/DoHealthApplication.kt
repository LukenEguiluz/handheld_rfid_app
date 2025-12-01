package com.dohealth.handheld

import android.app.Application
import com.cf.zsdk.CfSdk
import java.util.concurrent.Executors

class DoHealthApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Inicializar SDK RFID
        CfSdk.load(Executors.newCachedThreadPool())
    }
    
    override fun onTerminate() {
        super.onTerminate()
        CfSdk.release()
    }
}


