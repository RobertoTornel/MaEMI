package dev.nei.compat;

import java.util.function.Predicate;

import dev.emi.emi.api.stack.EmiStack;

/** A named item subset contributed by a compat module, e.g. "GregTech". */
public record SubsetPreset(String name, Predicate<EmiStack> filter) {
}
