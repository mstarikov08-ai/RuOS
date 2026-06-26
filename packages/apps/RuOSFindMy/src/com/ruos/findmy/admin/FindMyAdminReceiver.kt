package com.ruos.findmy.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

/**
 * Device-admin component for Find My RuOS. Activating it grants the privileges needed for
 * **remote lock** (lockNow) and **remote wipe** (wipeData). The user must enable it
 * explicitly via the system Add-Device-Admin screen — it can't be silently granted.
 */
class FindMyAdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun component(context: Context) = ComponentName(context, FindMyAdminReceiver::class.java)
    }
}
