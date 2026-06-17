package com.yii2helper.route

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.jetbrains.php.lang.psi.PhpPsiElementFactory
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Creates a missing Yii2 controller action method that renders the matching view, then navigates
 * to it. The target is recomputed from the highlighted route literal at fix time.
 */
class CreateActionQuickFix(
    private val actionMethodName: String,
    private val actionId: String,
) : LocalQuickFix {

    override fun getFamilyName(): String = "Create Yii2 action method"

    override fun getName(): String = "Create action method '$actionMethodName()'"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val literal = descriptor.psiElement as? StringLiteralExpression ?: return
        val call = RouteReferenceContributor.routeCallFor(literal) ?: return
        val target = Yii2RouteResolver.resolve(call, literal.contents)
        val controller = target.controllerClass ?: return
        val name = target.actionMethodName ?: return
        val id = target.actionId ?: return
        if (controller.findMethodByName(name) != null) return

        val method = buildMethod(project, name, id) ?: return
        val added = insertIntoClass(controller, method) as? Method ?: return
        CodeStyleManager.getInstance(project).reformat(added)
        added.navigate(true)
    }

    private fun buildMethod(project: Project, name: String, id: String): Method? {
        val body = "public function $name()\n{\n    return ${"$"}this->render('$id');\n}"
        val tmpClass = PhpPsiElementFactory.createPhpPsiFromText(
            project,
            PhpClass::class.java,
            "class __Yii2HelperTmp__ {\n$body\n}",
        )
        return tmpClass.ownMethods.firstOrNull()
    }

    /** Inserts [member] as the last member of [phpClass] (just before its closing brace). */
    private fun insertIntoClass(phpClass: PhpClass, member: Method) =
        phpClass.addBefore(member, phpClass.lastChild)
}
