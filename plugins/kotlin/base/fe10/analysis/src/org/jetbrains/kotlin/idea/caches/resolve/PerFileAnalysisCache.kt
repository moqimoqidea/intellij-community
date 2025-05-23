// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.resolve

import com.google.common.collect.ImmutableMap
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentInFile
import com.intellij.psi.util.findTopmostParentInFile
import com.intellij.psi.util.findTopmostParentOfType
import com.intellij.psi.util.parents
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cfg.ControlFlowInformationProviderImpl
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.GlobalContext
import org.jetbrains.kotlin.context.withModule
import org.jetbrains.kotlin.context.withProject
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithSource
import org.jetbrains.kotlin.descriptors.InvalidModuleException
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactoryWithPsiElement
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.diagnostics.DiagnosticUtils
import org.jetbrains.kotlin.diagnostics.PositioningStrategies.DECLARATION_WITH_BODY
import org.jetbrains.kotlin.frontend.di.createContainerForLazyBodyResolve
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.findAnalyzerServices
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.caches.trackers.clearInBlockModifications
import org.jetbrains.kotlin.idea.caches.trackers.inBlockModifications
import org.jetbrains.kotlin.idea.caches.trackers.removeInBlockModifications
import org.jetbrains.kotlin.idea.compiler.IdeMainFunctionDetectorFactory
import org.jetbrains.kotlin.idea.compiler.IdeSealedClassInheritorsProvider
import org.jetbrains.kotlin.idea.core.util.CodeFragmentUtils
import org.jetbrains.kotlin.idea.project.IdeaAbsentDescriptorHandler
import org.jetbrains.kotlin.idea.project.IdeaModuleStructureOracle
import org.jetbrains.kotlin.idea.stubindex.resolve.PluginDeclarationProviderFactory
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics
import org.jetbrains.kotlin.resolve.diagnostics.DiagnosticsElementsCache
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.storage.CancellableSimpleLock
import org.jetbrains.kotlin.storage.guarded
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.util.slicedMap.ReadOnlySlice
import org.jetbrains.kotlin.util.slicedMap.WritableSlice
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.utils.checkWithAttachment
import java.util.concurrent.locks.ReentrantLock

internal class PerFileAnalysisCache(val file: KtFile, componentProvider: ComponentProvider) {
    private val globalContext = componentProvider.get<GlobalContext>()
    private val moduleDescriptor = componentProvider.get<ModuleDescriptor>()
    private val resolveSession = componentProvider.get<ResolveSession>()
    private val codeFragmentAnalyzer = componentProvider.get<CodeFragmentAnalyzer>()
    private val bodyResolveCache = componentProvider.get<BodyResolveCache>()
    private val pluginDeclarationProviderFactory = componentProvider.get<PluginDeclarationProviderFactory>()

    private val cache = HashMap<PsiElement, AnalysisResult>()
    private var fileResult: AnalysisResult? = null
    private val lock = ReentrantLock()
    private val guardLock = CancellableSimpleLock(lock,
                                                  checkCancelled = {
                                                      ProgressIndicatorProvider.checkCanceled()
                                                  },
                                                  interruptedExceptionHandler = { throw ProcessCanceledException(it) })

    private fun check(element: KtElement) {
        checkWithAttachment(element.containingFile == file, {
            "Expected $file, but was ${element.containingFile} for ${if (element.isValid) "valid" else "invalid"} $element "
        }) {
            it.withPsiAttachment("element.kt", element)
            it.withPsiAttachment("file.kt", element.containingFile)
            it.withPsiAttachment("original.kt", file)
        }
    }

    internal val isValid: Boolean get() = moduleDescriptor.isValid

    internal fun fetchAnalysisResults(element: KtElement): AnalysisResult? {
        check(element)

        if (lock.tryLock()) {
            try {
                updateFileResultFromCache()

                return fileResult?.takeIf { file.inBlockModifications.isEmpty() }
            } finally {
                lock.unlock()
            }
        }
        return null
    }

    internal fun getAnalysisResults(element: KtElement, callback: DiagnosticSink.DiagnosticsCallback? = null): AnalysisResult {
        check(element)

        val analyzableParent = KotlinResolveDataProvider.findAnalyzableParent(element) ?: return AnalysisResult.EMPTY

        fun handleResult(result: AnalysisResult, callback: DiagnosticSink.DiagnosticsCallback?): AnalysisResult {
            callback?.let { result.bindingContext.diagnostics.forEach(it::callback) }
            return result
        }

        return guardLock.guarded {

            // It is necessary to ignore the codeFragment used in the evaluator for compilation
            // because caching can lead to data consistency bugs (see KTIJ-22496). However, the code fragments that come from the evaluator,
            // which are not intended for compilation (e.g., for highlighting), should be cached in order to maintain the current performance of the evaluator.
            if (analyzableParent.isUsedForCompilationInEvaluator()) return@guarded performAnalyze(element, callback)

            // step 1: perform incremental analysis IF it is applicable
            getIncrementalAnalysisResult(callback)?.let {
                return@guarded handleResult(it, callback)
            }

            // cache does not contain AnalysisResult per each kt/psi element
            // instead it looks up analysis for its parents - see lookUp(analyzableElement)

            // step 2: return result if it is cached
            lookUp(analyzableParent)?.let {
                return@guarded handleResult(it, callback)
            }

            // step 3: perform analyze of analyzableParent as nothing has been cached yet
            val result = performAnalyze(analyzableParent, callback)

            cache[analyzableParent] = result

            return@guarded result
        }
    }

    private fun performAnalyze(element: KtElement, callback: DiagnosticSink.DiagnosticsCallback? = null): AnalysisResult {
        val localDiagnostics = mutableSetOf<Diagnostic>()
        val localCallback = if (callback != null) { d: Diagnostic ->
            localDiagnostics.add(d)
            callback.callback(d)
        } else null

        val result = try {
            analyze(element, null, localCallback)
        } catch (e: Throwable) {
            e.throwAsInvalidModuleException {
                ProcessCanceledException(it)
            }
            throw e
        }

        // some diagnostics could be not handled with a callback - send out the rest
        callback?.let { c ->
            result.bindingContext.diagnostics.filterNot { it in localDiagnostics }.forEach(c::callback)
        }
        return result
    }

    private fun getIncrementalAnalysisResult(callback: DiagnosticSink.DiagnosticsCallback?): AnalysisResult? {
        updateFileResultFromCache()

        val inBlockModifications = file.inBlockModifications
        if (inBlockModifications.isNotEmpty()) {
            try {
                // IF there is a cached result for ktFile and there are inBlockModifications
                fileResult = fileResult?.let { result ->
                    var analysisResult = result
                    // Force full analysis when existed is erroneous
                    if (analysisResult.isError()) return@let null
                    for (inBlockModification in inBlockModifications) {
                        val resultCtx = analysisResult.bindingContext

                        val stackedCtx = resultCtx as? StackedCompositeBindingContextTrace.StackedCompositeBindingContext
                        // no incremental analysis IF it is not applicable
                        if (stackedCtx?.isIncrementalAnalysisApplicable() == false) return@let null

                        val trace: StackedCompositeBindingContextTrace =
                            if (stackedCtx != null && stackedCtx.element() == inBlockModification) {
                                val trace = stackedCtx.bindingTrace()
                                trace.clear()
                                trace
                            } else {
                                // to reflect a depth of stacked binding context
                                val depth = (stackedCtx?.depth() ?: 0) + 1

                                StackedCompositeBindingContextTrace(
                                    depth,
                                    element = inBlockModification,
                                    resolveContext = resolveSession.bindingContext,
                                    parentContext = resultCtx
                                )
                            }

                        callback?.let { trace.parentDiagnosticsApartElement.forEach(it::callback) }

                        val newResult = analyze(inBlockModification, trace, callback)
                        analysisResult = wrapResult(result, newResult, trace)
                    }
                    file.removeInBlockModifications(inBlockModifications)

                    analysisResult
                }
            } catch (e: Throwable) {
                e.throwAsInvalidModuleException {
                    clearFileResultCache()
                    ProcessCanceledException(it)
                }
                if (e !is ControlFlowException) {
                    clearFileResultCache()
                }
                throw e
            }
        }
        if (fileResult == null) {
            file.clearInBlockModifications()
        }
        return fileResult
    }

    private fun updateFileResultFromCache() {
        // move fileResult from cache if it is stored there
        if (fileResult == null && cache.containsKey(file)) {
            fileResult = cache[file]

            // drop existed results for entire cache:
            // if incremental analysis is applicable it will produce a single value for file
            // otherwise those results are potentially stale
            cache.clear()
        }
    }

    private fun lookUp(analyzableElement: KtElement): AnalysisResult? {
        // Looking for parent elements that are already analyzed
        // Also removing all elements whose parents are already analyzed, to guarantee consistency
        val descendantsOfCurrent = arrayListOf<PsiElement>()
        val toRemove = hashSetOf<PsiElement>()

        var result: AnalysisResult? = null
        for (current in analyzableElement.parentsWithSelf) {
            val cached = cache[current]
            if (cached != null) {
                result = cached
                toRemove.addAll(descendantsOfCurrent)
                descendantsOfCurrent.clear()
            }

            descendantsOfCurrent.add(current)
        }

        cache.keys.removeAll(toRemove)

        return result
    }

    private fun wrapResult(
        oldResult: AnalysisResult,
        newResult: AnalysisResult,
        elementBindingTrace: StackedCompositeBindingContextTrace
    ): AnalysisResult {
        val newBindingCtx = elementBindingTrace.stackedContext
        return when {
            oldResult.isError() -> {
                oldResult.error.throwAsInvalidModuleException()
                AnalysisResult.internalError(newBindingCtx, oldResult.error)
            }
            newResult.isError() -> {
                newResult.error.throwAsInvalidModuleException()
                AnalysisResult.internalError(newBindingCtx, newResult.error)
            }
            else -> {
                AnalysisResult.success(
                    newBindingCtx,
                    oldResult.moduleDescriptor,
                    oldResult.shouldGenerateCode
                )
            }
        }
    }

    private fun analyze(
        analyzableElement: KtElement,
        bindingTrace: BindingTrace?,
        callback: DiagnosticSink.DiagnosticsCallback?
    ): AnalysisResult {
        ProgressIndicatorProvider.checkCanceled()

        val project = analyzableElement.project
        if (DumbService.isDumb(project)) {
            return AnalysisResult.EMPTY
        }

        moduleDescriptor.assertValid()
        try {
            return KotlinResolveDataProvider.analyze(
                project,
                globalContext,
                moduleDescriptor,
                resolveSession,
                codeFragmentAnalyzer,
                pluginDeclarationProviderFactory,
                bodyResolveCache,
                analyzableElement,
                bindingTrace,
                callback
            )
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: IndexNotReadyException) {
            throw e
        } catch (e: Throwable) {
            e.throwAsInvalidModuleException()

            DiagnosticUtils.throwIfRunningOnServer(e)
            LOG.warn(e)

            return AnalysisResult.internalError(BindingContext.EMPTY, e)
        }
    }

    private fun clearFileResultCache() {
        file.clearInBlockModifications()
        fileResult = null
    }

    private fun KtElement.isUsedForCompilationInEvaluator(): Boolean =
        containingFile is KtCodeFragment && containingFile.getCopyableUserData(CodeFragmentUtils.USED_FOR_COMPILATION_IN_IR_EVALUATOR) ?: false
}

private fun Throwable.asInvalidModuleException(): InvalidModuleException? {
    return when (this) {
        is InvalidModuleException -> this
        is AssertionError ->
            // temporary workaround till 1.6.0 / KT-48977
            if (message?.contains("contained in his own dependencies, this is probably a misconfiguration") == true)
                InvalidModuleException(message!!)
            else null
        else -> cause?.takeIf { it != this }?.asInvalidModuleException()
    }
}

private inline fun Throwable.throwAsInvalidModuleException(crossinline action: (InvalidModuleException) -> Throwable = { it }) {
    asInvalidModuleException()?.let {
        throw action(it)
    }
}

private class MergedDiagnostics(
    val diagnostics: Collection<Diagnostic>,
    val noSuppressionDiagnostics: Collection<Diagnostic>,
    override val modificationTracker: ModificationTracker
) : Diagnostics {
    private val elementsCache = DiagnosticsElementsCache(this) { true }

    override fun all() = diagnostics

    override fun forElement(psiElement: PsiElement): MutableCollection<Diagnostic> = elementsCache.getDiagnostics(psiElement)

    override fun noSuppression() = if (noSuppressionDiagnostics.isEmpty()) {
        this
    } else {
        MergedDiagnostics(noSuppressionDiagnostics, emptyList(), modificationTracker)
    }
}

/**
 * Keep in mind: trace fallbacks to [resolveContext] (is used during resolve) that does not have any
 * traces of earlier resolve for this [element]
 *
 * When trace turned into [BindingContext] it fallbacks to [parentContext]:
 * It is expected that all slices specific to [element] (and its descendants) are stored in this binding context
 * and for the rest elements it falls back to [parentContext].
 */
private class StackedCompositeBindingContextTrace(
    val depth: Int, // depth of stack over original ktFile bindingContext
    val element: KtElement,
    val resolveContext: BindingContext,
    val parentContext: BindingContext
) : DelegatingBindingTrace(
    resolveContext,
    "Stacked trace for resolution of $element",
    allowSliceRewrite = true
) {
    /**
     * Effectively StackedCompositeBindingContext holds up-to-date and partially outdated contexts (parentContext)
     *
     * The most up-to-date results for element are stored here (in a DelegatingBindingTrace#map)
     *
     * Note: It does not delete outdated results rather hide it therefore there is some extra memory footprint.
     *
     * Note: stackedContext differs from DelegatingBindingTrace#bindingContext:
     *      if result is not present in this context it goes to parentContext rather to resolveContext
     *      diagnostics are aggregated from this context and parentContext
     */
    val stackedContext = StackedCompositeBindingContext()

    /**
     * All diagnostics from parentContext apart this diagnostics this belongs to the element or its descendants
     */
    val parentDiagnosticsApartElement: Collection<Diagnostic> =
        (resolveContext.diagnostics.all() + parentContext.diagnostics.all()).filterApartElement()

    val parentDiagnosticsNoSuppressionApartElement: Collection<Diagnostic> =
        (resolveContext.diagnostics.noSuppression() + parentContext.diagnostics.noSuppression()).filterApartElement()

    private fun Collection<Diagnostic>.filterApartElement() =
        toSet().let { s ->
            s.filter { it.psiElement == element && selfDiagnosticToHold(it) } +
                    s.filter { it.psiElement.parentsWithSelf.none { e -> e == element } }
        }

    inner class StackedCompositeBindingContext : BindingContext {
        var cachedDiagnostics: Diagnostics? = null

        fun bindingTrace(): StackedCompositeBindingContextTrace = this@StackedCompositeBindingContextTrace

        fun element(): KtElement = this@StackedCompositeBindingContextTrace.element

        fun depth(): Int = this@StackedCompositeBindingContextTrace.depth

        // to prevent too deep stacked binding context
        fun isIncrementalAnalysisApplicable(): Boolean = this@StackedCompositeBindingContextTrace.depth < 16

        // Predicate to check if the receiver is a PsiElement that was reanalyzed and therefore should
        // have a result in the reanalysis context. We should not look such elements up in the
        // parent context when there is no information for it in the current context. Because of mutations
        // to PsiElements, that could result in incorrect information
        // (see https://youtrack.jetbrains.com/issue/KTIJ-26856).
        private fun <K: Any?> K.containedInReanalyzedElement(): Boolean {
            return when (element) {
                is KtDeclarationWithBody -> {
                    // Psi elements within the body of a reanalyzed function should have
                    // information only in reanalysis context.
                    val body = element.bodyExpression ?: return false
                    (this as? PsiElement)?.parentsWithSelf?.contains(body) == true
                }
                is KtClassOrObject -> {
                    // Psi elements within anonymous initializers and secondary constructors should have information
                    // only in the reanalysis context.
                    (this as? PsiElement)?.parents(withSelf = false)?.any {
                        it in element.getAnonymousInitializers() || it in element.secondaryConstructors
                    } == true
                }
                else -> false
            }
        }

        override fun getDiagnostics(): Diagnostics {
            if (cachedDiagnostics == null) {
                val mergedDiagnostics = mutableSetOf<Diagnostic>()
                mergedDiagnostics.addAll(parentDiagnosticsApartElement)
                this@StackedCompositeBindingContextTrace.mutableDiagnostics?.all()?.let {
                    mergedDiagnostics.addAll(it)
                }

                val mergedNoSuppressionDiagnostics = mutableSetOf<Diagnostic>()
                mergedNoSuppressionDiagnostics += parentDiagnosticsNoSuppressionApartElement
                this@StackedCompositeBindingContextTrace.mutableDiagnostics?.noSuppression()?.let {
                    mergedNoSuppressionDiagnostics.addAll(it)
                }

                cachedDiagnostics = MergedDiagnostics(mergedDiagnostics, mergedNoSuppressionDiagnostics, parentContext.diagnostics.modificationTracker)
            }
            return cachedDiagnostics!!
        }

        override fun <K : Any?, V : Any?> get(slice: ReadOnlySlice<K, V>, key: K): V? {
            selfGet(slice, key)?.let { return it }
            if (!key.containedInReanalyzedElement()) {
                return parentContext.get(slice, key)?.takeIf {
                    (it as? DeclarationDescriptorWithSource)?.source?.getPsi()?.isValid != false
                }
            }
            return null
        }

        override fun getType(expression: KtExpression): KotlinType? {
            val typeInfo = get(BindingContext.EXPRESSION_TYPE_INFO, expression)
            return typeInfo?.type
        }

        override fun <K, V> getKeys(slice: WritableSlice<K, V>): Collection<K> {
            val keys = map.getKeys(slice)
            val fromParent = parentContext.getKeys(slice).filter {
                !it.containedInReanalyzedElement()
            }
            if (keys.isEmpty()) return fromParent
            if (fromParent.isEmpty()) return keys

            return keys + fromParent
        }

        override fun <K : Any, V : Any> getSliceContents(slice: ReadOnlySlice<K, V>): ImmutableMap<K, V> {
            val parentSliceContents = parentContext.getSliceContents(slice).filter {
                !it.key.containedInReanalyzedElement()
            }
            val mapSliceContents = map.getSliceContents(slice)
            return ImmutableMap.copyOf(parentSliceContents + mapSliceContents)
        }

        override fun addOwnDataTo(trace: BindingTrace, commitDiagnostics: Boolean) = throw UnsupportedOperationException()

        override fun getProject(): Project? {
            return this@StackedCompositeBindingContextTrace.project
        }
    }

    override fun <K : Any?, V : Any?> get(slice: ReadOnlySlice<K, V>, key: K): V? =
        if (slice == BindingContext.ANNOTATION) {
            selfGet(slice, key) ?: parentContext.get(slice, key)
        } else {
            super.get(slice, key)
        }

    override fun clear() {
        super.clear()
        stackedContext.cachedDiagnostics = null
    }

    companion object {
        private fun selfDiagnosticToHold(d: Diagnostic): Boolean {
            @Suppress("MoveVariableDeclarationIntoWhen")
            val positioningStrategy = d.factory.safeAs<DiagnosticFactoryWithPsiElement<*, *>>()?.positioningStrategy
            return when (positioningStrategy) {
                DECLARATION_WITH_BODY -> false
                else -> true
            }
        }
    }
}

private object KotlinResolveDataProvider {
    fun findAnalyzableParent(element: KtElement): KtElement? {
        if (element is KtFile) return element

        val topmostElement = element.findTopmostParentInFile {
            it is KtNamedFunction ||
            it is KtAnonymousInitializer ||
            it is KtProperty ||
            it is KtImportDirective ||
            it is KtPackageDirective ||
            it is KtCodeFragment ||
            // TODO: Non-analyzable so far, add more granular analysis
            it is KtAnnotationEntry ||
            it is KtTypeConstraint ||
            it is KtSuperTypeList ||
            it is KtTypeParameter ||
            it is KtParameter ||
            it is KtTypeAlias
        } as KtElement?

        // parameters and supertype lists are not analyzable by themselves, but if we don't count them as topmost, we'll stop inside, say,
        // object expressions inside arguments of super constructors of classes (note that classes themselves are not topmost elements)
        val analyzableElement = when (topmostElement) {
            is KtAnnotationEntry,
            is KtTypeConstraint,
            is KtSuperTypeList,
            is KtTypeParameter,
            is KtParameter -> topmostElement.findParentInFile { it is KtClassOrObject || it is KtCallableDeclaration } as? KtElement?
            else -> topmostElement
        }
        // Primary constructor should never be returned
        if (analyzableElement is KtPrimaryConstructor) return analyzableElement.getContainingClassOrObject()
        // Class initializer should be replaced by containing class to provide full analysis
        if (analyzableElement is KtClassInitializer) return analyzableElement.containingDeclaration
        return analyzableElement
        // if none of the above worked, take the outermost declaration
            ?: element.findTopmostParentOfType<KtDeclaration>()
            // if even that didn't work, take the whole file
            ?: element.containingFile as? KtFile
    }

    fun analyze(
        project: Project,
        globalContext: GlobalContext,
        moduleDescriptor: ModuleDescriptor,
        resolveSession: ResolveSession,
        codeFragmentAnalyzer: CodeFragmentAnalyzer,
        pluginDeclarationProviderFactory: PluginDeclarationProviderFactory,
        bodyResolveCache: BodyResolveCache,
        analyzableElement: KtElement,
        bindingTrace: BindingTrace?,
        callback: DiagnosticSink.DiagnosticsCallback?
    ): AnalysisResult {
        try {
            if (analyzableElement is KtCodeFragment) {
                val bodyResolveMode = BodyResolveMode.PARTIAL_FOR_COMPLETION
                val trace: BindingTrace = codeFragmentAnalyzer.analyzeCodeFragment(analyzableElement, bodyResolveMode)
                val bindingContext = trace.bindingContext
                return AnalysisResult.success(bindingContext, moduleDescriptor)
            }

            val trace = bindingTrace ?: BindingTraceForBodyResolve(
                resolveSession.bindingContext,
                "Trace for resolution of $analyzableElement"
            )

            val moduleInfo = analyzableElement.containingKtFile.moduleInfo

            val targetPlatform = moduleInfo.platform

            var callbackSet = false
            try {
                callbackSet = callback?.let(trace::setCallbackIfNotSet) ?: false
                /*
                Note that currently we *have* to re-create LazyTopDownAnalyzer with custom trace in order to disallow resolution of
                bodies in top-level trace (trace from DI-container).
                Resolving bodies in top-level trace may lead to memory leaks and incorrect resolution, because top-level
                trace isn't invalidated on in-block modifications (while body resolution surely does)

                Also note that for function bodies, we'll create DelegatingBindingTrace in ResolveElementCache anyways
                (see 'functionAdditionalResolve'). However, this trace is still needed, because we have other
                codepaths for other KtDeclarationWithBodies (like property accessors/secondary constructors/class initializers)
                 */
                val lazyTopDownAnalyzer = createContainerForLazyBodyResolve(
                    //TODO: should get ModuleContext
                    globalContext.withProject(project).withModule(moduleDescriptor),
                    resolveSession,
                    trace,
                    targetPlatform,
                    bodyResolveCache,
                    targetPlatform.findAnalyzerServices(project),
                    analyzableElement.languageVersionSettings,
                    IdeaModuleStructureOracle(),
                    IdeMainFunctionDetectorFactory(),
                    IdeSealedClassInheritorsProvider,
                    ControlFlowInformationProviderImpl.Factory,
                    absentDescriptorHandler = IdeaAbsentDescriptorHandler(pluginDeclarationProviderFactory),
                    optimizingOptions = null
                ).get<LazyTopDownAnalyzer>()

                lazyTopDownAnalyzer.analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, listOf(analyzableElement))
            } finally {
                if (callbackSet) {
                    trace.resetCallback()
                }
            }

            return AnalysisResult.success(trace.bindingContext, moduleDescriptor)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: IndexNotReadyException) {
            throw e
        } catch (e: Throwable) {
            e.throwAsInvalidModuleException()

            DiagnosticUtils.throwIfRunningOnServer(e)
            LOG.warn(e)

            return AnalysisResult.internalError(BindingContext.EMPTY, e)
        }
    }
}
