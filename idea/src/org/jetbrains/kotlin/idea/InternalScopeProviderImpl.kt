/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import org.jetbrains.kotlin.idea.caches.resolve.*
import org.jetbrains.kotlin.idea.search.allScope
import org.jetbrains.kotlin.psi.InternalScopeProvider
import org.jetbrains.kotlin.psi.KtElement

class InternalScopeProviderImpl : InternalScopeProvider {
    override fun getInternalScope(element: KtElement): SearchScope {
        val ktFile = element.getContainingKtFile()
        val project = ktFile.project
        val module = ModuleUtilCore.findModuleForPsiElement(element)
        val moduleScope = module?.moduleScope ?: return project.allScope()
        val resolutionFacade = ktFile.getResolutionFacade()
        val moduleDescriptor = ktFile.getNullableModuleInfo()?.let { resolutionFacade.findModuleDescriptor(it) } ?: return moduleScope
        return object : GlobalSearchScope() {
            private fun isSearchInModuleContent(aModule: Module, virtualFile: VirtualFile?): Boolean {
                if (aModule == module) return true

                val aModuleInfos = if (virtualFile != null) {
                    listOf(getModuleInfoByVirtualFile(project, virtualFile, false))
                } else {
                    listOf(aModule.productionSourceInfo(), aModule.testSourceInfo())
                }
                return aModuleInfos.any { resolutionFacade.findModuleDescriptor(it)?.shouldSeeInternalsOf(moduleDescriptor) ?: false }
            }

            override fun compare(file1: VirtualFile, file2: VirtualFile) = moduleScope.compare(file1, file2)

            override fun isSearchInModuleContent(aModule: Module) = isSearchInModuleContent(aModule, null)

            override fun isSearchInLibraries() = moduleScope.isSearchInLibraries

            override fun contains(file: VirtualFile): Boolean {
                if (moduleScope.contains(file)) return true
                val moduleForFile = ModuleUtilCore.findModuleForFile(file, project) ?: return false
                return isSearchInModuleContent(moduleForFile, file)
            }
        }
    }
}