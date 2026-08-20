package net.minecraft.client.render

typealias BufferBuilder = com.mojang.blaze3d.vertex.BufferBuilder
typealias BuiltBuffer = com.mojang.blaze3d.vertex.MeshData
typealias Camera = net.minecraft.client.Camera
typealias Frustum = net.minecraft.client.renderer.culling.Frustum
typealias LightmapTextureManager = net.minecraft.client.renderer.Lightmap
typealias OverlayTexture = net.minecraft.client.renderer.texture.OverlayTexture
typealias RenderLayer = net.minecraft.client.renderer.rendertype.RenderType
typealias RenderLayers = net.minecraft.client.renderer.rendertype.RenderTypes
typealias RenderTickCounter = net.minecraft.client.DeltaTracker
typealias VertexConsumer = com.mojang.blaze3d.vertex.VertexConsumer
// 26.2 deleted MultiBufferSource outright; axion.client.render.ImmediateCompat
// stands in for it on top of SubmitNodeCollector.
typealias VertexConsumerProvider = axion.client.render.AxionSubmitBufferSource
typealias WorldRenderer = net.minecraft.client.renderer.LevelRenderer
typealias LightingProvider = net.minecraft.world.level.lighting.LevelLightEngine
typealias DrawMode = com.mojang.blaze3d.PrimitiveTopology
typealias DrawParameters = com.mojang.blaze3d.vertex.MeshData.DrawState
typealias BlockRenderManager = net.minecraft.client.renderer.block.ModelBlockRenderer
typealias ClientWorld = net.minecraft.client.multiplayer.ClientLevel
typealias BlockModel = net.minecraft.client.renderer.block.dispatch.BlockStateModel
typealias Immediate = axion.client.render.AxionSubmitBufferSource
// ShapeRenderer was deleted in 26.2; outlines go through
// SubmitNodeCollector.submitShapeOutline, bridged in axion.client.render.CompatExtensions.
typealias VertexRendering = axion.client.render.VertexRendering
