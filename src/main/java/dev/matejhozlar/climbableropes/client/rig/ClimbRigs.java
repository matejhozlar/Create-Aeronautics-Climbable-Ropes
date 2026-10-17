package dev.matejhozlar.climbableropes.client.rig;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.matejhozlar.climbableropes.ClimbableRopes;
import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import dev.matejhozlar.climbableropes.client.ClimbAnimationController;
import dev.matejhozlar.climbableropes.client.ClimbAnimationController.ClimbState;
import dev.matejhozlar.climbableropes.client.RemoteClimbAnimations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = ClimbableRopes.MODID, value = Dist.CLIENT)
public final class ClimbRigs {
    private static final float ARM_JOINT = 4.0F;
    private static final float ARM_UPPER = 4.0F;
    private static final float ARM_LOWER = 5.0F;
    private static final float LEG_JOINT = 6.0F;
    private static final float LEG_UPPER = 6.0F;
    private static final float LEG_LOWER = 5.0F;
    private static final float SHRUG_UP = 1.5F;
    private static final float SHRUG_IN = 1.0F;
    private static final float SHRUG_FORWARD = 1.0F;
    // Feet only half follow the body sway: they hang off the rope rather than being clamped to it.
    private static final float FOOT_ROPE_FOLLOW = 0.5F;
    private static final float MAX_ROPE_CORRECTION = 0.25F;
    private static final float MAX_HEAD_YAW = (float) Math.toRadians(75.0);
    private static final Vector3f PIVOT = new Vector3f(0.0F, ClimbRig.PIVOT_Y, ClimbRig.ROPE_Z);

    private static final Map<UUID, ClimbRig> RIGS = new HashMap<>();
    private static int suppressDepth;

    private ClimbRigs() {}

    public static void pushSuppression() {
        suppressDepth++;
    }

    public static void popSuppression() {
        if (suppressDepth > 0) suppressDepth--;
    }

    public static boolean isSuppressed() {
        return suppressDepth > 0;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            RIGS.clear();
            return;
        }
        if (mc.isPaused()) return;

        boolean enabled = ClimbableRopesConfig.ENABLE_CLIMB_ANIMATION.get();
        for (AbstractClientPlayer player : mc.level.players()) {
            UUID id = player.getUUID();
            boolean local = player == mc.player;
            boolean active = enabled && ClimbAnimationController.isCustomPoseActive(player);
            ClimbRig rig = RIGS.get(id);
            if (rig == null) {
                if (!active) continue;
                rig = new ClimbRig();
                RIGS.put(id, rig);
            }
            ClimbState state = local
                    ? ClimbAnimationController.currentState()
                    : RemoteClimbAnimations.stateFor(id);
            Vec3 ropePoint = local ? ClimbAnimationController.currentRopePoint() : null;
            Vec3 tangent = local
                    ? ClimbAnimationController.currentRopeTangent()
                    : RemoteClimbAnimations.tangentFor(id);
            rig.tick(player, active, state, ropePoint, tangent);
        }
        RIGS.entrySet().removeIf(e -> e.getValue().isIdle() || mc.level.getPlayerByUUID(e.getKey()) == null);
    }

    static ClimbRig.Pose sample(Player player, float partialTick) {
        if (!ClimbableRopesConfig.ENABLE_CLIMB_ANIMATION.get()) return null;
        ClimbRig rig = RIGS.get(player.getUUID());
        if (rig == null) return null;
        ClimbRig.Pose pose = rig.sample(partialTick);
        return pose.weight() > 0.0F ? pose : null;
    }

    // Pivots the whole model about the top grip, which sits on the rope, so sway and rope alignment both keep
    // the hands where the rope is.
    public static boolean placeBody(Player player, float partialTick, Quaternionf align, PoseStack poseStack) {
        ClimbRig.Pose pose = sample(player, partialTick);
        if (pose == null && align == null) return false;

        float scale = player.getScale() * ClimbRig.PX;
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        Quaternionf toWorld = new Quaternionf().rotationY((180.0F - bodyYaw) * Mth.DEG_TO_RAD).rotateZ(Mth.PI);
        Vector3f pivot = toWorld.transform(new Vector3f(PIVOT).sub(0.0F, ClimbRig.MODEL_ORIGIN_Y, 0.0F)).mul(scale);
        Vector3f anchor = new Vector3f(pivot);
        Quaternionf sway = new Quaternionf();
        Vector3f lift = new Vector3f();
        if (pose != null) {
            float w = pose.weight();
            Quaternionf worldSway = new Quaternionf(toWorld).mul(pose.sway()).mul(new Quaternionf(toWorld).conjugate());
            sway.slerp(worldSway, w);
            toWorld.transform(lift.set(0.0F, -pose.lift(), 0.0F)).mul(scale * w);
            Vec3 offset = pose.ropeOffset();
            if (offset != null) {
                float dx = (float) offset.x - pivot.x;
                float dz = (float) offset.z - pivot.z;
                float len = Mth.sqrt(dx * dx + dz * dz);
                if (len > MAX_ROPE_CORRECTION) {
                    dx *= MAX_ROPE_CORRECTION / len;
                    dz *= MAX_ROPE_CORRECTION / len;
                }
                anchor.add(dx * w, 0.0F, dz * w);
            }
        }

        poseStack.pushPose();
        poseStack.translate(anchor.x, anchor.y, anchor.z);
        if (align != null) poseStack.mulPose(align);
        poseStack.mulPose(sway);
        poseStack.translate(lift.x - pivot.x, lift.y - pivot.y, lift.z - pivot.z);
        return true;
    }

    public static void pose(PlayerModel<?> model, AbstractClientPlayer player, float ageInTicks) {
        clearBends(model);
        if (isSuppressed()) return;
        ClimbRig.Pose pose = sample(player, Mth.clamp(ageInTicks - player.tickCount, 0.0F, 1.0F));
        if (pose == null) return;
        float w = pose.weight();

        Quaternionf unsway = new Quaternionf(pose.sway()).conjugate();
        Vector3f lift = new Vector3f(0.0F, -pose.lift(), 0.0F);

        model.head.xRot += pose.headPitch() * w;
        model.head.yRot = Mth.lerp(w, model.head.yRot, Mth.clamp(model.head.yRot, -MAX_HEAD_YAW, MAX_HEAD_YAW));
        model.hat.copyFrom(model.head);

        float twist = pose.twist();
        blend(model.body, 0.0F, 0.0F, 0.0F, 0.0F, twist, 0.0F, w);

        poseArm(model.rightArm, toModel(pose.handR(), unsway, lift, 1.0F), twist, pose.breath(), w);
        poseArm(model.leftArm, toModel(pose.handL(), unsway, lift, 1.0F), twist, pose.breath(), w);
        poseLeg(model.rightLeg, toModel(pose.footR(), unsway, lift, FOOT_ROPE_FOLLOW), w);
        poseLeg(model.leftLeg, toModel(pose.footL(), unsway, lift, FOOT_ROPE_FOLLOW), w);
    }

    public static void clearBends(PlayerModel<?> model) {
        setBend(model.rightArm, null);
        setBend(model.leftArm, null);
        setBend(model.rightLeg, null);
        setBend(model.leftLeg, null);
        setBend(model.rightSleeve, null);
        setBend(model.leftSleeve, null);
        setBend(model.rightPants, null);
        setBend(model.leftPants, null);
    }

    private static Vector3f toModel(Vector3f ropeFrame, Quaternionf unsway, Vector3f lift, float follow) {
        Vector3f swayed = new Vector3f(ropeFrame).sub(PIVOT);
        unsway.transform(swayed);
        swayed.add(PIVOT);
        return new Vector3f(ropeFrame).lerp(swayed, follow).sub(lift);
    }

    private static void poseArm(ModelPart arm, Vector3f target, float twist, float breath, float w) {
        PartPose rest = arm.getInitialPose();
        float side = Math.signum(rest.x);
        float shrug = smooth01((2.0F - target.y) / 8.0F);
        Vector3f pivot = new Vector3f(
                rest.x - side * SHRUG_IN * shrug,
                rest.y - SHRUG_UP * shrug - breath,
                rest.z - SHRUG_FORWARD * shrug);
        new Quaternionf().rotationY(twist).transform(pivot);
        Vector3f pole = new Vector3f(side, 0.7F, 0.35F).normalize();
        LimbIk.Solution ik = LimbIk.solve(pivot, centerX(arm), target, ARM_UPPER, ARM_LOWER, pole, -1.0F);
        blend(arm, pivot.x, pivot.y, pivot.z, ik.xRot(), ik.yRot(), ik.zRot(), w);
        setBend(arm, new LimbBend(ARM_JOINT, ik.bend() * w));
    }

    private static void poseLeg(ModelPart leg, Vector3f target, float w) {
        PartPose rest = leg.getInitialPose();
        float side = Math.signum(rest.x);
        Vector3f pivot = new Vector3f(rest.x, rest.y, rest.z);
        Vector3f pole = new Vector3f(side * 0.3F, 0.0F, -1.0F).normalize();
        LimbIk.Solution ik = LimbIk.solve(pivot, centerX(leg), target, LEG_UPPER, LEG_LOWER, pole, 1.0F);
        blend(leg, pivot.x, pivot.y, pivot.z, ik.xRot(), ik.yRot(), ik.zRot(), w);
        setBend(leg, new LimbBend(LEG_JOINT, ik.bend() * w));
    }

    private static void blend(ModelPart part, float x, float y, float z, float xRot, float yRot, float zRot, float w) {
        part.x = Mth.lerp(w, part.x, x);
        part.y = Mth.lerp(w, part.y, y);
        part.z = Mth.lerp(w, part.z, z);
        part.xRot = lerpAngle(w, part.xRot, xRot);
        part.yRot = lerpAngle(w, part.yRot, yRot);
        part.zRot = lerpAngle(w, part.zRot, zRot);
    }

    private static float lerpAngle(float w, float from, float to) {
        return from + Mth.wrapDegrees((to - from) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * w;
    }

    private static float smooth01(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float centerX(ModelPart part) {
        return ((BendablePart) (Object) part).climbableRopes$centerX();
    }

    private static void setBend(ModelPart part, LimbBend bend) {
        ((BendablePart) (Object) part).climbableRopes$setBend(bend);
    }
}
