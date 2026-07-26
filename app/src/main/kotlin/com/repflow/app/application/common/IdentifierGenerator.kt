package com.repflow.app.application.common

/**
 * A testable indirection over unique identifier generation (a random UUID
 * string in production), so tests can supply deterministic ids.
 */
fun interface IdentifierGenerator {
    fun newId(): String
}
