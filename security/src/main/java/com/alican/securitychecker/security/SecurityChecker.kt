package com.alican.securitychecker.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import com.alican.securitychecker.security.internal.DeviceUtil
import com.alican.securitychecker.security.internal.isAppInstalled
import com.scottyab.rootbeer.RootBeer
import java.io.File
import java.security.MessageDigest

/**
 * Runs on-device tamper/root/emulator/debugger checks used to gate access to an app.
 *
 * @param isProductionEnvironment whether the caller's current build flavor/variant is its
 * production one; passed in explicitly because flavor naming is app-specific and this library
 * has no generic way to read it (avoids depending on any consuming app's generated `BuildConfig`).
 * @param expectedSignatureHash the caller's known-good release signing certificate SHA-256 hash
 * (colon-separated hex, e.g. `"AB:CD:..."`). When provided, [isDeviceNotSecure] also fails if the
 * running APK's signature doesn't match it. Left `null` to skip signature verification entirely.
 */
class SecurityChecker(
    private val context: Context,
    private val isProductionEnvironment: Boolean,
    private val expectedSignatureHash: String? = null
) {
    private val isDebugBuild: Boolean
        get() = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun isDeviceNotSecure(): Boolean {
        return isDeviceRooted() || canExecuteSu() || isDeviceRootedViaRootBeer() || isClonedApp() || isBlackListAppInstalled()
                || isEmulatorForProdRelease() || isDebuggerAttached() || isSignatureMismatch()
    }

    private fun isSignatureMismatch(): Boolean {
        val expected = expectedSignatureHash ?: return false
        return !verifyAppSignature(expected)
    }

    companion object {
        /**
         * DI-agnostic entry point for callers that don't want to construct/register a
         * [SecurityChecker] instance (e.g. no DI framework in use). Equivalent to
         * `SecurityChecker(context, isProductionEnvironment, expectedSignatureHash).isDeviceNotSecure()`.
         */
        @JvmStatic
        fun isDeviceNotSecure(
            context: Context,
            isProductionEnvironment: Boolean,
            expectedSignatureHash: String? = null
        ): Boolean =
            SecurityChecker(context, isProductionEnvironment, expectedSignatureHash).isDeviceNotSecure()
    }

    fun isDeviceRooted(): Boolean {
        val buildTags = Build.TAGS
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/system/xbin/su",
            "/system/bin/su",
            "/system/sbin/su",
            "/system/xbin/which",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "su",
            "/sbin/su",
            "/vendor/bin/su",
            "/data/local/su",
            "/su/bin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
        )
        return paths.any { path -> File(path).exists() }
                || (buildTags != null && buildTags.contains("test-keys"))
    }

    fun isDeviceRootedViaRootBeer(): Boolean =
        !isDebugBuild && RootBeer(context).isRooted

    fun canExecuteSu(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            process.inputStream.read() != -1
        } catch (e: Exception) {
            false
        }
    }

    fun isClonedApp(): Boolean {
        val path = context.filesDir.path
        val pathDotCount = path.count { it == '.' }
        val appIdDotCount = context.packageName.count { it == '.' }

        if (pathDotCount > appIdDotCount) {
            return true
        }
        val stacks = Thread.currentThread().stackTrace
        for (stackTraceElement in stacks) {
            val methodName = stackTraceElement.methodName
            val className = stackTraceElement.className
            if (methodName.equals("callActivityOnCreate") && className.startsWith("com")) {
                return true
            }
        }
        return false
    }

    private val blackListAppList: List<String> = listOf(
        "com.waxmoon.ma.gp",
        "com.cmaster.cloner",
        "com.xunijun.app.gp"
    )

    fun isBlackListAppInstalled(): Boolean {
        blackListAppList.forEach { packageName ->
            if (context.isAppInstalled(packageName)) {
                return true
            }
        }
        return false
    }

    fun isEmulatorForProdRelease(): Boolean {
        if (isProductionEnvironment && !isDebugBuild) {
            return DeviceUtil.isEmulator()
        }
        return false
    }

    fun isDebuggerAttached(): Boolean {
        if (isDebugBuild) {
            return false
        }
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
            return true
        }

        try {
            val debugField = Class.forName("android.os.Debug")
                .getDeclaredMethod("isDebuggerConnected")
            if (debugField.invoke(null) as Boolean) {
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * Returns true if the running APK's signing certificate matches [expectedSignature]
     * (colon-separated hex SHA-256, e.g. `"AB:CD:..."`) — i.e. the build is genuine and unmodified.
     */
    @Suppress("DEPRECATION") // GET_SIGNATURES/.signatures is the only API available below API 28 (P)
    fun verifyAppSignature(expectedSignature: String): Boolean {
        val packageName = context.packageName
        val pm = context.packageManager
        val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }

        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            packageInfo.signatures
        }

        val md = MessageDigest.getInstance("SHA-256")
        if (signatures != null) {
            for (signature in signatures) {
                val hash = md.digest(signature.toByteArray())
                val hexHash = hash.joinToString(":") { "%02x".format(it) }
                if (hexHash.equals(expectedSignature, ignoreCase = true)) {
                    return true // signature matches expected
                }
            }
        }
        return false
    }
}
