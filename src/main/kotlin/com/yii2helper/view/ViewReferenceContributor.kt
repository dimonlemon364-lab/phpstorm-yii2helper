package com.yii2helper.view

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Registers a reference on the view-name argument of Yii2 render-family calls so the view file
 * becomes navigable (Ctrl/Cmd+Click, Go To Declaration).
 */
class ViewReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(StringLiteralExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext,
                ): Array<PsiReference> = referencesFor(element)
            },
        )
    }

    private fun referencesFor(element: PsiElement): Array<PsiReference> {
        val literal = element as? StringLiteralExpression ?: return PsiReference.EMPTY_ARRAY
        val call = renderCallFor(literal) ?: return PsiReference.EMPTY_ARRAY
        return arrayOf(ViewFileReference(literal, call, literal.valueRange))
    }

    companion object {
        /**
         * Returns the enclosing call when [literal] names a view: a render-family call's first
         * argument, or a mailer `compose()` view argument (bare string or `html`/`text` config).
         */
        fun renderCallFor(literal: StringLiteralExpression): MethodReference? {
            renderFamilyCall(literal)?.let { return it }
            return composeCall(literal)
        }

        /** A render-family call (render/renderPartial/renderAjax/renderFile) with [literal] as arg0. */
        private fun renderFamilyCall(literal: StringLiteralExpression): MethodReference? {
            val params = literal.parent as? ParameterList ?: return null
            if (params.getParameters().firstOrNull() !== literal) return null
            val call = params.parent as? MethodReference ?: return null
            return if (call.name in Yii2Names.RENDER_METHODS) call else null
        }

        /** A `mailer->compose()` call whose view argument is [literal]. */
        private fun composeCall(literal: StringLiteralExpression): MethodReference? {
            val call = composeViewArgCall(literal) ?: return null
            val matches = call.name == Yii2Names.COMPOSE_METHOD &&
                Yii2Psi.isInstanceCall(call, Yii2Names.MAILER_FQN)
            return if (matches) call else null
        }

        /**
         * The call carrying [literal] as a compose view name: either the bare first argument, or a
         * `html`/`text` value inside the first-argument config array.
         */
        private fun composeViewArgCall(literal: StringLiteralExpression): MethodReference? {
            (literal.parent as? ParameterList)?.let { params ->
                if (params.getParameters().firstOrNull() === literal) return params.parent as? MethodReference
            }
            val hash = PsiTreeUtil.getParentOfType(literal, ArrayHashElement::class.java) ?: return null
            if ((hash.key as? StringLiteralExpression)?.contents !in Yii2Names.MAILER_VIEW_KEYS) return null
            if (!PsiTreeUtil.isAncestor(hash.value ?: return null, literal, false)) return null
            val configArray = hash.parent as? ArrayCreationExpression ?: return null
            val params = configArray.parent as? ParameterList ?: return null
            if (params.getParameters().firstOrNull() !== configArray) return null
            return params.parent as? MethodReference
        }
    }
}
