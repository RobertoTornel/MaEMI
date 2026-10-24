package dev.nei.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class NeiSearchParserTest {
	private static final SearchTarget IRON = new SearchTarget() {
		@Override
		public String name() {
			return "Iron Ingot";
		}

		@Override
		public String modId() {
			return "minecraft";
		}

		@Override
		public String modName() {
			return "Minecraft";
		}

		@Override
		public String identifier() {
			return "minecraft:iron_ingot";
		}

		@Override
		public List<String> tooltip() {
			return List.of("A sturdy ingot", "Smelts into tools");
		}

		@Override
		public List<String> tags() {
			return List.of("c:iron_ingots", "forge:ingots/iron");
		}
	};

	private static boolean match(String query, SearchTarget target) {
		return NeiSearchParser.parse(query, SearchMode.WILDCARD).matches(target);
	}

	@Test
	void emptyMatchesEverything() {
		assertTrue(match("", IRON));
		assertTrue(match("   ", IRON));
	}

	@Test
	void nameSearchIsCaseInsensitiveAndSubstring() {
		assertTrue(match("iron", IRON));
		assertTrue(match("INGOT", IRON));
		assertFalse(match("gold", IRON));
	}

	@Test
	void identifierAndModPrefixes() {
		assertTrue(match("@minecraft", IRON));
		assertTrue(match("&minecraft:iron_ingot", IRON));
		assertTrue(match("&iron_ingot", IRON));
		assertFalse(match("@gregtech", IRON));
	}

	@Test
	void tagAndTooltipPrefixes() {
		assertTrue(match("$forge:ingots", IRON));
		assertTrue(match("$iron", IRON));
		assertFalse(match("$copper", IRON));
		assertTrue(match("#sturdy", IRON));
		assertFalse(match("#shiny", IRON));
	}

	@Test
	void booleanOperators() {
		assertTrue(match("iron | gold", IRON));
		assertFalse(match("gold | copper", IRON));
		assertTrue(match("iron ingot", IRON));
		assertFalse(match("iron copper", IRON));
		assertTrue(match("-gold", IRON));
		assertTrue(match("iron -gold", IRON));
		assertFalse(match("!iron", IRON));
	}

	@Test
	void groupingAndQuotes() {
		assertTrue(match("{iron | gold} ingot", IRON));
		assertTrue(match("\"Iron Ingot\"", IRON));
		assertFalse(match("\"Iron\"", IRON));
	}

	@Test
	void wildcardsAndRegex() {
		assertTrue(match("ir*n", IRON));
		assertTrue(match("ir?n", IRON));
		assertTrue(match("ir?n ingot", IRON));
		assertFalse(match("ir?x ingot", IRON));
		assertTrue(match("r/^Iron.*$", IRON));
		assertTrue(match("r/ingot$", IRON));
	}

	@Test
	void literalModeQuotesEverything() {
		assertTrue(NeiSearchParser.parse("ir*n", SearchMode.LITERAL).matches(IRON) == false);
		assertTrue(NeiSearchParser.parse("Iron Ingot", SearchMode.LITERAL).matches(IRON));
	}
}