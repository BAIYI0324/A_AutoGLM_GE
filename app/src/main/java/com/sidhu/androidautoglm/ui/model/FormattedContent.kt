package com.sidhu.androidautoglm.ui.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.sidhu.androidautoglm.action.ActionType

/**
 * Sealed class hierarchy for formatted message content.
 * Enables type-safe representation of different content structures in the UI.
 */
sealed class FormattedContent {
    /**
     * Plain text content (e.g., thinking part of assistant response, user messages)
     */
    data class TextContent(val text: String) : FormattedContent()

    /**
     * Action content with type, description, icon, and optional details
     */
    data class ActionContent(
        val actionType: ActionType,
        val description: String,
        val icon: ImageVector? = null,
        val details: Map<String, String>? = null
    ) : FormattedContent()

    /**
     * Mixed content with text and optional action (for assistant messages)
     */
    data class MixedContent(
        val text: String,
        val action: ActionContent? = null
    ) : FormattedContent()
}
