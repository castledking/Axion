package axion.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

enum class AxionCompressionMode {
    NONE,
    GZIP,
}

object AxionTransportCodec {
    const val COMPRESSION_THRESHOLD_BYTES: Int = 32 * 1024
    const val MAX_CHUNK_BYTES: Int = 24 * 1024
    const val MAX_SERIALIZED_BYTES: Int = 2 * 1024 * 1024
    const val MAX_CHUNKS: Int = 128

    fun encodeServerMessage(message: AxionServerMessage, transferId: Long): List<ByteArray> {
        val rawPayload = AxionProtocolCodec.encodeServerMessage(message)
        require(rawPayload.size <= MAX_SERIALIZED_BYTES) {
            "Serialized Axion message exceeded maximum size"
        }

        val compression = if (rawPayload.size >= COMPRESSION_THRESHOLD_BYTES) {
            AxionCompressionMode.GZIP
        } else {
            AxionCompressionMode.NONE
        }
        val encodedPayload = applyCompression(rawPayload, compression)
        if (encodedPayload.size <= MAX_CHUNK_BYTES) {
            return listOf(encodeSingleFrame(compression, encodedPayload))
        }

        val chunkCount = ((encodedPayload.size + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES)
        require(chunkCount <= MAX_CHUNKS) {
            "Axion message required too many chunks"
        }

        return List(chunkCount) { chunkIndex ->
            val start = chunkIndex * MAX_CHUNK_BYTES
            val end = minOf(encodedPayload.size, start + MAX_CHUNK_BYTES)
            encodeChunkFrame(
                transferId = transferId,
                compression = compression,
                chunkCount = chunkCount,
                chunkIndex = chunkIndex,
                payload = encodedPayload.copyOfRange(start, end),
            )
        }
    }

    fun decodeServerFrame(bytes: ByteArray): DecodedServerFrame {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            return when (input.readUnsignedByte()) {
                SINGLE_FRAME -> {
                    val compression = AxionCompressionMode.entries[input.readUnsignedByte()]
                    val payload = input.readNBytes(input.readInt())
                    DecodedServerFrame.Complete(
                        decodeServerPayload(payload, compression),
                    )
                }

                CHUNK_FRAME -> {
                    DecodedServerFrame.Chunk(
                        transferId = input.readLong(),
                        compression = AxionCompressionMode.entries[input.readUnsignedByte()],
                        chunkCount = input.readInt(),
                        chunkIndex = input.readInt(),
                        payload = input.readNBytes(input.readInt()),
                    )
                }

                else -> error("Unknown Axion transport frame")
            }
        }
    }

    fun decodeServerPayload(payload: ByteArray, compression: AxionCompressionMode): AxionServerMessage {
        return AxionProtocolCodec.decodeServerMessage(removeCompression(payload, compression))
    }

    fun removeCompression(payload: ByteArray, compression: AxionCompressionMode): ByteArray {
        return when (compression) {
            AxionCompressionMode.NONE -> payload
            AxionCompressionMode.GZIP -> GZIPInputStream(ByteArrayInputStream(payload)).use { it.readBytes() }
        }
    }

    private fun applyCompression(payload: ByteArray, compression: AxionCompressionMode): ByteArray {
        return when (compression) {
            AxionCompressionMode.NONE -> payload
            AxionCompressionMode.GZIP -> {
                val output = ByteArrayOutputStream()
                GZIPOutputStream(output).use { it.write(payload) }
                output.toByteArray()
            }
        }
    }

    private fun encodeSingleFrame(
        compression: AxionCompressionMode,
        payload: ByteArray,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeByte(SINGLE_FRAME)
            data.writeByte(compression.ordinal)
            data.writeInt(payload.size)
            data.write(payload)
        }
        return output.toByteArray()
    }

    private fun encodeChunkFrame(
        transferId: Long,
        compression: AxionCompressionMode,
        chunkCount: Int,
        chunkIndex: Int,
        payload: ByteArray,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeByte(CHUNK_FRAME)
            data.writeLong(transferId)
            data.writeByte(compression.ordinal)
            data.writeInt(chunkCount)
            data.writeInt(chunkIndex)
            data.writeInt(payload.size)
            data.write(payload)
        }
        return output.toByteArray()
    }

    sealed interface DecodedServerFrame {
        data class Complete(
            val message: AxionServerMessage,
        ) : DecodedServerFrame

        data class Chunk(
            val transferId: Long,
            val compression: AxionCompressionMode,
            val chunkCount: Int,
            val chunkIndex: Int,
            val payload: ByteArray,
        ) : DecodedServerFrame
    }

    private const val SINGLE_FRAME: Int = 1
    private const val CHUNK_FRAME: Int = 2
}
