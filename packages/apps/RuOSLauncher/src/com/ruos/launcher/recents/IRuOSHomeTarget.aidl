package com.ruos.launcher.recents;

import android.graphics.Rect;

/**
 * Cross-process channel SystemUI's gesture engine uses to drive the launcher's
 * home-screen reveal during the swipe-to-home gesture.
 *
 * Called from SystemUI (the GestureNavigationController / HomeRevealController).
 * Implemented by RuOSHomeTargetService in the launcher process.
 */
interface IRuOSHomeTarget {

    /**
     * Per-frame home-swipe progress.
     * @param progress 0 = app fullscreen (icons hidden), 1 = home fully revealed.
     */
    oneway void onHomeProgress(float progress);

    /**
     * The gesture finished.
     * @param toHome true if the app was dismissed to home, false if it sprang back.
     */
    oneway void onHomeSettled(boolean toHome);

    /**
     * On-screen bounds of the given package's icon (home grid or dock), so the
     * home animator can fly the shrinking app surface into its exact icon slot.
     * Returns null if the package has no visible icon.
     */
    Rect getLandingBounds(String packageName);
}
