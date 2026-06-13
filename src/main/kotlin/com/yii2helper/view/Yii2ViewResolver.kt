package com.yii2helper.view

import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpTypedElement

/**
 * Computed location of a Yii2 view referenced from a render() call.
 *
 * @param virtualFile the resolved view file if it currently exists, otherwise null
 * @param targetPath  absolute path where the view file should live (for the "create" quick fix),
 *                    or null when the location cannot be determined statically
 */
data class ViewTarget(
    val virtualFile: VirtualFile?,
    val anchorDir: VirtualFile?,
    val relativePath: String?,
    val targetPath: String?,
) {
    val isResolvable: Boolean get() = targetPath != null
}

/**
 * Resolves a logical Yii2 view name (as written in render/renderPartial/renderAjax/renderFile)
 * to a view file location, following the rules of yii\base\View::findViewFile().
 *
 * Resolution is purely structural (directory layout + class hierarchy); runtime configuration
 * such as custom aliases, themes, or non-standard module layouts is not consulted.
 */
object Yii2ViewResolver {

    /** An existing [anchor] directory plus the view file path relative to it (no extension yet). */
    private data class Located(val anchor: VirtualFile, val relative: String)

    private val EMPTY = ViewTarget(null, null, null, null)

    fun resolve(call: MethodReference, viewName: String): ViewTarget {
        val located = locate(call, viewName) ?: return EMPTY
        val relative = withExtension(normalize(located.relative))
        val anchorPath = located.anchor.path.trimEnd('/')
        val existing = located.anchor.findFileByRelativePath(relative)
        return ViewTarget(existing, located.anchor, relative, "$anchorPath/$relative")
    }

    private fun locate(call: MethodReference, viewName: String): Located? {
        val callFile = call.containingFile?.virtualFile ?: return null
        val method = call.name ?: return null

        // renderFile takes a path/alias directly rather than a logical view name.
        if (method in Yii2Names.RENDER_FILE_METHODS) {
            return if (viewName.startsWith("@")) resolveAlias(callFile, viewName) else null
        }

        return when {
            viewName.startsWith("@") -> resolveAlias(callFile, viewName)
            // Relative to the application view path: @app/views.
            viewName.startsWith("//") ->
                appBaseOf(callFile)?.let { Located(it, "views/" + viewName.removePrefix("//")) }
            // Relative to the current module view path.
            viewName.startsWith("/") ->
                moduleBaseOf(call)?.let { Located(it, "views/" + viewName.removePrefix("/")) }
            else -> resolveContextRelative(call, callFile, viewName)
        }
    }

    // --- context-relative (plain name) -------------------------------------------------------

    private fun resolveContextRelative(call: MethodReference, callFile: VirtualFile, viewName: String): Located? {
        val phpClass = contextClass(call)
        val project = call.project

        if (phpClass != null && Yii2Names.isController(project, phpClass)) {
            return controllerViewDir(phpClass)?.let { (base, sub) ->
                Located(base, "$sub/$viewName")
            }
        }
        if (phpClass != null && Yii2Names.isWidget(project, phpClass)) {
            val classDir = phpClass.containingFile?.virtualFile?.parent ?: return null
            return Located(classDir, "views/$viewName")
        }
        // View object or a plain .php view file: relative to the current file's directory.
        val dir = callFile.parent ?: return null
        return Located(dir, viewName)
    }

    // --- alias -------------------------------------------------------------------------------

    private fun resolveAlias(callFile: VirtualFile, viewName: String): Located? {
        return when (viewName.substringBefore('/')) {
            "@app" -> appBaseOf(callFile)?.let { Located(it, viewName.removePrefix("@app/")) }
            else -> null // custom / other built-in aliases are out of scope for now
        }
    }

    // --- base directory discovery ------------------------------------------------------------

    /**
     * Controller view path expressed as <moduleBase> + "views/<subpath>/<controllerId>".
     * Returns the module base directory (which exists) and the relative views sub-path.
     */
    private fun controllerViewDir(phpClass: PhpClass): Pair<VirtualFile, String>? {
        val classFile = phpClass.containingFile?.virtualFile ?: return null
        val controllersDir = ancestorNamed(classFile, "controllers") ?: return null
        val moduleBase = controllersDir.parent ?: return null
        val subPath = relativeDirPath(controllersDir, classFile.parent)
        val controllerId = Yii2Names.controllerId(phpClass.name)
        val views = if (subPath.isEmpty()) "views/$controllerId" else "views/$subPath/$controllerId"
        return moduleBase to views
    }

    /**
     * Nearest ancestor directory that looks like an application/module base
     * (contains both controllers/ and views/).
     */
    private fun appBaseOf(file: VirtualFile): VirtualFile? {
        var dir: VirtualFile? = if (file.isDirectory) file else file.parent
        while (dir != null) {
            if (dir.findChild("controllers") != null && dir.findChild("views") != null) return dir
            dir = dir.parent
        }
        return null
    }

    /** Module base for the controller of a render call: parent of its controllers/ directory. */
    private fun moduleBaseOf(call: MethodReference): VirtualFile? {
        contextClass(call)?.containingFile?.virtualFile?.let { classFile ->
            ancestorNamed(classFile, "controllers")?.parent?.let { return it }
        }
        return call.containingFile?.virtualFile?.let { appBaseOf(it) }
    }

    // --- helpers -----------------------------------------------------------------------------

    /** Resolves the concrete class of the render call's object (typically $this). */
    private fun contextClass(call: MethodReference): PhpClass? {
        val classRef = call.classReference as? PhpTypedElement ?: return null
        val project = call.project
        val index = PhpIndex.getInstance(project)
        for (fqn in classRef.type.global(project).types) {
            if (!fqn.startsWith("\\")) continue
            index.getClassesByFQN(fqn).firstOrNull()?.let { return it }
        }
        return null
    }

    private fun ancestorNamed(file: VirtualFile, name: String): VirtualFile? {
        var dir = file.parent
        while (dir != null) {
            if (dir.name == name) return dir
            dir = dir.parent
        }
        return null
    }

    /** Directory path of [child] relative to [ancestor] (forward slashes, no leading/trailing). */
    private fun relativeDirPath(ancestor: VirtualFile, child: VirtualFile?): String {
        if (child == null) return ""
        val ancestorPath = ancestor.path.trimEnd('/')
        val childPath = child.path.trimEnd('/')
        if (childPath == ancestorPath || !childPath.startsWith("$ancestorPath/")) return ""
        return childPath.removePrefix("$ancestorPath/")
    }

    private fun normalize(path: String): String = path.trim('/').replace(Regex("/+"), "/")

    /** Appends the default extension when the last path segment has none. */
    private fun withExtension(path: String): String {
        val last = path.substringAfterLast('/')
        return if (last.contains('.')) path else "$path.${Yii2Names.DEFAULT_EXTENSION}"
    }
}
