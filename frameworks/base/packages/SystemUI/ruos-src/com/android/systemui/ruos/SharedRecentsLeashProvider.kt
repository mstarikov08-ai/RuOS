package com.android.systemui.ruos

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceControl

/**
 * Concrete [RecentsLeashProvider] backed by SystemUI's shared recents compat layer.
 *
 * ── Why the compat layer and not IActivityTaskManager directly ────────────────
 * SystemUI ships `com.android.systemui.shared.system.RecentsAnimationControllerCompat`,
 * `RecentsAnimationListener`, and `ActivityManagerWrapper`, which wrap the raw
 * `IActivityTaskManager.startRecentsActivity()` / `IRecentsAnimationController` AIDL.
 * Launcher3 Quickstep is built on exactly these classes. They absorb most of the
 * signature churn between AOSP point releases, so binding to them is the stable
 * choice from inside SystemUI.
 *
 * ── The reflection bridge ─────────────────────────────────────────────────────
 * `ruos-src` is compiled as part of SystemUI, but the shared recents classes live
 * in a sibling module (`SystemUISharedLib`) that ruos-src may or may not have on
 * its compile classpath depending on how the build glue adds our sources. To keep
 * THIS file compiling regardless of that wiring, the bridge to the shared layer is
 * done reflectively and fails soft: if the classes are absent at runtime, the
 * gesture engine degrades to a no-op leash (logs once) instead of crashing
 * SystemUI. Once the build is confirmed to put SystemUISharedLib on ruos-src's
 * classpath, the reflective block can be swapped for direct calls — the public
 * surface of this class does not change.
 *
 * This is the ONLY place in the gesture engine that is not pure, testable math.
 */
class SharedRecentsLeashProvider(
    private val context: Context
) : RecentsLeashProvider {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var active = false
    private var controllerRef: Any? = null      // RecentsAnimationControllerCompat
    private var callbacks: RecentsLeashProvider.Callbacks? = null
    private val taskPackages = HashMap<Int, String>()

    override val isActive: Boolean get() = active

    /** Package name for a task captured at the last handover, or null. */
    fun packageForTask(taskId: Int): String? = taskPackages[taskId]

    override fun startRecents(homeIntent: Intent, callbacks: RecentsLeashProvider.Callbacks) {
        if (active) {
            Log.w(TAG, "startRecents called while a transition is already active")
            return
        }
        this.callbacks = callbacks
        active = true

        try {
            beginViaSharedLayer(homeIntent, callbacks)
        } catch (t: Throwable) {
            // Shared recents layer not reachable on this classpath/version.
            // Fail soft: surface a degraded (empty) handover so the gesture
            // controller can still drive the home-screen reveal without moving
            // a real app surface, rather than taking down SystemUI.
            if (!warnedOnce) {
                Log.w(TAG, "Shared recents layer unavailable; running in degraded " +
                    "no-leash mode. Reconcile SharedRecentsLeashProvider with the " +
                    "target AOSP recents API.", t)
                warnedOnce = true
            }
            mainHandler.post {
                callbacks.onRecentsStarted(emptyList(), null, Rect())
            }
        }
    }

    /**
     * The real handover. Written against the shape of
     * `ActivityManagerWrapper.startRecentsActivity(Intent, AssistDataReceiver?,
     *  RecentsAnimationListener)` and `RecentsAnimationListener.onAnimationStart(
     *  RecentsAnimationControllerCompat, RemoteAnimationTarget[], RemoteAnimationTarget[],
     *  Rect, Rect)`.
     *
     * Kept reflective so this module compiles even before the SharedLib dependency
     * is added to ruos-src's srcs. See class doc.
     */
    private fun beginViaSharedLayer(
        homeIntent: Intent,
        callbacks: RecentsLeashProvider.Callbacks
    ) {
        val amwClass = Class.forName("com.android.systemui.shared.system.ActivityManagerWrapper")
        val listenerClass = Class.forName("com.android.systemui.shared.system.RecentsAnimationListener")
        val targetClass = Class.forName("android.view.RemoteAnimationTarget")

        val amw = amwClass.getMethod("getInstance").invoke(null)

        // Build a dynamic proxy implementing RecentsAnimationListener.
        val listenerProxy = java.lang.reflect.Proxy.newProxyInstance(
            listenerClass.classLoader,
            arrayOf(listenerClass)
        ) { _, method, args ->
            when (method.name) {
                "onAnimationStart" -> {
                    controllerRef = args[0]
                    @Suppress("UNCHECKED_CAST")
                    val apps = args[1] as? Array<Any> ?: emptyArray()
                    val wallpapers = args.getOrNull(2) as? Array<Any> ?: emptyArray()
                    val homeInsets = args.getOrNull(3) as? Rect ?: Rect()
                    deliverStart(targetClass, apps, wallpapers, homeInsets, callbacks)
                    null
                }
                "onAnimationCanceled" -> {
                    active = false
                    mainHandler.post { callbacks.onRecentsCancelled() }
                    null
                }
                "onTasksAppeared" -> null
                else -> defaultForReturn(method.returnType)
            }
        }

        // ActivityManagerWrapper.startRecentsActivity(Intent, AssistDataReceiver?, RecentsAnimationListener)
        val start = amwClass.methods.firstOrNull {
            it.name == "startRecentsActivity" && it.parameterTypes.size >= 3
        } ?: error("startRecentsActivity not found on ActivityManagerWrapper")

        val params = arrayOfNulls<Any>(start.parameterTypes.size)
        params[0] = homeIntent
        // middle args (AssistDataReceiver, etc.) left null
        params[start.parameterTypes.size - 1] = listenerProxy
        start.invoke(amw, *params)
    }

    private fun deliverStart(
        targetClass: Class<*>,
        apps: Array<Any>,
        wallpapers: Array<Any>,
        homeInsets: Rect,
        callbacks: RecentsLeashProvider.Callbacks
    ) {
        val leashField = targetClass.getField("leash")
        val taskIdField = runCatching { targetClass.getField("taskId") }.getOrNull()
        val modeField = runCatching { targetClass.getField("mode") }.getOrNull()
        val boundsField = runCatching { targetClass.getField("screenSpaceBounds") }.getOrNull()
            ?: runCatching { targetClass.getField("sourceContainerBounds") }.getOrNull()
        val taskInfoField = runCatching { targetClass.getField("taskInfo") }.getOrNull()

        taskPackages.clear()
        val remoteLeashes = apps.mapIndexed { index, t ->
            val leash = leashField.get(t) as SurfaceControl
            val taskId = (taskIdField?.get(t) as? Int) ?: index
            val bounds = (boundsField?.get(t) as? Rect) ?: Rect()
            // mode == MODE_CLOSING (1) is the app being dismissed → foreground
            val mode = (modeField?.get(t) as? Int) ?: MODE_CLOSING
            // Capture taskId → package for landing-icon lookup.
            runCatching {
                val ti = taskInfoField?.get(t)
                val topActivity = ti?.javaClass?.getField("topActivity")?.get(ti)
                val pkg = topActivity?.javaClass?.getMethod("getPackageName")?.invoke(topActivity) as? String
                if (pkg != null) taskPackages[taskId] = pkg
            }
            RecentsLeashProvider.RemoteLeash(
                taskId = taskId,
                leash = leash,
                startBounds = bounds,
                isForeground = (mode == MODE_CLOSING),
                taskInfo = null
            )
        }.sortedByDescending { it.isForeground }

        val wallpaperLeash = wallpapers.firstOrNull()?.let { leashField.get(it) as? SurfaceControl }

        mainHandler.post {
            callbacks.onRecentsStarted(remoteLeashes, wallpaperLeash, homeInsets)
        }
    }

    override fun finish(toHome: Boolean, onFinished: (() -> Unit)?) {
        val controller = controllerRef
        active = false
        if (controller == null) {
            mainHandler.post { onFinished?.invoke() }
            return
        }
        try {
            // RecentsAnimationControllerCompat.finish(boolean toHome, boolean sendUserLeaveHint)
            val finishMethod = controller.javaClass.methods.firstOrNull {
                it.name == "finish" && it.parameterTypes.size >= 2
            }
            finishMethod?.invoke(controller, toHome, false)
        } catch (t: Throwable) {
            Log.w(TAG, "finish() failed", t)
        } finally {
            controllerRef = null
            mainHandler.post { onFinished?.invoke() }
        }
    }

    private fun defaultForReturn(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        else -> null
    }

    companion object {
        private const val TAG = "RuOSRecents"
        private const val MODE_CLOSING = 1   // RemoteAnimationTarget.MODE_CLOSING
        @Volatile private var warnedOnce = false
    }
}
