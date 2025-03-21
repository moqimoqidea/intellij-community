// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Holds <a href="http://semver.org">Semantic Version</a>.
 */
public final class SemVer implements Comparable<SemVer> {

  private final String myRawVersion;
  private final int myMajor;
  private final int myMinor;
  private final int myPatch;
  private final @Nullable String myPreRelease;
  private final @Nullable String myBuildMeta;

  public SemVer(@NotNull String rawVersion, int major, int minor, int patch) {
    this(rawVersion, major, minor, patch, null, null);
  }

  public SemVer(@NotNull String rawVersion, int major, int minor, int patch, @Nullable String preRelease) {
    this(rawVersion, major, minor, patch, preRelease, null);
  }

  public SemVer(@NotNull String rawVersion, int major, int minor, int patch, @Nullable String preRelease, @Nullable String buildMeta) {
    myRawVersion = rawVersion;
    myMajor = major;
    myMinor = minor;
    myPatch = patch;
    myPreRelease = preRelease;
    myBuildMeta = buildMeta;
  }

  public @NotNull @NlsSafe String getRawVersion() {
    return myRawVersion;
  }

  public int getMajor() {
    return myMajor;
  }

  public int getMinor() {
    return myMinor;
  }

  public int getPatch() {
    return myPatch;
  }

  public @Nullable @NlsSafe String getPreRelease() {
    return myPreRelease;
  }

  public @Nullable @NlsSafe String getBuildMeta() {
    return myBuildMeta;
  }

  public @NotNull @NlsSafe String getParsedVersion() {
    return myMajor + "." + myMinor + "." + myPatch + (myPreRelease != null ? "-" + myPreRelease : "") + (myBuildMeta != null ? "+" + myBuildMeta : "");
  }

  @Override
  public int compareTo(SemVer other) {
    int diff = myMajor - other.myMajor;
    if (diff != 0) return diff;

    diff = myMinor - other.myMinor;
    if (diff != 0) return diff;

    diff = myPatch - other.myPatch;
    if (diff != 0) return diff;

    return comparePrerelease(myPreRelease, other.myPreRelease);
  }

  public boolean isGreaterOrEqualThan(int major, int minor, int patch) {
    if (myMajor != major) return myMajor > major;
    if (myMinor != minor) return myMinor > minor;
    return myPatch >= patch;
  }

  public boolean isGreaterOrEqualThan(@NotNull SemVer version) {
    return compareTo(version) >= 0;
  }

  public boolean isGreaterThan(@NotNull SemVer version) {
    return compareTo(version) > 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SemVer semVer = (SemVer)o;
    return myMajor == semVer.myMajor
           && myMinor == semVer.myMinor
           && myPatch == semVer.myPatch
           && Objects.equals(myPreRelease, semVer.myPreRelease)
           && Objects.equals(myBuildMeta, semVer.myBuildMeta);
  }

  @Override
  public int hashCode() {
    int result = myMajor;
    result = 31 * result + myMinor;
    result = 31 * result + myPatch;
    if (myPreRelease != null) {
      result = 31 * result + myPreRelease.hashCode();
    }
    if (myBuildMeta != null) {
      result = 31 * result + myBuildMeta.hashCode();
    }
    return result;
  }

  @Override
  public String toString() {
    return myRawVersion;
  }

  private static int comparePrerelease(@Nullable String pre1, @Nullable String pre2) {
    if (pre1 == null) {
      return pre2 == null ? 0 : 1;
    }
    else if (pre2 == null) {
      return -1;
    }
    int length1 = pre1.length();
    int length2 = pre2.length();

    if (length1 == length2 && pre1.equals(pre2)) return 0;

    int start1 = 0;
    int start2 = 0;
    int diff;

    // compare each segment separately
    do {
      int end1 = pre1.indexOf('.', start1);
      int end2 = pre2.indexOf('.', start2);

      if (end1 < 0) end1 = length1;
      if (end2 < 0) end2 = length2;

      CharSequence segment1 = new CharSequenceSubSequence(pre1, start1, end1);
      CharSequence segment2 = new CharSequenceSubSequence(pre2, start2, end2);
      if (Strings.isNotNegativeNumber(segment1)) {
        if (!Strings.isNotNegativeNumber(segment2)) {
          // According to SemVer specification numeric segments has lower precedence
          // than non-numeric segments
          return -1;
        }
        diff = compareNumeric(segment1, segment2);
      }
      else if (Strings.isNotNegativeNumber(segment2)) {
        return 1;
      }
      else {
        diff = Strings.compare(segment1, segment2, false);
      }
      start1 = end1 + 1;
      start2 = end2 + 1;
    }
    while (diff == 0 && start1 < length1 && start2 < length2);

    if (diff != 0) return diff;

    return start1 < length1 ? 1 : -1;
  }

  private static int compareNumeric(CharSequence segment1, CharSequence segment2) {
    int length1 = segment1.length();
    int length2 = segment2.length();
    int diff = Integer.compare(length1, length2);
    for (int i = 0; i < length1 && diff == 0; i++) {
      diff = segment1.charAt(i) - segment2.charAt(i);
    }
    return diff;
  }

  public static @Nullable SemVer parseFromText(@Nullable String text) {
    if (text != null) {
      int majorEndIdx = text.indexOf('.');
      if (majorEndIdx >= 0) {
        int minorEndIdx = text.indexOf('.', majorEndIdx + 1);
        if (minorEndIdx >= 0) {
          int preReleaseIdx = text.indexOf('-', minorEndIdx + 1);
          int buildMetaIdx = text.indexOf('+', minorEndIdx + 1);
          int patchEndIdx = preReleaseIdx >= 0 && buildMetaIdx >= 0
                            ? Math.min(preReleaseIdx, buildMetaIdx)
                            : (preReleaseIdx >= 0 || buildMetaIdx >= 0 ? Math.max(preReleaseIdx, buildMetaIdx) : text.length());

          int major = StringUtilRt.parseInt(text.substring(0, majorEndIdx), -1);
          int minor = StringUtilRt.parseInt(text.substring(majorEndIdx + 1, minorEndIdx), -1);
          int patch = StringUtilRt.parseInt(text.substring(minorEndIdx + 1, patchEndIdx), -1);

          String preRelease = preReleaseIdx >= 0 && (buildMetaIdx < 0 || preReleaseIdx < buildMetaIdx)
                              ? text.substring(preReleaseIdx + 1, buildMetaIdx >= 0 ? buildMetaIdx : text.length()) : null;
          String buildMeta = buildMetaIdx >= 0 ? text.substring(buildMetaIdx + 1) : null;

          if (major >= 0 && minor >= 0 && patch >= 0) {
            return new SemVer(text, major, minor, patch, preRelease, buildMeta);
          }
        }
      }
    }

    return null;
  }
}
