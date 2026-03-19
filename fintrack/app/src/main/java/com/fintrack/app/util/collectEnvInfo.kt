package com.fintrack.app.util

import android.content.Context
import com.fintrack.app.appLocale
import com.fintrack.app.systemLocale

fun collectEnvInfo(context: Context): String {
    return """
        ---- Device info ---------------------------
        Device: ${android.os.Build.DEVICE}
        Model: ${android.os.Build.MODEL}
        Product: ${android.os.Build.PRODUCT}
        Android version: ${android.os.Build.VERSION.RELEASE}
        SDK: ${android.os.Build.VERSION.SDK_INT}
        ---- Locale info ---------------------------
        App locale: ${context.appLocale}
        System locale: ${context.systemLocale}
    """.trimIndent()
}