package dev.nei.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/**
 * Read-only view of the geometry and state fields of vanilla's {@link HandledScreen}. The MaEMI panel
 * is drawn next to the container background, so it needs the background origin and size; the handler
 * and focused slot are exposed for future slot-aware interactions.
 *
 * <p>This interface must be registered in {@code nei.mixins.json} under {@code client} for the mixin
 * to be applied.</p>
 */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
	@Accessor("x")
	int nei$getX();

	@Accessor("y")
	int nei$getY();

	@Accessor("backgroundWidth")
	int nei$getBackgroundWidth();

	@Accessor("backgroundHeight")
	int nei$getBackgroundHeight();

	@Accessor("handler")
	ScreenHandler nei$getHandler();

	@Accessor("focusedSlot")
	Slot nei$getFocusedSlot();
}
