package com.ruos.auth.biometric

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators

/**
 * Silent, automatic detection of what biometric hardware the device has, classified
 * into RuOS's iOS-style names. Uses public PackageManager features for the *kind* of
 * sensor and BiometricManager for *availability/enrolment* — no technical detail is
 * ever shown to the user.
 */
object BiometricCapability {

    enum class Kind(val ruName: String) {
        FACE("Face ID"),
        FINGERPRINT("Touch ID"),
        BOTH("Face ID и Touch ID"),
        NONE("Код-пароль")
    }

    fun kind(context: Context): Kind {
        val pm = context.packageManager
        val hasFace = pm.hasSystemFeature(PackageManager.FEATURE_FACE)
        val hasFinger = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        return when {
            hasFace && hasFinger -> Kind.BOTH
            hasFace -> Kind.FACE
            hasFinger -> Kind.FINGERPRINT
            else -> Kind.NONE
        }
    }

    /** True if any strong/weak biometric is present AND enrolled. */
    fun isReady(context: Context): Boolean {
        val bm = context.getSystemService(BiometricManager::class.java) ?: return false
        val status = bm.canAuthenticate(Authenticators.BIOMETRIC_WEAK)
        return status == BiometricManager.BIOMETRIC_SUCCESS
    }

    /** True if hardware exists but nothing is enrolled yet (→ offer setup). */
    fun needsEnrolment(context: Context): Boolean {
        val bm = context.getSystemService(BiometricManager::class.java) ?: return false
        return bm.canAuthenticate(Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
    }

    /** Localised name for the active biometric, used across the UI. */
    fun displayName(context: Context): String = kind(context).ruName
}
