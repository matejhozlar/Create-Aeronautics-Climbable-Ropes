package dev.matejhozlar.climbableropes.client.ride;

import com.simibubi.create.AllTags;
import dev.matejhozlar.climbableropes.ClimbableRopes;
import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import dev.matejhozlar.climbableropes.client.ClimbAnimationController;
import dev.matejhozlar.climbableropes.client.ClimbableRopesKeybinds;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ZiplineClientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

@EventBusSubscriber(modid = ClimbableRopes.MODID, value = Dist.CLIENT)
public final class RopeRideDispatcher {
    private static boolean prevUseDown = false;

    private RopeRideDispatcher() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            prevUseDown = false;
            StrandClimbController.reset();
            PlungerClimbController.reset();
            PlungerZiplineController.reset();
            ClimbAnimationController.reset();
            return;
        }
        if (mc.isPaused()) return;

        ClimbableRopesKeybinds.update(player);

        boolean useDown = mc.options.keyUse.isDown();
        boolean justPressed = useDown && !prevUseDown;
        prevUseDown = useDown;

        if (StrandClimbController.isRiding() || PlungerClimbController.isRiding() || PlungerZiplineController.isRiding()) {
            if (justPressed) tryHoverEmbark(mc, player, true);
            tickActiveRide(mc, player);
            return;
        }

        if (ZiplineClientManager.ridingRope != null) return;

        tryHoverEmbark(mc, player, justPressed);
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        // While climbing, the mod fully drives movement through setDeltaMovement. Vanilla walk input
        // (faster with sprint) would otherwise leak through and let the player walk off the rope.
        // The zipline mode is excluded: it intentionally rides on vanilla WASD movement.
        if (!StrandClimbController.isRiding() && !PlungerClimbController.isRiding()) return;
        Input input = event.getInput();
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
    }

    static void leaveActiveRides() {
        StrandClimbController.disembark();
        PlungerClimbController.disembark();
        PlungerZiplineController.disembark();
    }

    private static void tickActiveRide(Minecraft mc, LocalPlayer player) {
        if (StrandClimbController.isRiding()) StrandClimbController.tick(mc, player);
        else if (PlungerClimbController.isRiding()) PlungerClimbController.tick(mc, player);
        else if (PlungerZiplineController.isRiding()) PlungerZiplineController.tick(mc, player);
    }

    private static void tryHoverEmbark(Minecraft mc, LocalPlayer player, boolean justPressed) {
        if (AllTags.AllItemTags.CHAIN_RIDEABLE.matches(player.getMainHandItem())) {
            if (!player.isShiftKeyDown() && ClimbableRopesConfig.ALLOW_PLUNGER_ZIPLINE.get()) {
                PlungerZiplineController.tryHoverEmbark(mc, player, justPressed);
            }
            return;
        }

        if (!player.getMainHandItem().isEmpty()) return;
        if (player.isShiftKeyDown()) return;

        if (ClimbableRopesConfig.ALLOW_VERTICAL_ROPE_CLIMBING.get()
                && StrandClimbController.tryHoverEmbark(mc, player, justPressed)) {
            return;
        }
        if (ClimbableRopesConfig.ALLOW_PLUNGER_CLIMBING.get()) {
            PlungerClimbController.tryHoverEmbark(mc, player, justPressed);
        }
    }
}
