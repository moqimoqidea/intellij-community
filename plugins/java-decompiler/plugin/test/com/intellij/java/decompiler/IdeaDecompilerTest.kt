// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.decompiler

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.execution.filters.LineNumbersMapping
import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.impl.java.JavaAnonymousClassesNodeProvider
import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.withValue
import com.intellij.openapi.util.removeUserData
import com.intellij.openapi.vfs.*
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiCompiledFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.io.URLUtil
import org.jetbrains.java.decompiler.DecompilerPreset
import org.jetbrains.java.decompiler.IDEA_DECOMPILER_BANNER
import org.jetbrains.java.decompiler.IdeaDecompiler
import org.jetbrains.java.decompiler.IdeaDecompilerSettings

class IdeaDecompilerTest : LightJavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.testDataPath = "${PluginPathManager.getPluginHomePath("java-decompiler")}/plugin/testData"
  }

  override fun tearDown() {
    try {
      FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
      EditorHistoryManager.getInstance(project).removeAllFiles()

      val defaultState = IdeaDecompilerSettings.State.fromPreset(DecompilerPreset.HIGH)
      IdeaDecompilerSettings.getInstance().loadState(defaultState)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testSimple() {
    val file = getTestFile("${IdeaTestUtil.getMockJdk18Path().path}/jre/lib/rt.jar!/java/lang/String.class")
    val decompiled = IdeaDecompiler().getText(file).toString()
    assertTrue(decompiled, decompiled.startsWith("${IDEA_DECOMPILER_BANNER}package java.lang;\n"))
    assertTrue(decompiled, decompiled.contains("public final class String"))
    assertTrue(decompiled, decompiled.contains("@deprecated"))
    assertTrue(decompiled, decompiled.contains("private static class CaseInsensitiveComparator"))
    assertFalse(decompiled, decompiled.contains("/* compiled code */"))
    assertFalse(decompiled, decompiled.contains("synthetic"))
    assertFalse(decompiled, decompiled.contains("Limits for direct nodes are exceeded"))
    assertFalse(decompiled, decompiled.contains("Limits for variable nodes are exceeded"))
  }

  fun testSimpleWithNodeLimit() {
    val advancedSetting = "decompiler.max.direct.nodes.count"
    val previousCount = AdvancedSettings.getInt(advancedSetting)
    try {
      AdvancedSettings.setInt(advancedSetting, 5)
      val file = getTestFile("${IdeaTestUtil.getMockJdk18Path().path}/jre/lib/rt.jar!/java/lang/String.class")
      val decompiled = IdeaDecompiler().getText(file).toString()
      assertTrue(decompiled, decompiled.contains("Limits for direct nodes are exceeded"))
      //small methods are decompiled normally
      assertTrue(decompiled, decompiled.contains("""
          public String(char[] var1) {
                  this.value = Arrays.copyOf(var1, var1.length);
              }""".trimIndent()))
    }
    finally {
      AdvancedSettings.setInt(advancedSetting, previousCount)
    }
  }

  fun testSimpleWithVariableLimit() {
    val advancedSetting = "decompiler.max.variable.nodes.count"
    val previousCount = AdvancedSettings.getInt(advancedSetting)
    try {
      AdvancedSettings.setInt(advancedSetting, 5)
      val file = getTestFile("${IdeaTestUtil.getMockJdk18Path().path}/jre/lib/rt.jar!/java/lang/String.class")
      val decompiled = IdeaDecompiler().getText(file).toString()
      assertTrue(decompiled, decompiled.contains("Limits for variable nodes are exceeded"))
      //small methods are decompiled normally
      assertTrue(decompiled, decompiled.contains(
        """
        public char[] toCharArray() {
                char[] var1 = new char[this.value.length];
                System.arraycopy(this.value, 0, var1, 0, this.value.length);
                return var1;
            }""".trimIndent()))
    }
    finally {
      AdvancedSettings.setInt(advancedSetting, previousCount)
    }
  }

  fun testStubCompatibility() {
    val visitor = MyFileVisitor(psiManager)
    Registry.get("decompiler.dump.original.lines").withValue(true) {
      VfsUtilCore.visitChildrenRecursively(getTestFile("${JavaTestUtil.getJavaTestDataPath()}/psi/cls/mirror"), visitor)
      VfsUtilCore.visitChildrenRecursively(getTestFile("${PluginPathManager.getPluginHomePath("java-decompiler")}/engine/testData/classes"),
                                           visitor)
      VfsUtilCore.visitChildrenRecursively(getTestFile("${IdeaTestUtil.getMockJdk18Path().path}/jre/lib/rt.jar!/java/lang"), visitor)
    }
  }

  fun testNavigation_high() {
    val state = IdeaDecompilerSettings.State.fromPreset(DecompilerPreset.HIGH)
    IdeaDecompilerSettings.getInstance().loadState(state)

    myFixture.openFileInEditor(getTestFile("Navigation.class"))
    doTestNavigation(8, 14, 11, 10)  // to "m2()"
    doTestNavigation(12, 21, 11, 17)  // to "int i"
    doTestNavigation(13, 28, 12, 13)  // to "int r"
  }

  fun testNavigation_medium() {
    val state = IdeaDecompilerSettings.State.fromPreset(DecompilerPreset.MEDIUM)
    IdeaDecompilerSettings.getInstance().loadState(state)

    myFixture.openFileInEditor(getTestFile("Navigation.class"))
    doTestNavigation(12, 14, 15, 10)  // to "m2()"
    doTestNavigation(16, 21, 15, 17)  // to "int i"
    doTestNavigation(17, 28, 16, 13)  // to "int r"
  }

  fun testNavigation_low() {
    val state = IdeaDecompilerSettings.State.fromPreset(DecompilerPreset.LOW)
    IdeaDecompilerSettings.getInstance().loadState(state)

    myFixture.openFileInEditor(getTestFile("Navigation.class"))
    doTestNavigation(12, 14, 15, 10)  // to "m2()"
    doTestNavigation(16, 21, 15, 17)  // to "int i"
    doTestNavigation(17, 28, 16, 13)  // to "int r"
  }

  fun testUnicode_high() {
    val state = IdeaDecompilerSettings.State.fromPreset(DecompilerPreset.HIGH)
    IdeaDecompilerSettings.getInstance().loadState(state)

    val file = getTestFile("UnicodeTest.class")
    val decompiled = IdeaDecompiler().getText(file).toString()
    assertTrue(decompiled, decompiled.contains("你好"))
    assertFalse(decompiled, decompiled.contains("\\u4f60"))
  }

  private fun doTestNavigation(line: Int, column: Int, expectedLine: Int, expectedColumn: Int) {
    val target = GotoDeclarationAction.findTargetElement(project, myFixture.editor, offset(line, column)) as Navigatable
    target.navigate(true)
    val expected = offset(expectedLine, expectedColumn)
    assertEquals(expected, myFixture.caretOffset)
  }

  private fun offset(line: Int, column: Int): Int = myFixture.editor.document.getLineStartOffset(line - 1) + column - 1

  fun testHighlighting() {
    myFixture.setReadEditorMarkupModel(true)
    IdentifierHighlighterPassFactory.doWithIdentifierHighlightingEnabled(project, Runnable {
      myFixture.openFileInEditor(getTestFile("Navigation.class"))
      myFixture.editor.caretModel.moveToOffset(offset(8, 14))  // m2(): usage, declaration
      assertEquals(2, highlightUnderCaret().size)
      myFixture.editor.caretModel.moveToOffset(offset(11, 10))  // m2(): usage, declaration
      assertEquals(2, highlightUnderCaret().size)
      myFixture.editor.caretModel.moveToOffset(offset(11, 17))  // int i: usage, declaration
      assertEquals(2, highlightUnderCaret().size)
      myFixture.editor.caretModel.moveToOffset(offset(12, 21))  // int i: usage, declaration
      assertEquals(2, highlightUnderCaret().size)
      myFixture.editor.caretModel.moveToOffset(offset(12, 13))  // int r: usage, declaration
      assertEquals(2, highlightUnderCaret().size)
      myFixture.editor.caretModel.moveToOffset(offset(13, 28))  // int r: usage, declaration
      assertEquals(2, highlightUnderCaret().size)
      myFixture.editor.caretModel.moveToOffset(offset(16, 24))  // throws: declaration, m4() call
      assertEquals(2, highlightUnderCaret().size)
    })
  }

  fun testNameHighlightingInsideCompiledFile() {
    myFixture.setReadEditorMarkupModel(true)
    myFixture.openFileInEditor(getTestFile("NamesHighlightingInsideCompiledFile.class"))
    IdentifierHighlighterPassFactory.doWithIdentifierHighlightingEnabled(project, Runnable {
      val infos = myFixture.doHighlighting()
      assertTrue(infos.toString(), infos.all { info: HighlightInfo -> info.severity === HighlightInfoType.SYMBOL_TYPE_SEVERITY })
      assertEquals(68, infos.size)
    })
  }

  fun testNameHighlightingInsideCompiledModuleFile() {
    myFixture.setReadEditorMarkupModel(true)
    myFixture.openFileInEditor(getTestFile("module-info.class"))
    IdentifierHighlighterPassFactory.doWithIdentifierHighlightingEnabled(project, Runnable {
      val infos = myFixture.doHighlighting()
        .filter { it.severity === HighlightInfoType.SYMBOL_TYPE_SEVERITY }
      assertEquals(5, infos.size)
      val texts = infos.map { it.text }.toSet()
      assertContainsElements(
        texts,
        "module",
        "requires",
        "exports",
      )
    })
  }

  fun testNameHighlightingInsideCompiledFileWithRecords() {
    myFixture.setReadEditorMarkupModel(true)
    val testFile = getTestFile("RecordHighlighting.class")
    testFile.parent.children; testFile.parent.refresh(false, true)  // inner classes
    myFixture.openFileInEditor(testFile)
    IdentifierHighlighterPassFactory.doWithIdentifierHighlightingEnabled(project, Runnable {
      val infos = myFixture.doHighlighting()
        .filter { it.severity === HighlightInfoType.SYMBOL_TYPE_SEVERITY }
      val texts = infos.map { it.text }.toSet()
      assertContainsElements(
        texts,
        "sealed",
        "record",
        "permits",
      )
    })
  }

  private fun highlightUnderCaret(): List<HighlightInfo> {
    IdentifierHighlighterPassFactory.waitForIdentifierHighlighting(editor)
    return myFixture.doHighlighting().filter { it.severity === HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY }
  }

  fun testLineNumberMapping_high() = doTestLineMapping(DecompilerPreset.HIGH) { mapping ->
    assertEquals(8, mapping.bytecodeToSource(3)) // Assert that line 8 in decompiled class file maps to line 3 in Java source file
    assertEquals(18, mapping.bytecodeToSource(13))
    assertEquals(13, mapping.sourceToBytecode(18)) // Assert that line 13 in Java source file maps to line 18 in Java class file
    assertEquals(-1, mapping.bytecodeToSource(1000))
    assertEquals(-1, mapping.sourceToBytecode(1000))
  }

  fun testLineNumberMapping_medium() = doTestLineMapping(DecompilerPreset.MEDIUM) { mapping ->
    assertEquals(12, mapping.bytecodeToSource(3)) // Assert that line 12 in decompiled class file maps to line 2 in Java source file
    assertEquals(3, mapping.sourceToBytecode(12))
    assertEquals(21, mapping.bytecodeToSource(12))
    assertEquals(12, mapping.sourceToBytecode(21))
    assertEquals(-1, mapping.bytecodeToSource(1000))
    assertEquals(-1, mapping.sourceToBytecode(1000))
  }

  fun testLineNumberMapping_low() = doTestLineMapping(DecompilerPreset.LOW) { mapping ->
    assertEquals(12, mapping.bytecodeToSource(3)) // Assert that line 12 in decompiled class file maps to line 2 in Java source file
    assertEquals(3, mapping.sourceToBytecode(12))
    assertEquals(21, mapping.bytecodeToSource(12))
    assertEquals(12, mapping.sourceToBytecode(21))
    assertEquals(-1, mapping.bytecodeToSource(1000))
    assertEquals(-1, mapping.sourceToBytecode(1000))
  }

  private fun doTestLineMapping(preset: DecompilerPreset, assertion: (mapping: LineNumbersMapping) -> Unit) {
    Registry.get("decompiler.use.line.mapping").withValue(true) {
      val file = getTestFile("LineNumbers.class")
      try {
        val state = IdeaDecompilerSettings.State.fromPreset(preset)
        IdeaDecompilerSettings.getInstance().loadState(state)
        assertNull(file.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY))

        IdeaDecompiler().getText(file)

        val mapping = file.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY)!!
        assertion(mapping)
      }
      finally {
        file.removeUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY)
      }
    }
  }

  fun testStructureView() {
    val file = getTestFile("StructureView.class")
    file.parent.children; file.parent.refresh(false, true)  // inner classes
    checkStructure(file, """
      -StructureView.class
       -StructureView
        -B
         B()
         build(int): StructureView
        StructureView()
        getData(): int
        setData(int): void
        data: int""")

    (PsiManager.getInstance(project).findFile(file) as? PsiCompiledFile)?.decompiledPsiFile

    checkStructure(file, """
      -StructureView.java
       -StructureView
        -B
         -build(int): StructureView
          -$1
           class initializer
        getData(): int
        setData(int): void
        data: int""")
  }

  private fun checkStructure(file: VirtualFile, s: String) {
    val editor = FileEditorManager.getInstance(project).openFile(file, false)[0]
    val builder = StructureViewBuilder.getProvider().getStructureViewBuilder(JavaClassFileType.INSTANCE, file, project)!!
    val svc = builder.createStructureView(editor, project) as StructureViewComponent
    Disposer.register(myFixture.testRootDisposable, svc)
    svc.setActionActive(JavaAnonymousClassesNodeProvider.ID, true)
    PlatformTestUtil.expandAll(svc.tree)
    PlatformTestUtil.assertTreeEqual(svc.tree, s.trimIndent())
  }

  private fun getTestFile(name: String): VirtualFile {
    val path = if (FileUtil.isAbsolute(name)) name else "${myFixture.testDataPath}/${name}"
    val fs = if (path.contains(URLUtil.JAR_SEPARATOR)) StandardFileSystems.jar() else StandardFileSystems.local()
    val file = fs.refreshAndFindFileByPath(path)!!
    if (file.isDirectory) file.refresh(false, true)
    return file
  }

  private class MyFileVisitor(private val psiManager: PsiManager) : VirtualFileVisitor<Any>() {
    private val negativeTests = setOf("TestUnsupportedConstantPoolEntry")

    override fun visitFile(file: VirtualFile): Boolean {
      if (file.isDirectory) {
        LOG.debug(file.path)
      }
      else if (file.fileType === JavaClassFileType.INSTANCE && !file.name.contains('$')) {
        val psiFile = psiManager.findFile(file)
        if (psiFile == null) {
          throw AssertionError("PSI file for ${file.name} not found")
        }
        if (psiFile.language != JavaLanguage.INSTANCE) {
          return true //do not test kotlin decompiler here
        }
        if (psiFile !is ClsFileImpl) {
          throw AssertionError("PSI file for ${file.name} should be an instance of ${ClsFileImpl::javaClass.name}")
        }

        if (file.nameWithoutExtension in negativeTests) {
          assertEquals("corrupted_class_file", psiFile.packageName)
          return true
        }

        val decompiled = psiFile.mirror.text
        assertTrue(file.path, decompiled.startsWith(IDEA_DECOMPILER_BANNER) || file.name.endsWith("-info.class"))

        // check that no mapped line number is on an empty line
        val prefix = "// "
        decompiled.split("\n").dropLastWhile(String::isEmpty).toTypedArray().forEach { s ->
          val pos = s.indexOf(prefix)
          if (pos == 0 && prefix.length < s.length && Character.isDigit(s[prefix.length])) {
            fail("Incorrect line mapping in the file " + file.path + " line: " + s)
          }
        }
      }
      else if (file.fileType === ArchiveFileType.INSTANCE) {
        val jarRoot = JarFileSystem.getInstance().getRootByLocal(file)
        if (jarRoot != null) {
          VfsUtilCore.visitChildrenRecursively(jarRoot, this)
        }
      }

      return true
    }
  }
}