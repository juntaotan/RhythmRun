package com.compx551.rhythmrun.processing.processor

import com.compx551.rhythmrun.processing.model.ProcessingRequest

abstract class ProcessingHandler {

    private var next: ProcessingHandler? = null

    fun setNext(handler: ProcessingHandler): ProcessingHandler {
        next = handler
        return handler
    }

    suspend fun handle(request: ProcessingRequest) {
        if (processSuspending(request)) {
            next?.handle(request)
        }
    }

    /** Return true to continue the chain; false to stop it. */
    protected open fun process(request: ProcessingRequest): Boolean = true

    /** Persistence can perform a suspending write. */
    protected open suspend fun processSuspending(request: ProcessingRequest): Boolean = process(request)
}
