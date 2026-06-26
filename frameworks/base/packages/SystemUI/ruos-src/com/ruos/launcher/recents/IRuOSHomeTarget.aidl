package com.ruos.launcher.recents;

import android.graphics.Rect;

/**
 * SystemUI-side copy of the launcher's home-target AIDL. Must stay byte-identical
 * to packages/apps/RuOSLauncher/.../recents/IRuOSHomeTarget.aidl so the binder
 * transaction codes match across the two processes. (Duplicated rather than shared
 * because the launcher and SystemUI are separate build modules.)
 */
interface IRuOSHomeTarget {
    oneway void onHomeProgress(float progress);
    oneway void onHomeSettled(boolean toHome);
    Rect getLandingBounds(String packageName);
}
