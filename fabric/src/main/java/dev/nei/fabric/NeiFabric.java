package dev.nei.fabric;

import dev.nei.Nei;
import net.fabricmc.api.ClientModInitializer;

public final class NeiFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Nei.init(new FabricPlatform());
	}
}
