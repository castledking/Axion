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
typealias VertexConsumerProvider = net.minecraft.client.renderer.MultiBufferSource
typealias WorldRenderer = net.minecraft.client.renderer.LevelRenderer
typealias LightingProvider = net.minecraft.world.level.lighting.LevelLightEngine
typealias DrawMode = com.mojang.blaze3d.vertex.VertexFormat.Mode
typealias DrawParameters = com.mojang.blaze3d.vertex.MeshData.DrawState
typealias BlockRenderManager = net.minecraft.client.renderer.block.ModelBlockRenderer
typealias ClientWorld = net.minecraft.client.multiplayer.ClientLevel
typealias BlockModel = net.minecraft.client.renderer.block.dispatch.BlockStateModel
typealias Immediate = net.minecraft.client.renderer.MultiBufferSource.BufferSource
typealias VertexRendering = net.minecraft.client.renderer.ShapeRenderer
