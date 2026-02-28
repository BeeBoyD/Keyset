/**
 * Loader-agnostic snapshots of live keybindings exposed to shared core features.
 *
 * <p>Platform shims translate Minecraft's version-specific keybinding objects into these immutable
 * descriptors before handing them to conflict analysis, auto-resolve, or UI contracts.
 */
package net.beeboyd.keyset.core.binding;
