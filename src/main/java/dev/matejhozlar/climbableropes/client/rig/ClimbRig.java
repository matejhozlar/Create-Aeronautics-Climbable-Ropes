package dev.matejhozlar.climbableropes.client.rig;

import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import dev.matejhozlar.climbableropes.client.ClimbAnimationController.ClimbState;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// Per-player climb state, ticked at 20 Hz and sampled per frame. Rope-frame coordinates are model pixels
// (y down, forward -Z) before the body sway; the rope is the vertical line x = 0, z = ROPE_Z.
@OnlyIn(Dist.CLIENT)
final class ClimbRig {
    static final float PX = 0.9375F / 16.0F;
    static final float MODEL_ORIGIN_Y = 1.501F * 16.0F;
    static final float ROPE_Z = -5.8F;
    static final float PIVOT_Y = -6.0F;

    private static final float GRIP_TOP = -5.5F;
    private static final float GRIP_BOTTOM = 5.0F;
    private static final float HAND_X = 0.8F;
    private static final float SWING = 0.35F;
    private static final float PLANTED = 1.0F - SWING;
    // One hand's full cycle in blocks: the planted part of it matches the grip window exactly, so a gripping
    // hand stays fixed on the rope while the body moves past it.
    private static final double STRIDE = (GRIP_BOTTOM - GRIP_TOP) * PX / PLANTED;
    // Climb speeds well past a human cadence would turn the hands into a blur; above this they slip instead.
    private static final double MAX_CYCLE_SPEED = 0.08;
    private static final double MAX_PHASE_STEP = 0.2;
    private static final double SETTLE_STEP = SWING / 5.0;
    private static final double RESYNC_STEP = 0.01;
    private static final double PHASE_EPSILON = 1.0e-4;

    private static final float FADE_STEP = 0.25F;
    private static final float PENDULUM_STIFFNESS = 0.16F;
    private static final float PENDULUM_DAMPING = 0.24F;
    private static final float PENDULUM_ACCEL_GAIN = 3.0F;
    private static final float PENDULUM_DRAG_GAIN = 0.35F;
    private static final float PENDULUM_LIMIT = 0.3F;
    private static final float TWIST = 0.12F;
    // Tilts the body about the grip so the rope, which hangs in front of the chest, runs down between the feet.
    private static final float LEAN = -0.12F;

    // The feet pinch the rope between their inner faces (a leg is 4 px wide, the rope about 1.5 px).
    private static final float FOOT_X = 3.2F;
    private static final float FOOT_Y = 22.8F;
    private static final float FOOT_RISE = 1.8F;
    private static final float FOOT_RELEASE_X = 1.0F;
    private static final float FOOT_RELEASE_Z = 0.8F;
    private static final float SLIDE_FOOT_Y = 23.3F;
    private static final float RECLAMP_LIFT = 1.4F;

    // onGround flickers as the rope sways the player at the bottom, so a contact holds for a few ticks.
    private static final int GROUND_HOLD_TICKS = 6;
    private static final int FIDGET_MIN_TICKS = 80;
    private static final int FIDGET_RANDOM_TICKS = 100;
    private static final int RECLAMP_TICKS = 14;

    private float weight;
    private float weightO;
    private double phaseR = 0.1;
    private double phaseRO = phaseR;
    private double phaseL = 0.6;
    private double phaseLO = phaseL;
    private int direction = 1;
    private float slide;
    private float slideO;
    private float stepping;
    private float steppingO;
    private float moving;
    private float movingO;
    private float pitch;
    private float pitchO;
    private float pitchVel;
    private float roll;
    private float rollO;
    private float rollVel;
    private Vec3 prevPos;
    private Vec3 velocity = Vec3.ZERO;
    private Vec3 ropeOffset;
    private Vec3 ropeOffsetO;
    private float facing;
    private boolean facingKnown;
    private int age;

    private float accelForward;
    private float accelRight;

    private int groundTicks;
    private float grounded;
    private float groundedO;

    private final RandomSource random = RandomSource.create();
    private int idleTicks;
    private int nextFidget = FIDGET_MIN_TICKS;
    private int reclampTicks;
    private int reclampTicksO;
    private float reclampSide = -1.0F;

    record Pose(float weight, float lean, Quaternionf sway, float lift, float twist, float headPitch, float breath,
                float grounded, Vector3f handR, Vector3f handL, Vector3f footR, Vector3f footL, Vec3 ropeOffset) {}

    boolean isIdle() {
        return weight <= 0.0F && weightO <= 0.0F;
    }

    void tick(Player player, boolean active, ClimbState state, Vec3 ropePoint, Vec3 tangent) {
        weightO = weight;
        phaseRO = phaseR;
        phaseLO = phaseL;
        slideO = slide;
        steppingO = stepping;
        movingO = moving;
        pitchO = pitch;
        rollO = roll;
        ropeOffsetO = ropeOffset;
        groundedO = grounded;
        reclampTicksO = reclampTicks;
        age++;

        weight = approach(weight, active ? 1.0F : 0.0F, FADE_STEP);
        if (!active || state == null) state = ClimbState.IDLE;

        double speed = switch (state) {
            case CLIMB_UP -> Math.min(ClimbableRopesConfig.CLIMB_SPEED.get(), MAX_CYCLE_SPEED);
            case DESCEND -> -Math.min(ClimbableRopesConfig.DESCEND_SPEED.get(), MAX_CYCLE_SPEED);
            default -> 0.0;
        };
        double step = Mth.clamp(speed * ClimbableRopesConfig.ANIMATION_SPEED_MULTIPLIER.get() / STRIDE,
                -MAX_PHASE_STEP, MAX_PHASE_STEP);
        advanceHands(step);

        slide = approach(slide, state == ClimbState.SLIDE ? 1.0F : 0.0F, 0.2F);
        stepping = approach(stepping,
                state == ClimbState.CLIMB_UP ? 1.0F : state == ClimbState.DESCEND ? 0.6F : 0.0F, 0.15F);
        moving = approach(moving, step != 0.0 ? 1.0F : 0.0F, 0.15F);

        tickFacing(player, active, ropePoint, tangent);
        tickPendulum(player);

        groundTicks = active && player.onGround() ? GROUND_HOLD_TICKS : Math.max(0, groundTicks - 1);
        grounded = approach(grounded, groundTicks > 0 ? 1.0F : 0.0F, 0.2F);

        tickFidget(active && stepping < 0.05F && slide < 0.05F && grounded < 0.5F);
    }

    private void tickFidget(boolean calm) {
        if (reclampTicks > 0) reclampTicks--;
        if (!calm) {
            idleTicks = 0;
        } else if (reclampTicks == 0 && ++idleTicks >= nextFidget) {
            idleTicks = 0;
            nextFidget = FIDGET_MIN_TICKS + random.nextInt(FIDGET_RANDOM_TICKS);
            reclampSide = random.nextBoolean() ? -1.0F : 1.0F;
            reclampTicks = RECLAMP_TICKS;
        }
    }

    private void tickFacing(Player player, boolean active, Vec3 ropePoint, Vec3 tangent) {
        float target = player.getYHeadRot();
        ropeOffset = null;
        if (ropePoint != null) {
            double pivotHeight = (MODEL_ORIGIN_Y - PIVOT_Y) * PX * player.getScale();
            Vec3 atPivot = ropePoint;
            if (tangent != null && Math.abs(tangent.y) > 1.0e-3) {
                atPivot = ropePoint.add(tangent.scale((player.getY() + pivotHeight - ropePoint.y) / tangent.y));
            }
            ropeOffset = new Vec3(atPivot.x - player.getX(), 0.0, atPivot.z - player.getZ());
            if (ropeOffset.horizontalDistanceSqr() > 0.0025) {
                target = (float) Math.toDegrees(Math.atan2(-ropeOffset.x, ropeOffset.z));
            }
        }
        if (ropeOffsetO == null) ropeOffsetO = ropeOffset;
        facing = facingKnown ? Mth.rotLerp(0.5F, facing, target) : target;
        facingKnown = true;
        if (active) player.yBodyRot = facing;
    }

    private void tickPendulum(Player player) {
        Vec3 pos = player.position();
        Vec3 frameVelocity = prevPos == null ? Vec3.ZERO : pos.subtract(prevPos);
        prevPos = pos;
        Vec3 previous = velocity;
        velocity = velocity.lerp(frameVelocity, 0.5);
        Vec3 accel = velocity.subtract(previous);

        double yaw = Math.toRadians(facing);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        accelForward = (float) (accel.x * fx + accel.z * fz);
        accelRight = (float) (-accel.x * fz + accel.z * fx);
        float velForward = (float) (velocity.x * fx + velocity.z * fz);
        float velRight = (float) (-velocity.x * fz + velocity.z * fx);

        float calm = 1.0F - 0.6F * slide;
        float pitchTarget = Mth.clamp(accelForward * PENDULUM_ACCEL_GAIN + velForward * PENDULUM_DRAG_GAIN,
                -PENDULUM_LIMIT, PENDULUM_LIMIT)
                + calm * 0.025F * Mth.sin(age * 0.07F)
                + 0.05F * slide;
        float rollTarget = -Mth.clamp(accelRight * PENDULUM_ACCEL_GAIN + velRight * PENDULUM_DRAG_GAIN,
                -PENDULUM_LIMIT, PENDULUM_LIMIT)
                + calm * 0.02F * Mth.sin(age * 0.045F + 1.3F);

        pitchVel += (pitchTarget - pitch) * PENDULUM_STIFFNESS - pitchVel * PENDULUM_DAMPING;
        rollVel += (rollTarget - roll) * PENDULUM_STIFFNESS - rollVel * PENDULUM_DAMPING;
        pitch += pitchVel;
        roll += rollVel;
    }

    private void advanceHands(double step) {
        if (step == 0.0) {
            phaseR = settle(phaseR);
            phaseL = settle(phaseL);
            return;
        }
        direction = step > 0.0 ? 1 : -1;
        phaseR = advance(phaseR, step, swinging(phaseL));
        phaseL = advance(phaseL, step, swinging(phaseR));
        if (!swinging(phaseR) && !swinging(phaseL)) {
            double error = wrapHalf(phaseL - phaseR - 0.5);
            phaseL -= Mth.clamp(error, -RESYNC_STEP, RESYNC_STEP);
        }
    }

    // A hand may not let go while the other one is still reaching, so one hand always holds the rope.
    private static double advance(double phase, double step, boolean otherSwinging) {
        double next = phase + step;
        if (otherSwinging && !swinging(phase) && swinging(next)) {
            double base = Math.floor(phase);
            next = step > 0.0 ? base + PLANTED - PHASE_EPSILON : base + PHASE_EPSILON;
        }
        return next;
    }

    private double settle(double phase) {
        if (!swinging(phase)) return phase;
        double base = Math.floor(phase);
        return direction > 0
                ? Math.min(phase + SETTLE_STEP, base + 1.0)
                : Math.max(phase - SETTLE_STEP, base + PLANTED - PHASE_EPSILON);
    }

    Pose sample(float partialTick) {
        float w = Mth.lerp(partialTick, weightO, weight);
        double pR = Mth.lerp(partialTick, phaseRO, phaseR);
        double pL = Mth.lerp(partialTick, phaseLO, phaseL);
        float sl = Mth.lerp(partialTick, slideO, slide);
        float st = Mth.lerp(partialTick, steppingO, stepping);
        float mv = Mth.lerp(partialTick, movingO, moving);
        float t = age + partialTick;
        float climb = st * (1.0F - sl);

        float reachR = reach(pR);
        float reachL = reach(pL);
        Vector3f handR = hand(pR, -1.0F).lerp(slideHand(-1.0F, t), sl);
        Vector3f handL = hand(pL, 1.0F).lerp(slideHand(1.0F, t), sl);

        float g = Mth.lerp(partialTick, groundedO, grounded);
        float reclamp = reclampTicks > 0 || reclampTicksO > 0
                ? Mth.sin((1.0F - Mth.lerp(partialTick, reclampTicksO, reclampTicks) / RECLAMP_TICKS) * Mth.PI)
                : 0.0F;
        Vector3f footR = foot(pL, -1.0F, st, sl, reclampSide < 0.0F ? reclamp : 0.0F);
        Vector3f footL = foot(pR, 1.0F, st, sl, reclampSide > 0.0F ? reclamp : 0.0F);

        float upright = 1.0F - g;
        float swayPitch = (Mth.lerp(partialTick, pitchO, pitch) + sl * 0.012F * Mth.sin(t * 1.9F)) * upright;
        float swayRoll = (Mth.lerp(partialTick, rollO, roll) + 0.035F * (reachL - reachR) * climb) * upright;
        Quaternionf sway = new Quaternionf().rotationZYX(swayRoll, 0.0F, swayPitch);

        float lift = 0.4F * climb * Mth.sin((float) (pR * Math.PI * 4.0));
        float twist = -TWIST * (reachR - reachL) * (1.0F - sl) + 0.03F * Mth.sin(t * 0.05F) * (1.0F - mv);
        float headPitch = -0.12F * climb + 0.18F * sl;
        float breath = 0.3F * Mth.sin(t * 0.09F) * (1.0F - 0.5F * mv);

        Vec3 offset = ropeOffset == null || ropeOffsetO == null ? ropeOffset : ropeOffsetO.lerp(ropeOffset, partialTick);
        return new Pose(w, LEAN * upright, sway, lift, twist, headPitch, breath, g,
                handR, handL, footR, footL, offset);
    }

    private static Vector3f hand(double phase, float side) {
        float f = frac(phase);
        float x = side * HAND_X;
        float y;
        float z = ROPE_Z;
        if (f < PLANTED) {
            y = Mth.lerp(f / PLANTED, GRIP_TOP, GRIP_BOTTOM);
        } else {
            float s = (f - PLANTED) / SWING;
            float arc = Mth.sin(s * Mth.PI);
            y = Mth.lerp(easeInOut(s), GRIP_BOTTOM, GRIP_TOP) - 1.2F * arc;
            x += side * 1.6F * arc;
            z -= 1.8F * arc;
        }
        // A fist centred on the rope is deeper than the gap to the face, so ease it forward as it passes the face.
        float face = (y + 3.5F) / 4.5F;
        z -= 0.6F * Math.max(0.0F, 1.0F - face * face);
        return new Vector3f(x, y, z);
    }

    private static Vector3f slideHand(float side, float t) {
        float y = side < 0.0F ? -4.6F : -0.9F;
        return new Vector3f(side * 0.7F, y + 0.25F * Mth.sin(t * 2.1F + side), ROPE_Z);
    }

    // Both feet clamp the rope. While the opposite hand holds, the leg extends as the body rises past the foot;
    // while that hand swings, the foot lets go, steps up and clamps again.
    private static Vector3f foot(double phase, float side, float stepping, float slide, float reclamp) {
        float f = frac(phase);
        float rise;
        float release;
        if (f < PLANTED) {
            rise = 1.0F - f / PLANTED;
            release = 0.0F;
        } else {
            float sw = (f - PLANTED) / SWING;
            rise = easeInOut(sw);
            release = Mth.sin(sw * Mth.PI);
        }
        Vector3f clamp = new Vector3f(side * FOOT_X, FOOT_Y, ROPE_Z);
        Vector3f step = new Vector3f(
                side * (FOOT_X + FOOT_RELEASE_X * release),
                FOOT_Y - FOOT_RISE * rise,
                ROPE_Z + FOOT_RELEASE_Z * release);
        clamp.lerp(step, stepping);
        clamp.add(side * FOOT_RELEASE_X * reclamp, -RECLAMP_LIFT * reclamp, FOOT_RELEASE_Z * reclamp);

        Vector3f slid = new Vector3f(side * FOOT_X, SLIDE_FOOT_Y, ROPE_Z);
        return clamp.lerp(slid, slide);
    }

    private static float reach(double phase) {
        float f = frac(phase);
        return f < PLANTED ? 0.0F : Mth.sin((f - PLANTED) / SWING * Mth.PI);
    }

    private static boolean swinging(double phase) {
        return frac(phase) >= PLANTED;
    }

    private static float frac(double phase) {
        return (float) (phase - Math.floor(phase));
    }

    private static double wrapHalf(double value) {
        return value - Math.floor(value + 0.5);
    }

    private static float easeInOut(float t) {
        return 0.5F - 0.5F * Mth.cos(Mth.clamp(t, 0.0F, 1.0F) * Mth.PI);
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }
}
