package dev.matejhozlar.climbableropes;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(ClimbableRopes.MODID)
public class ClimbableRopes {
    public static final String MODID = "climbable_ropes";

    public ClimbableRopes(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ClimbableRopesConfig.SPEC);
    }
}
