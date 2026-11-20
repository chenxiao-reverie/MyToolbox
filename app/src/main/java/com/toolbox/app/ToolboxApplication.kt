package com.toolbox.app

import android.app.Application

class ToolboxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}