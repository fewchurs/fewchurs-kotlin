package com.fewchurs.sample

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.fewchurs.sdk.Fewchurs

/** A classic View-based screen: one call opens the themed board Activity. */
class ViewBasedActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        layout.addView(TextView(this).apply { text = "This screen uses Views, not Compose." })
        layout.addView(Button(this).apply {
            text = "Fewchurs.showBoard(context)"
            setOnClickListener { Fewchurs.showBoard(this@ViewBasedActivity) }
        })
        setContentView(layout)
    }
}
