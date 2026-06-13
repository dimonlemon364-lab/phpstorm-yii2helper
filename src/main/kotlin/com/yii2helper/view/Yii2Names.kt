package com.yii2helper.view

import com.intellij.openapi.project.Project
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass

/**
 * Yii2 framework constants and small helpers shared by the view-navigation feature.
 */
object Yii2Names {

    /** Render methods whose first string argument is a logical view name / file. */
    val RENDER_METHODS: Set<String> = setOf("render", "renderPartial", "renderAjax", "renderFile")

    /** Methods whose argument is already a file path/alias rather than a logical view name. */
    val RENDER_FILE_METHODS: Set<String> = setOf("renderFile")

    const val CONTROLLER_FQN = "\\yii\\base\\Controller"
    const val WIDGET_FQN = "\\yii\\base\\Widget"
    const val VIEW_FQN = "\\yii\\base\\View"

    /** Default view file extension (yii\base\View::$defaultExtension). */
    const val DEFAULT_EXTENSION = "php"

    /**
     * Converts a CamelCase string to an id (Yii Inflector::camel2id with '-' separator).
     * e.g. "UserProfile" -> "user-profile", "SiteController" handled by caller stripping suffix.
     */
    fun camel2id(name: String, separator: String = "-"): String {
        if (name.isEmpty()) return name
        val sb = StringBuilder()
        for ((i, ch) in name.withIndex()) {
            if (ch.isUpperCase() && i > 0) {
                sb.append(separator)
            }
            sb.append(ch.lowercaseChar())
        }
        return sb.toString()
    }

    /** Controller id derived from class name: strip "Controller" suffix then camel2id. */
    fun controllerId(className: String): String {
        val base = className.removeSuffix("Controller")
        return camel2id(base)
    }

    fun isController(project: Project, phpClass: PhpClass): Boolean =
        isInstanceOf(project, phpClass, CONTROLLER_FQN)

    fun isWidget(project: Project, phpClass: PhpClass): Boolean =
        isInstanceOf(project, phpClass, WIDGET_FQN)

    fun isView(project: Project, phpClass: PhpClass): Boolean =
        isInstanceOf(project, phpClass, VIEW_FQN)

    /**
     * Walks the superclass chain of [phpClass] looking for [superFqn].
     * Comparison is done on normalized FQNs (leading backslash, case-insensitive).
     */
    fun isInstanceOf(project: Project, phpClass: PhpClass, superFqn: String): Boolean {
        val target = normalizeFqn(superFqn)
        val seen = HashSet<String>()
        val index = PhpIndex.getInstance(project)
        val queue = ArrayDeque<PhpClass>()
        queue.add(phpClass)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val fqn = normalizeFqn(current.fqn)
            if (!seen.add(fqn)) continue
            if (fqn == target) return true
            current.superFQN?.let { superFqn2 ->
                index.getClassesByFQN(superFqn2).forEach { queue.add(it) }
            }
        }
        return false
    }

    private fun normalizeFqn(fqn: String): String {
        val withSlash = if (fqn.startsWith("\\")) fqn else "\\$fqn"
        return withSlash.lowercase()
    }
}
