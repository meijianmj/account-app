package com.example.myapplication

import android.app.Application
import android.content.Context

class MyApplication : Application() {
    companion object {
        lateinit var instance: Context

        fun isReady(): Boolean {
            return ::instance.isInitialized
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = applicationContext
        BillRepository.init(this)
        AccountRepository.init(this)
    }
}