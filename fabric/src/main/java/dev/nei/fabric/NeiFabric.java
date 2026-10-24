package dev.nei.fabric;

import dev.nei.Nei;
import dev.nei.NeiClient;
import dev.nei.NeiKeybinds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class NeiFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		FabricPlatform platform = new FabricPlatform();
		Nei.init(platform);
		NeiClient.init(platform);
		NeiKeybinds.registerAll(platform);
		ClientTickEvents.END_CLIENT_TICK.register(client -> NeiKeybinds.tick());
	}
}
