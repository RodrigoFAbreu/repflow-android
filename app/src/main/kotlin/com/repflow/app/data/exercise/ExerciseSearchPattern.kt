package com.repflow.app.data.exercise

/**
 * Builds a full, already-escaped SQLite `LIKE` pattern for a substring
 * search against `name_key`, so search input is never interpolated into SQL
 * (see plan.md additional implementation correction 5).
 *
 * Escape order is significant (correction 6): backslash first (so
 * already-escaped characters are not double-escaped), then `%`, then `_`.
 *
 * An empty [normalizedQuery] yields `"%%"`, which matches every row - the
 * "no query filter" case (see plan.md section B).
 */
fun buildNameSearchPattern(normalizedQuery: String): String {
    val escaped =
        normalizedQuery
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    return "%$escaped%"
}
