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

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class KotlinMemberInplaceRenameHandler : MemberInplaceRenameHandler() {
    override fun isAvailable(element: PsiElement?, editor: Editor, file: PsiFile): Boolean {
        var nameSuggestionContext = file.findElementAt(editor.caretModel.offset)
        if (nameSuggestionContext == null && editor.caretModel.offset > 0) {
            nameSuggestionContext = file.findElementAt(editor.caretModel.offset - 1)
        }

        var elementToRename = element
        if (elementToRename == null && LookupManager.getActiveLookup(editor) != null) {
            elementToRename = PsiTreeUtil.getParentOfType(nameSuggestionContext, PsiNamedElement::class.java)
        }

        return editor.settings.isVariableInplaceRenameEnabled && elementToRename is KtNamedDeclaration
    }

    override fun createMemberRenamer(element: PsiElement, elementToRename: PsiNameIdentifierOwner, editor: Editor?): MemberInplaceRenamer {
        val name = elementToRename.nameIdentifier?.text
        return MemberInplaceRenamer(elementToRename, element, editor, name, name)
    }
}
