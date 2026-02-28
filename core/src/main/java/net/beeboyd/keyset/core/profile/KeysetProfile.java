package net.beeboyd.keyset.core.profile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable snapshot of a named profile and its stored keybind assignments. */
public final class KeysetProfile {
  private final String id;
  private final String name;
  private final boolean builtIn;
  private final Map<String, KeysetBindingSnapshot> bindings;

  public KeysetProfile(
      String id, String name, boolean builtIn, Map<String, KeysetBindingSnapshot> bindings) {
    this.id = requireText(id, "id");
    this.name = requireText(name, "name");
    this.builtIn = builtIn;
    this.bindings = Collections.unmodifiableMap(copyBindings(bindings));
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public boolean isBuiltIn() {
    return builtIn;
  }

  public Map<String, KeysetBindingSnapshot> getBindings() {
    return bindings;
  }

  public KeysetProfile withName(String updatedName) {
    return new KeysetProfile(id, updatedName, builtIn, bindings);
  }

  public KeysetProfile withBuiltIn(boolean updatedBuiltIn) {
    return new KeysetProfile(id, name, updatedBuiltIn, bindings);
  }

  public KeysetProfile withBinding(String bindingId, KeysetBindingSnapshot snapshot) {
    String normalizedBindingId = requireText(bindingId, "bindingId");
    if (snapshot == null) {
      throw new IllegalArgumentException("snapshot must not be null");
    }

    Map<String, KeysetBindingSnapshot> updatedBindings =
        new LinkedHashMap<String, KeysetBindingSnapshot>(bindings);
    updatedBindings.put(normalizedBindingId, snapshot);
    return new KeysetProfile(id, name, builtIn, updatedBindings);
  }

  public KeysetProfile withoutBinding(String bindingId) {
    String normalizedBindingId = requireText(bindingId, "bindingId");
    if (!bindings.containsKey(normalizedBindingId)) {
      return this;
    }

    Map<String, KeysetBindingSnapshot> updatedBindings =
        new LinkedHashMap<String, KeysetBindingSnapshot>(bindings);
    updatedBindings.remove(normalizedBindingId);
    return new KeysetProfile(id, name, builtIn, updatedBindings);
  }

  private static Map<String, KeysetBindingSnapshot> copyBindings(
      Map<String, KeysetBindingSnapshot> bindings) {
    if (bindings == null || bindings.isEmpty()) {
      return Collections.emptyMap();
    }

    TreeMap<String, KeysetBindingSnapshot> sortedBindings =
        new TreeMap<String, KeysetBindingSnapshot>();
    for (Map.Entry<String, KeysetBindingSnapshot> entry : bindings.entrySet()) {
      String bindingId = requireText(entry.getKey(), "bindingId");
      KeysetBindingSnapshot snapshot = entry.getValue();
      if (snapshot != null) {
        sortedBindings.put(bindingId, snapshot);
      }
    }

    return new LinkedHashMap<String, KeysetBindingSnapshot>(sortedBindings);
  }

  private static String requireText(String value, String fieldName) {
    if (value == null) {
      throw new IllegalArgumentException(fieldName + " must not be null");
    }

    String normalized = value.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }

    return normalized;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof KeysetProfile)) {
      return false;
    }
    KeysetProfile that = (KeysetProfile) other;
    return builtIn == that.builtIn
        && id.equals(that.id)
        && name.equals(that.name)
        && bindings.equals(that.bindings);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, builtIn, bindings);
  }
}
