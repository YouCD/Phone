package org.fossify.phone.activities

import android.app.Activity
import android.os.Bundle
import org.fossify.commons.extensions.toast
import org.fossify.phone.extensions.config
import org.fossify.phone.R

class ToggleInterceptionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val newState = !config.callInterceptionEnabled
        config.callInterceptionEnabled = newState

        if (newState) {
            toast(R.string.call_interception_enabled)
        } else {
            toast(R.string.call_interception_disabled)
        }

        finish()
    }
}
