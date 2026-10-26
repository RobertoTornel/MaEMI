package dev.nei.config;

import java.util.Map;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.text.LiteralText;

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
			.setTitle(new TranslatableText("nei.config.title"))
			.setSavingRunnable(NeiConfig::save);
		ConfigEntryBuilder eb = builder.entryBuilder();

		ConfigCategory general = builder.getOrCreateCategory(new TranslatableText("nei.config.general"));
		general.addEntry(eb.startBooleanToggle(new TranslatableText("nei.config.enabled"), d.general.enabled)
			.setDefaultValue(true)
			.setTooltip(new TranslatableText("nei.config.enabled.tooltip"))
			.setSaveConsumer(v -> d.general.enabled = v)
			.build());
		general.addEntry(eb.startBooleanToggle(new TranslatableText("nei.config.log_compat"), d.general.logCompatReport)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.general.logCompatReport = v)
			.build());

		ConfigCategory calc = builder.getOrCreateCategory(new TranslatableText("nei.config.calculator"));
		calc.addEntry(eb.startBooleanToggle(new TranslatableText("nei.config.calculator.search_bar"),
			d.calculator.showInSearchBar)
			.setDefaultValue(true)
			.setTooltip(new TranslatableText("nei.config.calculator.search_bar.tooltip"))
			.setSaveConsumer(v -> d.calculator.showInSearchBar = v)
			.build());
		calc.addEntry(eb.startStrField(new TranslatableText("nei.config.calculator.name"), d.calculator.name)
			.setDefaultValue("")
			.setSaveConsumer(v -> d.calculator.name = v == null ? "" : v.trim())
			.build());

		ConfigCategory search = builder.getOrCreateCategory(new TranslatableText("nei.config.search"));
		search.addEntry(eb.startEnumSelector(new TranslatableText("nei.config.search.mode"), NeiConfigData.SearchMode.class, d.search.mode)
			.setDefaultValue(NeiConfigData.SearchMode.WILDCARD)
			.setEnumNameProvider(mode -> new TranslatableText("nei.config.search.mode." + mode.name().toLowerCase()))
			.setSaveConsumer(v -> d.search.mode = v)
			.build());
		search.addEntry(eb.startBooleanToggle(new TranslatableText("nei.config.search.tooltips"), d.search.searchTooltipsByDefault)
			.setDefaultValue(false)
			.setSaveConsumer(v -> d.search.searchTooltipsByDefault = v)
			.build());

		ConfigCategory autocraft = builder.getOrCreateCategory(new TranslatableText("nei.config.autocraft"));
		autocraft.addEntry(eb.startBooleanToggle(new TranslatableText("nei.config.autocraft.enabled"), d.autocraft.enabled)
			.setDefaultValue(true)
			.setTooltip(new TranslatableText("nei.config.autocraft.enabled.tooltip"))
			.setSaveConsumer(v -> d.autocraft.enabled = v)
			.build());
		autocraft.addEntry(eb.startIntField(new TranslatableText("nei.config.autocraft.depth"), d.autocraft.maxDepth)
			.setDefaultValue(12)
			.setMin(1)
			.setMax(64)
			.setSaveConsumer(v -> d.autocraft.maxDepth = v)
			.build());
		autocraft.addEntry(eb.startIntField(new TranslatableText("nei.config.autocraft.max"), d.autocraft.maxCrafts)
			.setDefaultValue(512)
			.setMin(1)
			.setMax(4096)
			.setSaveConsumer(v -> d.autocraft.maxCrafts = v)
			.build());
		autocraft.addEntry(eb.startBooleanToggle(new TranslatableText("nei.config.autocraft.stations"), d.autocraft.allowOtherStations)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.autocraft.allowOtherStations = v)
			.build());
		autocraft.addEntry(eb.startBooleanToggle(new TranslatableText("nei.config.autocraft.pull"), d.autocraft.pullFromInventory)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.autocraft.pullFromInventory = v)
			.build());

		ConfigCategory cheat = builder.getOrCreateCategory(new TranslatableText("nei.config.cheat"));
		cheat.addEntry(eb.startBooleanToggle(new TranslatableText("nei.config.cheat.enabled"), d.cheat.enabled)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.cheat.enabled = v)
			.build());
		cheat.addEntry(eb.startBooleanToggle(new TranslatableText("nei.config.cheat.give"), d.cheat.giveStacks)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.cheat.giveStacks = v)
			.build());
		cheat.addEntry(eb.startBooleanToggle(new TranslatableText("nei.config.cheat.delete"), d.cheat.deleteItems)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.cheat.deleteItems = v)
			.build());
		cheat.addEntry(eb.startBooleanToggle(new TranslatableText("nei.config.cheat.utility"), d.cheat.utilityButtons)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.cheat.utilityButtons = v)
			.build());

		ConfigCategory tooltip = builder.getOrCreateCategory(new TranslatableText("nei.config.tooltip"));
		tooltip.addEntry(eb.startBooleanToggle(new TranslatableText("nei.config.tooltip.enabled"), d.tooltip.enabled)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.tooltip.enabled = v)
			.build());
		tooltip.addEntry(eb.startBooleanToggle(new TranslatableText("nei.config.tooltip.mod"), d.tooltip.showModName)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.tooltip.showModName = v)
			.build());
		tooltip.addEntry(eb.startBooleanToggle(new TranslatableText("nei.config.tooltip.id"), d.tooltip.showRegistryName)
			.setDefaultValue(false)
			.setSaveConsumer(v -> d.tooltip.showRegistryName = v)
			.build());
		tooltip.addEntry(eb.startBooleanToggle(new TranslatableText("nei.config.tooltip.nbt"), d.tooltip.showNbt)
			.setDefaultValue(false)
			.setSaveConsumer(v -> d.tooltip.showNbt = v)
			.build());
		tooltip.addEntry(eb.startBooleanToggle(new TranslatableText("nei.config.tooltip.tags"), d.tooltip.showTags)
			.setDefaultValue(false)
			.setSaveConsumer(v -> d.tooltip.showTags = v)
			.build());
		tooltip.addEntry(eb.startBooleanToggle(new TranslatableText("nei.config.tooltip.hint"), d.tooltip.showRecipeHint)
			.setDefaultValue(true)
			.setSaveConsumer(v -> d.tooltip.showRecipeHint = v)
			.build());

		ConfigCategory compat = builder.getOrCreateCategory(new TranslatableText("nei.config.compat"));
		compat.addEntry(eb.startTextDescription(new TranslatableText("nei.config.compat.description")).build());
		for (Map.Entry<String, String> entry : d.compat.modules.entrySet()) {
			compat.addEntry(eb.startStrField(new LiteralText(entry.getKey()), entry.getValue())
				.setDefaultValue("auto")
				.setSaveConsumer(v -> entry.setValue(v))
				.build());
		}

		return builder.build();
	}
}