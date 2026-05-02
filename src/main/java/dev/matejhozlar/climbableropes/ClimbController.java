package dev.matejhozlar.climbableropes;

import com.simibubi.create.foundation.utility.RaycastHelper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ZiplineClientManager;
import dev.simulated_team.simulated.network.packets.RopeRidingPacket;
import foundry.veil.api.network.VeilPacketManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3d;

import java.util.UUID;

@EventBusSubscriber(modid = ClimbableRopes.MODID, value = Dist.CLIENT)
public final class ClimbController {
    public static final double VERTICAL_THRESHOLD = 0.85;

    private static final double CLIMB_SPEED = 0.18;
    private static final double DESCEND_SPEED = 0.22;
    private static final double SLIDE_SPEED = 1.2;
    private static final double SLIDE_ACCEL = 0.05;
    private static final double SLIDE_DECEL = 0.04;
    private static final double SNAP_PULL = 0.55;
    private static final double SNAP_HORIZ_CAP = 0.35;
    private static final double BOTTOM_DISMOUNT_OFFSET = 0.6;
    private static final double CLIMB_SIDE_OFFSET = 0.3;
    private static final int BOTTOM_GROUNDED_DISMOUNT_TICKS = 5;
    private static final double HALF_THICKNESS = 4.0 / 16.0;
    private static final double JUMP_OFF_VELOCITY = 0.42;

    private static final Vector3d UP = new Vector3d(0.0, 1.0, 0.0);

    private static UUID climbingRope = null;
    private static int bottomGroundedTimer = 0;
    private static boolean prevUseDown = false;
    private static double slideVelocity = 0.0;

    private ClimbController() {}

    // Run after Simulated's tick so we overwrite hoveringRope (which it clears for empty hand).
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            climbingRope = null;
            bottomGroundedTimer = 0;
            prevUseDown = false;
            slideVelocity = 0.0;
            PlungerClimbController.reset();
            return;
        }
        if (mc.isPaused()) return;

        boolean useDown = mc.options.keyUse.isDown();
        boolean justPressed = useDown && !prevUseDown;
        prevUseDown = useDown;

        if (climbingRope != null) {
            tickClimb(mc, player);
            return;
        }

        if (PlungerClimbController.isClimbing()) {
            PlungerClimbController.tickClimb(mc, player);
            return;
        }

        if (!player.getMainHandItem().isEmpty()) return;
        if (player.isShiftKeyDown()) return;
        if (ZiplineClientManager.ridingRope != null) return;

        UUID hovered = findVerticalHover(mc, player);
        if (hovered != null) {
            ZiplineClientManager.hoveringRope = hovered;
            if (justPressed) embark(hovered, mc, player);
            return;
        }

        PlungerClimbController.tryHoverEmbark(mc, player, justPressed);
    }

    private static UUID findVerticalHover(Minecraft mc, LocalPlayer player) {
        ClientLevelRopeManager mgr = ClientLevelRopeManager.getOrCreate(mc.level);
        double maxRange = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1;
        HitResult hitResult = mc.hitResult;

        Vector3d from = JOMLConversion.toJOML(player.getEyePosition());
        Vector3d to = JOMLConversion.toJOML(
                RaycastHelper.getTraceTarget(player, maxRange, JOMLConversion.toMojang(from)));
        double bestDiffSqr = hitResult == null
                ? Double.MAX_VALUE
                : Sable.HELPER.projectOutOfSubLevel(mc.level, hitResult.getLocation())
                        .distanceToSqr(from.x, from.y, from.z);

        UUID found = ZiplineClientManager.raycastRope(mgr, from, to, bestDiffSqr, HALF_THICKNESS);
        if (found == null) return null;

        ClientRopeStrand strand = mgr.getStrand(found);
        if (strand == null) return null;

        ZiplineClientManager.ClosestQuery query =
                ZiplineClientManager.getClosestPointOnStrand(strand, player);
        return Math.abs(query.normal().dot(UP)) >= VERTICAL_THRESHOLD ? found : null;
    }

    private static void embark(UUID rope, Minecraft mc, LocalPlayer player) {
        climbingRope = rope;
        bottomGroundedTimer = 0;

        player.getAbilities().flying = false;
        player.stopFallFlying();

        mc.gui.setOverlayMessage(
                Component.translatable("mount.onboard", mc.options.keyShift.getTranslatedKeyMessage()),
                false);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOOL_HIT, 1f, 0.5f));

        VeilPacketManager.server().sendPacket(new RopeRidingPacket(rope, false));
    }

    private static void disembark() {
        if (climbingRope != null) {
            VeilPacketManager.server().sendPacket(new RopeRidingPacket(climbingRope, true));
        }
        climbingRope = null;
        bottomGroundedTimer = 0;
        slideVelocity = 0.0;

        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.WOOL_HIT, 0.75f, 0.35f));
    }

    private static void tickClimb(Minecraft mc, LocalPlayer player) {
        if (player.getAbilities().flying || !player.getMainHandItem().isEmpty()) {
            disembark();
            return;
        }

        ClientLevelRopeManager mgr = ClientLevelRopeManager.getOrCreate(mc.level);
        ClientRopeStrand strand = mgr.getStrand(climbingRope);
        if (strand == null) {
            disembark();
            return;
        }

        ZiplineClientManager.ClosestQuery query =
                ZiplineClientManager.getClosestPointOnStrand(strand, player);

        boolean climbUp = mc.options.keyUp.isDown();
        boolean climbDown = mc.options.keyDown.isDown();
        boolean sprint = mc.options.keySprint.isDown();
        boolean dismount = mc.options.keyShift.isDown();
        boolean jumpOff = mc.options.keyJump.isDown();

        if (jumpOff) {
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, Math.max(v.y, JUMP_OFF_VELOCITY), v.z);
            disembark();
            return;
        }
        if (dismount) {
            disembark();
            return;
        }

        Vec3 ropeWorld = JOMLConversion.toMojang(query.position());
        Vec3 anchor = anchor(player);

        Vec3 firstPoint = JOMLConversion.toMojang(strand.getPoints().getFirst().position());
        Vec3 lastPoint = JOMLConversion.toMojang(strand.getPoints().getLast().position());
        Vec3 bottomPoint = firstPoint.y < lastPoint.y ? firstPoint : lastPoint;
        Vec3 topPoint = firstPoint.y < lastPoint.y ? lastPoint : firstPoint;

        if (player.onGround() && !climbUp && anchor.y < bottomPoint.y + BOTTOM_DISMOUNT_OFFSET) {
            if (++bottomGroundedTimer > BOTTOM_GROUNDED_DISMOUNT_TICKS) {
                disembark();
                return;
            }
        } else {
            bottomGroundedTimer = 0;
        }

        if (climbUp && anchor.y >= topPoint.y) climbUp = false;

        boolean sliding = climbDown && sprint;
        if (climbUp) {
            slideVelocity = 0.0;
        } else if (sliding) {
            if (slideVelocity < DESCEND_SPEED) slideVelocity = DESCEND_SPEED;
            slideVelocity = Math.min(SLIDE_SPEED, slideVelocity + SLIDE_ACCEL);
        } else if (slideVelocity > 0) {
            slideVelocity = Math.max(0.0, slideVelocity - SLIDE_DECEL);
        }

        double yVel;
        if (climbUp) yVel = CLIMB_SPEED;
        else if (slideVelocity > DESCEND_SPEED) yVel = -slideVelocity;
        else if (climbDown) yVel = -DESCEND_SPEED;
        else if (slideVelocity > 0) yVel = -slideVelocity;
        else yVel = 0.0;

        double yawRad = Math.toRadians(player.getYRot());
        double dx = ropeWorld.x + Math.sin(yawRad) * CLIMB_SIDE_OFFSET - anchor.x;
        double dz = ropeWorld.z - Math.cos(yawRad) * CLIMB_SIDE_OFFSET - anchor.z;
        double xVel = dx * SNAP_PULL;
        double zVel = dz * SNAP_PULL;

        double horizMag = Math.hypot(xVel, zVel);
        if (horizMag > SNAP_HORIZ_CAP) {
            double scale = SNAP_HORIZ_CAP / horizMag;
            xVel *= scale;
            zVel *= scale;
        }

        player.setDeltaMovement(xVel, yVel, zVel);
        player.fallDistance = 0.0F;

        if (AnimationTickHolder.getTicks() % 10 == 0) {
            VeilPacketManager.server().sendPacket(new RopeRidingPacket(climbingRope, false));
        }
    }

    private static Vec3 anchor(LocalPlayer player) {
        double chainYOffset = 0.5 * player.getScale();
        return player.position().add(0.0, player.getBoundingBox().getYsize() + chainYOffset, 0.0);
    }
}
