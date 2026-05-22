package dev.matejhozlar.climbableropes;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClimbableRopesConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue CLIMB_SPEED;
    public static final ModConfigSpec.DoubleValue DESCEND_SPEED;
    public static final ModConfigSpec.DoubleValue JUMP_OFF_VELOCITY;
    public static final ModConfigSpec.DoubleValue MAX_CLIMB_ANGLE_FROM_VERTICAL;

    public static final ModConfigSpec.DoubleValue SLIDE_SPEED;
    public static final ModConfigSpec.DoubleValue SLIDE_ACCELERATION;
    public static final ModConfigSpec.DoubleValue SLIDE_DECELERATION;

    public static final ModConfigSpec.BooleanValue ALLOW_VERTICAL_ROPE_CLIMBING;
    public static final ModConfigSpec.BooleanValue ALLOW_PLUNGER_CLIMBING;
    public static final ModConfigSpec.BooleanValue ALLOW_PLUNGER_ZIPLINE;
    public static final ModConfigSpec.BooleanValue ALLOW_BLOCK_MANTLE;

    public static final ModConfigSpec.DoubleValue SNAP_PULL;
    public static final ModConfigSpec.DoubleValue SNAP_VELOCITY_CAP;
    public static final ModConfigSpec.DoubleValue MAX_LEASH_DISTANCE;
    public static final ModConfigSpec.DoubleValue BOTTOM_DISMOUNT_OFFSET;
    public static final ModConfigSpec.IntValue BOTTOM_GROUNDED_DISMOUNT_TICKS;
    public static final ModConfigSpec.DoubleValue ROPE_HOVER_RADIUS;

    public static final ModConfigSpec.BooleanValue ENABLE_CLIMB_ANIMATION;
    public static final ModConfigSpec.DoubleValue ANIMATION_SPEED_MULTIPLIER;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Climbing motion (blocks per tick).").push("climbing");
        CLIMB_SPEED = b
                .comment("Vertical speed when holding forward to climb up.")
                .defineInRange("climbSpeed", 0.18, 0.0, 5.0);
        DESCEND_SPEED = b
                .comment("Vertical speed when holding back to climb down.")
                .defineInRange("descendSpeed", 0.22, 0.0, 5.0);
        JUMP_OFF_VELOCITY = b
                .comment("Upward impulse applied when jumping off the rope.")
                .defineInRange("jumpOffVelocity", 0.42, 0.0, 5.0);
        MAX_CLIMB_ANGLE_FROM_VERTICAL = b
                .comment(
                        "Maximum angle from vertical (in degrees) at which a hanging rope segment can be grabbed for climbing.",
                        "0 = only perfectly vertical ropes; 90 = any angle including horizontal. Increase to climb diagonal lines.")
                .defineInRange("maxClimbAngleFromVertical", 90.0, 0.0, 90.0);
        b.pop();

        b.comment("Sprint-while-descending slide mechanics.").push("sliding");
        SLIDE_SPEED = b
                .comment("Maximum slide speed.")
                .defineInRange("slideSpeed", 1.2, 0.0, 10.0);
        SLIDE_ACCELERATION = b
                .comment("How quickly the slide ramps up to slideSpeed.")
                .defineInRange("slideAcceleration", 0.05, 0.0, 1.0);
        SLIDE_DECELERATION = b
                .comment("How quickly the slide eases out after releasing back.")
                .defineInRange("slideDeceleration", 0.04, 0.0, 1.0);
        b.pop();

        b.comment("Toggle individual climbing features.").push("features");
        ALLOW_VERTICAL_ROPE_CLIMBING = b
                .comment("Allow climbing vertical hanging ropes.")
                .define("allowVerticalRopeClimbing", true);
        ALLOW_PLUNGER_CLIMBING = b
                .comment("Allow climbing rope lines between two plungers.")
                .define("allowPlungerClimbing", true);
        ALLOW_PLUNGER_ZIPLINE = b
                .comment("Allow ziplining along plunger rope lines while holding a CHAIN_RIDEABLE-tagged item (e.g. Create's wrench), mirroring Simulated's existing zipline on hanging rope strands.")
                .define("allowPlungerZipline", true);
        ALLOW_BLOCK_MANTLE = b
                .comment("Allow mantling onto the block above the rope when jumping off at its top end. When disabled, jumping off at the top performs a normal upward impulse instead.")
                .define("allowBlockMantle", true);
        b.pop();

        b.comment(
                "Advanced physics and targeting tuning. These affect how climbing feels and when you are forced off a rope.",
                "Defaults preserve the standard behavior; change at your own risk.").push("advanced");
        SNAP_PULL = b
                .comment("How aggressively the spring drags you toward the rope each tick.")
                .defineInRange("snapPull", 0.55, 0.0, 5.0);
        SNAP_VELOCITY_CAP = b
                .comment("Maximum per-tick velocity the snap spring can contribute.")
                .defineInRange("snapVelocityCap", 0.35, 0.0, 5.0);
        MAX_LEASH_DISTANCE = b
                .comment("Distance (in blocks) from the rope at which external forces dismount you.")
                .defineInRange("maxLeashDistance", 3.0, 0.0, 32.0);
        BOTTOM_DISMOUNT_OFFSET = b
                .comment("How close (in blocks) to the lower endpoint counts as \"at the bottom\" for the grounded auto-dismount.")
                .defineInRange("bottomDismountOffset", 0.6, 0.0, 5.0);
        BOTTOM_GROUNDED_DISMOUNT_TICKS = b
                .comment("Ticks of ground contact at the bottom of a rope before you are auto-dismounted.")
                .defineInRange("bottomGroundedDismountTicks", 5, 0, 200);
        ROPE_HOVER_RADIUS = b
                .comment("Raycast hitbox radius (in blocks) for rope hover detection. Larger values make ropes easier to aim at.")
                .defineInRange("ropeHoverRadius", 4.0 / 16.0, 0.0, 2.0);
        b.pop();

        b.comment("Player climb animation playback (KosmX playerAnimator layer).").push("animation");
        ENABLE_CLIMB_ANIMATION = b
                .comment("Play the rope-climb animations on your local player while attached to a rope.")
                .define("enableClimbAnimation", true);
        ANIMATION_SPEED_MULTIPLIER = b
                .comment("Playback speed multiplier for the climb animations. 1.0 is authored speed.")
                .defineInRange("animationSpeedMultiplier", 1.0, 0.1, 5.0);
        b.pop();

        SPEC = b.build();
    }

    private ClimbableRopesConfig() {}
}
