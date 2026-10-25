package dev.nei.panel;

import org.lwjgl.glfw.GLFW;

import dev.nei.item.ItemSubset;
import dev.nei.item.ItemSubsetList;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Small modal for renaming or deleting a user subset, opened by shift+right-clicking its tab.
 * Renaming rejects blank or duplicate names with an inline message and keeps the selection on the
 * renamed subset. Deleting falls back to the {@code MaEMI} tab. Closing returns to the screen that
 * opened it.
 */
public final class SubsetEditScreen extends Screen {
	private static final int PANEL_WIDTH = 240;
	private static final int PANEL_HEIGHT = 142;
	private static final int BUTTON_WIDTH = 64;
	private static final int BUTTON_GAP = 4;
	private static final int MAX_NAME_LENGTH = 48;

	private final Screen parent;
	private String subsetName;
	private String draft;
	private Text error;
	private TextFieldWidget nameField;

	public SubsetEditScreen(Screen parent, String subsetName) {
		super(Text.translatable("nei.subset.title"));
		this.parent = parent;
		this.subsetName = subsetName == null ? "" : subsetName;
		this.draft = this.subsetName;
	}

	@Override
	protected void init() {
		int left = (width - PANEL_WIDTH) / 2;
		int top = (height - PANEL_HEIGHT) / 2;
		nameField = new TextFieldWidget(textRenderer, left + 20, top + 50, PANEL_WIDTH - 40, 20,
				Text.translatable("nei.subset.name"));
		nameField.setMaxLength(MAX_NAME_LENGTH);
		nameField.setText(draft);
		nameField.setChangedListener(text -> {
			draft = text;
			error = null;
		});
		addDrawableChild(nameField);

		int x = left + 20;
		int y = top + 108;
		addDrawableChild(ButtonWidget.builder(Text.translatable("nei.subset.rename"), button -> rename())
				.dimensions(x, y, BUTTON_WIDTH, 20)
				.build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("nei.subset.delete"), button -> delete())
				.dimensions(x + BUTTON_WIDTH + BUTTON_GAP, y, BUTTON_WIDTH, 20)
				.build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("nei.subset.cancel"), button -> close())
				.dimensions(x + 2 * (BUTTON_WIDTH + BUTTON_GAP), y, BUTTON_WIDTH, 20)
				.build());

		setInitialFocus(nameField);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		int left = (width - PANEL_WIDTH) / 2;
		int top = (height - PANEL_HEIGHT) / 2;
		context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF0101010);
		context.drawBorder(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF606060);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 12, 0xFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.translatable("nei.subset.name"), left + 20, top + 36,
				0xA0A0A0);
		if (error != null) {
			context.drawCenteredTextWithShadow(textRenderer, error, width / 2, top + 80, 0xFF5555);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
				&& nameField != null && nameField.isFocused()) {
			rename();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}

	private void rename() {
		draft = nameField.getText();
		String name = draft.trim();
		if (name.isEmpty()) {
			error = Text.translatable("nei.subset.name_empty");
			return;
		}

		ItemSubsetList list = ItemSubsetList.get();
		ItemSubset subset = list.byName(subsetName);
		if (subset == null) {
			// The subset disappeared (another screen deleted it); there is nothing to rename.
			close();
			return;
		}
		if (!name.equals(subsetName) && list.isNameTaken(name)) {
			error = Text.translatable("nei.subset.name_taken", name);
			return;
		}

		ItemSubset renamed = list.rename(subset, name);
		if (renamed == null) {
			error = Text.translatable("nei.subset.name_taken", name);
			return;
		}

		NeiPanelState state = NeiPanel.get().state();
		if (subsetName.equals(state.selectedSubset())) {
			state.setSelectedSubset(renamed.name());
		}
		subsetName = renamed.name();
		draft = subsetName;
		ItemPanelSource.get().invalidate();
		close();
	}

	private void delete() {
		ItemSubsetList list = ItemSubsetList.get();
		ItemSubset subset = list.byName(subsetName);
		if (subset != null && list.delete(subset)) {
			NeiPanelState state = NeiPanel.get().state();
			// Deleting a subset always falls back to the MaEMI tab, even if another tab was selected.
			state.setSelectedSubset(null);
			state.resetPage();
			dev.nei.util.Messages.status(Text.translatable("nei.subset.deleted", subsetName));
			ItemPanelSource.get().invalidate();
		}
		close();
	}
}