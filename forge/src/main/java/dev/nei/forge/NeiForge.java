package dev.nei.forge;

import dev.nei.Nei;
import dev.nei.NeiClient;
import dev.nei.NeiKeybinds;
import net.minecraft.client.MinecraftClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Nei.MOD_ID)
public final class NeiForge {
	private static ForgePlatform platform;

	public NeiForge() {
		if (FMLEnvironment.dist != Dist.CLIENT) {
			return;
		}
		platform = new ForgePlatform();
		Nei.init(platform);
		NeiClient.init(platform);
		NeiKeybinds.registerAll(platform);

		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerKeys);
		MinecraftForge.EVENT_BUS.addListener(this::clientTick);
		ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
			() -> new ConfigScreenHandler.ConfigScreenFactory((client, parent) -> dev.nei.config.ConfigScreens.create(parent)));
	}

	private void clientSetup(FMLClientSetupEvent event) {
	}

	private void registerKeys(RegisterKeyMappingsEvent event) {
		for (ForgePlatform.PendingBind bind : platform.pendingBinds()) {
			event.register(bind.create());
		}
	}

	private void clientTick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.END && MinecraftClient.getInstance().player != null) {
			NeiKeybinds.tick();
		}
	}
}
