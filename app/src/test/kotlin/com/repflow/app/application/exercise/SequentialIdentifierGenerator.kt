package com.repflow.app.application.exercise

import com.repflow.app.application.common.IdentifierGenerator

/** Produces predictable, sequential ids (`"<prefix>-1"`, `"<prefix>-2"`, ...) for tests. */
class SequentialIdentifierGenerator(
    private val prefix: String = "exercise",
) : IdentifierGenerator {
    private var counter = 0

    override fun newId(): String {
        counter += 1
        return "$prefix-$counter"
    }
}
