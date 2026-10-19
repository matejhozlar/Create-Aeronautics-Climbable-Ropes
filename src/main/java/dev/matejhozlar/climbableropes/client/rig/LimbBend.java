package dev.matejhozlar.climbableropes.client.rig;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// Hinge about the part's X axis at jointY; the joint ring takes a mitred half turn so the skin stays closed.
@OnlyIn(Dist.CLIENT)
public record LimbBend(float jointY, float angle) {
    private static final float MIN_ANGLE = 1.0e-4F;

    public boolean isStraight() {
        return Math.abs(angle) < MIN_ANGLE;
    }

    public static void applyToHand(ModelPart arm, PoseStack poseStack) {
        LimbBend bend = ((BendablePart) (Object) arm).climbableRopes$getBend();
        if (bend == null || bend.isStraight()) return;
        float y = bend.jointY / 16.0F;
        poseStack.translate(0.0F, y, 0.0F);
        poseStack.mulPose(new Quaternionf().rotationX(bend.angle));
        poseStack.translate(0.0F, -y, 0.0F);
    }

    public void render(ModelPart.Cube cube, PoseStack.Pose pose, VertexConsumer buffer,
                       int light, int overlay, int color) {
        Emitter emitter = new Emitter(this, (cube.minZ + cube.maxZ) * 0.5F, pose, buffer, light, overlay, color);
        for (ModelPart.Polygon polygon : cube.polygons) {
            ModelPart.Vertex[] vertices = polygon.vertices;
            int lower = 0;
            for (ModelPart.Vertex vertex : vertices) {
                if (isLower(vertex)) lower++;
            }
            if (lower == 0 || lower == vertices.length || vertices.length != 4) {
                emitter.quad(polygon.normal, lower > 0, vertices);
            } else {
                emitter.splitQuad(polygon.normal, vertices);
            }
        }
    }

    private boolean isLower(ModelPart.Vertex vertex) {
        return vertex.pos.y() > jointY;
    }

    private static final class Emitter {
        private static final int UPPER = 0;
        private static final int MIDDLE = 1;
        private static final int LOWER = 2;

        private final float jointY;
        private final float jointZ;
        private final float cos;
        private final float sin;
        private final float halfTan;
        private final Matrix4f matrix;
        private final PoseStack.Pose pose;
        private final VertexConsumer buffer;
        private final int light;
        private final int overlay;
        private final int color;
        private final Vector3f scratch = new Vector3f();
        private final Vector3f normal = new Vector3f();

        private final float[] x = new float[6];
        private final float[] y = new float[6];
        private final float[] z = new float[6];
        private final float[] u = new float[6];
        private final float[] v = new float[6];
        private final int[] kind = new int[6];

        Emitter(LimbBend bend, float jointZ, PoseStack.Pose pose, VertexConsumer buffer,
                int light, int overlay, int color) {
            this.jointY = bend.jointY;
            this.jointZ = jointZ;
            this.cos = (float) Math.cos(bend.angle);
            this.sin = (float) Math.sin(bend.angle);
            this.halfTan = (float) Math.tan(bend.angle * 0.5F);
            this.matrix = pose.pose();
            this.pose = pose;
            this.buffer = buffer;
            this.light = light;
            this.overlay = overlay;
            this.color = color;
        }

        void quad(Vector3f faceNormal, boolean lower, ModelPart.Vertex[] vertices) {
            setNormal(faceNormal, lower);
            for (ModelPart.Vertex vertex : vertices) {
                emit(vertex.pos.x(), vertex.pos.y(), vertex.pos.z(), vertex.u, vertex.v,
                        vertex.pos.y() > jointY ? LOWER : UPPER);
            }
        }

        void splitQuad(Vector3f faceNormal, ModelPart.Vertex[] vertices) {
            int n = 0;
            for (int i = 0; i < 4; i++) {
                ModelPart.Vertex a = vertices[i];
                ModelPart.Vertex b = vertices[(i + 1) % 4];
                n = put(n, a.pos.x(), a.pos.y(), a.pos.z(), a.u, a.v, a.pos.y() > jointY ? LOWER : UPPER);
                boolean aLower = a.pos.y() > jointY;
                if (aLower != (b.pos.y() > jointY) && n < 6) {
                    float t = (jointY - a.pos.y()) / (b.pos.y() - a.pos.y());
                    n = put(n,
                            a.pos.x() + (b.pos.x() - a.pos.x()) * t,
                            jointY,
                            a.pos.z() + (b.pos.z() - a.pos.z()) * t,
                            a.u + (b.u - a.u) * t,
                            a.v + (b.v - a.v) * t,
                            MIDDLE);
                }
            }
            int first = -1;
            for (int i = 0; i < 6; i++) {
                if (kind[i] == MIDDLE) {
                    first = i;
                    break;
                }
            }
            if (n != 6 || first < 0) {
                quad(faceNormal, false, vertices);
                return;
            }
            emitHalf(faceNormal, first);
            emitHalf(faceNormal, (first + 3) % 6);
        }

        private void emitHalf(Vector3f faceNormal, int start) {
            setNormal(faceNormal, kind[(start + 1) % 6] == LOWER);
            for (int k = 0; k < 4; k++) {
                int i = (start + k) % 6;
                emit(x[i], y[i], z[i], u[i], v[i], kind[i]);
            }
        }

        private int put(int i, float px, float py, float pz, float pu, float pv, int k) {
            x[i] = px;
            y[i] = py;
            z[i] = pz;
            u[i] = pu;
            v[i] = pv;
            kind[i] = k;
            return i + 1;
        }

        private void setNormal(Vector3f faceNormal, boolean lower) {
            float ny = faceNormal.y();
            float nz = faceNormal.z();
            if (lower) {
                normal.set(faceNormal.x(), ny * cos - nz * sin, ny * sin + nz * cos);
            } else {
                normal.set(faceNormal);
            }
            pose.transformNormal(normal, normal);
        }

        private void emit(float px, float py, float pz, float pu, float pv, int k) {
            float outY = py;
            float outZ = pz;
            if (k == LOWER) {
                float dy = py - jointY;
                float dz = pz - jointZ;
                outY = jointY + dy * cos - dz * sin;
                outZ = jointZ + dy * sin + dz * cos;
            } else if (k == MIDDLE) {
                outY = jointY - (pz - jointZ) * halfTan;
            }
            Vector3f p = matrix.transformPosition(px / 16.0F, outY / 16.0F, outZ / 16.0F, scratch);
            buffer.addVertex(p.x(), p.y(), p.z(), color, pu, pv, overlay, light,
                    normal.x(), normal.y(), normal.z());
        }
    }
}
