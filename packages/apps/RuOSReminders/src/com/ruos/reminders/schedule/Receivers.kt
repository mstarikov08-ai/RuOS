package com.ruos.reminders.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import com.ruos.reminders.notify.ReminderNotifier
import com.ruos.reminders.store.ReminderStore

/** Time trigger fired by AlarmManager → show the reminder notification. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_ID) ?: return
        ReminderStore(context).byId(id)?.takeIf { !it.completed }?.let {
            ReminderNotifier.show(context, it)
        }
    }
}

/** Location trigger fired by LocationManager proximity alert. */
class ProximityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_ID) ?: return
        val entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)
        val r = ReminderStore(context).byId(id)?.takeIf { !it.completed } ?: return
        // fire on arrival when onArrival, on leaving otherwise
        if (entering == r.onArrival) ReminderNotifier.show(context, r)
    }
}

/** "Выполнено" action on the notification → mark done + cancel triggers + dismiss. */
class CompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_ID) ?: return
        val store = ReminderStore(context)
        store.byId(id)?.let {
            val done = it.copy(completed = true)
            store.upsert(done); ReminderScheduler.cancel(context, done)
        }
        ReminderNotifier.dismiss(context, id)
    }
}

/** Re-arm all triggers after reboot / time change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { ReminderScheduler.scheduleAll(context) }   // never crash at boot
    }
}
