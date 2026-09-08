package dev.matejhozlar.climbableropes.client.ride;

import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.entities.launched_plunger.LaunchedPlungerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
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
        HoverRay ray = HoverRay.from(mc, player);

        Set<Integer> seen = new HashSet<>();
        Pair best = null;
        double bestDepthSq = ray.blockDistSqr();
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
            HoverRay.Hit hit = ray.hit(a, b);
            if (hit == null) continue;
            if (hit.lateralSqr() > radiusSq) continue;
            if (hit.depthSqr() > bestDepthSq) continue;
            bestDepthSq = hit.depthSqr();
            best = new Pair(p, other);
        }
        return best;
    }
}
