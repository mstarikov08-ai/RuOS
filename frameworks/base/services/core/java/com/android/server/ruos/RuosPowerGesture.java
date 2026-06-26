/*
 * RuOS — double-press power launches MIR Pay.
 *
 * The double-press-power gesture is detected only in system_server
 * (GestureLauncherService). Stock AOSP routes it to the camera. RuOS routes it to
 * MIR Pay instead: a one-line call inserted into
 * GestureLauncherService.handleCameraGesture() (see
 * vendor/ruos/patches/gesture-launcher-mirpay.patch) calls launchMirPay() and, if it
 * launches, returns before the camera path runs.
 *
 * This is the whole feature — the fastest possible shortcut to MIR Pay, working from
 * screen-off and from the lock screen (the gesture already holds a wakelock to turn
 * the screen on; MIR Pay shows over the keyguard per its own flags).
 */
package com.android.server.ruos;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.Slog;

public final class RuosPowerGesture {

    private static final String TAG = "RuosPowerGesture";

    /**
     * Candidate MIR Pay package ids (NSPK). The first one that is installed and has a
     * launchable activity wins.
     */
    private static final String[] MIR_PAY_PACKAGES = new String[] {
            "ru.nspk.mirpay",
    };

    private RuosPowerGesture() { }

    /**
     * Launches MIR Pay for the current user, if installed.
     *
     * @return true if MIR Pay was launched (caller should NOT also launch the
     *         camera); false if MIR Pay is not installed (caller falls through to the
     *         stock camera behaviour).
     */
    public static boolean launchMirPay(Context context) {
        if (context == null) {
            return false;
        }
        for (String pkg : MIR_PAY_PACKAGES) {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) {
                continue;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                context.startActivityAsUser(launch, UserHandle.CURRENT);
                Slog.i(TAG, "Double-press power launched MIR Pay (" + pkg + ")");
                return true;
            } catch (Exception e) {
                Slog.w(TAG, "Failed to launch MIR Pay (" + pkg + ")", e);
            }
        }
        Slog.d(TAG, "MIR Pay not installed; falling back to default gesture.");
        return false;
    }
}
