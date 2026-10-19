package com.rossomak.morpheus.samples.capture

/** A failure the tool reports by its message alone, then exits non-zero. */
class CaptureException(message: String, cause: Throwable? = null) : Exception(message, cause)
