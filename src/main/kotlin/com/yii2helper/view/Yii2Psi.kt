package com.yii2helper.view

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpTypedElement

/**
 * Structural PSI/VFS helpers shared by the view and route resolvers. Resolution is purely based
 * on directory layout and the class hierarchy (no runtime configuration).
 */
object Yii2Psi {

    /** Concrete class of a call's object expression (typically `$this`), via its PHP type. */
    fun contextClass(call: MethodReference): PhpClass? {
        val classRef = call.classReference as? PhpTypedElement ?: return null
        val project = call.project
        val index = PhpIndex.getInstance(project)
        for (fqn in classRef.type.global(project).types) {
            if (!fqn.startsWith("\\")) continue
            index.getClassesByFQN(fqn).firstOrNull()?.let { return it }
        }
        return null
    }

    /** Nearest PHP class that lexically encloses [element], if any. */
    fun enclosingClass(element: PsiElement): PhpClass? =
        PsiTreeUtil.getParentOfType(element, PhpClass::class.java)

    /**
     * True when the call's object/class reference resolves to a class that is (or extends) [fqn].
     * Works for both instance receivers (`$x->m()`) and static class refs (`Foo::m()`).
     */
    fun isInstanceCall(call: MethodReference, fqn: String): Boolean =
        contextClass(call)?.let { Yii2Names.isInstanceOf(call.project, it, fqn) } ?: false

    /**
     * Nearest ancestor directory that looks like an application/module base
     * (contains both `controllers/` and `views/`).
     */
    fun appBaseOf(file: VirtualFile): VirtualFile? {
        var dir: VirtualFile? = if (file.isDirectory) file else file.parent
        while (dir != null) {
            if (dir.findChild("controllers") != null && dir.findChild("views") != null) return dir
            dir = dir.parent
        }
        return null
    }

    /** Module base for a call's controller: the parent of its `controllers/` directory. */
    fun moduleBaseOf(call: MethodReference): VirtualFile? {
        contextClass(call)?.containingFile?.virtualFile?.let { classFile ->
            ancestorNamed(classFile, "controllers")?.parent?.let { return it }
        }
        return call.containingFile?.virtualFile?.let { appBaseOf(it) }
    }

    /** Nearest ancestor directory of [file] whose name equals [name]. */
    fun ancestorNamed(file: VirtualFile, name: String): VirtualFile? {
        var dir = file.parent
        while (dir != null) {
            if (dir.name == name) return dir
            dir = dir.parent
        }
        return null
    }

    /** Directory path of [child] relative to [ancestor] (forward slashes, no leading/trailing). */
    fun relativeDirPath(ancestor: VirtualFile, child: VirtualFile?): String {
        if (child == null) return ""
        val ancestorPath = ancestor.path.trimEnd('/')
        val childPath = child.path.trimEnd('/')
        if (childPath == ancestorPath || !childPath.startsWith("$ancestorPath/")) return ""
        return childPath.removePrefix("$ancestorPath/")
    }
}
