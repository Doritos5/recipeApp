package com.example.recipeapp.ui.util

import android.content.res.ColorStateList
import android.graphics.Color
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

object TagChipUtils {

    fun splitInputTags(rawInput: String): List<String> {
        return rawInput
            .split(",")
            .mapNotNull { normalizeTag(it).takeIf(String::isNotEmpty) }
    }

    fun normalizeTag(rawTag: String): String {
        return rawTag.trim().removePrefix("#").trim()
    }

    fun addTagIfValid(
        rawTag: String,
        tags: MutableList<String>,
        chipGroup: ChipGroup,
        onDuplicate: (() -> Unit)? = null,
        onInvalid: (() -> Unit)? = null
    ): Boolean {
        val cleanTag = normalizeTag(rawTag)

        if (cleanTag.isEmpty()) {
            onInvalid?.invoke()
            return false
        }

        if (tags.any { it.equals(cleanTag, ignoreCase = true) }) {
            onDuplicate?.invoke()
            return false
        }

        tags.add(cleanTag)
        chipGroup.addView(createStyledChip(chipGroup, cleanTag) {
            tags.removeAll { it.equals(cleanTag, ignoreCase = true) }
        })
        return true
    }

    fun renderTags(tags: MutableList<String>, chipGroup: ChipGroup, sourceTags: List<String>) {
        tags.clear()
        chipGroup.removeAllViews()

        sourceTags.forEach { tag ->
            addTagIfValid(tag, tags, chipGroup)
        }
    }

    private fun createStyledChip(
        chipGroup: ChipGroup,
        tag: String,
        onRemove: () -> Unit
    ): Chip {
        return Chip(chipGroup.context).apply {
            text = "#$tag"
            isClickable = false
            isCheckable = false
            isCloseIconVisible = true

            setTextColor(Color.WHITE)
            closeIconTint = ColorStateList.valueOf(Color.WHITE)
            chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#EE7DA8"))

            chipCornerRadius = 18f
            chipStrokeWidth = 0f
            chipMinHeight = 36f

            setEnsureMinTouchTargetSize(false)
            rippleColor = null

            setOnCloseIconClickListener {
                onRemove()
                chipGroup.removeView(this)
            }
        }
    }
}

