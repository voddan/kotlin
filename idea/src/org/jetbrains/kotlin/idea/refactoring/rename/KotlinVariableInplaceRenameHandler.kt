/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.lang.LanguageRefactoringSupport
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
import org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringSupportProvider

class KotlinVariableInplaceRenameHandler : VariableInplaceRenameHandler() {
    override fun isAvailable(element: PsiElement?, editor: Editor, file: PsiFile): Boolean {
        if (element == null) return false
        val supportProvider = LanguageRefactoringSupport.INSTANCE.forLanguage (element.language)
        return supportProvider is KotlinRefactoringSupportProvider &&
               editor.settings.isVariableInplaceRenameEnabled &&
               supportProvider.isKotlinVariableInplaceRenameAvailable(element)
    }

    override fun createRenamer(elementToRename: PsiElement, editor: Editor): VariableInplaceRenamer? {
        val namedElement = elementToRename as? PsiNameIdentifierOwner ?: return null
        val name = namedElement.nameIdentifier?.text
        return VariableInplaceRenamer(namedElement, editor, elementToRename.project, name, name)
    }
}
