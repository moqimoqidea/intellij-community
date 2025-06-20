/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyInstantTypeProvider;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyNumericLiteralExpressionImpl extends PyElementImpl implements PyNumericLiteralExpression, PyInstantTypeProvider {

  public PyNumericLiteralExpressionImpl(@NotNull ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyNumericLiteralExpression(this);
  }

  @Override
  public @Nullable PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    if (isIntegerLiteral()) {
      return PyBuiltinCache.getInstance(this).getIntType();
    }

    final IElementType type = getNode().getElementType();
    if (type == PyElementTypes.FLOAT_LITERAL_EXPRESSION) {
      return PyBuiltinCache.getInstance(this).getFloatType();
    }
    else if (type == PyElementTypes.IMAGINARY_LITERAL_EXPRESSION) {
      return PyBuiltinCache.getInstance(this).getComplexType();
    }

    return null;
  }
}
