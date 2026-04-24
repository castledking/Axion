package axion.client.tool

import axion.client.AxionClientState
import axion.common.operation.ClearRegionOperation
import axion.common.operation.CloneEntitiesOperation
import axion.common.operation.CloneRegionOperation
import axion.common.operation.CompositeOperation
import axion.common.operation.EditOperation
import axion.common.operation.EntityMoveMirrorAxis
import axion.common.operation.FilteredCloneRegionOperation
import axion.common.operation.MoveEntitiesOperation
import axion.common.operation.SymmetryBlockPlacement
import axion.common.operation.SymmetryPlacementOperation
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos

object PlacementCommitService {
    fun toOperation(preview: ClonePreviewState): EditOperation {
        val copyAir = AxionClientState.copyAirEnabled
        val cloneOperation = when {
            preview.transform.isIdentity() &&
                isFullCuboidCapture(preview.sourceRegion, preview.sourceClipboardBuffer) &&
                copyAir &&
                !AxionClientState.keepExistingEnabled &&
                !regionsOverlap(preview.sourceRegion, preview.destinationRegion) -> CloneRegionOperation(
                    sourceRegion = preview.sourceRegion,
                    destinationOrigin = preview.destinationRegion.minCorner(),
                )

            preview.transform.isIdentity() &&
                isFullCuboidCapture(preview.sourceRegion, preview.sourceClipboardBuffer) -> FilteredCloneRegionOperation(
                    sourceRegion = preview.sourceRegion,
                    destinationOrigin = preview.destinationRegion.minCorner(),
                    copyAir = copyAir,
                    keepExisting = AxionClientState.keepExistingEnabled,
                )

            else -> buildClonePlacementOperation(preview)
        }

        return when (preview.mode) {
            PlacementToolMode.CLONE -> {
                if (!AxionClientState.copyEntitiesEnabled || preview.entityUuids.isEmpty()) {
                    cloneOperation
                } else {
                    compositeWithOptionalEntityClone(
                        operations = listOf(cloneOperation),
                        entityCloneOperation = CloneEntitiesOperation(
                            entityUuids = preview.entityUuids,
                            sourceRegion = preview.sourceRegion,
                            destinationOrigin = preview.destinationRegion.minCorner(),
                            rotationQuarterTurns = preview.transform.normalizedRotationQuarterTurns,
                            mirrorAxis = when (preview.transform.mirrorAxis) {
                                PlacementMirrorAxis.NONE -> EntityMoveMirrorAxis.NONE
                                PlacementMirrorAxis.X -> EntityMoveMirrorAxis.X
                                PlacementMirrorAxis.Y -> EntityMoveMirrorAxis.Y
                                PlacementMirrorAxis.Z -> EntityMoveMirrorAxis.Z
                            },
                        ),
                    )
                }
            }
            PlacementToolMode.MOVE -> buildMoveOperation(preview, cloneOperation)
        }
    }

    private fun buildClonePlacementOperation(preview: ClonePreviewState): SymmetryPlacementOperation {
        val sourceRegion = preview.sourceRegion.normalized()
        val keepExisting = AxionClientState.keepExistingEnabled
        val copyAir = AxionClientState.copyAirEnabled
        return SymmetryPlacementOperation(
            preview.destinationClipboardBuffer.cells.mapNotNull { cell ->
                val destinationPos = preview.destinationRegion.minCorner().add(cell.offset).toImmutable()
                if (!copyAir && cell.state.isAir) {
                    null
                } else if (keepExisting && sourceRegion.contains(destinationPos)) {
                    null
                } else {
                    SymmetryBlockPlacement(
                        pos = destinationPos,
                        state = cell.state,
                        blockEntityData = cell.blockEntityData?.copy(),
                    )
                }
            },
        )
    }

    private fun buildMoveOperation(
        preview: ClonePreviewState,
        cloneOperation: EditOperation,
    ): EditOperation {
        val entityMoveOperation = if (AxionClientState.copyEntitiesEnabled) {
            MoveEntitiesOperation(
                sourceRegion = preview.sourceRegion,
                destinationOrigin = preview.destinationRegion.minCorner(),
                rotationQuarterTurns = preview.transform.normalizedRotationQuarterTurns,
                mirrorAxis = when (preview.transform.mirrorAxis) {
                    PlacementMirrorAxis.NONE -> EntityMoveMirrorAxis.NONE
                    PlacementMirrorAxis.X -> EntityMoveMirrorAxis.X
                    PlacementMirrorAxis.Y -> EntityMoveMirrorAxis.Y
                    PlacementMirrorAxis.Z -> EntityMoveMirrorAxis.Z
                },
            )
        } else {
            null
        }

        if (preview.transform.isIdentity() &&
            !regionsOverlap(preview.sourceRegion, preview.destinationRegion) &&
            isFullCuboidCapture(preview.sourceRegion, preview.sourceClipboardBuffer)
        ) {
            return compositeWithOptionalEntityMove(
                operations = listOf(
                    cloneOperation,
                    ClearRegionOperation(preview.sourceRegion),
                ),
                entityMoveOperation = entityMoveOperation,
            )
        }

        val destinationPlacements = preview.destinationClipboardBuffer.cells.mapNotNull { cell ->
            if (!AxionClientState.copyAirEnabled && cell.state.isAir) {
                null
            } else {
                SymmetryBlockPlacement(
                    pos = preview.destinationRegion.minCorner().add(cell.offset),
                    state = cell.state,
                    blockEntityData = cell.blockEntityData?.copy(),
                )
            }
        }
        val destinationPositions = destinationPlacements.mapTo(linkedSetOf()) { it.pos.toImmutable() }
        val sourcePositions = preview.sourceClipboardBuffer.cells.mapTo(linkedSetOf()) { cell ->
            preview.sourceRegion.minCorner().add(cell.offset).toImmutable()
        }
        val sourceOnlyAirPlacements = buildList {
            for (immutablePos in sourcePositions) {
                if (immutablePos !in destinationPositions) {
                    add(
                        SymmetryBlockPlacement(
                            pos = immutablePos,
                            state = Blocks.AIR.defaultState,
                            blockEntityData = null,
                        ),
                    )
                }
            }
        }

        return compositeWithOptionalEntityMove(
            operations = listOf(
                SymmetryPlacementOperation(
                    placements = sourceOnlyAirPlacements + destinationPlacements,
                ),
            ),
            entityMoveOperation = entityMoveOperation,
        )
    }

    private fun compositeWithOptionalEntityMove(
        operations: List<EditOperation>,
        entityMoveOperation: MoveEntitiesOperation?,
    ): EditOperation {
        val flattened = if (entityMoveOperation == null) {
            operations
        } else {
            operations + entityMoveOperation
        }
        return when (flattened.size) {
            0 -> SymmetryPlacementOperation(emptyList())
            1 -> flattened.first()
            else -> CompositeOperation(flattened)
        }
    }

    private fun compositeWithOptionalEntityClone(
        operations: List<EditOperation>,
        entityCloneOperation: CloneEntitiesOperation?,
    ): EditOperation {
        val flattened = if (entityCloneOperation == null) operations else operations + entityCloneOperation
        return when (flattened.size) {
            0 -> SymmetryPlacementOperation(emptyList())
            1 -> flattened.first()
            else -> CompositeOperation(flattened)
        }
    }

    private fun regionsOverlap(a: axion.common.model.BlockRegion, b: axion.common.model.BlockRegion): Boolean {
        val left = a.normalized()
        val right = b.normalized()
        return left.minCorner().x <= right.maxCorner().x &&
            left.maxCorner().x >= right.minCorner().x &&
            left.minCorner().y <= right.maxCorner().y &&
            left.maxCorner().y >= right.minCorner().y &&
            left.minCorner().z <= right.maxCorner().z &&
            left.maxCorner().z >= right.minCorner().z
    }

    private fun isFullCuboidCapture(
        region: axion.common.model.BlockRegion,
        clipboardBuffer: axion.common.model.ClipboardBuffer,
    ): Boolean {
        val size = region.normalized().size()
        return (size.x * size.y * size.z) == clipboardBuffer.cells.size
    }
}
