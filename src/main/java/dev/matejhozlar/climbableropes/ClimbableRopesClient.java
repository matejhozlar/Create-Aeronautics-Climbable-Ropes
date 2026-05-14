package dev.matejhozlar.climbableropes;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(value = ClimbableRopes.MODID, dist = Dist.CLIENT)
public class ClimbableRopesClient {
    public ClimbableRopesClient(IEventBus modEventBus) {
        modEventBus.addListener(ClimbableRopesKeybinds::register);
    }
}
