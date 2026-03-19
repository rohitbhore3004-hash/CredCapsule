package com.findtrack.app.widget.extend

import android.content.Context
import com.findtrack.app.widget.WidgetReceiver
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ExtendWidgetReceiver : WidgetReceiver() {
    companion object {
        fun requestUpdateData(context: Context) {
            requestUpdateData(context, ExtendWidgetReceiver::class.java)
        }
    }

    override val glanceAppWidget = ExtendWidget()
}
