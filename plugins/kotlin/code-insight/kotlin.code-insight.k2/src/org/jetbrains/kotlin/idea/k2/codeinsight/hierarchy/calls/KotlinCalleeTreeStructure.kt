// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.calls

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.hierarchy.call.CallHierarchyNodeDescriptor
import com.intellij.ide.hierarchy.call.CalleeMethodsTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.util.ArrayUtil
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

class KotlinCalleeTreeStructure(
    element: KtElement,
    private val scopeType: String
) : HierarchyTreeStructure(
    element.project,
    KotlinCallHierarchyNodeDescriptor(null, element, true, false)
) {
    private fun KtElement.getCalleeSearchScope(): List<KtElement> = when (this) {
        is KtNamedFunction, is KtFunctionLiteral, is KtPropertyAccessor -> listOf((this as KtDeclarationWithBody).bodyExpression)
        is KtProperty -> accessors.map { it.bodyExpression }
        is KtClassOrObject -> {
            superTypeListEntries.filterIsInstance<KtCallElement>() +
                    getAnonymousInitializers().map { it.body } +
                    declarations.asSequence().filterIsInstance<KtProperty>().map { it.initializer }.toList()
        }
        else -> emptyList()
    }.filterNotNull()

    override fun buildChildren(nodeDescriptor: HierarchyNodeDescriptor): Array<Any> {
        if (nodeDescriptor is CallHierarchyNodeDescriptor) {
            val psiMethod = nodeDescriptor.enclosingElement as? PsiMethod ?: return ArrayUtil.EMPTY_OBJECT_ARRAY
            return CalleeMethodsTreeStructure(myProject, psiMethod as PsiMember, scopeType).getChildElements(nodeDescriptor)
        }

        val element = nodeDescriptor.psiElement as? KtElement ?: return ArrayUtil.EMPTY_OBJECT_ARRAY

        val result = LinkedHashSet<HierarchyNodeDescriptor>()
        val baseClass = (element as? KtDeclaration)?.containingClassOrObject
        val calleeToDescriptorMap = HashMap<PsiElement, NodeDescriptor<*>>()

        element.getCalleeSearchScope().forEach {
            it.accept(
                object : CalleeReferenceVisitorBase(false) {
                    override fun processDeclaration(reference: KtSimpleNameExpression, declaration: PsiElement) {
                        if (!isInScope(baseClass, declaration, scopeType)) return
                        result += (getOrCreateNodeDescriptor(
                            parent = nodeDescriptor, originalElement = declaration, reference = null,
                            navigateToReference = false,
                            elementToDescriptorMap = calleeToDescriptorMap,
                            isJavaMap = false
                        )
                            ?: return)
                    }
                }
            )
        }

        for (it in KotlinFindUsagesSupport.searchOverriders(element, element.useScope)) {
            val overrider = it.unwrapped as? KtElement ?: continue
            if (!isInScope(baseClass, overrider, scopeType)) continue
            result += KotlinCallHierarchyNodeDescriptor(nodeDescriptor, overrider, false, false)
        }

        return result.toArray()
    }
}