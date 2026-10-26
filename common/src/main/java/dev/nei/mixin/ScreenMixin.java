package dev.nei.mixin;

import dev.nei.hook.NeiHooks;
import dev.nei.render.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws and feeds the calculator overlay on screens that are not container screens. Container screens
 * are handled by {@link HandledScreenMixin}, which hooks the point after the item grid; the
 * {@code instanceof} guard here is what keeps a single screen from being drawn twice.
 *
 * <p>The render hook is {@code Screen.renderWithTooltip} rather than {@code Screen.render} on
 * purpose. Vanilla's {@code renderWithTooltip} calls {@code render} and then draws the pending
 * tooltip, and on Forge it is the method {@code ForgeHooksClient.drawScreen} wraps with
 * {@code ScreenEvent.Render.Pre/Post} — so its tail is the last point still inside the vanilla screen
 * pipeline. Hooking there puts the window above EMI's sidebars, EMI's recipe overlays and tooltips
 * instead of being painted over by a tooltip. The method is {@code final}; an injection into it is
 * fine because nothing here overrides it.</p>
 *
 * <p>Mouse input is not handled here: those methods are interface defaults, so {@link MouseMixin}
 * takes them instead. Only keys are routed from this mixin, which {@code Screen} does declare.</p>
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Inject(method = "renderWithTooltip", at = @At("TAIL"))
	private void nei$renderOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!((Object) this instanceof HandledScreen<?>)) {
			NeiHooks.render(context, (Screen) (Object) this, mouseX, mouseY, delta);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void nei$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if (!((Object) this instanceof HandledScreen<?>)
				&& NeiHooks.keyPressed((Screen) (Object) this, keyCode, scanCode, modifiers)) {
			cir.setReturnValue(true);
		}
	}

}
