package com.rnotlnglgq.netprobe.probe

import android.os.Build
import android.telephony.SubscriptionInfo
import com.rnotlnglgq.netprobe.model.*
import java.util.UUID

/** Device-local subscription IDs are only used internally, never exported. */
class SessionSubscriptionIds {
    private val ids = mutableMapOf<Int, String>()
    @Synchronized fun alias(id: Int): String = ids.getOrPut(id) { "sub-${UUID.randomUUID()}" }
}

object PhoneAccess {
    const val PHONE = "READ_PHONE_STATE"
    const val SERVICE_STATE = "READ_PHONE_STATE + ACCESS_COARSE_LOCATION"
    fun serviceField(name: String): String = if (name in listOf("operatorNumeric", "operatorAlphaLong", "operatorAlphaShort"))
        DiagnosticCatalog.legacy("access_coarse_location_or_access_fine_location_field_visibility") else "none"
    fun dataNetworkType(api: Int): String = if (api >= 33) "READ_PHONE_STATE or READ_BASIC_PHONE_STATE" else PHONE
}

object SubscriptionRecordFields {
    data class Spec(val key: String, val getter: String, val minApi: Int = 26)
    val specs = listOf(
        Spec("slotIndex", "getSimSlotIndex"), Spec("displayName", "getDisplayName"),
        Spec("carrierName", "getCarrierName"), Spec("countryIso", "getCountryIso"),
        Spec("embedded", "isEmbedded", 28), Spec("mcc", "getMccString", 29), Spec("mnc", "getMncString", 29),
        Spec("carrierId", "getCarrierId", 29), Spec("dataRoaming", "getDataRoaming"),
        Spec("opportunistic", "isOpportunistic", 29), Spec("subscriptionType", "getSubscriptionType", 29),
        Spec("portIndex", "getPortIndex", 33), Spec("usageSetting", "getUsageSetting", 33),
        Spec("onlyNonTerrestrial", "isOnlyNonTerrestrialNetwork", 35))

    fun collect(prefix: String, alias: String, api: Int, read: (String) -> Any?): List<ProbeField<*>> =
        listOf(readField("$prefix.sessionSubscriptionId", "session_random_uuid", sensitive = true) { alias }
            .withAcquisition(PhoneAccess.PHONE)) + specs.map { spec ->
            val field = if (api < spec.minApi) unavailable("$prefix.${spec.key}", "SubscriptionInfo.${spec.getter}",
                FieldStatus.unsupported_api, note=DiagnosticCatalog.legacy("requires_api_1_s" ,spec.minApi), sensitive=true)
            else readField("$prefix.${spec.key}", "SubscriptionInfo.${spec.getter}", sensitive=true,
                note=if (spec.key == "displayName") DiagnosticCatalog.legacy("name_may_be_user_edited_or_retain_its_system_default") else null) { read(spec.key) }
            field.withAcquisition(PhoneAccess.PHONE)
        }

    fun collect(prefix: String, info: SubscriptionInfo, ids: SessionSubscriptionIds): List<ProbeField<*>> =
        collect(prefix, ids.alias(info.subscriptionId), Build.VERSION.SDK_INT) { key -> when (key) {
            "slotIndex" -> info.simSlotIndex
            "displayName" -> info.displayName?.toString()
            "carrierName" -> info.carrierName?.toString()
            "countryIso" -> info.countryIso
            "dataRoaming" -> info.dataRoaming
            "embedded" -> if (Build.VERSION.SDK_INT >= 28) info.isEmbedded else null
            "mcc" -> if (Build.VERSION.SDK_INT >= 29) info.mccString else null
            "mnc" -> if (Build.VERSION.SDK_INT >= 29) info.mncString else null
            "carrierId" -> if (Build.VERSION.SDK_INT >= 29) info.carrierId.takeUnless { it == -1 } else null
            "opportunistic" -> if (Build.VERSION.SDK_INT >= 29) info.isOpportunistic else null
            "subscriptionType" -> if (Build.VERSION.SDK_INT >= 29) info.subscriptionType else null
            "portIndex" -> if (Build.VERSION.SDK_INT >= 33) info.portIndex else null
            "usageSetting" -> if (Build.VERSION.SDK_INT >= 33) info.usageSetting else null
            "onlyNonTerrestrial" -> if (Build.VERSION.SDK_INT >= 35) info.isOnlyNonTerrestrialNetwork else null
            else -> null
        } }
}
