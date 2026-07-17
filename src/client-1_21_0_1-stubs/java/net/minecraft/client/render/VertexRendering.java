package net.minecraft.client.render;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.shape.VoxelShape;

/**
 * Stub for VertexRendering which does not exist in Minecraft 1.21-1.21.1.
 * Provides manual outline rendering implementation.
 */
public final class VertexRendering {
    private VertexRendering() {}

    public static void drawOutline(
        MatrixStack matrices,
        VertexConsumer vertexConsumer,
        VoxelShape shape,
        double offsetX,
        double offsetY,
        double offsetZ,
        int color
    ) {
        // Manual implementation - iterate over shape boxes and draw lines
        shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
            drawBox(matrices, vertexConsumer,
                minX + offsetX, minY + offsetY, minZ + offsetZ,
                maxX + offsetX, maxY + offsetY, maxZ + offsetZ,
                color);
        });
    }

    public static void drawOutline(
        MatrixStack matrices,
        VertexConsumer vertexConsumer,
        VoxelShape shape,
        double offsetX,
        double offsetY,
        double offsetZ,
        int color,
        float lineWidth
    ) {
        drawOutline(matrices, vertexConsumer, shape, offsetX, offsetY, offsetZ, color);
    }

    public static void drawFilledBox(
        MatrixStack matrices,
        VertexConsumer vertexConsumer,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        int r = (int)(red * 255);
        int g = (int)(green * 255);
        int b = (int)(blue * 255);
        int a = (int)(alpha * 255);
        int color = (a << 24) | (r << 16) | (g << 8) | b;
        drawBox(matrices, vertexConsumer, minX, minY, minZ, maxX, maxY, maxZ, color);
    }

    private static void drawBox(
        MatrixStack matrices,
        VertexConsumer vertexConsumer,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ,
        int color
    ) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        var entry = matrices.peek();

        // Bottom face
        vertexConsumer.vertex(entry, (float)minX, (float)minY, (float)minZ).color(r, g, b, a).normal(entry, 0f, -1f, 0f);
        vertexConsumer.vertex(entry, (float)maxX, (float)minY, (float)minZ).color(r, g, b, a).normal(entry, 0f, -1f, 0f);
        vertexConsumer.vertex(entry, (float)maxX, (float)minY, (float)maxZ).color(r, g, b, a).normal(entry, 0f, -1f, 0f);
        vertexConsumer.vertex(entry, (float)minX, (float)minY, (float)maxZ).color(r, g, b, a).normal(entry, 0f, -1f, 0f);

        // Top face
        vertexConsumer.vertex(entry, (float)minX, (float)maxY, (float)minZ).color(r, g, b, a).normal(entry, 0f, 1f, 0f);
        vertexConsumer.vertex(entry, (float)minX, (float)maxY, (float)maxZ).color(r, g, b, a).normal(entry, 0f, 1f, 0f);
        vertexConsumer.vertex(entry, (float)maxX, (float)maxY, (float)maxZ).color(r, g, b, a).normal(entry, 0f, 1f, 0f);
        vertexConsumer.vertex(entry, (float)maxX, (float)maxY, (float)minZ).color(r, g, b, a).normal(entry, 0f, 1f, 0f);

        // Front face
        vertexConsumer.vertex(entry, (float)minX, (float)minY, (float)minZ).color(r, g, b, a).normal(entry, 0f, 0f, -1f);
        vertexConsumer.vertex(entry, (float)minX, (float)maxY, (float)minZ).color(r, g, b, a).normal(entry, 0f, 0f, -1f);
        vertexConsumer.vertex(entry, (float)maxX, (float)maxY, (float)minZ).color(r, g, b, a).normal(entry, 0f, 0f, -1f);
        vertexConsumer.vertex(entry, (float)maxX, (float)minY, (float)minZ).color(r, g, b, a).normal(entry, 0f, 0f, -1f);

        // Back face
        vertexConsumer.vertex(entry, (float)minX, (float)minY, (float)maxZ).color(r, g, b, a).normal(entry, 0f, 0f, 1f);
        vertexConsumer.vertex(entry, (float)maxX, (float)minY, (float)maxZ).color(r, g, b, a).normal(entry, 0f, 0f, 1f);
        vertexConsumer.vertex(entry, (float)maxX, (float)maxY, (float)maxZ).color(r, g, b, a).normal(entry, 0f, 0f, 1f);
        vertexConsumer.vertex(entry, (float)minX, (float)maxY, (float)maxZ).color(r, g, b, a).normal(entry, 0f, 0f, 1f);

        // Left face
        vertexConsumer.vertex(entry, (float)minX, (float)minY, (float)minZ).color(r, g, b, a).normal(entry, -1f, 0f, 0f);
        vertexConsumer.vertex(entry, (float)minX, (float)minY, (float)maxZ).color(r, g, b, a).normal(entry, -1f, 0f, 0f);
        vertexConsumer.vertex(entry, (float)minX, (float)maxY, (float)maxZ).color(r, g, b, a).normal(entry, -1f, 0f, 0f);
        vertexConsumer.vertex(entry, (float)minX, (float)maxY, (float)minZ).color(r, g, b, a).normal(entry, -1f, 0f, 0f);

        // Right face
        vertexConsumer.vertex(entry, (float)maxX, (float)minY, (float)minZ).color(r, g, b, a).normal(entry, 1f, 0f, 0f);
        vertexConsumer.vertex(entry, (float)maxX, (float)maxY, (float)minZ).color(r, g, b, a).normal(entry, 1f, 0f, 0f);
        vertexConsumer.vertex(entry, (float)maxX, (float)maxY, (float)maxZ).color(r, g, b, a).normal(entry, 1f, 0f, 0f);
        vertexConsumer.vertex(entry, (float)maxX, (float)minY, (float)maxZ).color(r, g, b, a).normal(entry, 1f, 0f, 0f);
    }
}
