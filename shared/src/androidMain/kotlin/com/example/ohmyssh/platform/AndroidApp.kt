package com.example.ohmyssh.platform

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("StaticFieldLeak")
object AndroidApp {
    lateinit var context: Context
        private set

    fun init(appContext: Context) {
        context = appContext.applicationContext
    }
}
