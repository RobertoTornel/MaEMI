package dev.nei.config;

import java.util.Map;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Builds the settings screen with Cloth Config. Every entry writes straight into {@link #data()}
 * and the whole file is flushed when the screen closes.
 */
public final class NeiConfigScreen {
	private NeiConfigScreen() {
	}

	public static Screen create(Screen parent) {
		NeiConfigData d = NeiConfig.get();
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Text.translatable("nei.config.title"))
			.setSavingRunnable(NeiConfig::save);
		ConfigEntryBuilder eb = builder.entryBuilder();

		ConfigCategory general = builder.getOrCreateCategory(Text.translatable("nei.config.general"));
		general.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.enabled"), d.general.enabled)
			.setDefaultValue(true)
			.setTooltip(Text.translatable("nei.config.enabled.tooltip"))
			.setSaveConsumer(v -> d.general.enabled = v)
			.build());
		general.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.show_everywhere"), d.general.showEverywhere)
			.setDefaultValue(false)
			.setSaveConsumer(v -> d.general.showEverywhere = v)
			.build());
		general.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.log_compat"), d.general.logCompatReport)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.general.logCompatReport = v)
			.build());

		ConfigCategory panel = builder.getOrCreateCategory(Text.translatable("nei.config.panel"));
		panel.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.panel.enabled"), d.panel.enabled)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.panel.enabled = v)
			.build());
		panel.addEntry(eb.startEnumSelector(Text.translatable("nei.config.panel.side"), NeiConfigData.Side.class, d.panel.side)
			.setDefaultValue(NeiConfigData.Side.LEFT)
			.setEnumNameProvider(side -> Text.translatable("nei.config.side." + side.name().toLowerCase()))
			.setSaveConsumer(v -> d.panel.side = v)
			.build());
		panel.addEntry(eb.startIntSlider(Text.translatable("nei.config.panel.columns"), d.panel.columns, 4, 12)
			.setDefaultValue(9)
			.setSaveConsumer(v -> d.panel.columns = v)
			.build());
		panel.addEntry(eb.startIntSlider(Text.translatable("nei.config.panel.rows"), d.panel.rows, 2, 20)
			.setDefaultValue(8)
			.setSaveConsumer(v -> d.panel.rows = v)
			.build());
		panel.addEntry(eb.startEnumSelector(Text.translatable("nei.config.panel.sort"), NeiConfigData.Sort.class, d.panel.sort)
			.setDefaultValue(NeiConfigData.Sort.MOD)
			.setEnumNameProvider(sort -> Text.translatable("nei.config.sort." + sort.name().toLowerCase()))
			.setSaveConsumer(v -> d.panel.sort = v)
			.build());
		panel.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.panel.item_count"), d.panel.showItemCount)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.panel.showItemCount = v)
			.build());
		panel.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.panel.item_ids"), d.panel.showItemIds)
			.setDefaultValue(false)
			.setSaveConsumer(v -> d.panel.showItemIds = v)
			.build());
		panel.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.panel.hidden"), d.panel.showHiddenItems)
			.setDefaultValue(false)
			.setSaveConsumer(v -> d.panel.showHiddenItems = v)
			.build());
		panel.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.panel.tabs"), d.panel.showTabs)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.panel.showTabs = v)
			.build());
		panel.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.panel.replace_emi"), d.panel.replaceEmiIndex)
			.setDefaultValue(false)
			.setTooltip(Text.translatable("nei.config.panel.replace_emi.tooltip"))
			.setSaveConsumer(v -> {
				d.panel.replaceEmiIndex = v;
				dev.nei.integration.EmiPanelBridge.sync();
			})
			.build());

		ConfigCategory search = builder.getOrCreateCategory(Text.translatable("nei.config.search"));
		search.addEntry(eb.startEnumSelector(Text.translatable("nei.config.search.mode"), NeiConfigData.SearchMode.class, d.search.mode)
			.setDefaultValue(NeiConfigData.SearchMode.WILDCARD)
			.setEnumNameProvider(mode -> Text.translatable("nei.config.search.mode." + mode.name().toLowerCase()))
			.setSaveConsumer(v -> d.search.mode = v)
			.build());
		search.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.search.tooltips"), d.search.searchTooltipsByDefault)
			.setDefaultValue(false)
			.setSaveConsumer(v -> d.search.searchTooltipsByDefault = v)
			.build());

		ConfigCategory autocraft = builder.getOrCreateCategory(Text.translatable("nei.config.autocraft"));
		autocraft.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.autocraft.enabled"), d.autocraft.enabled)
			.setDefaultValue(true)
			.setTooltip(Text.translatable("nei.config.autocraft.enabled.tooltip"))
			.setSaveConsumer(v -> d.autocraft.enabled = v)
			.build());
		autocraft.addEntry(eb.startIntField(Text.translatable("nei.config.autocraft.depth"), d.autocraft.maxDepth)
			.setDefaultValue(12)
			.setMin(1)
			.setMax(64)
			.setSaveConsumer(v -> d.autocraft.maxDepth = v)
			.build());
		autocraft.addEntry(eb.startIntField(Text.translatable("nei.config.autocraft.max"), d.autocraft.maxCrafts)
			.setDefaultValue(512)
			.setMin(1)
			.setMax(4096)
			.setSaveConsumer(v -> d.autocraft.maxCrafts = v)
			.build());
		autocraft.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.autocraft.stations"), d.autocraft.allowOtherStations)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.autocraft.allowOtherStations = v)
			.build());
		autocraft.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.autocraft.pull"), d.autocraft.pullFromInventory)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.autocraft.pullFromInventory = v)
			.build());

		ConfigCategory cheat = builder.getOrCreateCategory(Text.translatable("nei.config.cheat"));
		cheat.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.cheat.enabled"), d.cheat.enabled)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.cheat.enabled = v)
			.build());
		cheat.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.cheat.give"), d.cheat.giveStacks)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.cheat.giveStacks = v)
			.build());
		cheat.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.cheat.delete"), d.cheat.deleteItems)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.cheat.deleteItems = v)
			.build());
		cheat.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.cheat.utility"), d.cheat.utilityButtons)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.cheat.utilityButtons = v)
			.build());

		ConfigCategory tooltip = builder.getOrCreateCategory(Text.translatable("nei.config.tooltip"));
		tooltip.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.tooltip.enabled"), d.tooltip.enabled)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.tooltip.enabled = v)
			.build());
		tooltip.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.tooltip.mod"), d.tooltip.showModName)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.tooltip.showModName = v)
			.build());
		tooltip.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.tooltip.id"), d.tooltip.showRegistryName)
			.setDefaultValue(false)
			.setSaveConsumer(v -> d.tooltip.showRegistryName = v)
			.build());
		tooltip.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.tooltip.tags"), d.tooltip.showTags)
			.setDefaultValue(false)
			.setSaveConsumer(v -> d.tooltip.showTags = v)
			.build());
		tooltip.addEntry(eb.startBooleanToggle(Text.translatable("nei.config.tooltip.hint"), d.tooltip.showRecipeHint)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.tooltip.showRecipeHint = v)
			.build());

		ConfigCategory compat = builder.getOrCreateCategory(Text.translatable("nei.config.compat"));
		compat.addEntry(eb.startTextDescription(Text.translatable("nei.config.compat.description")).build());
		for (Map.Entry<String, String> entry : d.compat.modules.entrySet()) {
			compat.addEntry(eb.startStrField(Text.literal(entry.getKey()), entry.getValue())
				.setDefaultValue("auto")
				.setSaveConsumer(v -> entry.setValue(v))
				.build());
		}

		return builder.build();
	}
}