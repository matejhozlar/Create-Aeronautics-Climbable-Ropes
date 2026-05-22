package dev.matejhozlar.climbableropes;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.entities.launched_plunger.LaunchedPlungerEntity;
import dev.simulated_team.simulated.index.SimClickInteractions;
import dev.simulated_team.simulated.network.packets.RopeRidingPacket;
import foundry.veil.api.network.VeilPacketManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

final class PlungerClimbController {
    private static final double CLIMB_SIDE_OFFSET = 0.3;
    private static final double VERTICAL_BIAS = 0.5;
    private static final double PLUNGER_END_OFFSET = 0.6;
    private static final double AT_END_ARC_EPSILON = 0.2;

    private static LaunchedPlungerEntity backwardPlunger;
    private static LaunchedPlungerEntity forwardPlunger;
    private static int bottomGroundedTimer;
    private static boolean parkedAtBottom;
    private static double slideVelocity;

    private PlungerClimbController() {}

    static boolean isClimbing() {
        return forwardPlunger != null;
    }

    private static boolean isRidingPair(Pair pair) {
        if (forwardPlunger == null) return false;
        int a = pair.a().getId();
        int b = pair.b().getId();
        int f = forwardPlunger.getId();
        int k = backwardPlunger.getId();
        return (a == f && b == k) || (a == k && b == f);
    }

    static void reset() {
        backwardPlunger = null;
        forwardPlunger = null;
        bottomGroundedTimer = 0;
        parkedAtBottom = false;
        slideVelocity = 0.0;
    }

    static void tryHoverEmbark(Minecraft mc, LocalPlayer player, boolean justPressed) {
        if (!justPressed) return;
        Pair pair = findHoveredPair(mc, player);
        if (pair == null) return;
        embark(pair, mc, player);
    }

    static void tickClimb(Minecraft mc, LocalPlayer player) {
        if (player.getAbilities().flying || !player.getMainHandItem().isEmpty() || SimClickInteractions.HANDLE_HANDLER.isActive()) {
            disembark();
            return;
        }
        if (backwardPlunger == null || backwardPlunger.isRemoved() || !backwardPlunger.isPlunged()
                || forwardPlunger == null || forwardPlunger.isRemoved() || !forwardPlunger.isPlunged()) {
            disembark();
            return;
        }

        boolean climbUp = mc.options.keyUp.isDown();
        boolean climbDown = mc.options.keyDown.isDown();
        boolean sprint = mc.options.keySprint.isDown();
        boolean dismount = mc.options.keyShift.isDown();
        boolean jumpOff = mc.options.keyJump.isDown();

        if (jumpOff) {
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, Math.max(v.y, ClimbableRopesConfig.JUMP_OFF_VELOCITY.get()), v.z);
            disembark();
            return;
        }
        if (dismount) {
            disembark();
            return;
        }

        Vec3 back = ropeEndWorld(backwardPlunger);
        Vec3 fwd = ropeEndWorld(forwardPlunger);
        Vec3 ab = fwd.subtract(back);
        double abLen = ab.length();
        if (abLen < 1e-4) {
            disembark();
            return;
        }
        Vec3 dir = ab.scale(1.0 / abLen);

        Vec3 anchor = anchor(player);
        double t = Math.max(0.0, Math.min(abLen, anchor.subtract(back).dot(dir)));
        Vec3 ropeWorld = back.add(dir.scale(t));

        double maxLeash = ClimbableRopesConfig.MAX_LEASH_DISTANCE.get();
        if (anchor.distanceToSqr(ropeWorld) > maxLeash * maxLeash) {
            disembark();
            return;
        }

        Vec3 lowerEnd = back.y < fwd.y ? back : fwd;
        boolean nearBottom = anchor.y < lowerEnd.y + ClimbableRopesConfig.BOTTOM_DISMOUNT_OFFSET.get();
        if (player.onGround() && !climbUp && nearBottom) {
            if (++bottomGroundedTimer > ClimbableRopesConfig.BOTTOM_GROUNDED_DISMOUNT_TICKS.get()) {
                disembark();
                return;
            }
        } else {
            bottomGroundedTimer = 0;
        }

        double climbSpeed = ClimbableRopesConfig.CLIMB_SPEED.get();
        double descendSpeed = ClimbableRopesConfig.DESCEND_SPEED.get();
        double slideSpeed = ClimbableRopesConfig.SLIDE_SPEED.get();
        double slideAccel = ClimbableRopesConfig.SLIDE_ACCELERATION.get();
        double slideDecel = ClimbableRopesConfig.SLIDE_DECELERATION.get();

        double remainingUp = Math.max(0.0, abLen - t);
        if (climbUp && remainingUp <= 0.0) climbUp = false;
        // onGround and the arc test both flicker per tick, so they latch this rather than gating descent live.
        if (climbUp) parkedAtBottom = false;
        else if (t <= AT_END_ARC_EPSILON || player.onGround()) parkedAtBottom = true;
        boolean descentBlocked = parkedAtBottom;

        double sinAngle = Math.abs(dir.y);
        boolean slideEffective = climbDown && sprint && !descentBlocked && slideSpeed * sinAngle > descendSpeed;
        if (climbUp || descentBlocked) {
            slideVelocity = 0.0;
        } else if (slideEffective) {
            if (slideVelocity < descendSpeed) slideVelocity = descendSpeed;
            slideVelocity = Math.min(slideSpeed * sinAngle, slideVelocity + slideAccel * sinAngle);
        } else if (slideVelocity > 0) {
            slideVelocity = Math.max(0.0, slideVelocity - slideDecel);
        }

        double speedAlong;
        if (climbUp) speedAlong = Math.min(climbSpeed, remainingUp);
        else if (descentBlocked) speedAlong = 0.0;
        else if (slideVelocity > descendSpeed) speedAlong = -slideVelocity;
        else if (climbDown) speedAlong = -descendSpeed;
        else if (slideVelocity > 0) speedAlong = -slideVelocity;
        else speedAlong = 0.0;
        if (speedAlong < 0.0) speedAlong = -Math.min(-speedAlong, t);
        Vec3 climbVel = dir.scale(speedAlong);

        double snapPull = ClimbableRopesConfig.SNAP_PULL.get();
        double snapVelCap = ClimbableRopesConfig.SNAP_VELOCITY_CAP.get();
        Vec3 target = ropeWorld.add(sideOffset(ClimbableRopesKeybinds.climbYaw(), dir));
        double xVel = (target.x - anchor.x) * snapPull;
        double yVel = (target.y - anchor.y) * snapPull;
        double zVel = (target.z - anchor.z) * snapPull;
        double horizMag = Math.sqrt(xVel * xVel + zVel * zVel);
        if (horizMag > snapVelCap) {
            double scale = snapVelCap / horizMag;
            xVel *= scale;
            zVel *= scale;
        }
        yVel = Math.max(-snapVelCap, Math.min(snapVelCap, yVel));

        player.setDeltaMovement(climbVel.x + xVel, climbVel.y + yVel, climbVel.z + zVel);
        player.fallDistance = 0.0F;

        ClimbAnimationController.ClimbState animState;
        if (climbUp) animState = ClimbAnimationController.ClimbState.CLIMB_UP;
        else if (slideVelocity > descendSpeed) animState = ClimbAnimationController.ClimbState.SLIDE;
        else if (speedAlong < 0.0) animState = ClimbAnimationController.ClimbState.DESCEND;
        else animState = ClimbAnimationController.ClimbState.IDLE;
        ClimbAnimationController.onTick(dir, animState);

        if (AnimationTickHolder.getTicks() % 10 == 0) {
            VeilPacketManager.server().sendPacket(new RopeRidingPacket(forwardPlunger.getUUID(), false));
        }
    }

    private static void embark(Pair pair, Minecraft mc, LocalPlayer player) {
        if (isRidingPair(pair)) return;
        Vec3 posA = ropeEndWorld(pair.a());
        Vec3 posB = ropeEndWorld(pair.b());
        Vec3 ab = posB.subtract(posA);
        double abLen = ab.length();
        if (abLen < 1e-4) return;
        Vec3 dirAB = ab.scale(1.0 / abLen);

        boolean forwardIsB;
        if (Math.abs(dirAB.y) > VERTICAL_BIAS) {
            forwardIsB = posB.y > posA.y;
        } else {
            forwardIsB = player.getLookAngle().dot(dirAB) >= 0;
        }

        if (!snapToEmbarkPoint(mc, player, posA, posB, dirAB, abLen)) return;

        ClimbController.leaveActiveRides();

        forwardPlunger = forwardIsB ? pair.b() : pair.a();
        backwardPlunger = forwardIsB ? pair.a() : pair.b();
        bottomGroundedTimer = 0;
        parkedAtBottom = false;
        slideVelocity = 0.0;

        player.getAbilities().flying = false;
        player.stopFallFlying();
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;

        mc.gui.setOverlayMessage(
                Component.translatable("mount.onboard", mc.options.keyShift.getTranslatedKeyMessage()),
                false);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOOL_HIT, 1f, 0.5f));

        VeilPacketManager.server().sendPacket(new RopeRidingPacket(forwardPlunger.getUUID(), false));
        ClimbAnimationController.onEmbark(ClimbAnimationController.ClimbMode.PLUNGER_ROPE);
    }

    private static boolean snapToEmbarkPoint(Minecraft mc, LocalPlayer player,
                                             Vec3 posA, Vec3 posB, Vec3 dirAB, double abLen) {
        Vec3 anchor = anchor(player);
        double t = Math.max(0.0, Math.min(abLen, anchor.subtract(posA).dot(dirAB)));
        Vec3 ropePoint = posA.add(dirAB.scale(t));

        Vec3 lowerEnd = posA.y < posB.y ? posA : posB;
        boolean atBottom = ropePoint.distanceToSqr(lowerEnd) < 1.0;

        double targetY;
        if (atBottom) {
            ropePoint = lowerEnd;
            targetY = lowerEnd.y;
        } else {
            double chainYOffset = 0.5 * player.getScale();
            targetY = ropePoint.y - (player.getBoundingBox().getYsize() + chainYOffset);
        }

        double yawRad = Math.toRadians(player.getYRot());
        Vec3 offsetTarget = new Vec3(
                ropePoint.x + Math.sin(yawRad) * CLIMB_SIDE_OFFSET,
                targetY,
                ropePoint.z - Math.cos(yawRad) * CLIMB_SIDE_OFFSET);

        AABB offsetAabb = player.getBoundingBox().move(offsetTarget.subtract(player.position()));
        if (mc.level.noCollision(player, offsetAabb)) {
            player.setPos(offsetTarget);
            return true;
        }
        Vec3 centerTarget = new Vec3(ropePoint.x, targetY, ropePoint.z);
        AABB centerAabb = player.getBoundingBox().move(centerTarget.subtract(player.position()));
        if (mc.level.noCollision(player, centerAabb)) {
            player.setPos(centerTarget);
            return true;
        }
        return false;
    }

    static void disembark() {
        if (forwardPlunger == null) return;
        VeilPacketManager.server().sendPacket(new RopeRidingPacket(forwardPlunger.getUUID(), true));
        reset();
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.WOOL_HIT, 0.75f, 0.35f));
        ClimbAnimationController.onDisembark();
    }

    static Pair findHoveredPair(Minecraft mc, LocalPlayer player) {
        if (mc.level == null) return null;
        double maxRange = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        HitResult hitResult = mc.hitResult;
        double blockDistSq = hitResult == null
                ? maxRange * maxRange
                : Sable.HELPER.projectOutOfSubLevel(mc.level, hitResult.getLocation()).distanceToSqr(eye);

        Set<Integer> seen = new HashSet<>();
        Pair best = null;
        double bestDepthSq = blockDistSq;
        double radius = ClimbableRopesConfig.ROPE_HOVER_RADIUS.get();
        double radiusSq = radius * radius;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LaunchedPlungerEntity p)) continue;
            if (!p.isPlunged() || seen.contains(p.getId())) continue;
            LaunchedPlungerEntity other = p.getOther();
            if (other == null || other.isRemoved() || !other.isPlunged()) continue;
            seen.add(p.getId());
            seen.add(other.getId());

            Vec3 a = ropeEndWorld(p);
            Vec3 b = ropeEndWorld(other);
            RaySegHit hit = raySegmentHit(eye, look, maxRange, a, b);
            if (hit == null) continue;
            if (hit.lateralSq() > radiusSq) continue;
            if (hit.depthSq() > bestDepthSq) continue;
            bestDepthSq = hit.depthSq();
            best = new Pair(p, other);
        }
        return best;
    }

    private record RaySegHit(double lateralSq, double depthSq) {}

    private static RaySegHit raySegmentHit(Vec3 eye, Vec3 look, double maxLen, Vec3 a, Vec3 b) {
        Vec3 segDir = b.subtract(a);
        double segLen = segDir.length();
        if (segLen < 1e-6) return null;
        Vec3 segUnit = segDir.scale(1.0 / segLen);

        double dotLD = look.dot(segUnit);
        double denom = 1.0 - dotLD * dotLD;
        Vec3 r = eye.subtract(a);
        double rSeg = r.dot(segUnit);
        double rLook = r.dot(look);

        double s, t;
        if (denom < 1e-9) {
            s = 0.0;
            t = rSeg;
        } else {
            s = (dotLD * rSeg - rLook) / denom;
            t = (rSeg - dotLD * rLook) / denom;
        }
        s = Math.max(0.0, Math.min(maxLen, s));
        t = Math.max(0.0, Math.min(segLen, t));

        Vec3 onRay = eye.add(look.scale(s));
        Vec3 onSeg = a.add(segUnit.scale(t));
        double dx = onRay.x - onSeg.x;
        double dy = onRay.y - onSeg.y;
        double dz = onRay.z - onSeg.z;
        return new RaySegHit(dx * dx + dy * dy + dz * dz, s * s);
    }

    static Vec3 ropeEndWorld(LaunchedPlungerEntity p) {
        Direction dir = p.getData(LaunchedPlungerEntity.PLUNGED_DIRECTION);
        Vec3 normal = Vec3.atLowerCornerOf(dir.getNormal());
        Vec3 local = p.position().add(normal.scale(PLUNGER_END_OFFSET));
        SubLevel sl = Sable.HELPER.getContainingClient(p.position());
        return sl == null ? local : sl.logicalPose().transformPosition(local);
    }

    private static Vec3 sideOffset(double yaw, Vec3 ropeDir) {
        double yawRad = Math.toRadians(yaw);
        Vec3 forward = new Vec3(Math.sin(yawRad), 0.0, -Math.cos(yawRad));
        Vec3 perp = forward.subtract(ropeDir.scale(forward.dot(ropeDir)));
        double len = perp.length();
        if (len < 1e-6) return Vec3.ZERO;
        return perp.scale(CLIMB_SIDE_OFFSET / len);
    }

    private static Vec3 anchor(LocalPlayer player) {
        double chainYOffset = 0.5 * player.getScale();
        return player.position().add(0.0, player.getBoundingBox().getYsize() + chainYOffset, 0.0);
    }

    record Pair(LaunchedPlungerEntity a, LaunchedPlungerEntity b) {}
}
