// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class ExplicitTypeCanBeDiamondInspection extends AbstractBaseJavaLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance(ExplicitTypeCanBeDiamondInspection.class);

  @Override
  public @Nls @NotNull String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.language.level.specific.issues.and.migration.aids");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull String getShortName() {
    return "Convert2Diamond";
  }

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.DIAMOND_TYPES);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitNewExpression(@NotNull PsiNewExpression expression) {
        if (PsiDiamondTypeUtil.canCollapseToDiamond(expression, expression, null)) {
          final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
          LOG.assertTrue(classReference != null);
          final PsiReferenceParameterList parameterList = classReference.getParameterList();
          LOG.assertTrue(parameterList != null);
          if (PsiTreeUtil.findChildOfType(parameterList, PsiAnnotation.class) != null) {
            return;
          }
          final PsiElement firstChild = parameterList.getFirstChild();
          final PsiElement lastChild = parameterList.getLastChild();
          final TextRange range = new TextRange(firstChild != null && firstChild.getNode().getElementType() == JavaTokenType.LT ? 1 : 0,
                                                parameterList.getTextLength() - (lastChild != null && lastChild.getNode().getElementType() == JavaTokenType.GT ? 1 : 0));
          holder.registerProblem(parameterList, range, JavaAnalysisBundle.message("explicit.type.argument.ref.loc.can.be.replaced.with"), new ReplaceWithDiamondFix());
        }
      }
    };
  }

  private static class ReplaceWithDiamondFix extends PsiUpdateModCommandQuickFix implements HighPriorityAction {
    @Override
    public @NotNull String getFamilyName() {
      return InspectionsBundle.message("quickfix.family.replace.with.diamond");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiNewExpression newExpression =
        PsiTreeUtil.getParentOfType(RemoveRedundantTypeArgumentsUtil.replaceExplicitWithDiamond(element), PsiNewExpression.class);
      if (newExpression != null) {
        CodeStyleManager.getInstance(project).reformat(newExpression);
      }
    }
  }
}
