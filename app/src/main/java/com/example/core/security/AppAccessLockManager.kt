package com.example.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Manages the **App Access Lock (UI Session Gate)** for Paperwork.
 *
 * Security Boundary Distinction:
 * - This manager controls interactive entry into the Paperwork UI session using either
 *   Android's [BiometricPrompt] (`BIOMETRIC_STRONG or DEVICE_CREDENTIAL`) or the salted
 *   4-digit PIN.
 * - It is explicitly documented as an application access lock rather than a per-block
 *   cryptographic key unlocker, keeping multi-page background rendering and print spooling
 *   free from mid-stream `UserNotAuthenticatedException` crashes.
 */
class AppAccessLockManager(private val context: Context) {

    fun canAuthenticateWithBiometricOrDeviceCredential(): Boolean {
        return try {
            val manager = BiometricManager.from(context)
            val result = manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            result == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    fun promptBiometricOrDeviceCredential(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }
            }

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Paperwork")
                .setSubtitle("Verify your identity to access your Paperwork session")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()

            BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
        } catch (e: Exception) {
            onError(e.message ?: "Biometric authentication unavailable.")
        }
    }
}
