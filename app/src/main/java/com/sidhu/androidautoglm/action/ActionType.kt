package com.sidhu.androidautoglm.action

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Api
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Enumeration of all supported AutoGLM action types with associated metadata.
 *
 * This enum provides a single source of truth for action type information,
 * including optional UI icons. String resource mappings are handled
 * internally by ActionDescriber to decouple domain types from UI resources.
 *
 * @property icon Optional Compose ImageVector for UI icons
 */
enum class ActionType(
    val icon: ImageVector? = null
) {
    TAP(Icons.Filled.TouchApp),
    DOUBLE_TAP(Icons.Filled.Gesture),
    LONG_PRESS(Icons.Filled.Gesture),
    SWIPE(Icons.Filled.Swipe),
    TYPE(Icons.Filled.Keyboard),
    TYPE_NAME(Icons.Filled.Keyboard),
    LAUNCH(Icons.AutoMirrored.Filled.ExitToApp),
    BACK(Icons.AutoMirrored.Filled.KeyboardArrowLeft),
    HOME(Icons.Filled.Home),
    WAIT(Icons.Filled.AccessTime),
    FINISH(Icons.Filled.Check),
    TAKE_OVER(Icons.Filled.Phone),
    INTERACT(Icons.Filled.QuestionMark),
    NOTE(Icons.Filled.ContentCopy),
    CALL_API(Icons.Filled.Api),
    UNKNOWN(Icons.Default.QuestionMark);

    companion object {
        /**
         * Maps action type strings from model responses to ActionType enum values.
         * Handles case-insensitive matching and provides fallback to UNKNOWN.
         */
        private val TYPE_MAP = entries.associateBy { it.name.lowercase() }

        /**
         * Converts a string action type to an ActionType enum value.
         * Handles various formats: "Tap", "tap", "double tap", "DoubleTap", etc.
         *
         * @param action The action type string (case-insensitive, spaces/hyphens/underscores are ignored)
         * @return The corresponding ActionType, or UNKNOWN if not found
         */
        fun fromString(action: String): ActionType {
            if (action.isBlank()) return UNKNOWN

            // Normalize: lowercase and remove spaces, underscores, hyphens
            val normalized = action.trim()
                .lowercase()
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")

            return TYPE_MAP[normalized] ?: UNKNOWN
        }
    }
}

/**
 * Extension function to convert String to ActionType.
 * Provides a convenient way to parse action type strings.
 *
 * @return The corresponding ActionType, or UNKNOWN if not found
 */
fun String.toActionType(): ActionType = ActionType.fromString(this)
