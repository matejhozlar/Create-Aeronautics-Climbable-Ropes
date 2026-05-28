package dev.matejhozlar.climbableropes.client;

import dev.matejhozlar.climbableropes.ClimbableRopes;
import dev.matejhozlar.climbableropes.ClimbableRopesConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = ClimbableRopes.MODID, dist = Dist.CLIENT)
public class ClimbableRopesClient {
    public ClimbableRopesClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(ClimbableRopesKeybinds::register);
        container.registerConfig(ModConfig.Type.CLIENT, ClimbableRopesConfig.CLIENT_SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
