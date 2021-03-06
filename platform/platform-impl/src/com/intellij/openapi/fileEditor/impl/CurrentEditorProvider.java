// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.fileEditor.FileEditor;

/**
 * @author max
 */
public interface CurrentEditorProvider {
  FileEditor getCurrentEditor();
}
