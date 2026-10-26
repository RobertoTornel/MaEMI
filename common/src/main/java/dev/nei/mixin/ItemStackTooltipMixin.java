package dev.nei.mixin;

import java.util.List;

import dev.nei.tooltip.TooltipLines;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.item.TooltipType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds MaEMI's extra tooltip lines to every item stack tooltip.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
	@Inject(method = "getTooltip", at = @At("RETURN"), require = 1, remap = true)
	private void nei$appendTooltip(Item.TooltipContext context, PlayerEntity player, TooltipType type,
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
