/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.util.xml;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * Base DOM class to convert objects of a definite type into {@link String} and back. Most often used with
 * {@link com.intellij.util.xml.Convert} annotation with methods returning {@link com.intellij.util.xml.GenericDomValue}&lt;T&gt;.
 *
 * @see com.intellij.util.xml.ResolvingConverter
 * @see com.intellij.util.xml.CustomReferenceConverter
 *
 * @author peter
 */
public abstract class Converter<T> {
  public abstract @Nullable T fromString(@Nullable @NonNls String s, final ConvertContext context);
  public abstract @Nullable String toString(@Nullable T t, final ConvertContext context);

  /**
   * @param s string value that couldn't be resolved
   * @param context context
   * @return error message used to highlight the errors somewhere in the UI, most often - like unresolved references in XML
   */
  @Nullable
  public String getErrorMessage(@Nullable String s, final ConvertContext context) {
    return CodeInsightBundle.message("error.cannot.convert.default.message", s);
  }

  @Deprecated
  public static final Converter<Integer> INTEGER_CONVERTER = new Converter<Integer>() {
    public Integer fromString(final String s, final ConvertContext context) {
      if (s == null) return null;
      try {
        return Integer.decode(s);
      }
      catch (Exception e) {
        return null;
      }
    }

    public String toString(final Integer t, final ConvertContext context) {
      return t == null? null: t.toString();
    }

    public String getErrorMessage(final String s, final ConvertContext context) {
      return IdeBundle.message("value.should.be.integer");
    }
  };

  @Deprecated
  public static final Converter<String> EMPTY_CONVERTER = new Converter<String>() {
    public String fromString(final String s, final ConvertContext context) {
      return s;
    }

    public String toString(final String t, final ConvertContext context) {
      return t;
    }

  };

  @Deprecated
  public static final Converter<PsiClass> PSI_CLASS_CONVERTER = new Converter<PsiClass>() {
    public PsiClass fromString(final String s, final ConvertContext context) {
      return s == null? null:context.findClass(s, null);
    }

    public String toString(final PsiClass t, final ConvertContext context) {
      return t==null?null:t.getQualifiedName();
    }

  };

  @Deprecated
  public static final Converter<PsiType> PSI_TYPE_CONVERTER = new Converter<PsiType>() {
    public PsiType fromString(final String s, final ConvertContext context) {
      if (s == null) return null;
      try {
        return context.getFile().getManager().getElementFactory().createTypeFromText(s, null);
      }
      catch (IncorrectOperationException e) {
        return null;
      }
    }

    public String toString(final PsiType t, final ConvertContext context) {
      return t == null? null:t.getCanonicalText();
    }

  };

}
