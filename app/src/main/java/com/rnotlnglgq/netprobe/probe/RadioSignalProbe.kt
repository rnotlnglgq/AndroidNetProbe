package com.rnotlnglgq.netprobe.probe

import android.os.Build
import android.telephony.TelephonyManager
import com.rnotlnglgq.netprobe.model.*

/** The getter itself needs no READ_PHONE_STATE; per-subscription selection may need enumeration. */
object RadioSignalProbe {
    fun collect(prefix: String, tm: TelephonyManager): List<ProbeField<*>> {
        val source = "TelephonyManager.getSignalStrength"
        val signal = if (Build.VERSION.SDK_INT >= 28) readField("$prefix.signalStrength.state", source, sensitive=true) { tm.signalStrength }
            else unavailable("$prefix.signalStrength.state", source, FieldStatus.unsupported_api, note="需要 API 28", sensitive=true)
        val fields = mutableListOf<ProbeField<*>>(ProbeField(signal.path, signal.value?.let { "available" }, signal.status,
            source, "none", signal.note, signal.errorType, true))
        if (Build.VERSION.SDK_INT >= 28 && signal.value != null) {
            fields += readField("$prefix.signalStrength.level", "SignalStrength.getLevel", sensitive=true, note="系统缓存信号等级") { signal.value.level }
            fields += if (Build.VERSION.SDK_INT >= 29) readField("$prefix.signalStrength.radios",
                "SignalStrength.getCellSignalStrengths / CellSignalStrength getters", sensitive=true) {
                signal.value.cellSignalStrengths.map { RadioSignal(it.javaClass.simpleName,
                    it.dbm.takeUnless { v -> v == Int.MAX_VALUE }, it.asuLevel.takeUnless { v -> v == Int.MAX_VALUE }, it.level) }
            } else unavailable("$prefix.signalStrength.radios", "SignalStrength.getCellSignalStrengths", FieldStatus.unsupported_api, note="需要 API 29", sensitive=true)
        }
        return fields
    }
}
