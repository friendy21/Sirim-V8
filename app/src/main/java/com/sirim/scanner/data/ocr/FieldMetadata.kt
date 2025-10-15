package com.sirim.scanner.data.ocr

/**
 * Represents the origin of a captured field. This can be extended later if additional
 * acquisition sources are introduced without requiring schema changes.
 */
enum class FieldSource(val storageValue: String) {
    QrScanner("qr_scanner"),
    ManualEntry("manual_entry"),
    ExternalImport("external_import");

    companion object {
        fun fromStorage(value: String?): FieldSource? =
            values().firstOrNull { it.storageValue == value }
    }
}

/**
 * Lightweight wrapper to capture free-form notes associated with a field.
 */
@JvmInline
value class FieldNote(val value: String) {
    override fun toString(): String = value

    companion object {
        fun fromStorage(serialized: String?): Set<FieldNote> = serialized
            ?.split('|')
            ?.mapNotNull { entry -> entry.trim().takeIf(String::isNotEmpty)?.let(::FieldNote) }
            ?.toSet()
            ?: emptySet()

        fun toStorage(notes: Set<FieldNote>): String? = notes
            .map(FieldNote::value)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "|")
    }
}
