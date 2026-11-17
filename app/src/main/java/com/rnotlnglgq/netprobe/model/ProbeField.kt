package com.rnotlnglgq.netprobe.model

enum class FieldStatus {
    available, empty, permission_denied, redacted, unsupported_api,
    unsupported_feature, network_unavailable, error, not_requested
}

/** Stable schema paths; values are typed at collection, never API debug strings. */
data class ProbeField<T>(
    val path: String,
    val value: T? = null,
    val status: FieldStatus = FieldStatus.available,
    val source: String,
    val permission: String = "none",
    val note: String? = null,
    val errorType: String? = null,
    val sensitive: Boolean,
    val observedStatus: FieldStatus? = null,
    val acquisitionPermissions: List<String> = emptyList()
)

fun <T> readField(
    path: String, source: String, permission: String = "none",
    sensitive: Boolean, note: String? = null, block: () -> T?
): ProbeField<T> = try {
    val value = block()
    val empty = value == null || value is String && value.isBlank() ||
        value is Collection<*> && value.isEmpty()
    ProbeField(path, value, if (empty) FieldStatus.empty else FieldStatus.available,
        source, permission, note, sensitive = sensitive)
} catch (e: java.util.concurrent.CancellationException) {
    throw e
} catch (e: SecurityException) {
    ProbeField(path, status = FieldStatus.permission_denied, source = source,
        permission = permission, note = "系统拒绝此 API", errorType = e.javaClass.simpleName,
        sensitive = sensitive)
} catch (e: UnsupportedOperationException) {
    ProbeField(path, status = FieldStatus.unsupported_feature, source = source, permission = permission,
        note = "系统不支持此操作", errorType = e.javaClass.simpleName, sensitive = sensitive)
} catch (e: Exception) {
    // Messages can contain private data: retain only the exception type.
    ProbeField(path, status = FieldStatus.error, source = source, permission = permission,
        note = "API 调用失败", errorType = e.javaClass.simpleName, sensitive = sensitive)
}

fun unavailable(
    path: String, source: String, status: FieldStatus, permission: String = "none",
    note: String? = null, sensitive: Boolean
) = ProbeField<Nothing>(path, status = status, source = source, permission = permission,
    note = note, sensitive = sensitive)

/** Permissions needed earlier in the data flow, separate from the getter/visibility permission. */
fun ProbeField<*>.withAcquisition(vararg permissions: String): ProbeField<*> =
    copy(acquisitionPermissions = (acquisitionPermissions + permissions).distinct())
