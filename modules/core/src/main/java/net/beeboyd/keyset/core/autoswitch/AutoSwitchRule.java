package net.beeboyd.keyset.core.autoswitch;

import java.util.Objects;

/** A single glob-pattern-to-profile mapping used by the auto-switch feature. */
public final class AutoSwitchRule {
  private final String pattern;
  private final String profileId;

  public AutoSwitchRule(String pattern, String profileId) {
    if (pattern == null || pattern.trim().isEmpty()) {
      throw new IllegalArgumentException("pattern must not be blank");
    }
    if (profileId == null || profileId.trim().isEmpty()) {
      throw new IllegalArgumentException("profileId must not be blank");
    }
    this.pattern = pattern.trim();
    this.profileId = profileId.trim();
  }

  public String getPattern() {
    return pattern;
  }

  public String getProfileId() {
    return profileId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AutoSwitchRule)) return false;
    AutoSwitchRule that = (AutoSwitchRule) o;
    return Objects.equals(pattern, that.pattern) && Objects.equals(profileId, that.profileId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pattern, profileId);
  }
}
