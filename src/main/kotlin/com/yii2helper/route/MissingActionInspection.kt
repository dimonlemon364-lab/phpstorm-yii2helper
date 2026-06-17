package com.yii2helper.route

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Warns when a Yii2 route references a controller action method that does not exist, offering a
 * quick fix to create it. Only fires when the controller class is resolved but lacks the action.
 */
class MissingActionInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is StringLiteralExpression) return
                val call = RouteReferenceContributor.routeCallFor(element) ?: return
                val route = element.contents
                if (route.isEmpty()) return

                val target = Yii2RouteResolver.resolve(call, route)
                if (!target.isResolvable || target.method != null) return

                holder.registerProblem(
                    element,
                    element.valueRange,
                    "Yii2 action '${target.actionMethodName}' does not exist in ${target.controllerClass?.name}",
                    CreateActionQuickFix(target.actionMethodName!!, target.actionId!!),
                )
            }
        }
}
