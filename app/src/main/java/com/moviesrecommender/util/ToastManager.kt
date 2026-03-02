package com.moviesrecommender.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object ToastManager {
    private var context: Context? = null

    fun init(ctx: Context) { context = ctx.applicationContext }

    fun show(message: String) {
        val ctx = context ?: return
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
    }
}
