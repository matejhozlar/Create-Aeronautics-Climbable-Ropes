package dev.matejhozlar.climbableropes.client.ride;

import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;

final class RopeMotion {
    // A rope moving further than this in one tick (60 m/s) is a resync jump, not motion to chase.
    private static final double MAX_CARRY_PER_TICK = 3.0;

    private RopeMotion() {}

    // Sable already carries a player standing on (or recently launched from) a sub-level, outside of
    // deltaMovement, so only the part of the rope's motion the player does not inherit is applied.
    static Vec3 carry(LocalPlayer player, Vec3 ropeVelocity) {
        if (ropeVelocity.lengthSqr() > MAX_CARRY_PER_TICK * MAX_CARRY_PER_TICK) return Vec3.ZERO;
        Vector3dc inherited = ((LivingEntityMovementExtension) player).sable$getInheritedVelocity();
        return ropeVelocity.subtract(inherited.x(), inherited.y(), inherited.z());
    }
}
