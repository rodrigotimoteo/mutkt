package com.example.mutkt.android

import android.content.Context

/** Greets by name with a string resource, so its test needs Robolectric. */
class Greeter(private val context: Context) {
    fun greet(name: String): String =
        if (name.isBlank()) {
            context.getString(R.string.greeting_anonymous)
        } else {
            context.getString(R.string.greeting, name)
        }
}
