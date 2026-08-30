package dev.matejhozlar.climbableropes;

import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;

final class RopeMotion {
    private RopeMotion() {}

    // Sable already carries a player standing on (or recently launched from) a sub-level, outside of
    // deltaMovement, so only the part of the rope's motion the player does not inherit is applied.
    static Vec3 carry(LocalPlayer player, Vec3 ropeVelocity) {
        double maxLeash = ClimbableRopesConfig.MAX_LEASH_DISTANCE.get();
        // A rope moving further than the leash in one tick is a resync jump, not motion to chase.
        if (ropeVelocity.lengthSqr() > maxLeash * maxLeash) return Vec3.ZERO;
        Vector3dc inherited = ((LivingEntityMovementExtension) player).sable$getInheritedVelocity();
        return ropeVelocity.subtract(inherited.x(), inherited.y(), inherited.z());
    }
}
