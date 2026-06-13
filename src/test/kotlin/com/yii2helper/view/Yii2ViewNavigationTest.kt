package com.yii2helper.view

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class Yii2ViewNavigationTest : BasePlatformTestCase() {

    private val yiiStub = """
        <?php
        namespace yii\base;
        class Controller {
            public function render(${'$'}view, ${'$'}params = []) {}
            public function renderPartial(${'$'}view, ${'$'}params = []) {}
            public function renderAjax(${'$'}view, ${'$'}params = []) {}
            public function renderFile(${'$'}file, ${'$'}params = []) {}
        }
        class Widget {
            public function render(${'$'}view, ${'$'}params = []) {}
        }
        class View {}
    """.trimIndent()

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("yii/base/classes.php", yiiStub)
    }

    fun `test render plain name resolves to controller view`() {
        val view = myFixture.addFileToProject("views/site/index.php", "<?php\n")
        val controller = myFixture.addFileToProject(
            "controllers/SiteController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                class SiteController extends Controller {
                    public function actionIndex() {
                        return ${'$'}this->render('index', []);
                    }
                }
            """.trimIndent(),
        )

        assertSame(view.virtualFile, resolveViewRef(controller, "index"))
    }

    fun `test renderPartial in subnamespace controller maps id to dashes`() {
        val view = myFixture.addFileToProject("views/admin/user-profile/list.php", "<?php\n")
        val controller = myFixture.addFileToProject(
            "controllers/admin/UserProfileController.php",
            """
                <?php
                namespace app\controllers\admin;
                use yii\base\Controller;
                class UserProfileController extends Controller {
                    public function actionList() {
                        return ${'$'}this->renderPartial('list');
                    }
                }
            """.trimIndent(),
        )

        assertSame(view.virtualFile, resolveViewRef(controller, "list"))
    }

    fun `test double slash resolves against application view path`() {
        val view = myFixture.addFileToProject("views/layouts/main.php", "<?php\n")
        val controller = myFixture.addFileToProject(
            "controllers/SiteController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                class SiteController extends Controller {
                    public function actionIndex() {
                        return ${'$'}this->render('//layouts/main');
                    }
                }
            """.trimIndent(),
        )

        assertSame(view.virtualFile, resolveViewRef(controller, "//layouts/main"))
    }

    fun `test app alias resolves against application base`() {
        val view = myFixture.addFileToProject("views/site/about.php", "<?php\n")
        val controller = myFixture.addFileToProject(
            "controllers/SiteController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                class SiteController extends Controller {
                    public function actionAbout() {
                        return ${'$'}this->render('@app/views/site/about');
                    }
                }
            """.trimIndent(),
        )

        assertSame(view.virtualFile, resolveViewRef(controller, "@app/views/site/about"))
    }

    fun `test missing view is flagged with create quick fix`() {
        myFixture.enableInspections(MissingViewInspection())
        val controller = myFixture.addFileToProject(
            "controllers/UserController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                class UserController extends Controller {
                    public function actionIndex() {
                        return ${'$'}this->render('index');
                    }
                }
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(controller.virtualFile)

        val highlights = myFixture.doHighlighting()
        assertTrue(
            "Expected a missing-view warning",
            highlights.any { it.description?.contains("does not exist") == true },
        )

        val fix = myFixture.getAllQuickFixes().firstOrNull { it.familyName == "Create Yii2 view file" }
        assertNotNull("Expected a create-view quick fix", fix)
        myFixture.launchAction(fix!!)

        val created = myFixture.findFileInTempDir("views/user/index.php")
        assertNotNull("Quick fix should create the view file", created)
    }

    private fun resolveViewRef(file: PsiFile, viewName: String): Any? {
        val literal = PsiTreeUtil.collectElementsOfType(file, StringLiteralExpression::class.java)
            .first { it.contents == viewName }
        val reference = literal.references.firstOrNull { it is ViewFileReference }
        assertNotNull("Expected a ViewFileReference on '$viewName'", reference)
        val resolved = reference!!.resolve()
        return (resolved as? PsiFile)?.virtualFile
    }
}
