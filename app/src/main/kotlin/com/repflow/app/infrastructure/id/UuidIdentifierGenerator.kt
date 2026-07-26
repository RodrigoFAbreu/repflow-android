package com.repflow.app.infrastructure.id

import com.repflow.app.application.common.IdentifierGenerator
import java.util.UUID
import javax.inject.Inject

/** The production [IdentifierGenerator], backed by random UUIDs. */
class UuidIdentifierGenerator
    @Inject
    constructor() : IdentifierGenerator {
        override fun newId(): String = UUID.randomUUID().toString()
    }
