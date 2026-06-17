package com.yii2helper.route

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.yii2helper.view.Yii2Names
import com.yii2helper.view.Yii2Psi

/**
 * Resolved controller action for a Yii2 route.
 *
 * @param method           the existing action method, or null when it does not exist yet
 * @param controllerClass  the resolved controller class (target for the create fix), or null
 * @param actionMethodName the action method name, e.g. "actionIndex"
 * @param actionId         the action id, e.g. "index" (used for the render() stub)
 */
data class ActionTarget(
    val method: Method?,
    val controllerClass: PhpClass?,
    val actionMethodName: String?,
    val actionId: String?,
) {
    val isResolvable: Boolean get() = controllerClass != null && actionMethodName != null
}

/**
 * Resolves a Yii2 route string (as written in redirect/Url::to/Url::toRoute/Html::a) to a
 * controller action method, following Yii's route parsing rules.
 *
 * Resolution is purely structural (directory layout + class hierarchy); modules with custom
 * controller namespaces, standalone action classes, and UrlManager rules are not consulted.
 */
object Yii2RouteResolver {

    private val EMPTY = ActionTarget(null, null, null, null)

    fun resolve(call: MethodReference, route: String): ActionTarget {
        val absolute = route.startsWith("/")
        val segments = route.removePrefix("/").split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return EMPTY

        val actionId = segments.last()
        val controllerSegments = segments.dropLast(1)

        val controllerClass = if (controllerSegments.isEmpty()) {
            // Single-segment route: action of the current controller.
            if (absolute) null else currentController(call)
        } else {
            resolveControllerByPath(call, controllerSegments, absolute)
        } ?: return EMPTY

        val methodName = Yii2Names.actionMethodName(actionId)
        val method = controllerClass.findMethodByName(methodName)
        return ActionTarget(method, controllerClass, methodName, actionId)
    }

    /**
     * The controller class a single-segment relative route belongs to: `$this` for redirect,
     * the enclosing controller in controller code, or — in a view file, where neither exists —
     * the controller that renders the view, recovered from the view's directory path.
     */
    private fun currentController(call: MethodReference): PhpClass? {
        val project = call.project
        Yii2Psi.contextClass(call)?.let { if (Yii2Names.isController(project, it)) return it }
        Yii2Psi.enclosingClass(call)?.let { if (Yii2Names.isController(project, it)) return it }
        return controllerOfViewFile(call)
    }

    /**
     * Reverse-maps a view file to its controller using Yii's layout convention: a view under
     * `<base>/views/<dirs>/<controllerId>/` is rendered by
     * `<base>/controllers/<dirs>/<Camelize(controllerId)>Controller.php`.
     */
    private fun controllerOfViewFile(call: MethodReference): PhpClass? {
        val viewFile = call.containingFile?.virtualFile ?: return null
        val base = Yii2Psi.appBaseOf(viewFile) ?: return null
        val viewsDir = base.findChild("views") ?: return null
        val rel = Yii2Psi.relativeDirPath(viewsDir, viewFile.parent)
        val segments = rel.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null

        val className = Yii2Names.id2camel(segments.last()) + "Controller"
        val dirs = segments.dropLast(1)
        val relative = (listOf("controllers") + dirs + "$className.php").joinToString("/")
        val file = base.findFileByRelativePath(relative) ?: return null
        return phpClassNamed(call, file, className)?.takeIf { Yii2Names.isController(call.project, it) }
    }

    /**
     * Resolves a controller path (route segments before the action id) to a controller class via
     * `<base>/controllers/<dirs…>/<Camelize(last)>Controller.php`.
     */
    private fun resolveControllerByPath(
        call: MethodReference,
        controllerSegments: List<String>,
        absolute: Boolean,
    ): PhpClass? {
        val callFile = call.containingFile?.virtualFile ?: return null
        val base = (if (absolute) Yii2Psi.appBaseOf(callFile) else Yii2Psi.moduleBaseOf(call))
            ?: return null

        val className = Yii2Names.id2camel(controllerSegments.last()) + "Controller"
        val dirs = controllerSegments.dropLast(1)
        val relative = (listOf("controllers") + dirs + "$className.php").joinToString("/")
        val file = base.findFileByRelativePath(relative) ?: return null
        return phpClassNamed(call, file, className)
    }

    private fun phpClassNamed(call: MethodReference, file: VirtualFile, className: String): PhpClass? {
        val psiFile = PsiManager.getInstance(call.project).findFile(file) as? PhpFile ?: return null
        return PsiTreeUtil.findChildrenOfType(psiFile, PhpClass::class.java)
            .firstOrNull { it.name == className }
    }
}
