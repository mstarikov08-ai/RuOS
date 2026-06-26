package com.ruos.screenrecord

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast

/**
 * Transparent entry point for the Control Centre tile. Toggles recording:
 *  • if recording → tell the service to stop;
 *  • else → request the MediaProjection consent dialog, then start the service.
 * A "toggle_mic" extra flips the microphone preference (for the CC long-press).
 */
class ScreenRecordRequestActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.getBooleanExtra("toggle_mic", false) == true) {
            val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val on = !prefs.getBoolean(KEY_MIC, false)
            prefs.edit().putBoolean(KEY_MIC, on).apply()
            Toast.makeText(this, if (on) "Микрофон включён" else "Микрофон выключен", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        if (ScreenRecordService.isRunning) {
            startService(Intent(this, ScreenRecordService::class.java).setAction(ScreenRecordService.ACTION_STOP))
            finish(); return
        }

        val mpm = getSystemService(MediaProjectionManager::class.java)
        runCatching { startActivityForResult(mpm.createScreenCaptureIntent(), REQ) }
            .onFailure { finish() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ && resultCode == RESULT_OK && data != null) {
            val mic = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_MIC, false)
            val svc = Intent(this, ScreenRecordService::class.java)
                .putExtra(ScreenRecordService.EXTRA_CODE, resultCode)
                .putExtra(ScreenRecordService.EXTRA_DATA, data)
                .putExtra(ScreenRecordService.EXTRA_MIC, mic)
            startForegroundService(svc)
        }
        finish()
    }

    companion object {
        private const val REQ = 9001
        const val PREFS = "ruos_screenrec"
        const val KEY_MIC = "mic"
    }
}
