package dev.matejhozlar.climbableropes.client.rig;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

// Two-bone IK in model pixels (y down). The limb hangs along local +Y and folds toward local Z * bendZ.
final class LimbIk {
    record Solution(float xRot, float yRot, float zRot, float bend) {}

    private LimbIk() {}

    static Solution solve(Vector3f pivot, float offsetX, Vector3f target, float upper, float lower,
                          Vector3f pole, float bendZ) {
        Vector3f root = new Vector3f(pivot.x + offsetX, pivot.y, pivot.z);
        Vector3f xAxis = new Vector3f();
        solveFrom(root, target, upper, lower, pole, bendZ, xAxis);
        // The limb's centre line sits offsetX off the pivot, so re-root along the solved X axis once.
        root.set(xAxis).mul(offsetX).add(pivot);
        return solveFrom(root, target, upper, lower, pole, bendZ, xAxis);
    }

    private static Solution solveFrom(Vector3f root, Vector3f target, float upper, float lower,
                                      Vector3f pole, float bendZ, Vector3f xAxisOut) {
        Vector3f dir = new Vector3f(target).sub(root);
        float dist = dir.length();
        if (dist < 1.0e-4F) {
            dir.set(0.0F, 1.0F, 0.0F);
        } else {
            dir.div(dist);
        }
        float reach = Mth.clamp(dist, Math.abs(upper - lower) + 0.01F, upper + lower - 0.001F);

        Vector3f plane = new Vector3f(pole).sub(new Vector3f(dir).mul(pole.dot(dir)));
        if (plane.lengthSquared() < 1.0e-8F) {
            plane.set(0.0F, 0.0F, -bendZ).sub(new Vector3f(dir).mul(-bendZ * dir.z));
            if (plane.lengthSquared() < 1.0e-8F) plane.set(1.0F, 0.0F, 0.0F);
        }
        plane.normalize();

        float cosA = Mth.clamp((upper * upper + reach * reach - lower * lower) / (2.0F * upper * reach), -1.0F, 1.0F);
        float sinA = (float) Math.sqrt(1.0F - cosA * cosA);
        Vector3f upperDir = new Vector3f(dir).mul(cosA).add(new Vector3f(plane).mul(sinA));
        Vector3f elbow = new Vector3f(upperDir).mul(upper).add(root);
        Vector3f lowerDir = new Vector3f(dir).mul(reach).add(root).sub(elbow).normalize();

        float cosB = Mth.clamp(upperDir.dot(lowerDir), -1.0F, 1.0F);
        float beta = (float) Math.acos(cosB);
        Vector3f fold = new Vector3f(lowerDir).sub(new Vector3f(upperDir).mul(cosB));
        if (fold.lengthSquared() < 1.0e-8F) {
            fold.set(plane).sub(new Vector3f(upperDir).mul(plane.dot(upperDir))).negate();
        }
        fold.normalize();

        Vector3f yAxis = upperDir;
        Vector3f zAxis = fold.mul(bendZ);
        Vector3f xAxis = new Vector3f(yAxis).cross(zAxis);
        xAxisOut.set(xAxis);

        float m20 = Mth.clamp(xAxis.z, -1.0F, 1.0F);
        float yRot = (float) Math.asin(-m20);
        float xRot;
        float zRot;
        if (Math.abs(m20) < 0.9999F) {
            xRot = (float) Math.atan2(yAxis.z, zAxis.z);
            zRot = (float) Math.atan2(xAxis.y, xAxis.x);
        } else {
            xRot = (float) Math.atan2(-zAxis.y, yAxis.y);
            zRot = 0.0F;
        }
        return new Solution(xRot, yRot, zRot, bendZ * beta);
    }
}
