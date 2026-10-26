package dev.nei.render;

import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Minimal {@code DrawContext} stand-in for Minecraft 1.19.x, where the class does not exist yet.
 *
 * <p>MaEMI's panel renderer is written against the 1.20+ {@code DrawContext} API. Rather than fork
 * every drawing call for older versions, this shim exposes the same handful of methods and forwards
 * them to the {@link MatrixStack}-based helpers that 1.19.x ships. The render entry points build one
 * of these per frame.
 */
public final class DrawContext {
	private final MatrixStack matrices;

	private DrawContext(MatrixStack matrices) {
		this.matrices = matrices;
	}

	public static DrawContext of(MatrixStack matrices) {
		return new DrawContext(matrices);
	}

	public MatrixStack getMatrices() {
		return matrices;
	}

	/** No-op: 1.19.x helpers flush their own buffers. */
	public void flush() {
	}

	public void fill(int x1, int y1, int x2, int y2, int color) {
		DrawableHelper.fill(matrices, x1, y1, x2, y2, color);
	}

	public void drawBorder(int x, int y, int width, int height, int color) {
		DrawableHelper.drawBorder(matrices, x, y, width, height, color);
	}

	public void drawText(TextRenderer textRenderer, String text, int x, int y, int color, boolean shadow) {
		if (shadow) {
			textRenderer.drawWithShadow(matrices, text, x, y, color);
		} else {
			textRenderer.draw(matrices, text, x, y, color);
		}
	}

	public void drawText(TextRenderer textRenderer, Text text, int x, int y, int color, boolean shadow) {
		if (shadow) {
			textRenderer.drawWithShadow(matrices, text, x, y, color);
		} else {
			textRenderer.draw(matrices, text, x, y, color);
		}
	}

	public void drawTextWithShadow(TextRenderer textRenderer, Text text, int x, int y, int color) {
		textRenderer.drawWithShadow(matrices, text, x, y, color);
	}

	public void drawTextWithShadow(TextRenderer textRenderer, String text, int x, int y, int color) {
		textRenderer.drawWithShadow(matrices, text, x, y, color);
	}

	public void drawCenteredTextWithShadow(TextRenderer textRenderer, Text text, int centerX, int y, int color) {
		DrawableHelper.drawCenteredTextWithShadow(matrices, textRenderer, text, centerX, y, color);
	}

	public void drawCenteredTextWithShadow(TextRenderer textRenderer, String text, int centerX, int y, int color) {
		DrawableHelper.drawCenteredTextWithShadow(matrices, textRenderer, text, centerX, y, color);
	}

	public void drawItem(ItemStack stack, int x, int y) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.getItemRenderer().renderInGuiWithOverrides(matrices, stack, x, y);
	}

	public void drawItemInSlot(TextRenderer textRenderer, ItemStack stack, int x, int y) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.getItemRenderer().renderGuiItemOverlay(matrices, textRenderer, stack, x, y);
	}

	public void drawTooltip(TextRenderer textRenderer, List<Text> lines, int mouseX, int mouseY) {
		Screen screen = MinecraftClient.getInstance().currentScreen;
		if (screen != null) {
			screen.renderTooltip(matrices, lines, mouseX, mouseY);
		}
	}
}
