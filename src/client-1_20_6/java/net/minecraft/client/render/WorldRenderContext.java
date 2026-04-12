package net.minecraft.client.render;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/**
 * Stub for 1.20.6 WorldRenderContext - provides cameraPos that 1.21+ has natively
 */
public class WorldRenderContext {
    private final Camera camera;
    
    public WorldRenderContext(Camera camera) {
        this.camera = camera;
    }
    
    public Vec3d getCameraPos() {
        return camera.getPos();
    }
    
    public Camera getCamera() {
        return camera;
    }
}
