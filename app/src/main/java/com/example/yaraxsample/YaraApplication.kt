package com.example.yaraxsample

import android.app.Application

class YaraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        LocaleHelper.init(this)
        LocaleHelper.applyStoredLocale()
        ScheduleManager.applySchedule(this)
    }
}
