package axion.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

object AxionProtocolCodec {
    fun encodeClientMessage(message: AxionClientMessage): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            when (message) {
                is ClientHello -> {
                    data.writeByte(1)
                    data.writeInt(message.protocolVersion)
                    data.writeUTF(message.clientVersion)
                }

                is OperationBatchRequest -> {
                    data.writeByte(2)
                    data.writeLong(message.requestId)
                    data.writeInt(message.operations.size)
                    message.operations.forEach { writeOperation(data, it) }
                    data.writeBoolean(message.usesSymmetry)
                    data.writeBoolean(message.recordHistory)
                }

                is UndoRequest -> {
                    data.writeByte(3)
                    data.writeLong(message.requestId)
                    data.writeLong(message.transactionId)
                }

                is RedoRequest -> {
                    data.writeByte(4)
                    data.writeLong(message.requestId)
                    data.writeLong(message.transactionId)
                }

                is NoClipStateRequest -> {
                    data.writeByte(5)
                    data.writeBoolean(message.armed)
                }
            }
        }
        return output.toByteArray()
    }

    fun decodeClientMessage(bytes: ByteArray): AxionClientMessage {
        decodeClientMessageOrNull(bytes)?.let { return it }
        unwrapLengthPrefixed(bytes)?.let { unwrapped ->
            decodeClientMessageOrNull(unwrapped)?.let { return it }
        }
        unwrapIntLengthPrefixed(bytes)?.let { unwrapped ->
            decodeClientMessageOrNull(unwrapped)?.let { return it }
        }
        error("Unknown Axion client message")
    }

    private fun decodeClientMessageOrNull(bytes: ByteArray): AxionClientMessage? {
        DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            return when (data.readUnsignedByte()) {
                1 -> ClientHello(
                    protocolVersion = data.readInt(),
                    clientVersion = data.readUTF(),
                )

                2 -> OperationBatchRequest(
                    requestId = data.readLong(),
                    operations = List(data.readInt()) { readOperation(data) },
                    usesSymmetry = data.readBoolean(),
                    recordHistory = data.readBoolean(),
                )

                3 -> UndoRequest(
                    requestId = data.readLong(),
                    transactionId = data.readLong(),
                )

                4 -> RedoRequest(
                    requestId = data.readLong(),
                    transactionId = data.readLong(),
                )

                5 -> NoClipStateRequest(
                    armed = data.readBoolean(),
                )

                else -> null
            }
        }
    }

    fun encodeServerMessage(message: AxionServerMessage): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            when (message) {
                is ServerHello -> {
                    data.writeByte(1)
                    data.writeInt(message.protocolVersion)
                    data.writeInt(message.supportedOperations.size)
                    message.supportedOperations.forEach { data.writeUTF(it.name) }
                }

                is OperationBatchResult -> {
                    data.writeByte(2)
                    data.writeLong(message.requestId)
                    data.writeBoolean(message.accepted)
                    data.writeUTF(message.message)
                    data.writeInt(message.changedBlockCount)
                    data.writeUTF(message.code.name)
                    data.writeUTF(message.source.name)
                    data.writeBoolean(message.blockedPosition != null)
                    if (message.blockedPosition != null) {
                        writeVector(data, message.blockedPosition)
                    }
                    data.writeBoolean(message.transactionId != null)
                    if (message.transactionId != null) {
                        data.writeLong(message.transactionId)
                    }
                    data.writeBoolean(message.actionLabel != null)
                    if (message.actionLabel != null) {
                        data.writeUTF(message.actionLabel)
                    }
                    data.writeInt(message.changes.size)
                    message.changes.forEach { change ->
                        writeVector(data, change.pos)
                        data.writeUTF(change.oldState)
                        data.writeUTF(change.newState)
                        data.writeBoolean(change.oldBlockEntityData != null)
                        if (change.oldBlockEntityData != null) {
                            data.writeUTF(change.oldBlockEntityData)
                        }
                        data.writeBoolean(change.newBlockEntityData != null)
                        if (change.newBlockEntityData != null) {
                            data.writeUTF(change.newBlockEntityData)
                        }
                    }
                }
            }
        }
        return output.toByteArray()
    }

    fun decodeServerMessage(bytes: ByteArray): AxionServerMessage {
        DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            return when (data.readUnsignedByte()) {
                1 -> ServerHello(
                    protocolVersion = data.readInt(),
                    supportedOperations = buildSet {
                        repeat(data.readInt()) {
                            add(AxionOperationType.valueOf(data.readUTF()))
                        }
                    },
                )

                2 -> OperationBatchResult(
                    requestId = data.readLong(),
                    accepted = data.readBoolean(),
                    message = data.readUTF(),
                    changedBlockCount = data.readInt(),
                    code = AxionResultCode.valueOf(data.readUTF()),
                    source = AxionResultSource.valueOf(data.readUTF()),
                    blockedPosition = if (data.readBoolean()) readVector(data) else null,
                    transactionId = data.readNullableLong(),
                    actionLabel = data.readNullableUtf(),
                    changes = List(data.readInt()) {
                        CommittedBlockChangePayload(
                            pos = readVector(data),
                            oldState = data.readUTF(),
                            newState = data.readUTF(),
                            oldBlockEntityData = data.readNullableUtf(),
                            newBlockEntityData = data.readNullableUtf(),
                        )
                    },
                )

                else -> error("Unknown Axion server message")
            }
        }
    }

    private fun writeOperation(output: DataOutputStream, operation: AxionRemoteOperation) {
        output.writeUTF(operation.type.name)
        when (operation) {
            is ClearRegionRequest -> {
                writeVector(output, operation.min)
                writeVector(output, operation.max)
            }

            is CloneRegionRequest -> {
                writeVector(output, operation.sourceMin)
                writeVector(output, operation.sourceMax)
                writeVector(output, operation.destinationOrigin)
            }

            is FilteredCloneRegionRequest -> {
                writeVector(output, operation.sourceMin)
                writeVector(output, operation.sourceMax)
                writeVector(output, operation.destinationOrigin)
                output.writeBoolean(operation.copyAir)
                output.writeBoolean(operation.keepExisting)
            }

            is CloneEntitiesRequest -> {
                writeVector(output, operation.sourceMin)
                writeVector(output, operation.sourceMax)
                writeVector(output, operation.destinationOrigin)
                output.writeInt(operation.rotationQuarterTurns)
                output.writeUTF(operation.mirrorAxis.name)
            }

            is DeleteEntitiesRequest -> {
                writeVector(output, operation.sourceMin)
                writeVector(output, operation.sourceMax)
            }

            is MoveEntitiesRequest -> {
                writeVector(output, operation.sourceMin)
                writeVector(output, operation.sourceMax)
                writeVector(output, operation.destinationOrigin)
                output.writeInt(operation.rotationQuarterTurns)
                output.writeUTF(operation.mirrorAxis.name)
            }

            is StackRegionRequest -> {
                writeVector(output, operation.sourceOrigin)
                writeVector(output, operation.clipboardSize)
                writeClipboardCells(output, operation.cells)
                writeVector(output, operation.step)
                output.writeInt(operation.repeatCount)
            }

            is SmearRegionRequest -> {
                writeVector(output, operation.sourceOrigin)
                writeVector(output, operation.clipboardSize)
                writeClipboardCells(output, operation.cells)
                writeVector(output, operation.step)
                output.writeInt(operation.repeatCount)
            }

            is ExtrudeRequest -> {
                writeVector(output, operation.origin)
                writeVector(output, operation.direction)
                output.writeUTF(operation.expectedState)
                output.writeUTF(operation.mode.name)
                output.writeBoolean(operation.symmetry != null)
                if (operation.symmetry != null) {
                    writeDoubleVector(output, operation.symmetry.anchor)
                    output.writeBoolean(operation.symmetry.rotationalEnabled)
                    output.writeBoolean(operation.symmetry.mirrorEnabled)
                    output.writeUTF(operation.symmetry.mirrorAxis.name)
                }
            }

            is PlaceBlocksRequest -> {
                output.writeInt(operation.placements.size)
                operation.placements.forEach { placement ->
                    writeVector(output, placement.pos)
                    output.writeUTF(placement.blockState)
                    output.writeBoolean(placement.blockEntityData != null)
                    if (placement.blockEntityData != null) {
                        output.writeUTF(placement.blockEntityData)
                    }
                }
            }
        }
    }

    private fun readOperation(input: DataInputStream): AxionRemoteOperation {
        return when (AxionOperationType.valueOf(input.readUTF())) {
            AxionOperationType.CLEAR_REGION -> ClearRegionRequest(
                min = readVector(input),
                max = readVector(input),
            )

            AxionOperationType.CLONE_REGION -> CloneRegionRequest(
                sourceMin = readVector(input),
                sourceMax = readVector(input),
                destinationOrigin = readVector(input),
            )

            AxionOperationType.FILTERED_CLONE_REGION -> FilteredCloneRegionRequest(
                sourceMin = readVector(input),
                sourceMax = readVector(input),
                destinationOrigin = readVector(input),
                copyAir = input.readBoolean(),
                keepExisting = input.readBoolean(),
            )

            AxionOperationType.CLONE_ENTITIES -> CloneEntitiesRequest(
                sourceMin = readVector(input),
                sourceMax = readVector(input),
                destinationOrigin = readVector(input),
                rotationQuarterTurns = input.readInt(),
                mirrorAxis = PlacementMirrorAxisPayload.valueOf(input.readUTF()),
            )

            AxionOperationType.DELETE_ENTITIES -> DeleteEntitiesRequest(
                sourceMin = readVector(input),
                sourceMax = readVector(input),
            )

            AxionOperationType.MOVE_ENTITIES -> MoveEntitiesRequest(
                sourceMin = readVector(input),
                sourceMax = readVector(input),
                destinationOrigin = readVector(input),
                rotationQuarterTurns = input.readInt(),
                mirrorAxis = PlacementMirrorAxisPayload.valueOf(input.readUTF()),
            )

            AxionOperationType.STACK_REGION -> StackRegionRequest(
                sourceOrigin = readVector(input),
                clipboardSize = readVector(input),
                cells = readClipboardCells(input),
                step = readVector(input),
                repeatCount = input.readInt(),
            )

            AxionOperationType.SMEAR_REGION -> SmearRegionRequest(
                sourceOrigin = readVector(input),
                clipboardSize = readVector(input),
                cells = readClipboardCells(input),
                step = readVector(input),
                repeatCount = input.readInt(),
            )

            AxionOperationType.EXTRUDE -> ExtrudeRequest(
                origin = readVector(input),
                direction = readVector(input),
                expectedState = input.readUTF(),
                mode = AxionExtrudeMode.valueOf(input.readUTF()),
                symmetry = if (input.readBoolean()) {
                    SymmetryConfigPayload(
                        anchor = readDoubleVector(input),
                        rotationalEnabled = input.readBoolean(),
                        mirrorEnabled = input.readBoolean(),
                        mirrorAxis = SymmetryMirrorAxisPayload.valueOf(input.readUTF()),
                    )
                } else {
                    null
                },
            )

            AxionOperationType.PLACE_BLOCKS -> PlaceBlocksRequest(
                placements = List(input.readInt()) {
                    PlacedBlockPayload(
                        pos = readVector(input),
                        blockState = input.readUTF(),
                        blockEntityData = input.readNullableUtf(),
                    )
                },
            )
        }
    }

    private fun unwrapLengthPrefixed(bytes: ByteArray): ByteArray? {
        val decoded = readVarInt(bytes) ?: return null
        if (decoded.value != bytes.size - decoded.bytesRead) {
            return null
        }
        return bytes.copyOfRange(decoded.bytesRead, bytes.size)
    }

    private fun unwrapIntLengthPrefixed(bytes: ByteArray): ByteArray? {
        if (bytes.size < Int.SIZE_BYTES) {
            return null
        }

        val declaredSize =
            ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)
        if (declaredSize != bytes.size - Int.SIZE_BYTES) {
            return null
        }

        return bytes.copyOfRange(Int.SIZE_BYTES, bytes.size)
    }

    private fun readVarInt(bytes: ByteArray): DecodedVarInt? {
        var value = 0
        var position = 0
        while (position < bytes.size && position < MAX_VARINT_BYTES) {
            val current = bytes[position].toInt() and 0xFF
            value = value or ((current and SEGMENT_BITS) shl (position * 7))
            position += 1
            if ((current and CONTINUE_BIT) == 0) {
                return DecodedVarInt(value, position)
            }
        }
        return null
    }

    private data class DecodedVarInt(
        val value: Int,
        val bytesRead: Int,
    )

    private const val SEGMENT_BITS: Int = 0x7F
    private const val CONTINUE_BIT: Int = 0x80
    private const val MAX_VARINT_BYTES: Int = 5

    private fun writeClipboardCells(output: DataOutputStream, cells: List<ClipboardCellPayload>) {
        output.writeInt(cells.size)
        cells.forEach { cell ->
            writeVector(output, cell.offset)
            output.writeUTF(cell.blockState)
            output.writeBoolean(cell.blockEntityData != null)
            if (cell.blockEntityData != null) {
                output.writeUTF(cell.blockEntityData)
            }
        }
    }

    private fun readClipboardCells(input: DataInputStream): List<ClipboardCellPayload> {
        return List(input.readInt()) {
            ClipboardCellPayload(
                offset = readVector(input),
                blockState = input.readUTF(),
                blockEntityData = input.readNullableUtf(),
            )
        }
    }

    private fun writeVector(output: DataOutputStream, vector: IntVector3) {
        output.writeInt(vector.x)
        output.writeInt(vector.y)
        output.writeInt(vector.z)
    }

    private fun readVector(input: DataInputStream): IntVector3 {
        return IntVector3(
            x = input.readInt(),
            y = input.readInt(),
            z = input.readInt(),
        )
    }

    private fun writeDoubleVector(output: DataOutputStream, vector: DoubleVector3) {
        output.writeDouble(vector.x)
        output.writeDouble(vector.y)
        output.writeDouble(vector.z)
    }

    private fun readDoubleVector(input: DataInputStream): DoubleVector3 {
        return DoubleVector3(
            x = input.readDouble(),
            y = input.readDouble(),
            z = input.readDouble(),
        )
    }

    private fun DataInputStream.readNullableUtf(): String? {
        return if (readBoolean()) {
            readUTF()
        } else {
            null
        }
    }

    private fun DataInputStream.readNullableLong(): Long? {
        return if (readBoolean()) {
            readLong()
        } else {
            null
        }
    }
}
