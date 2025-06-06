// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.codeInsight.ContainerProvider;
import com.intellij.codeInsight.JavaContainerProvider;
import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.codeInsight.folding.impl.JavaCodeFoldingSettingsBase;
import com.intellij.codeInsight.folding.impl.JavaFoldingBuilderBase;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.frontback.psi.impl.syntax.JavaSyntaxDefinitionExtension;
import com.intellij.lang.LanguageASTFactory;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lang.java.syntax.JavaElementTypeConverterExtension;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.fileTypes.PlainTextParserDefinition;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.platform.syntax.psi.ElementTypeConverters;
import com.intellij.platform.syntax.psi.LanguageSyntaxDefinitions;
import com.intellij.psi.*;
import com.intellij.psi.compiled.ClassFileDecompilers;
import com.intellij.psi.impl.LanguageConstantExpressionEvaluator;
import com.intellij.psi.impl.PsiExpressionEvaluator;
import com.intellij.psi.impl.PsiSubstitutorFactoryImpl;
import com.intellij.psi.impl.compiled.ClassFileStubBuilder;
import com.intellij.psi.impl.compiled.ClsDecompilerImpl;
import com.intellij.psi.impl.file.PsiPackageImplementationHelper;
import com.intellij.psi.impl.java.stubs.JavaStubRegistryExtension;
import com.intellij.psi.impl.search.MethodSuperSearcher;
import com.intellij.psi.impl.source.tree.JavaASTFactory;
import com.intellij.psi.impl.source.tree.PlainTextASTFactory;
import com.intellij.psi.presentation.java.*;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.stubs.BinaryFileStubBuilders;
import com.intellij.psi.stubs.StubElementRegistryServiceImplKt;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UnusedDeclaration") // Used in Kotlin Compiler
public class JavaCoreApplicationEnvironment extends CoreApplicationEnvironment {
  public JavaCoreApplicationEnvironment(@NotNull Disposable parentDisposable) {
    this(parentDisposable, true);
  }

  public JavaCoreApplicationEnvironment(@NotNull Disposable parentDisposable, boolean unitTestMode) {
    super(parentDisposable, unitTestMode);

    registerFileType(JavaClassFileType.INSTANCE, "class");
    registerFileType(JavaFileType.INSTANCE, "java");
    registerFileType(ArchiveFileType.INSTANCE, "jar;zip");
    Registry.get("java.highest.language.level").setValue("24");
    registerFileType(PlainTextFileType.INSTANCE, "txt;sh;bat;cmd;policy;log;cgi;MF;jad;jam;htaccess");

    addExplicitExtension(LanguageASTFactory.INSTANCE, PlainTextLanguage.INSTANCE, new PlainTextASTFactory());
    registerParserDefinition(new PlainTextParserDefinition());
    addExtension(StubElementRegistryServiceImplKt.STUB_REGISTRY_EP, new JavaStubRegistryExtension());

    addExplicitExtension(FileTypeFileViewProviders.INSTANCE, JavaClassFileType.INSTANCE, new ClassFileViewProviderFactory());
    addExplicitExtension(BinaryFileStubBuilders.INSTANCE, JavaClassFileType.INSTANCE, new ClassFileStubBuilder());

    addExplicitExtension(LanguageASTFactory.INSTANCE, JavaLanguage.INSTANCE, new JavaASTFactory());
    addExplicitExtension(LanguageSyntaxDefinitions.getINSTANCE(), JavaLanguage.INSTANCE, new JavaSyntaxDefinitionExtension());
    addExplicitExtension(ElementTypeConverters.getInstance(), JavaLanguage.INSTANCE, new JavaElementTypeConverterExtension());
    registerParserDefinition(new JavaParserDefinition());
    addExplicitExtension(LanguageConstantExpressionEvaluator.INSTANCE, JavaLanguage.INSTANCE, new PsiExpressionEvaluator());

    registerApplicationExtensionPoint(ContainerProvider.EP_NAME, ContainerProvider.class);
    addExtension(ContainerProvider.EP_NAME, new JavaContainerProvider());

    application.registerService(PsiPackageImplementationHelper.class, new CorePsiPackageImplementationHelper());

    application.registerService(PsiSubstitutorFactory.class, new PsiSubstitutorFactoryImpl());
    application.registerService(JavaModuleGraphHelper.class, new DumbJavaModuleGraphHelper());
    application.registerService(JavaDirectoryService.class, createJavaDirectoryService());
    application.registerService(JavaVersionService.class, new JavaVersionService());

    addExplicitExtension(ItemPresentationProviders.INSTANCE, PsiPackage.class, new PackagePresentationProvider());
    addExplicitExtension(ItemPresentationProviders.INSTANCE, PsiClass.class, new ClassPresentationProvider());
    addExplicitExtension(ItemPresentationProviders.INSTANCE, PsiMethod.class, new MethodPresentationProvider());
    addExplicitExtension(ItemPresentationProviders.INSTANCE, PsiField.class, new FieldPresentationProvider());
    addExplicitExtension(ItemPresentationProviders.INSTANCE, PsiLocalVariable.class, new VariablePresentationProvider<>());
    addExplicitExtension(ItemPresentationProviders.INSTANCE, PsiParameter.class, new VariablePresentationProvider<>());

    registerApplicationService(JavaCodeFoldingSettings.class, new JavaCodeFoldingSettingsBase());
    addExplicitExtension(LanguageFolding.INSTANCE, JavaLanguage.INSTANCE, new JavaFoldingBuilderBase() {
      @Override
      protected boolean shouldShowExplicitLambdaType(@NotNull PsiAnonymousClass anonymousClass, @NotNull PsiNewExpression expression) {
        return false;
      }

      @Override
      protected boolean isBelowRightMargin(@NotNull PsiFile file, int lineLength) {
        return false;
      }
    });

    registerApplicationExtensionPoint(SuperMethodsSearch.EP_NAME, QueryExecutor.class);
    addExtension(SuperMethodsSearch.EP_NAME, new MethodSuperSearcher());

    registerApplicationDynamicExtensionPoint("com.intellij.filetype.decompiler", BinaryFileTypeDecompilers.class);
    registerApplicationDynamicExtensionPoint("com.intellij.psi.classFileDecompiler", ClassFileDecompilers.Decompiler.class);
    addExtension(ClassFileDecompilers.STATIC_EP_NAME, new ClsDecompilerImpl());
  }

  // overridden in upsource
  protected CoreJavaDirectoryService createJavaDirectoryService() {
    return new CoreJavaDirectoryService();
  }
}