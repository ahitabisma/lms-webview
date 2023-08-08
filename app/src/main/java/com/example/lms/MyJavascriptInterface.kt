package com.example.lms

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import com.onesignal.OneSignal

class MyJavascriptInterface(private val context: Context) : WebChromeClient() {

    // Ambil Device Token dari Device
    @JavascriptInterface
    fun getDeviceToken(): String? {
        val deviceState = OneSignal.getDeviceState()
        return deviceState?.userId
    }
}