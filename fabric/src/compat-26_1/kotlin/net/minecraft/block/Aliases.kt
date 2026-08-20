package net.minecraft.block

typealias Block = net.minecraft.world.level.block.Block
typealias BlockState = net.minecraft.world.level.block.state.BlockState
typealias Blocks = net.minecraft.world.level.block.Blocks
typealias FluidBlock = net.minecraft.world.level.block.LiquidBlock
typealias ShapeContext = net.minecraft.world.phys.shapes.CollisionContext
typealias BlockEntityProvider = net.minecraft.world.level.block.EntityBlock
typealias BlockRenderType = net.minecraft.world.level.block.RenderShape

// Phantom-block mixin targets — several block classes were renamed in 26.x
// while the common mixin sources still reference the old class names:
//   AbstractPressurePlateBlock → BasePressurePlateBlock
//   CobwebBlock → WebBlock
//   RedstoneOreBlock → RedStoneOreBlock
//   TripwireBlock → TripWireBlock
typealias AbstractPressurePlateBlock = net.minecraft.world.level.block.BasePressurePlateBlock
typealias BigDripleafBlock = net.minecraft.world.level.block.BigDripleafBlock
typealias CobwebBlock = net.minecraft.world.level.block.WebBlock
typealias RedstoneOreBlock = net.minecraft.world.level.block.RedStoneOreBlock
typealias SculkSensorBlock = net.minecraft.world.level.block.SculkSensorBlock
// Mirroring corner stairs needs to recognise the block: StairsBlock -> StairBlock.
typealias StairsBlock = net.minecraft.world.level.block.StairBlock
typealias TripwireBlock = net.minecraft.world.level.block.TripWireBlock
