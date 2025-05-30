// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiClassStubImpl<T extends PsiClass> extends StubBase<T> implements PsiClassStub<T> {
  private static final int DEPRECATED = 0x01;
  private static final int INTERFACE = 0x02;
  private static final int ENUM = 0x04;
  private static final int ENUM_CONSTANT_INITIALIZER = 0x08;
  private static final int ANONYMOUS = 0x10;
  private static final int ANON_TYPE = 0x20;
  private static final int IN_QUALIFIED_NEW = 0x40;
  private static final int DEPRECATED_ANNOTATION = 0x80;
  private static final int ANONYMOUS_INNER = 0x100;
  private static final int LOCAL_CLASS_INNER = 0x200;
  private static final int HAS_DOC_COMMENT = 0x400;
  private static final int RECORD = 0x800;
  private static final int IMPLICIT = 0x1000;
  private static final int VALUE_CLASS = 0x2000;

  private final @NotNull TypeInfo myTypeInfo;
  private final String myQualifiedName;
  private final String myName;
  private final String myBaseRefText;
  private final short myFlags;
  private String mySourceFileName;

  public PsiClassStubImpl(@NotNull IJavaElementType type,
                          StubElement parent,
                          @Nullable String qualifiedName,
                          @Nullable String name,
                          @Nullable String baseRefText,
                          short flags) {
    this(type, parent, TypeInfo.fromString(qualifiedName), name, baseRefText, flags);
  }

  public PsiClassStubImpl(@NotNull IJavaElementType  type,
                          StubElement parent,
                          @NotNull TypeInfo typeInfo,
                          @Nullable String name,
                          @Nullable String baseRefText,
                          short flags) {
    super(parent, type);
    myTypeInfo = typeInfo;
    myQualifiedName = typeInfo.text();
    myName = name;
    myBaseRefText = baseRefText;
    myFlags = flags;
    if (StubBasedPsiElementBase.ourTraceStubAstBinding) {
      String creationTrace = "Stub creation thread: " + Thread.currentThread() + "\n" + DebugUtil.currentStackTrace();
      putUserData(StubBasedPsiElementBase.CREATION_TRACE, creationTrace);
    }
  }

  @Override
  public String getName() {
    return myName;
  }
  
  public @NotNull TypeInfo getQualifiedNameTypeInfo() {
    return myTypeInfo;
  }
  
  @Override
  public String getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  public String getBaseClassReferenceText() {
    return myBaseRefText;
  }

  @Override
  public boolean isDeprecated() {
    return BitUtil.isSet(myFlags, DEPRECATED);
  }

  @Override
  public boolean hasDeprecatedAnnotation() {
    return BitUtil.isSet(myFlags, DEPRECATED_ANNOTATION);
  }

  @Override
  public boolean isInterface() {
    return BitUtil.isSet(myFlags, INTERFACE);
  }

  @Override
  public boolean isEnum() {
    return BitUtil.isSet(myFlags, ENUM);
  }

  @Override
  public boolean isRecord() {
    return BitUtil.isSet(myFlags, RECORD);
  }

  @Override
  public boolean isImplicit() {
    return BitUtil.isSet(myFlags, IMPLICIT);
  }

  @Override
  public boolean isValueClass() {
    return BitUtil.isSet(myFlags, VALUE_CLASS);
  }

  @Override
  public boolean isEnumConstantInitializer() {
    return isEnumConstInitializer(myFlags);
  }

  public static boolean isEnumConstInitializer(short flags) {
    return BitUtil.isSet(flags, ENUM_CONSTANT_INITIALIZER);
  }

  public static boolean isImplicit(short flags) {
    return BitUtil.isSet(flags, IMPLICIT);
  }

  @Override
  public boolean isAnonymous() {
    return isAnonymous(myFlags);
  }

  public static boolean isAnonymous(short flags) {
    return BitUtil.isSet(flags, ANONYMOUS);
  }

  @Override
  public boolean isAnnotationType() {
    return BitUtil.isSet(myFlags, ANON_TYPE);
  }

  @Override
  public boolean hasDocComment() {
    return BitUtil.isSet(myFlags, HAS_DOC_COMMENT);
  }

  @Override
  public String getSourceFileName() {
    return mySourceFileName;
  }

  public void setSourceFileName(String sourceFileName) {
    mySourceFileName = sourceFileName;
  }

  @Override
  public boolean isAnonymousInQualifiedNew() {
    return BitUtil.isSet(myFlags, IN_QUALIFIED_NEW);
  }

  public short getFlags() {
    return myFlags;
  }

  public static short packFlags(boolean isDeprecated,
                                boolean isInterface,
                                boolean isEnum,
                                boolean isEnumConstantInitializer,
                                boolean isAnonymous,
                                boolean isAnnotationType,
                                boolean isInQualifiedNew,
                                boolean hasDeprecatedAnnotation,
                                boolean anonymousInner,
                                boolean localClassInner,
                                boolean hasDocComment) {
    return packFlags(isDeprecated,
                     isInterface,
                     isEnum,
                     isEnumConstantInitializer,
                     isAnonymous,
                     isAnnotationType,
                     isInQualifiedNew,
                     hasDeprecatedAnnotation,
                     anonymousInner,
                     localClassInner,
                     hasDocComment,
                     false,
                     false,
                     false);
  }

  public static short packFlags(boolean isDeprecated,
                                boolean isInterface,
                                boolean isEnum,
                                boolean isEnumConstantInitializer,
                                boolean isAnonymous,
                                boolean isAnnotationType,
                                boolean isInQualifiedNew,
                                boolean hasDeprecatedAnnotation,
                                boolean anonymousInner,
                                boolean localClassInner,
                                boolean hasDocComment,
                                boolean isRecord,
                                boolean isImplicit,
                                boolean isValueClass) {
    short flags = 0;
    if (isDeprecated) flags |= DEPRECATED;
    if (isInterface) flags |= INTERFACE;
    if (isEnum) flags |= ENUM;
    if (isEnumConstantInitializer) flags |= ENUM_CONSTANT_INITIALIZER;
    if (isAnonymous) flags |= ANONYMOUS;
    if (isAnnotationType) flags |= ANON_TYPE;
    if (isInQualifiedNew) flags |= IN_QUALIFIED_NEW;
    if (hasDeprecatedAnnotation) flags |= DEPRECATED_ANNOTATION;
    if (anonymousInner) flags |= ANONYMOUS_INNER;
    if (localClassInner) flags |= LOCAL_CLASS_INNER;
    if (hasDocComment) flags |= HAS_DOC_COMMENT;
    if (isRecord) flags |= RECORD;
    if (isImplicit) flags |= IMPLICIT;
    if (isValueClass) flags |= VALUE_CLASS;
    return flags;
  }

  public boolean isAnonymousInner() {
    return BitUtil.isSet(myFlags, ANONYMOUS_INNER);
  }
  public boolean isLocalClassInner() {
    return BitUtil.isSet(myFlags, LOCAL_CLASS_INNER);
  }

  @Override
  @SuppressWarnings("SpellCheckingInspection")
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("PsiClassStub[");

    if (isInterface()) {
      builder.append("interface ");
    }

    if (isAnonymous()) {
      builder.append("anonymous ");
    }

    if (isEnum()) {
      builder.append("enum ");
    }

    if (isRecord()) {
      builder.append("record ");
    }

    if (isAnnotationType()) {
      builder.append("annotation ");
    }

    if (isEnumConstantInitializer()) {
      builder.append("enumInit ");
    }

    if (isDeprecated()) {
      builder.append("deprecated ");
    }

    if (hasDeprecatedAnnotation()) {
      builder.append("deprecatedA ");
    }

    builder.append("name=").append(getName()).append(" fqn=").append(getQualifiedName());

    if (getBaseClassReferenceText() != null) {
      builder.append(" baseref=").append(getBaseClassReferenceText());
    }

    if (isAnonymousInQualifiedNew()) {
      builder.append(" inqualifnew");
    }

    if (isAnonymousInner()) {
      builder.append(" jvmAnonymousInner");
    }

    if (isLocalClassInner()) {
      builder.append(" jvmLocalClassInner");
    }

    builder.append("]");

    return builder.toString();
  }
}
