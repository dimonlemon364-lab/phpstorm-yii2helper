package com.yii2helper.route

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Reference from a route string literal (in redirect/Url::to/Url::toRoute/Html::a) to the target
 * controller action method. Marked soft so a missing action is not reported here;
 * [MissingActionInspection] handles that and offers the create quick fix.
 */
class ActionReference(
    literal: StringLiteralExpression,
    private val call: MethodReference,
    range: TextRange,
) : PsiReferenceBase<StringLiteralExpression>(literal, range, /* soft = */ true) {

    override fun resolve(): PsiElement? {
        val route = element.contents
        if (route.isEmpty()) return null
        return Yii2RouteResolver.resolve(call, route).method
    }
}
