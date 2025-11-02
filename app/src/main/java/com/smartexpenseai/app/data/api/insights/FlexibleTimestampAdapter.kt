package com.smartexpenseai.app.data.api.insights

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

/**
 * Gson TypeAdapter that handles both Unix timestamp (Long) and ISO-8601 string formats
 *
 * Fixes deserialization error when o1-mini returns:
 * - "generated_at": "2025-08-28T12:00:00Z" (ISO-8601 string) ❌
 * Instead of expected:
 * - "generated_at": 1730635200 (Unix timestamp number) ✅
 *
 * This adapter accepts BOTH formats and converts them to Unix timestamp (Long)
 */
class FlexibleTimestampAdapter : TypeAdapter<Long>() {

    companion object {
        private const val TAG = "FlexibleTimestampAdapter"

        // ISO-8601 format parser (handles "2025-08-28T12:00:00Z")
        private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        // Alternative ISO format without 'Z' (handles "2025-08-28T12:00:00")
        private val ISO_FORMAT_NO_Z = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    /**
     * Write Long value to JSON (serialization)
     */
    override fun write(out: JsonWriter, value: Long?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value)
        }
    }

    /**
     * Read timestamp from JSON (deserialization)
     * Handles both Unix timestamp (number) and ISO-8601 string
     */
    override fun read(`in`: JsonReader): Long? {
        return when (`in`.peek()) {
            JsonToken.NULL -> {
                `in`.nextNull()
                Timber.tag(TAG).d("Timestamp is null")
                null
            }

            JsonToken.NUMBER -> {
                // Standard case: Unix timestamp as number
                val timestamp = `in`.nextLong()
                Timber.tag(TAG).d("Parsed Unix timestamp: $timestamp")
                timestamp
            }

            JsonToken.STRING -> {
                // o1-mini case: ISO-8601 string format
                val isoString = `in`.nextString()
                Timber.tag(TAG).d("Received ISO-8601 string: $isoString")

                try {
                    // Try parsing with 'Z' suffix first
                    val date = try {
                        ISO_FORMAT.parse(isoString)
                    } catch (e: Exception) {
                        // Try without 'Z' suffix
                        ISO_FORMAT_NO_Z.parse(isoString)
                    }

                    val unixTimestamp = date?.time ?: System.currentTimeMillis()
                    Timber.tag(TAG).d("Converted ISO-8601 to Unix timestamp: $unixTimestamp")
                    unixTimestamp

                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to parse ISO-8601 timestamp: $isoString")
                    // Fallback: use current time
                    val fallbackTime = System.currentTimeMillis()
                    Timber.tag(TAG).w("Using fallback timestamp: $fallbackTime")
                    fallbackTime
                }
            }

            else -> {
                Timber.tag(TAG).w("Unexpected token type: ${`in`.peek()}")
                `in`.skipValue()
                null
            }
        }
    }
}
