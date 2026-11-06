package com.rossomak.morpheus.samples.capture

/** Reported by its message alone; the tool then exits non-zero. */
class CaptureException(message: String, cause: Throwable? = null) : Exception(message, cause)
