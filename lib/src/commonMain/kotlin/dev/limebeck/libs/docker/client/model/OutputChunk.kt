package dev.limebeck.libs.docker.client.model

/** A binary output fragment. Boundaries need not coincide with lines or UTF-8 characters. */
class OutputChunk(val type: LogLine.Type, val bytes: ByteArray)
