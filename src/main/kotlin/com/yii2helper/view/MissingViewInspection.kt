package com.yii2helper.view

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Warns when a Yii2 render-family call references a view file that does not exist, offering a
 * quick fix to create it. Only fires when the view location can be determined statically.
 */
class MissingViewInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is StringLiteralExpression) return
                val call = ViewReferenceContributor.renderCallFor(element) ?: return
                val viewName = element.contents
                if (viewName.isEmpty()) return

                val target = Yii2ViewResolver.resolve(call, viewName)
                if (!target.isResolvable || target.virtualFile != null) return

                holder.registerProblem(
                    element,
                    element.valueRange,
                    "Yii2 view file '$viewName' does not exist",
                    CreateViewFileQuickFix(target.targetPath!!),
                )
            }
        }
}
