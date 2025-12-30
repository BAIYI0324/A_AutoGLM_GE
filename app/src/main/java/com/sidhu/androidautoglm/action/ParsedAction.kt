package com.sidhu.androidautoglm.action

/**
 * Represents a parsed action with all extracted parameters.
 *
 * This data model serves as an intermediate between parsing and formatting layers,
 * capturing all information from the action string without execution concerns.
 *
 * @property type The action type enum
 * @property rawParams Original string values from the action (e.g., element="[500, 750]")
 * @property normalizedParams Converted values (e.g., element=[500.0, 750.0])
 */
data class ParsedAction(
    val type: ActionType,
    val rawParams: Map<String, String> = emptyMap(),
    val normalizedParams: Map<String, Any> = emptyMap()
) {
    /**
     * Gets a raw string parameter by key.
     *
     * @param key The parameter name
     * @return The parameter value, or null if not found
     */
    fun getParam(key: String): String? = rawParams[key]

    /**
     * Checks if a parameter exists.
     *
     * @param key The parameter name
     * @return true if the parameter exists in either raw or normalized params
     */
    fun hasParam(key: String): Boolean = rawParams.containsKey(key) || normalizedParams.containsKey(key)
}
