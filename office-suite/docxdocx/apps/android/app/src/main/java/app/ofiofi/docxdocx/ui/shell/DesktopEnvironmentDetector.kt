package app.ofiofi.docxdocx.ui.shell

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.view.InputDevice

enum class DesktopModePreference {
    AUTO,
    FORCE_DESKTOP,
    FORCE_MOBILE,
}

enum class ShellMode {
    DESKTOP,
    TABLET,
    PHONE,
}

data class DesktopEnvironmentSignals(
    val isSamsungDex: Boolean,
    val isUiModeDesk: Boolean,
    val hasPcFeature: Boolean,
    val hasHardwareKeyboard: Boolean,
    val hasMousePointer: Boolean,
    val smallestScreenWidthDp: Int,
    val windowWidthDp: Int,
    val preference: DesktopModePreference = DesktopModePreference.AUTO,
) {
    val isHardwareDesktop: Boolean
        get() = isSamsungDex || isUiModeDesk || hasPcFeature || (windowWidthDp >= 840 && (hasMousePointer || hasHardwareKeyboard))
}

/**
 * Pure-Kotlin + Android runtime detector for Samsung DeX, Android Desktop Windowing,
 * external displays, and attached mouse/hardware keyboard setups.
 */
object DesktopEnvironmentDetector {
    fun resolveShellMode(signals: DesktopEnvironmentSignals): ShellMode = when (signals.preference) {
        DesktopModePreference.FORCE_DESKTOP -> ShellMode.DESKTOP
        DesktopModePreference.FORCE_MOBILE -> if (signals.windowWidthDp >= 600) ShellMode.TABLET else ShellMode.PHONE
        DesktopModePreference.AUTO -> when {
            signals.isHardwareDesktop -> ShellMode.DESKTOP
            signals.windowWidthDp >= 600 -> ShellMode.TABLET
            else -> ShellMode.PHONE
        }
    }

    fun inspect(
        context: Context,
        windowWidthDp: Int,
        preference: DesktopModePreference = DesktopModePreference.AUTO,
    ): DesktopEnvironmentSignals {
        val config = context.resources.configuration
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isUiModeDesk = (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_DESK ||
            uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_DESK

        val hasPcFeature = runCatching {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PC)
        }.getOrDefault(false)

        val isSamsungDex = detectSamsungDex(context, config)
        val (hasHardwareKeyboard, hasMousePointer) = detectInputDevices(context, config)

        return DesktopEnvironmentSignals(
            isSamsungDex = isSamsungDex,
            isUiModeDesk = isUiModeDesk,
            hasPcFeature = hasPcFeature,
            hasHardwareKeyboard = hasHardwareKeyboard,
            hasMousePointer = hasMousePointer,
            smallestScreenWidthDp = config.smallestScreenWidthDp,
            windowWidthDp = windowWidthDp,
            preference = preference,
        )
    }

    private fun detectSamsungDex(context: Context, config: Configuration): Boolean {
        // 1. Samsung Configuration fields (`semDesktopModeEnabled == 1` via reflection so standard SDK builds cleanly)
        val configDexEnabled = runCatching {
            val enabledField = config.javaClass.getField("semDesktopModeEnabled")
            val enabledVal = config.javaClass.getField("SEM_DESKTOP_MODE_ENABLED").getInt(null)
            enabledField.getInt(config) == enabledVal
        }.getOrDefault(false)
        if (configDexEnabled) return true

        // 2. SemDesktopModeManager (`desktopModeState.enabled == ENABLED`)
        return runCatching {
            val service = context.getSystemService("desktopmode") ?: return@runCatching false
            val getState = service.javaClass.getMethod("getDesktopModeState")
            val state = getState.invoke(service) ?: return@runCatching false
            val enabledField = state.javaClass.getField("enabled")
            val enabledConst = state.javaClass.getField("ENABLED").getInt(null)
            enabledField.getInt(state) == enabledConst
        }.getOrDefault(false)
    }

    private fun detectInputDevices(context: Context, config: Configuration): Pair<Boolean, Boolean> {
        var hasKeyboard = config.keyboard == Configuration.KEYBOARD_QWERTY &&
            config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
        var hasMouse = false

        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
        inputManager?.inputDeviceIds?.forEach { id ->
            val dev = inputManager.getInputDevice(id) ?: return@forEach
            val sources = dev.sources
            if ((sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                (sources and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD
            ) {
                hasMouse = true
            }
            if ((sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD &&
                dev.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
                !dev.isVirtual
            ) {
                hasKeyboard = true
            }
        }
        return hasKeyboard to hasMouse
    }
}
