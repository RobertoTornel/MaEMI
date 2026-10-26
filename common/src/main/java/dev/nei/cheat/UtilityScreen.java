package dev.nei.cheat;

import java.util.List;

import dev.nei.render.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * A compact screen listing the cheat/utility actions MaEMI used to show as buttons under its item
 * panel. Every action is permission-checked by {@link Cheats}; this screen only presents them.
 */
public final class UtilityScreen extends Screen {
	private final Screen parent;

	public UtilityScreen(Screen parent) {
		super(Text.translatable("nei.utility.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		List<Cheats.CheatAction> actions = Cheats.actions();
		int columns = Math.min(3, Math.max(1, actions.size()));
		int buttonWidth = 150;
		int buttonHeight = 20;
		int gap = 6;
		int totalWidth = columns * buttonWidth + (columns - 1) * gap;
		int startX = (width - totalWidth) / 2;
		int startY = Math.max(40, height / 2 - (actions.size() / columns) * (buttonHeight + gap) / 2);
		for (int i = 0; i < actions.size(); i++) {
			Cheats.CheatAction action = actions.get(i);
			int x = startX + (i % columns) * (buttonWidth + gap);
			int y = startY + (i / columns) * (buttonHeight + gap);
			addDrawableChild(new ButtonWidget(x, y, buttonWidth, buttonHeight, action.label(),
				button -> action.action().accept(this)));
		}
		addDrawableChild(new ButtonWidget(width / 2 - 60, height - 30, 120, 20,
			Text.translatable("gui.done"), button -> close()));
	}

	@Override
	public void render(net.minecraft.client.util.math.MatrixStack matrices, int mouseX, int mouseY, float delta) {
		DrawContext context = DrawContext.of(matrices);
		renderBackground(matrices);
		super.render(matrices, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}
}