package dev.matejhozlar.climbableropes.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class ClimbableRopesKeybinds {
    public static final KeyMapping LOCK_ROPE_CAMERA = new KeyMapping(
            "key.climbable_ropes.lock_rope_camera",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories.climbable_ropes");

    private static float lockedYaw;

    private ClimbableRopesKeybinds() {}

    static void register(RegisterKeyMappingsEvent event) {
        event.register(LOCK_ROPE_CAMERA);
    }

    public static void update(LocalPlayer player) {
        if (!LOCK_ROPE_CAMERA.isDown()) {
            lockedYaw = player.getYRot();
        }
    }

    public static float climbYaw() {
        return lockedYaw;
    }
}
