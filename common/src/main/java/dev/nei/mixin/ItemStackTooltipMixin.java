package dev.nei.mixin;

import java.util.List;

import dev.nei.tooltip.TooltipLines;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds MaEMI's extra tooltip lines to every item stack tooltip.
 *
 * <p>Verified against Yarn 1.20.1 (build 10): {@code ItemStack} has no
 * {@code appendTooltip(ItemStack, World, List, TooltipContext)} method - that one belongs to
 * {@code Item} ({@code method_7851}). The tooltip entry point of {@code ItemStack} itself is
 * {@code getTooltip(PlayerEntity, TooltipContext)} ({@code method_7950}), which returns the fully
 * built list, so lines are appended there.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
	@Inject(method = "getTooltip", at = @At("RETURN"), require = 1, remap = true)
	private void nei$appendTooltip(PlayerEntity player, TooltipContext context,
			CallbackInfoReturnable<List<Text>> cir) {
		List<Text> tooltip;
		try {
			tooltip = cir.getReturnValue();
		} catch (Throwable t) {
			return;
		}
		if (tooltip == null) {
			return;
		}
		TooltipLines.append((ItemStack) (Object) this, tooltip);
	}
}
