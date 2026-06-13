package com.yii2helper.view

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Reference from a view-name string literal in a render() call to the target view file.
 * Marked soft so an unresolved view is not reported as an error here; [MissingViewInspection]
 * handles the missing-file case and offers the create quick fix.
 */
class ViewFileReference(
    literal: StringLiteralExpression,
    private val call: MethodReference,
    range: TextRange,
) : PsiReferenceBase<StringLiteralExpression>(literal, range, /* soft = */ true) {

    override fun resolve(): PsiElement? {
        val viewName = element.contents
        if (viewName.isEmpty()) return null
        val target = Yii2ViewResolver.resolve(call, viewName)
        val file = target.virtualFile ?: return null
        return PsiManager.getInstance(element.project).findFile(file)
    }
}
