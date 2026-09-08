package dev.matejhozlar.climbableropes.client.ride;

import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.entities.launched_plunger.LaunchedPlungerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

final class PlungerRope {
    private static final double PLUNGER_END_OFFSET = 0.6;

    private PlungerRope() {}

    record Pair(LaunchedPlungerEntity a, LaunchedPlungerEntity b) {
        boolean sameEnds(LaunchedPlungerEntity x, LaunchedPlungerEntity y) {
            int ai = a.getId();
            int bi = b.getId();
            int xi = x.getId();
            int yi = y.getId();
            return (ai == xi && bi == yi) || (ai == yi && bi == xi);
        }
    }

    record RopeEnd(Vec3 position, Vec3 velocity) {}

    static boolean isPlunged(LaunchedPlungerEntity p) {
        return p != null && !p.isRemoved() && p.isPlunged();
    }

    static RopeEnd ropeEnd(LaunchedPlungerEntity p) {
        Vec3 local = ropeEndLocal(p, p.position());
        Vec3 localPrev = ropeEndLocal(p, new Vec3(p.xo, p.yo, p.zo));
        SubLevel sl = Sable.HELPER.getContainingClient(p.position());
        if (sl == null) return new RopeEnd(local, local.subtract(localPrev));
        Vec3 world = sl.logicalPose().transformPosition(local);
        return new RopeEnd(world, world.subtract(sl.lastPose().transformPosition(localPrev)));
    }

    private static Vec3 ropeEndLocal(LaunchedPlungerEntity p, Vec3 pos) {
        Direction dir = p.getData(LaunchedPlungerEntity.PLUNGED_DIRECTION);
        return pos.add(Vec3.atLowerCornerOf(dir.getNormal()).scale(PLUNGER_END_OFFSET));
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
            if (!isPlunged(other)) continue;
            seen.add(p.getId());
            seen.add(other.getId());

            Vec3 a = ropeEnd(p).position();
            Vec3 b = ropeEnd(other).position();
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
}
