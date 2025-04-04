/*
 * Copyright 2003-2024 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class ConstantMathCallInspection extends BaseInspection implements CleanupLocalInspectionTool {

  static final @NonNls Set<String> constantMathCall = Set.of(
    "abs", "acos", "asin", "atan",
    "cbrt", "ceil", "cos", "cosh",
    "exp", "expm1",
    "floor",
    "log", "log10", "log1p",
    "rint", "round",
    "sin", "sinh", "sqrt",
    "tan", "tanh",
    "toDegrees", "toRadians");

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "constant.math.call.problem.descriptor");
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new MakeStrictFix();
  }

  private static class MakeStrictFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "constant.conditional.expression.simplify.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiIdentifier nameIdentifier =
        (PsiIdentifier)startElement;
      final PsiReferenceExpression reference =
        (PsiReferenceExpression)nameIdentifier.getParent();
      assert reference != null;
      final PsiMethodCallExpression call =
        (PsiMethodCallExpression)reference.getParent();
      assert call != null;
      final PsiExpressionList argumentList = call.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final String methodName = reference.getReferenceName();
      final PsiExpression argument = arguments[0];
      final PsiMethod method = call.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      if (parameters.length != 1) {
        return;
      }
      final PsiType type = parameters[0].getType();
      final Object argumentValue =
        ConstantExpressionUtil.computeCastTo(argument, type);
      final String newExpression;
      if (argumentValue instanceof Float ||
          argumentValue instanceof Double) {
        final Number number = (Number)argumentValue;
        newExpression = createValueString(methodName,
                                          number.doubleValue());
      }
      else {
        final Number number = (Number)argumentValue;
        newExpression = createValueString(methodName,
                                          number.longValue());
      }
      if (newExpression == null) {
        return;
      }
      if (PsiTypes.longType().equals(type)) {
        PsiReplacementUtil.replaceExpressionAndShorten(call, newExpression + 'L', new CommentTracker());
      }
      else {
        PsiReplacementUtil.replaceExpressionAndShorten(call, newExpression, new CommentTracker());
      }
    }
  }

  @SuppressWarnings({"NestedMethodCall", "FloatingPointEquality"})
  static @Nullable @NonNls String createValueString(@NonNls String name, double value) {
    if ("abs".equals(name)) {
      return Double.toString(Math.abs(value));
    }
    else if ("floor".equals(name)) {
      return Double.toString(Math.floor(value));
    }
    else if ("ceil".equals(name)) {
      return Double.toString(Math.ceil(value));
    }
    else if ("toDegrees".equals(name)) {
      return Double.toString(Math.toDegrees(value));
    }
    else if ("toRadians".equals(name)) {
      return Double.toString(Math.toRadians(value));
    }
    else if ("sqrt".equals(name)) {
      return Double.toString(Math.sqrt(value));
    }
    else if ("cbrt".equals(name)) {
      return Double.toString(Math.pow(value, 1.0 / 3.0));
    }
    else if ("round".equals(name)) {
      return Long.toString(Math.round(value));
    }
    else if ("rint".equals(name)) {
      return Double.toString(Math.rint(value));
    }
    else if ("log".equals(name)) {
      if (value == 1.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("log10".equals(name)) {
      if (value == 1.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("log1p".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("exp".equals(name)) {
      if (value == 0.0) {
        return "1.0";
      }
      else if (value == 1.0) {
        return "Math.E";
      }
      else {
        return null;
      }
    }
    else if ("expm1".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("cos".equals(name) || "cosh".equals(name)) {
      if (value == 0.0) {
        return "1.0";
      }
      else {
        return null;
      }
    }
    else if ("acos".equals(name)) {
      if (value == 1.0) {
        return "0.0";
      }
      else if (value == 0.0) {
        return "(Math.PI/2.0)";
      }
      else {
        return null;
      }
    }
    else if ("acosh".equals(name)) {
      if (value == 1.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("sin".equals(name) || "sinh".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("asin".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else if (value == 1.0) {
        return "(Math.PI/2.0)";
      }
      else {
        return null;
      }
    }
    else if ("asinh".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("tan".equals(name) || "tanh".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("atan".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else if (value == 1.0) {
        return "(Math.PI/4.0)";
      }
      else {
        return null;
      }
    }
    else if ("atanh".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    return null;
  }

  static @Nullable @NonNls String createValueString(@NonNls String name, long value) {
    if ("abs".equals(name)) {
      return Long.toString(Math.abs(value));
    }
    return null;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConstantMathCallVisitor();
  }

  private static class ConstantMathCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!constantMathCall.contains(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression argument = arguments[0];
      final Object argumentValue =
        ConstantExpressionUtil.computeCastTo(argument, PsiTypes.doubleType());
      if (!(argumentValue instanceof Double)) {
        return;
      }
      final double doubleValue = ((Double)argumentValue).doubleValue();
      final String valueString = createValueString(methodName,
                                                   doubleValue);
      if (valueString == null) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass referencedClass = method.getContainingClass();
      if (referencedClass == null) {
        return;
      }
      final String className = referencedClass.getQualifiedName();
      if (!CommonClassNames.JAVA_LANG_MATH.equals(className)
          && !CommonClassNames.JAVA_LANG_STRICT_MATH.equals(className)) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}
