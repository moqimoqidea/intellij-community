// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class JarFileSystem extends ArchiveFileSystem implements VirtualFilePointerCapableFileSystem {
  public static final String PROTOCOL = StandardFileSystems.JAR_PROTOCOL;
  public static final String PROTOCOL_PREFIX = StandardFileSystems.JAR_PROTOCOL_PREFIX;
  public static final String JAR_SEPARATOR = URLUtil.JAR_SEPARATOR;

  public static JarFileSystem getInstance() {
    return (JarFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  public @Nullable VirtualFile getVirtualFileForJar(@Nullable VirtualFile entryFile) {
    return entryFile == null ? null : getLocalByEntry(entryFile);
  }

  public @Nullable VirtualFile getJarRootForLocalFile(@NotNull VirtualFile file) {
    return getRootByLocal(file);
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated use {@link #getLocalByEntry(VirtualFile)} (or {@link #getVirtualFileForJar(VirtualFile)}) instead */
  @Deprecated(forRemoval = true)
  public @Nullable VirtualFile getLocalVirtualFileFor(@Nullable VirtualFile entryVFile) {
    return getVirtualFileForJar(entryVFile);
  }

  /** @deprecated use {@code #findFileByPath(path)} instead */
  @Deprecated(forRemoval = true)
  public @Nullable VirtualFile findLocalVirtualFileByPath(@NotNull String path) {
    if (!path.contains(JAR_SEPARATOR)) {
      path += JAR_SEPARATOR;
    }
    return findFileByPath(path);
  }

  /** @deprecated no-op; stop using */
  @Deprecated(forRemoval = true)
  public void setNoCopyJarForPath(@SuppressWarnings("unused") @NotNull String pathInJar) { }
  //</editor-fold>
}
