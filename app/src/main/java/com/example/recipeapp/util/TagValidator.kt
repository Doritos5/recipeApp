package com.example.recipeapp.util

/**
 * Validator for recipe tags with custom rules:
 * - Only alphanumeric and # character allowed
 * - Max 25 characters per tag
 * - Max 10 tags per recipe
 * - No spaces or special characters (except #)
 */
object TagValidator {
    private const val MAX_TAG_LENGTH = 25
    private const val MAX_TAGS_PER_RECIPE = 10
    // Pattern: # followed by alphanumeric (e.g., #Italian, #Pizza, #QuickRecipe)
    private const val TAG_PATTERN = "^#?[a-zA-Z0-9]+$"

    /**
     * Validates a single tag
     */
    fun isValidTag(tag: String): Boolean {
        if (tag.length > MAX_TAG_LENGTH) return false
        if (!tag.matches(TAG_PATTERN.toRegex())) return false
        return tag.isNotEmpty()
    }

    /**
     * Sanitizes and validates comma-separated tags input
     * Returns only valid tags, limited to MAX_TAGS_PER_RECIPE
     */
    fun sanitizeTags(tagsInput: String): List<String> {
        if (tagsInput.isBlank()) return emptyList()

        return tagsInput.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { tag ->
                // Add # prefix if not present
                if (tag.startsWith("#")) tag else "#$tag"
            }
            .filter { isValidTag(it) }
            .take(MAX_TAGS_PER_RECIPE)
            .distinct()  // Remove duplicates
    }

    /**
     * Formats tags for display (already have # prefix)
     */
    fun formatTagForDisplay(tag: String): String {
        return if (tag.startsWith("#")) tag else "#$tag"
    }
}

