package com.yii2helper.view

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
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
         * Returns the enclosing render-family call when [literal] is its first (view-name) argument.
         */
        fun renderCallFor(literal: StringLiteralExpression): MethodReference? {
            val params = literal.parent as? ParameterList ?: return null
            if (params.getParameters().firstOrNull() !== literal) return null
            val call = params.parent as? MethodReference ?: return null
            if (call.name !in Yii2Names.RENDER_METHODS) return null
            return call
        }
    }
}
