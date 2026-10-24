package dev.nei.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link NeiPlugin} implementation for discovery on loaders without an entrypoint system
 * (Forge). Fabric addons use the {@code nei} entrypoint instead.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NeiPluginEntrypoint {
}
