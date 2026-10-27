package dev.nei.mixin;

import net.minecraft.client.gui.widget.ClickableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only view of a widget's origin on 1.19.x.
 *
 * <p>{@code ClickableWidget} only gained public {@code getX()}/{@code getY()} in 1.19.3; on this
 * branch the fields are protected, so the calculator cannot read EMI's search-field position through
 * the widget API. {@code getWidth()}/{@code getHeight()} are already public and are not repeated
 * here.</p>
 *
 * <p>This interface must be registered in {@code nei.mixins.json} under {@code client} for the mixin
 * to be applied.</p>
 */
@Mixin(ClickableWidget.class)
public interface ClickableWidgetAccessor {
	@Accessor("x")
	int nei$getX();

	@Accessor("y")
	int nei$getY();
}
