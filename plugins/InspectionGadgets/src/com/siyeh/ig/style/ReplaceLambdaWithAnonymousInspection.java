/*
 * Copyright 2011 Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.containers.HashMap;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class ReplaceLambdaWithAnonymousInspection extends BaseInspection {
  private static final Logger LOG = Logger.getInstance("#" + ReplaceLambdaWithAnonymousInspection.class.getName());

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("replace.lambda.with.anonymous.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("replace.lambda.with.anonymous.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LambdaToAnonymousVisitor();
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new LambdaToAnonymousFix();
  }

  private static void doFix(@NotNull Project project, @NotNull PsiLambdaExpression lambdaExpression) {
    final PsiParameter[] paramListCopy = ((PsiParameterList)lambdaExpression.getParameterList().copy()).getParameters();
    final PsiType functionalInterfaceType = lambdaExpression.getFunctionalInterfaceType();
    LOG.assertTrue(functionalInterfaceType != null);
    final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
    LOG.assertTrue(method != null);

    final String blockText = getBodyText(lambdaExpression);
    if (blockText == null) return;

    final PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(project);
    PsiCodeBlock blockFromText = psiElementFactory.createCodeBlockFromText(blockText, lambdaExpression);
    qualifyThisExpressions(lambdaExpression, psiElementFactory, blockFromText);
    blockFromText = psiElementFactory.createCodeBlockFromText(blockFromText.getText(), null);
    
    PsiNewExpression newExpression = (PsiNewExpression)psiElementFactory.createExpressionFromText("new " + functionalInterfaceType.getCanonicalText() + "(){}", lambdaExpression);
    newExpression = (PsiNewExpression)JavaCodeStyleManager.getInstance(project).shortenClassReferences(lambdaExpression.replace(newExpression));

    final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
    LOG.assertTrue(anonymousClass != null);
    final List<PsiGenerationInfo<PsiMethod>> infos = OverrideImplementUtil.overrideOrImplement(anonymousClass, method);
    if (infos != null && infos.size() == 1) {
      PsiMethod member = infos.get(0).getPsiMember();
      final PsiParameter[] parameters = member.getParameterList().getParameters();
      if (parameters.length == paramListCopy.length) {
        for (int i = 0; i < parameters.length; i++) {
          final PsiParameter parameter = parameters[i];
          final String lambdaParamName = paramListCopy[i].getName();
          if (lambdaParamName != null) {
            parameter.setName(lambdaParamName);
          }
        }
      }
      PsiCodeBlock codeBlock = member.getBody();
      LOG.assertTrue(codeBlock != null);
      codeBlock.replace(blockFromText);

      final PsiElement parent = anonymousClass.getParent().getParent();
      if (parent instanceof PsiTypeCastExpression && RedundantCastUtil.isCastRedundant((PsiTypeCastExpression)parent)) {
        final PsiExpression operand = ((PsiTypeCastExpression)parent).getOperand();
        LOG.assertTrue(operand != null);
        parent.replace(operand);
      }
    }
  }

  private static void qualifyThisExpressions(final PsiLambdaExpression lambdaExpression,
                                             final PsiElementFactory psiElementFactory,
                                             final PsiCodeBlock blockFromText) {
    ChangeContextUtil.encodeContextInfo(blockFromText, true);
    final PsiClass thisClass = RefactoringChangeUtil.getThisClass(lambdaExpression);
    final String thisClassName = thisClass != null ? thisClass.getName() : null;
    if (thisClassName != null) {
      final PsiThisExpression thisAccessExpr = thisClass instanceof PsiAnonymousClass ? null : RefactoringChangeUtil
        .createThisExpression(lambdaExpression.getManager(), thisClass);
      ChangeContextUtil.decodeContextInfo(blockFromText, thisClass, thisAccessExpr);
      final Map<PsiElement, PsiElement> replacements = new HashMap<PsiElement, PsiElement>();
      blockFromText.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitClass(PsiClass aClass) {}

        @Override
        public void visitSuperExpression(PsiSuperExpression expression) {
          super.visitSuperExpression(expression);
          if (expression.getQualifier() == null) {
            replacements.put(expression, psiElementFactory.createExpressionFromText(thisClassName + "." + expression.getText(), expression));
          }
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
          super.visitMethodCallExpression(expression);
          if (thisAccessExpr != null) {
            final PsiMethod psiMethod = expression.resolveMethod();
            if (psiMethod != null && !psiMethod.hasModifierProperty(PsiModifier.STATIC) && expression.getMethodExpression().getQualifierExpression() == null) {
              replacements.put(expression, psiElementFactory.createExpressionFromText(thisAccessExpr.getText() + "." + expression.getText(), expression));
            }
          }
        }
      });
      for (PsiElement psiElement : replacements.keySet()) {
        psiElement.replace(replacements.get(psiElement));
      }
    }
  }

  private static String getBodyText(PsiLambdaExpression lambdaExpression) {
    String blockText;
    final PsiElement body = lambdaExpression.getBody();
    if (body instanceof PsiExpression) {
      final PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(lambdaExpression);
      blockText = "{";
      blockText += PsiType.VOID.equals(returnType) ? "" : "return ";
      blockText +=  body.getText() + ";}";
    } else if (body != null) {
      blockText = body.getText();
    } else {
      blockText = null;
    }
    return blockText;
  }

  private static class LambdaToAnonymousVisitor extends BaseInspectionVisitor {
    @Override
    public void visitParameterList(PsiParameterList parameterList) {
      super.visitParameterList(parameterList);
      if (isConvertibleLambdaExpression(parameterList.getParent())) {
        registerError(parameterList);
      }
    }

    @Override
    public void visitJavaToken(PsiJavaToken token) {
      super.visitJavaToken(token);
      if (token.getTokenType() == JavaTokenType.ARROW) {
        if (isConvertibleLambdaExpression(token.getParent())) {
          registerError(token);
        }
      }
    }

    private static boolean isConvertibleLambdaExpression(PsiElement parent) {
      if (parent instanceof PsiLambdaExpression) {
        final PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)parent;
        final PsiClass thisClass = PsiTreeUtil.getParentOfType(lambdaExpression, PsiClass.class, true);
        if (thisClass == null || thisClass instanceof PsiAnonymousClass) {
          final PsiElement body = lambdaExpression.getBody();
          if (body == null) return false;
          final boolean [] disabled = new boolean[1];
          body.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitThisExpression(PsiThisExpression expression) {
              disabled[0] = true;
            }

            @Override
            public void visitSuperExpression(PsiSuperExpression expression) {
              disabled[0] = true;
            }
          });
          if (disabled[0]) return false;
        }
        final PsiType functionalInterfaceType = lambdaExpression.getFunctionalInterfaceType();
        if (functionalInterfaceType != null &&
            LambdaUtil.isLambdaFullyInferred(lambdaExpression, functionalInterfaceType) &&
            LambdaUtil.isFunctionalType(functionalInterfaceType)) {
          final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
          if (interfaceMethod != null) {
            final PsiSubstitutor substitutor =
              LambdaUtil.getSubstitutor(interfaceMethod, PsiUtil.resolveGenericsClassInType(functionalInterfaceType));
            for (PsiType type : interfaceMethod.getSignature(substitutor).getParameterTypes()) {
              if (!PsiTypesUtil.isDenotableType(type)) {
                return false;
              }
            }
            final PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);
            return PsiTypesUtil.isDenotableType(returnType);
          }
        }
      }
      return false;
    }
  }

  private static class LambdaToAnonymousFix extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("replace.lambda.with.anonymous.descriptor");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.lambda.with.anonymous.name");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiLambdaExpression) {
        ReplaceLambdaWithAnonymousInspection.doFix(project, (PsiLambdaExpression)parent);
      }
    }
  }
}
