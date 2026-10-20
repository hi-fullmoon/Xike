package com.xike.app.outdoor

internal enum class AmapFailureKind {
    NETWORK,
    AUTH,
    PERMISSION,
    TIMEOUT,
    OTHER,
}

/** Only fixed SDK diagnostic tags can leave the callback; never expose its raw detail text. */
internal enum class AmapNetworkDiagnostic(val tag: String) {
    OFFLINE("0401"),
    TRANSPORT("0402"),
    RESPONSE("0403"),
    HTTP_RESPONSE("0404"),
    RESPONSE_EXCEPTION("0403"),
    RESPONSE_NULL_REFERENCE("0403");

    companion object {
        fun fromDetail(detail: String): AmapNetworkDiagnostic? {
            if (detail.contains("#0403") && detail.contains("check response exception")) {
                return if (
                    detail.contains("null object reference") || detail.contains("null array")
                )
                    RESPONSE_NULL_REFERENCE
                else RESPONSE_EXCEPTION
            }
            return listOf(OFFLINE, TRANSPORT, RESPONSE, HTTP_RESPONSE).firstOrNull {
                detail.contains("#${it.tag}")
            }
        }
    }
}

internal fun amapFailureKind(code: Int): AmapFailureKind =
    when (code) {
        4 -> AmapFailureKind.NETWORK
        7 -> AmapFailureKind.AUTH
        12,
        20 -> AmapFailureKind.PERMISSION
        -2 -> AmapFailureKind.TIMEOUT
        else -> AmapFailureKind.OTHER
    }

/** Device-local credentials; never include this object in journal backups or diagnostic output. */
internal data class AmapSettings(
    val apiKey: String = "",
    val enabled: Boolean = false,
    val privacyAccepted: Boolean = false,
) {
    val canUseAmap: Boolean
        get() = enabled && apiKey.isNotBlank() && privacyAccepted

    fun normalized(): AmapSettings = copy(apiKey = apiKey.trim())

    fun isValid(): Boolean = apiKey.length <= 256 && (!enabled || canUseAmap)

    override fun toString(): String =
        "AmapSettings(keyConfigured=${apiKey.isNotBlank()}, enabled=$enabled, privacyAccepted=$privacyAccepted)"
}

/** A fresh three-tap sequence is required each time; slow or out-of-order taps restart it. */
internal class TripleTapGate(private val windowMillis: Long = 2_000L) {
    private var firstTap = 0L
    private var previousTap = 0L
    private var count = 0

    fun tap(now: Long): Boolean {
        if (count == 0 || now < previousTap || now - firstTap > windowMillis) {
            firstTap = now
            count = 0
        }
        previousTap = now
        count++
        return (count == 3).also { if (it) count = 0 }
    }
}

internal data class PlaceLabels(val label: String, val fullLabel: String)

internal fun amapPlaceLabels(
    country: String,
    province: String,
    city: String,
    district: String,
    street: String,
    address: String,
    includeStreet: Boolean,
): PlaceLabels? {
    fun List<String>.joined() =
        map(String::trim).filter(String::isNotBlank).distinct().joinToString(" · ")
    val regions = listOf(country, province, city, district).joined()
    val label =
        listOf(city, district, street.takeIf { includeStreet }.orEmpty()).joined().ifBlank {
            regions
        }
    if (label.isBlank()) return null
    val full =
        if (includeStreet)
            address.trim().ifBlank { listOf(country, province, city, district, street).joined() }
        else regions
    return PlaceLabels(label, full.ifBlank { label })
}
