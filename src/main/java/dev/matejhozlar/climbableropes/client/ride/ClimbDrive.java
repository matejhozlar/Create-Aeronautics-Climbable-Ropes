package dev.matejhozlar.climbableropes.client.ride;

import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import dev.matejhozlar.climbableropes.client.ClimbAnimationController.ClimbState;
import net.minecraft.client.Minecraft;

final class ClimbDrive {
    private static final double AT_END_ARC_EPSILON = 0.2;

    private int bottomGroundedTimer;
    private boolean parkedAtBottom;
    private double slideVelocity;

    record Keys(boolean up, boolean down, boolean sprint, boolean dismount, boolean jump) {
        static Keys read(Minecraft mc) {
            return new Keys(
                    mc.options.keyUp.isDown(),
                    mc.options.keyDown.isDown(),
                    mc.options.keySprint.isDown(),
                    mc.options.keyShift.isDown(),
                    mc.options.keyJump.isDown());
        }
    }

    record Remaining(double forward, double backward) {}

    record Step(double speedAlong, ClimbState animState) {}

    void reset() {
        bottomGroundedTimer = 0;
        parkedAtBottom = false;
        slideVelocity = 0.0;
    }

    boolean groundedAtBottomTooLong(boolean onGround, boolean upHeld, double anchorY, double bottomY) {
        if (onGround && !upHeld && anchorY < bottomY + ClimbableRopesConfig.BOTTOM_DISMOUNT_OFFSET.get()) {
            return ++bottomGroundedTimer > ClimbableRopesConfig.BOTTOM_GROUNDED_DISMOUNT_TICKS.get();
        }
        bottomGroundedTimer = 0;
        return false;
    }

    Step step(Keys keys, boolean onGround, double verticalComponent, Remaining remaining) {
        boolean climbUp = keys.up() && remaining.forward() > 0.0;
        // onGround and the arc test both flicker per tick, so they latch this rather than gating descent live.
        if (climbUp) parkedAtBottom = false;
        else if (remaining.backward() <= AT_END_ARC_EPSILON || onGround) parkedAtBottom = true;
        boolean descentBlocked = parkedAtBottom;

        double climbSpeed = ClimbableRopesConfig.CLIMB_SPEED.get();
        double descendSpeed = ClimbableRopesConfig.DESCEND_SPEED.get();
        double slideSpeed = ClimbableRopesConfig.SLIDE_SPEED.get();
        double slideAccel = ClimbableRopesConfig.SLIDE_ACCELERATION.get();
        double slideDecel = ClimbableRopesConfig.SLIDE_DECELERATION.get();

        boolean slideEffective = keys.down() && keys.sprint() && !descentBlocked
                && slideSpeed * verticalComponent > descendSpeed;
        if (climbUp || descentBlocked) {
            slideVelocity = 0.0;
        } else if (slideEffective) {
            if (slideVelocity < descendSpeed) slideVelocity = descendSpeed;
            slideVelocity = Math.min(slideSpeed * verticalComponent, slideVelocity + slideAccel * verticalComponent);
        } else if (slideVelocity > 0) {
            slideVelocity = Math.max(0.0, slideVelocity - slideDecel);
        }

        double speedAlong;
        if (climbUp) speedAlong = Math.min(climbSpeed, remaining.forward());
        else if (descentBlocked) speedAlong = 0.0;
        else if (slideVelocity > descendSpeed) speedAlong = -slideVelocity;
        else if (keys.down()) speedAlong = -descendSpeed;
        else if (slideVelocity > 0) speedAlong = -slideVelocity;
        else speedAlong = 0.0;
        if (speedAlong < 0.0) speedAlong = -Math.min(-speedAlong, remaining.backward());

        ClimbState animState;
        if (climbUp) animState = ClimbState.CLIMB_UP;
        else if (slideVelocity > descendSpeed) animState = ClimbState.SLIDE;
        else if (speedAlong < 0.0) animState = ClimbState.DESCEND;
        else animState = ClimbState.IDLE;
        return new Step(speedAlong, animState);
    }
}
