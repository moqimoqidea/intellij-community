// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BinaryFilePatch extends FilePatch {
  private final byte[] myBeforeContent;
  private final byte[] myAfterContent;

  public BinaryFilePatch(final byte[] beforeContent, final byte[] afterContent) {
    myBeforeContent = beforeContent;
    myAfterContent = afterContent;
  }

  @Override
  public boolean isNewFile() {
    return myBeforeContent == null;
  }

  @Override
  public boolean isDeletedFile() {
    return myAfterContent == null;
  }

  public byte @Nullable [] getBeforeContent() {
    return myBeforeContent;
  }

  public byte @Nullable [] getAfterContent() {
    return myAfterContent;
  }

  public @NotNull BinaryFilePatch copy() {
    BinaryFilePatch copied = new BinaryFilePatch(this.getBeforeContent(), this.getAfterContent());
    copied.setBeforeName(this.getBeforeName());
    copied.setAfterName(this.getAfterName());
    return copied;
  }
}
