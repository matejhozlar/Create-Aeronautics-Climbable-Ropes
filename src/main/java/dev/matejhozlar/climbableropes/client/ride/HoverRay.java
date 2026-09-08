package dev.matejhozlar.climbableropes.client.ride;

import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// blockDistSqr is the squared distance to the block under the crosshair, so ropes behind it are not picked.
record HoverRay(Vec3 eye, Vec3 look, double maxRange, double blockDistSqr) {

    static HoverRay from(Minecraft mc, LocalPlayer player) {
        double maxRange = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1;
        Vec3 eye = player.getEyePosition();
        HitResult hitResult = mc.hitResult;
        double blockDistSqr = hitResult == null
                ? maxRange * maxRange
                : Sable.HELPER.projectOutOfSubLevel(mc.level, hitResult.getLocation()).distanceToSqr(eye);
        return new HoverRay(eye, player.getLookAngle(), maxRange, blockDistSqr);
    }

    record Hit(double lateralSqr, double depthSqr, Vec3 segmentDir) {}

    Hit hit(Vec3 a, Vec3 b) {
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
        s = Math.max(0.0, Math.min(maxRange, s));
        t = Math.max(0.0, Math.min(segLen, t));

        Vec3 onRay = eye.add(look.scale(s));
        Vec3 onSeg = a.add(segUnit.scale(t));
        return new Hit(onRay.distanceToSqr(onSeg), s * s, segUnit);
    }
}
