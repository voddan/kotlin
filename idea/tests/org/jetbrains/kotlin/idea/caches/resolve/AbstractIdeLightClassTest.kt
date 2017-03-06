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

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.LightClassTestCommon
import org.jetbrains.kotlin.asJava.builder.LightClassConstructionContext
import org.jetbrains.kotlin.asJava.builder.LightClassConstructionContextImpl
import org.jetbrains.kotlin.asJava.builder.StubComputationTracker
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForSourceDeclaration
import org.jetbrains.kotlin.idea.KotlinDaemonAnalyzerTestCase
import org.jetbrains.kotlin.idea.caches.resolve.LightClassLazinessChecker.Tracker.Level.*
import org.jetbrains.kotlin.idea.caches.resolve.lightClasses.DummyLightClassConstructionContext
import org.jetbrains.kotlin.idea.completion.test.withServiceRegistered
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.load.java.JvmAnnotationNames
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.MockLibraryUtil
import org.junit.Assert
import java.io.File

abstract class AbstractIdeLightClassTest : KotlinLightCodeInsightFixtureTestCase() {
    fun doTest(testDataPath: String) {
        myFixture.configureByFile(testDataPath)
        val ktFile = myFixture.file as KtFile
        testLightClass(testDataPath, { LightClassTestCommon.removeEmptyDefaultImpls(it) }) { fqName ->
            val tracker = LightClassLazinessChecker.Tracker(fqName)
            project.withServiceRegistered<StubComputationTracker, PsiClass?>(tracker) {
                findClass(fqName, ktFile, project)?.apply {
                    LightClassLazinessChecker.check(this as KtLightClass, tracker)
                    tracker.allowLevel(FULL)
                    PsiElementChecker.checkPsiElementStructure(this)
                }
            }
        }
    }

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE
}

abstract class AbstractIdeCompiledLightClassTest : KotlinDaemonAnalyzerTestCase() {
    override fun setUp() {
        super.setUp()

        val testName = getTestName(false)
        if (KotlinTestUtils.isAllFilesPresentTest(testName)) return

        val filePath = "${KotlinTestUtils.getTestsRoot(this.javaClass)}/${getTestName(false)}.kt"

        Assert.assertTrue("File doesn't exist $filePath", File(filePath).exists())

        val libraryJar = MockLibraryUtil.compileLibraryToJar(filePath, libName(), false, false, false)
        val jarUrl = "jar://" + FileUtilRt.toSystemIndependentName(libraryJar.absolutePath) + "!/"
        ModuleRootModificationUtil.addModuleLibrary(module, jarUrl)
    }

    private fun libName() = "libFor" + getTestName(false)

    fun doTest(testDataPath: String) {
        testLightClass(testDataPath, { it }) {
            findClass(it, null, project)?.apply {
                PsiElementChecker.checkPsiElementStructure(this)
            }
        }
    }
}

private fun testLightClass(testDataPath: String, normalize: (String) -> String, findLightClass: (String) -> PsiClass?) {
    LightClassTestCommon.testLightClass(
            File(testDataPath),
            findLightClass,
            normalizeText = { text ->
                //NOTE: ide and compiler differ in names generated for parameters with unspecified names
                text
                        .replace("java.lang.String s,", "java.lang.String p,")
                        .replace("java.lang.String s)", "java.lang.String p)")
                        .replace("java.lang.String s1", "java.lang.String p1")
                        .replace("java.lang.String s2", "java.lang.String p2")
                        .removeLinesStartingWith("@" + JvmAnnotationNames.METADATA_FQ_NAME.asString())
                        .run(normalize)
            }
    )
}

private fun findClass(fqName: String, ktFile: KtFile?, project: Project): PsiClass? {
    return JavaPsiFacade.getInstance(project).findClass(fqName, GlobalSearchScope.allScope(project)) ?:
           PsiTreeUtil.findChildrenOfType(ktFile, KtClassOrObject::class.java)
                   .find { fqName.endsWith(it.nameAsName!!.asString()) }
                   ?.let { KtLightClassForSourceDeclaration.create(it) }
}

object LightClassLazinessChecker {

    class Tracker(private val fqName: String) : StubComputationTracker {

        private var level = NONE
            set(newLevel) {
                if (newLevel.ordinal <= field.ordinal) {
                    error("Level should not decrease at any point")
                }
                if (newLevel.ordinal > allowedLevel.ordinal) {
                    error("Level increased before it was expected $level -> $newLevel, allowed: $allowedLevel")
                }
                field = newLevel
            }

        private var allowedLevel = NONE

        enum class Level {
            NONE,
            DUMMY,
            FULL
        }

        override fun onStubComputed(javaFileStub: PsiJavaFileStub, context: LightClassConstructionContext) {
            assert(fqName == javaFileStub.classes.single().qualifiedName!!)
            when {
                context is DummyLightClassConstructionContext -> level = DUMMY
                context is LightClassConstructionContextImpl -> level = FULL
                else -> error("Unknown context ${context::class}")
            }
        }

        fun checkExactLevel(expectedLevel: Level) {
            assert(level == expectedLevel)
        }

        fun allowLevel(newAllowed: Level) {
            allowedLevel = newAllowed
        }
    }

    fun check(lightClass: KtLightClass, tracker: Tracker) {
        with(tracker) {
            allowLevel(DUMMY)
            lightClass.fields
            allowLevel(FULL)
            lightClass.superClass
        }
    }

}

private fun String.removeLinesStartingWith(prefix: String): String {
    return lines().filterNot { it.trimStart().startsWith(prefix) }.joinToString(separator = "\n")
}
