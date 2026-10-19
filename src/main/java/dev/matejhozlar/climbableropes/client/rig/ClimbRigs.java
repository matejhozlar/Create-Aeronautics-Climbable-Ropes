package dev.matejhozlar.climbableropes.client.rig;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.matejhozlar.climbableropes.ClimbableRopes;
import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import dev.matejhozlar.climbableropes.client.ClimbAnimationController;
import dev.matejhozlar.climbableropes.client.ClimbAnimationController.ClimbState;
import dev.matejhozlar.climbableropes.client.ClimbableRopesKeybinds;
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
import org.joml.Matrix3f;
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
    private static final float STANDING_FOOT_Y = 23.5F;
    // How far the drawn body may shift off the hitbox so the grip sits on the rope: on flat ropes the grip is
    // well above the upright model's reach.
    private static final float MAX_GRIP_CORRECTION = 0.75F;
    private static final float MAX_HEAD_YAW = (float) Math.toRadians(75.0);
    private static final float MAX_HEAD_PITCH = (float) Math.toRadians(70.0);

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
            float yaw = local ? ClimbableRopesKeybinds.climbYaw() : player.getYHeadRot();
            rig.tick(player, active, state, ropePoint, tangent, yaw);
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

    // Pivots the whole model about the top grip, which sits on the rope, so the rope alignment and the sway
    // both keep the hands where the rope is.
    public static boolean placeBody(Player player, float partialTick, PoseStack poseStack) {
        ClimbRig.Pose pose = sample(player, partialTick);
        if (pose == null) return false;

        float w = pose.weight();
        float scale = player.getScale() * ClimbRig.PX;
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        Quaternionf toWorld = toWorld(bodyYaw);
        Vector3f pivot = ClimbRig.pivotFromFeet(bodyYaw, scale);
        Vector3f anchor = new Vector3f(pivot);
        Quaternionf align = alignment(pose, bodyYaw);
        Quaternionf worldSway = new Quaternionf(toWorld).mul(bodyRotation(pose))
                .mul(new Quaternionf(toWorld).conjugate());
        Quaternionf sway = new Quaternionf().slerp(worldSway, w);
        Vector3f lift = toWorld.transform(new Vector3f(0.0F, -pose.lift(), 0.0F)).mul(scale * w);
        Vec3 grip = pose.gripOffset();
        if (grip != null) {
            Vector3f correction = new Vector3f((float) grip.x, (float) grip.y, (float) grip.z).sub(pivot);
            float len = correction.length();
            if (len > MAX_GRIP_CORRECTION) correction.mul(MAX_GRIP_CORRECTION / len);
            anchor.add(correction.mul(w));
        }

        poseStack.pushPose();
        poseStack.translate(anchor.x, anchor.y, anchor.z);
        poseStack.mulPose(align);
        poseStack.mulPose(sway);
        poseStack.translate(lift.x - pivot.x, lift.y - pivot.y, lift.z - pivot.z);
        return true;
    }

    public static void pose(PlayerModel<?> model, AbstractClientPlayer player, float ageInTicks) {
        clearBends(model);
        if (isSuppressed()) return;
        float partialTick = Mth.clamp(ageInTicks - player.tickCount, 0.0F, 1.0F);
        ClimbRig.Pose pose = sample(player, partialTick);
        if (pose == null) return;
        float w = pose.weight();

        Quaternionf unbody = bodyRotation(pose).conjugate();
        Vector3f lift = new Vector3f(0.0F, -pose.lift(), 0.0F);

        poseHead(model, player, pose, partialTick);

        float twist = pose.twist();
        blend(model.body, 0.0F, 0.0F, 0.0F, 0.0F, twist, 0.0F, w);

        poseArm(model.rightArm, toModel(pose.handR(), unbody, lift), twist, pose.breath(), w);
        poseArm(model.leftArm, toModel(pose.handL(), unbody, lift), twist, pose.breath(), w);
        poseLeg(model.rightLeg, toModel(pose.footR(), unbody, lift), pose.grounded(), w);
        poseLeg(model.leftLeg, toModel(pose.footL(), unbody, lift), pose.grounded(), w);
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

    // The head keeps looking where the player looks, whichever way the body is turned on the rope.
    private static void poseHead(PlayerModel<?> model, AbstractClientPlayer player, ClimbRig.Pose pose,
                                 float partialTick) {
        float w = pose.weight();
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float lookPitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        float lookYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        Vec3 lookWorld = Vec3.directionFromRotation(lookPitch, lookYaw);

        Quaternionf modelToWorld = alignment(pose, bodyYaw).mul(toWorld(bodyYaw)).mul(bodyRotation(pose));
        Vector3f look = modelToWorld.conjugate()
                .transform(new Vector3f((float) lookWorld.x, (float) lookWorld.y, (float) lookWorld.z));
        float pitch = (float) Math.asin(Mth.clamp(look.y, -1.0F, 1.0F)) + pose.headPitch();
        float yaw = (float) Math.atan2(-look.x, -look.z);
        pitch = Mth.clamp(pitch, -MAX_HEAD_PITCH, MAX_HEAD_PITCH);
        yaw = Mth.clamp(yaw, -MAX_HEAD_YAW, MAX_HEAD_YAW);

        model.head.xRot = lerpAngle(w, model.head.xRot, pitch);
        model.head.yRot = lerpAngle(w, model.head.yRot, yaw);
        model.hat.copyFrom(model.head);
    }

    private static Quaternionf toWorld(float bodyYaw) {
        return new Quaternionf().rotationY((180.0F - bodyYaw) * Mth.DEG_TO_RAD).rotateZ(Mth.PI);
    }

    // The world rotation that takes the upright, yawed body to the rope frame: its up axis along the rope and
    // its chest facing the rope. Faded with the pose weight so the body straightens as the pose lets go.
    private static Quaternionf alignment(ClimbRig.Pose pose, float bodyYaw) {
        float yawRad = bodyYaw * Mth.DEG_TO_RAD;
        Vector3f facing = new Vector3f(-Mth.sin(yawRad), 0.0F, Mth.cos(yawRad));
        Vector3f worldUp = new Vector3f(0.0F, 1.0F, 0.0F);
        Matrix3f upright = new Matrix3f(facing, worldUp, new Vector3f(facing).cross(worldUp));
        Vector3f front = pose.front();
        Vector3f up = pose.up();
        Matrix3f onRope = new Matrix3f(front, up, new Vector3f(front).cross(up));
        Quaternionf target = onRope.mul(upright.transpose()).getNormalizedRotation(new Quaternionf());
        return new Quaternionf().slerp(target, pose.weight());
    }

    private static Quaternionf bodyRotation(ClimbRig.Pose pose) {
        return new Quaternionf(pose.sway()).rotateX(pose.lean());
    }

    private static Vector3f toModel(Vector3f ropeFrame, Quaternionf unbody, Vector3f lift) {
        return unbody.transform(new Vector3f(ropeFrame).sub(ClimbRig.PIVOT)).add(ClimbRig.PIVOT).sub(lift);
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

    private static void poseLeg(ModelPart leg, Vector3f target, float grounded, float w) {
        PartPose rest = leg.getInitialPose();
        Vector3f pivot = new Vector3f(rest.x, rest.y, rest.z);
        target.lerp(new Vector3f(rest.x, STANDING_FOOT_Y, rest.z), grounded);
        Vector3f pole = new Vector3f(0.0F, 0.0F, -1.0F);
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
