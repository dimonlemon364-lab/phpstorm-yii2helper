package com.yii2helper.route

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class Yii2RouteNavigationTest : BasePlatformTestCase() {

    private val yiiStub = """
        <?php
        namespace yii\base;
        class Controller {
            public function render(${'$'}view, ${'$'}params = []) {}
            public function redirect(${'$'}url, ${'$'}statusCode = 302) {}
        }
        namespace yii\helpers;
        class Url {
            public static function to(${'$'}url = '', ${'$'}scheme = false) {}
            public static function toRoute(${'$'}route, ${'$'}scheme = false) {}
            public static function remember(${'$'}url = '', ${'$'}name = null) {}
        }
        class Html {
            public static function a(${'$'}text, ${'$'}url = null, ${'$'}options = []) {}
            public static function beginForm(${'$'}action = '', ${'$'}method = 'post', ${'$'}options = []) {}
        }
        namespace yii\web;
        class UrlManager {
            public function createUrl(${'$'}params) {}
            public function createAbsoluteUrl(${'$'}params, ${'$'}scheme = null) {}
        }
        namespace yii\base;
        class Widget {
            public static function begin(${'$'}config = []) {}
        }
        namespace yii\widgets;
        class ActiveForm extends \yii\base\Widget {}
    """.trimIndent()

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("yii/stub.php", yiiStub)
        // Make the project root look like an app base (controllers/ + views/).
        myFixture.addFileToProject("views/site/index.php", "<?php\n")
    }

    fun `test redirect single segment resolves to current controller action`() {
        val controller = myFixture.addFileToProject(
            "controllers/SiteController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                class SiteController extends Controller {
                    public function actionIndex() {}
                    public function actionGo() {
                        return ${'$'}this->redirect(['index']);
                    }
                }
            """.trimIndent(),
        )

        assertEquals("actionIndex" to "SiteController", resolveAction(controller, "index"))
    }

    fun `test redirect cross-controller route resolves`() {
        myFixture.addFileToProject(
            "controllers/PostController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                class PostController extends Controller {
                    public function actionView() {}
                }
            """.trimIndent(),
        )
        val controller = myFixture.addFileToProject(
            "controllers/SiteController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                class SiteController extends Controller {
                    public function actionGo() {
                        return ${'$'}this->redirect(['post/view']);
                    }
                }
            """.trimIndent(),
        )

        assertEquals("actionView" to "PostController", resolveAction(controller, "post/view"))
    }

    fun `test absolute route with subpath and dashed action resolves`() {
        myFixture.addFileToProject(
            "controllers/admin/UserController.php",
            """
                <?php
                namespace app\controllers\admin;
                use yii\base\Controller;
                class UserController extends Controller {
                    public function actionUpdateProfile() {}
                }
            """.trimIndent(),
        )
        val controller = myFixture.addFileToProject(
            "controllers/SiteController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                class SiteController extends Controller {
                    public function actionGo() {
                        return ${'$'}this->redirect(['/admin/user/update-profile']);
                    }
                }
            """.trimIndent(),
        )

        assertEquals(
            "actionUpdateProfile" to "UserController",
            resolveAction(controller, "/admin/user/update-profile"),
        )
    }

    fun `test Url to array resolves against enclosing controller`() {
        val controller = myFixture.addFileToProject(
            "controllers/SiteController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                use yii\helpers\Url;
                class SiteController extends Controller {
                    public function actionIndex() {}
                    public function actionGo() {
                        ${'$'}u = Url::to(['index']);
                    }
                }
            """.trimIndent(),
        )

        assertEquals("actionIndex" to "SiteController", resolveAction(controller, "index"))
    }

    fun `test Url toRoute bare string resolves`() {
        val controller = myFixture.addFileToProject(
            "controllers/SiteController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                use yii\helpers\Url;
                class SiteController extends Controller {
                    public function actionIndex() {}
                    public function actionGo() {
                        ${'$'}u = Url::toRoute('site/index');
                    }
                }
            """.trimIndent(),
        )

        assertEquals("actionIndex" to "SiteController", resolveAction(controller, "site/index"))
    }

    fun `test Url to relative route in a view resolves to its controller`() {
        myFixture.addFileToProject(
            "controllers/SiteController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                class SiteController extends Controller {
                    public function actionCreate() {}
                }
            """.trimIndent(),
        )
        val view = myFixture.addFileToProject(
            "views/site/links.php",
            "<?php use yii\\helpers\\Url; ?>\n<a href=\"<?= Url::to(['create']) ?>\">New</a>\n",
        )

        assertEquals("actionCreate" to "SiteController", resolveAction(view, "create"))
    }

    fun `test relative route in a nested-module view resolves to its controller`() {
        myFixture.addFileToProject(
            "controllers/admin/UserController.php",
            """
                <?php
                namespace app\controllers\admin;
                use yii\base\Controller;
                class UserController extends Controller {
                    public function actionCreate() {}
                }
            """.trimIndent(),
        )
        val view = myFixture.addFileToProject(
            "views/admin/user/index.php",
            "<?php use yii\\helpers\\Url; ?>\n<a href=\"<?= Url::to(['create']) ?>\">New</a>\n",
        )

        assertEquals("actionCreate" to "UserController", resolveAction(view, "create"))
    }

    fun `test Html a relative route in a view resolves to its controller`() {
        myFixture.addFileToProject(
            "controllers/SiteController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                class SiteController extends Controller {
                    public function actionCreate() {}
                }
            """.trimIndent(),
        )
        val view = myFixture.addFileToProject(
            "views/site/html.php",
            "<?php use yii\\helpers\\Html; echo Html::a('New', ['create']); ?>\n",
        )

        assertEquals("actionCreate" to "SiteController", resolveAction(view, "create"))
    }

    fun `test Url toRoute bare single segment in a view resolves to its controller`() {
        myFixture.addFileToProject(
            "controllers/SiteController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                class SiteController extends Controller {
                    public function actionCreate() {}
                }
            """.trimIndent(),
        )
        val view = myFixture.addFileToProject(
            "views/site/route.php",
            "<?php use yii\\helpers\\Url; echo Url::toRoute('create'); ?>\n",
        )

        assertEquals("actionCreate" to "SiteController", resolveAction(view, "create"))
    }

    fun `test cross-controller and absolute routes still resolve from a view`() {
        myFixture.addFileToProject(
            "controllers/PostController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                class PostController extends Controller {
                    public function actionView() {}
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "controllers/admin/UserController.php",
            """
                <?php
                namespace app\controllers\admin;
                use yii\base\Controller;
                class UserController extends Controller {
                    public function actionUpdateProfile() {}
                }
            """.trimIndent(),
        )
        val view = myFixture.addFileToProject(
            "views/site/cross.php",
            """
                <?php use yii\helpers\Url; ?>
                <a href="<?= Url::to(['post/view']) ?>">View</a>
                <a href="<?= Url::to(['/admin/user/update-profile']) ?>">Edit</a>
            """.trimIndent(),
        )

        assertEquals("actionView" to "PostController", resolveAction(view, "post/view"))
        assertEquals(
            "actionUpdateProfile" to "UserController",
            resolveAction(view, "/admin/user/update-profile"),
        )
    }

    fun `test Html beginForm array and string routes resolve in a view`() {
        myFixture.addFileToProject(
            "controllers/SiteController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                class SiteController extends Controller {
                    public function actionCreate() {}
                    public function actionIndex() {}
                }
            """.trimIndent(),
        )
        val view = myFixture.addFileToProject(
            "views/site/form.php",
            """
                <?php use yii\helpers\Html; ?>
                <?= Html::beginForm(['create']) ?>
                <?= Html::beginForm('site/index') ?>
            """.trimIndent(),
        )

        assertEquals("actionCreate" to "SiteController", resolveAction(view, "create"))
        assertEquals("actionIndex" to "SiteController", resolveAction(view, "site/index"))
    }

    fun `test Url remember array route resolves`() {
        val controller = myFixture.addFileToProject(
            "controllers/SiteController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                use yii\helpers\Url;
                class SiteController extends Controller {
                    public function actionCreate() {}
                    public function actionGo() {
                        Url::remember(['create']);
                    }
                }
            """.trimIndent(),
        )

        assertEquals("actionCreate" to "SiteController", resolveAction(controller, "create"))
    }

    fun `test urlManager createUrl and createAbsoluteUrl resolve`() {
        myFixture.addFileToProject(
            "controllers/PostController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                class PostController extends Controller {
                    public function actionView() {}
                }
            """.trimIndent(),
        )
        val controller = myFixture.addFileToProject(
            "controllers/SiteController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                use yii\web\UrlManager;
                class SiteController extends Controller {
                    public function actionCreate() {}
                    public function actionGo() {
                        ${'$'}um = new UrlManager();
                        ${'$'}um->createUrl(['create']);
                        ${'$'}um->createAbsoluteUrl(['post/view']);
                    }
                }
            """.trimIndent(),
        )

        assertEquals("actionCreate" to "SiteController", resolveAction(controller, "create"))
        assertEquals("actionView" to "PostController", resolveAction(controller, "post/view"))
    }

    fun `test createUrl on an unrelated class registers no action reference`() {
        val file = myFixture.addFileToProject(
            "controllers/SiteController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                class Router {
                    public function createUrl(${'$'}params) {}
                }
                class SiteController extends Controller {
                    public function actionGo() {
                        ${'$'}r = new Router();
                        ${'$'}r->createUrl(['create']);
                    }
                }
            """.trimIndent(),
        )

        assertFalse("Unrelated createUrl should not be a route", hasActionReference(file, "create"))
    }

    fun `test ActiveForm action config resolves array and string routes`() {
        myFixture.addFileToProject(
            "controllers/SiteController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                class SiteController extends Controller {
                    public function actionCreate() {}
                    public function actionIndex() {}
                }
            """.trimIndent(),
        )
        val view = myFixture.addFileToProject(
            "views/site/aform.php",
            """
                <?php use yii\widgets\ActiveForm; ?>
                <?php ActiveForm::begin(['action' => ['create'], 'method' => 'post']); ?>
                <?php ActiveForm::begin(['action' => 'site/index']); ?>
            """.trimIndent(),
        )

        assertEquals("actionCreate" to "SiteController", resolveAction(view, "create"))
        assertEquals("actionIndex" to "SiteController", resolveAction(view, "site/index"))
    }

    fun `test ActiveForm non-action config key registers no action reference`() {
        val view = myFixture.addFileToProject(
            "views/site/aform2.php",
            """
                <?php use yii\widgets\ActiveForm; ?>
                <?php ActiveForm::begin(['method' => 'post']); ?>
            """.trimIndent(),
        )

        assertFalse("A non-action config key should not be a route", hasActionReference(view, "post"))
    }

    fun `test missing action in a view is flagged with create quick fix`() {
        val controller = myFixture.addFileToProject(
            "controllers/SiteController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                class SiteController extends Controller {
                    public function actionIndex() {}
                }
            """.trimIndent(),
        )
        myFixture.enableInspections(MissingActionInspection())
        val view = myFixture.addFileToProject(
            "views/site/missing.php",
            "<?php use yii\\helpers\\Url; ?>\n<a href=\"<?= Url::to(['missing']) ?>\">X</a>\n",
        )
        myFixture.configureFromExistingVirtualFile(view.virtualFile)

        val highlights = myFixture.doHighlighting()
        assertTrue(
            "Expected a missing-action warning",
            highlights.any { it.description?.contains("does not exist") == true },
        )

        val fix = myFixture.getAllQuickFixes().firstOrNull { it.familyName == "Create Yii2 action method" }
        assertNotNull("Expected a create-action quick fix", fix)
        myFixture.launchAction(fix!!)

        val phpClass = PsiTreeUtil.collectElementsOfType(controller, PhpClass::class.java).first()
        assertNotNull("Quick fix should add the action method", phpClass.findMethodByName("actionMissing"))
    }

    fun `test missing action is flagged with create quick fix`() {
        myFixture.enableInspections(MissingActionInspection())
        val controller = myFixture.addFileToProject(
            "controllers/SiteController.php",
            """
                <?php
                namespace app\controllers;
                use yii\base\Controller;
                class SiteController extends Controller {
                    public function actionGo() {
                        return ${'$'}this->redirect(['missing']);
                    }
                }
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(controller.virtualFile)

        val highlights = myFixture.doHighlighting()
        assertTrue(
            "Expected a missing-action warning",
            highlights.any { it.description?.contains("does not exist") == true },
        )

        val fix = myFixture.getAllQuickFixes().firstOrNull { it.familyName == "Create Yii2 action method" }
        assertNotNull("Expected a create-action quick fix", fix)
        myFixture.launchAction(fix!!)

        val phpClass = PsiTreeUtil.collectElementsOfType(controller, PhpClass::class.java).first()
        assertNotNull("Quick fix should add the action method", phpClass.findMethodByName("actionMissing"))
    }

    /** Resolves the [route] literal's ActionReference and returns (methodName, controllerClassName). */
    private fun resolveAction(file: PsiFile, route: String): Pair<String?, String?>? {
        val literal = PsiTreeUtil.collectElementsOfType(file, StringLiteralExpression::class.java)
            .first { it.contents == route && it.references.any { ref -> ref is ActionReference } }
        val reference = literal.references.first { it is ActionReference }
        val resolved = reference.resolve() as? Method ?: return null
        return resolved.name to resolved.containingClass?.name
    }

    /** Whether any literal with [route] contents in [file] carries an ActionReference. */
    private fun hasActionReference(file: PsiFile, route: String): Boolean =
        PsiTreeUtil.collectElementsOfType(file, StringLiteralExpression::class.java)
            .filter { it.contents == route }
            .any { it.references.any { ref -> ref is ActionReference } }
}
