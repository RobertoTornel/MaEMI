package dev.nei.forge;

import dev.nei.Nei;
import net.minecraftforge.fml.common.Mod;

@Mod(Nei.MOD_ID)
public final class NeiForge {
	public NeiForge() {
		Nei.init(new ForgePlatform());
	}
}
