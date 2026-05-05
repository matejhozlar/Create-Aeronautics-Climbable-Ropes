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
                .defineInRange("maxClimbAngleFromVertical", 31.79, 0.0, 90.0);
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

        SPEC = b.build();
    }

    private ClimbableRopesConfig() {}
}
