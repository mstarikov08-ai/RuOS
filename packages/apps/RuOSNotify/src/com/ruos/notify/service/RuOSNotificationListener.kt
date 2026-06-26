package com.ruos.notify.service

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ruos.notify.badge.BadgeBroadcaster
import com.ruos.notify.banner.BannerManager
import com.ruos.notify.model.NotifAction
import com.ruos.notify.model.NotifItem
import com.ruos.notify.model.NotifSettings
import com.ruos.notify.sound.NotifSounds

/**
 * The backbone of RuOS notifications. Receives every posted/removed notification,
 * applies per-app settings, drives the heads-up banners, plays the gentle sound +
 * haptic, and maintains per-package unread counts for launcher badges.
 *
 * This is the data source the other iOS surfaces (lock-screen cards, Notification
 * Centre, Dynamic Island) build on; it keeps a live snapshot in [active].
 */
class RuOSNotificationListener : NotificationListenerService() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var banners: BannerManager
    private lateinit var sounds: NotifSounds
    private lateinit var settings: NotifSettings

    private val active = LinkedHashMap<String, NotifItem>()   // key -> item

    override fun onCreate() {
        super.onCreate()
        banners = BannerManager(this)
        sounds = NotifSounds(this)
        settings = NotifSettings(this)
    }

    override fun onListenerConnected() {
        // Seed the live snapshot from whatever is already showing.
        runCatching { activeNotifications }?.getOrNull()?.forEach { sbn ->
            buildItem(sbn)?.let { active[it.key] = it }
        }
        recomputeBadges()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val item = buildItem(sbn) ?: return
        val prefs = settings.forApp(item.pkg)
        if (!prefs.allow) { cancelNotification(sbn.key); return }

        active[item.key] = item
        recomputeBadges()

        if (item.isGroupSummary) return   // summaries don't get their own banner

        val pkgCount = countFor(item.pkg)
        main.post {
            sounds.play(item, prefs.sounds)
            // iOS: once a single app has many pending, collapse to a summary banner.
            if (prefs.grouping && pkgCount > SUMMARY_THRESHOLD) {
                banners.showSummary(item, pkgCount)
            } else {
                banners.show(item, persistent = prefs.persistent)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        active.remove(sbn.key)
        recomputeBadges()
        main.post { banners.dismissKey(sbn.key) }
    }

    /** Unread count per package = visible non-summary notifications. */
    private fun recomputeBadges() {
        val counts = HashMap<String, Int>()
        active.values.forEach {
            if (it.isGroupSummary) return@forEach
            if (!settings.forApp(it.pkg).badges) return@forEach   // respect per-app badge pref
            counts[it.pkg] = (counts[it.pkg] ?: 0) + 1
        }
        BadgeBroadcaster.send(this, counts)
    }

    private fun countFor(pkg: String) = active.values.count { it.pkg == pkg && !it.isGroupSummary }

    private fun buildItem(sbn: StatusBarNotification): NotifItem? {
        val n = sbn.notification ?: return null
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE) ?: ""
        val text = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT) ?: ""
        if (title.isBlank() && text.isBlank() && (n.flags and Notification.FLAG_GROUP_SUMMARY) == 0) {
            // Skip empty/transport-only notifications.
        }
        val appName = runCatching {
            val ai = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(ai).toString()
        }.getOrDefault(sbn.packageName)
        val icon = runCatching { packageManager.getApplicationIcon(sbn.packageName) }.getOrNull()

        val isCall = n.category == Notification.CATEGORY_CALL
        val isAlarmCat = n.category == Notification.CATEGORY_ALARM
        val timeSensitive = isCall || isAlarmCat ||
            n.priority >= Notification.PRIORITY_HIGH ||
            (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val critical = isCall || isAlarmCat

        val actions = n.actions?.map { NotifAction(it.title ?: "", it.actionIntent) } ?: emptyList()

        return NotifItem(
            key = sbn.key,
            pkg = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            whenMs = if (n.`when` > 0) n.`when` else sbn.postTime,
            icon = icon,
            isGroupSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            groupKey = sbn.groupKey,
            category = n.category,
            timeSensitive = timeSensitive,
            critical = critical,
            actions = actions,
            contentIntent = n.contentIntent
        )
    }

    companion object {
        private const val SUMMARY_THRESHOLD = 3
    }
}
