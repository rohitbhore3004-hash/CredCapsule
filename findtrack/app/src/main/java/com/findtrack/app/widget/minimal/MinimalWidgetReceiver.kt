package com.findtrack.app.widget.minimal

import android.content.Context
import com.findtrack.app.widget.WidgetReceiver
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MinimalWidgetReceiver : WidgetReceiver() {
    companion object {
        fun requestUpdateData(context: Context) {
            requestUpdateData(context, MinimalWidgetReceiver::class.java)
        }
    }

    override val glanceAppWidget = MinimalWidget()
}
