package com.github.rodrigotimoteo.mutation.sample.android

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager

@SuppressLint("NewApi")
class ResourceHelper(private val context: Context) {

    fun getAppName(): String = context.getString(context.applicationInfo.labelRes)

    fun getColorResource(resId: Int): Int = context.getColor(resId)

    @SuppressLint("MissingPermission")
    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return cm?.activeNetwork != null
    }

    fun formatCount(count: Int): String =
        when {
            count == 0 -> "None"
            count == 1 -> "1 item"
            count < 10 -> "$count items"
            else -> "Many"
        }
}
