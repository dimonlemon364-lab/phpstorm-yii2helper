package com.yii2helper.route

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
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.yii2helper.view.Yii2Names
import com.yii2helper.view.Yii2Psi

/**
 * Registers a reference on the route's action name in Yii2 route arrays so the controller action
 * method becomes navigable (Ctrl/Cmd+Click, Go To Declaration).
 */
class RouteReferenceContributor : PsiReferenceContributor() {

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
        val call = routeCallFor(literal) ?: return PsiReference.EMPTY_ARRAY
        return arrayOf(ActionReference(literal, call, literal.valueRange))
    }

    companion object {
        /**
         * Returns the enclosing call when [literal] is the route (first positional element of the
         * route array, or the bare string of `Url::toRoute`) of a supported route-producing call.
         */
        fun routeCallFor(literal: StringLiteralExpression): MethodReference? {
            positionalRouteCall(literal)?.let { return it }
            return activeFormActionCall(literal)
        }

        /** A route passed as a positional argument (the array literal or a bare string). */
        private fun positionalRouteCall(literal: StringLiteralExpression): MethodReference? {
            val (routeArg, isArray) = routeArgument(literal) ?: return null
            val params = routeArg.parent as? ParameterList ?: return null
            val call = params.parent as? MethodReference ?: return null
            val index = params.getParameters().indexOf(routeArg)

            val matches = when (call.name) {
                "redirect" -> isArray && index == 0
                "to" -> isArray && index == 0 && isHelper(call, Yii2Names.URL_HELPER_FQN)
                "toRoute" -> index == 0 && isHelper(call, Yii2Names.URL_HELPER_FQN)
                "remember" -> isArray && index == 0 && isHelper(call, Yii2Names.URL_HELPER_FQN)
                "a" -> isArray && index == 1 && isHelper(call, Yii2Names.HTML_HELPER_FQN)
                "beginForm" -> index == 0 && isHelper(call, Yii2Names.HTML_HELPER_FQN)
                "createUrl", "createAbsoluteUrl" ->
                    index == 0 && Yii2Psi.isInstanceCall(call, Yii2Names.URL_MANAGER_FQN)
                else -> false
            }
            return if (matches) call else null
        }

        /**
         * The route carried by the `'action'` config key of `ActiveForm::begin(['action' => …])`,
         * where the value is either a bare route string or a route array whose first element is
         * [literal].
         */
        private fun activeFormActionCall(literal: StringLiteralExpression): MethodReference? {
            val hash = PsiTreeUtil.getParentOfType(literal, ArrayHashElement::class.java) ?: return null
            if ((hash.key as? StringLiteralExpression)?.contents != "action") return null
            if (!hashCarriesRoute(hash, literal)) return null

            val configArray = hash.parent as? ArrayCreationExpression ?: return null
            val params = configArray.parent as? ParameterList ?: return null
            val call = params.parent as? MethodReference ?: return null
            val matches = call.name == "begin" &&
                params.getParameters().indexOf(configArray) == 0 &&
                Yii2Psi.isInstanceCall(call, Yii2Names.ACTIVE_FORM_FQN)
            return if (matches) call else null
        }

        /**
         * True when [literal] is the route of [hash]'s value: the value itself (string form) or the
         * first positional element of the value array (array form). Guards against the key and
         * non-route nested elements.
         */
        private fun hashCarriesRoute(hash: ArrayHashElement, literal: StringLiteralExpression): Boolean {
            val value = hash.value ?: return false
            if (value is ArrayCreationExpression) {
                val first = value.children.firstOrNull() ?: return false
                return first !is ArrayHashElement && PsiTreeUtil.isAncestor(first, literal, false)
            }
            return PsiTreeUtil.isAncestor(value, literal, false)
        }

        /**
         * Identifies the route-bearing argument: either the array literal is in (when [literal] is
         * its first positional element) paired with `true`, or [literal] itself paired with `false`
         * for the bare-string form. Returns null when [literal] is not a positional route argument.
         */
        private fun routeArgument(literal: StringLiteralExpression): Pair<PsiElement, Boolean>? {
            val parent = literal.parent
            // A bare string argument: its parent is the call's ParameterList.
            if (parent is ParameterList) return literal to false
            // An array element value is wrapped in a PhpPsiElement whose parent is the array.
            val array = parent?.parent as? ArrayCreationExpression ?: return null
            val first = array.children.firstOrNull()
            if (first is ArrayHashElement || first !== parent) return null
            return array to true
        }

        private fun isHelper(call: MethodReference, fqn: String): Boolean {
            val classRef = call.classReference as? ClassReference ?: return false
            return classRef.fqn?.equals(fqn, ignoreCase = true) == true
        }
    }
}
