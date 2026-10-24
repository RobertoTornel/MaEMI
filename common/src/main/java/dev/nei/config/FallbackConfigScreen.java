package dev.nei.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Shown instead of the Cloth Config screen when Cloth Config is absent. It explains where the JSON
 * file lives and lets the player reload it.
 */
public final class FallbackConfigScreen extends Screen {
	private final Screen parent;

	public FallbackConfigScreen(Screen parent) {
		super(Text.translatable("nei.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
			.dimensions(width / 2 - 100, height - 40, 200, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 40, 0xFFFFFF);
		String path = NeiConfig.file() == null ? "config/nei.json" : NeiConfig.file().toString();
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("nei.config.fallback.cloth"), width / 2, 70, 0xFFAAAA);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal(path), width / 2, 90, 0xAAAAAA);
	}

	@Override
	public void close() {
		NeiConfig.save();
		client.setScreen(parent);
	}
}
