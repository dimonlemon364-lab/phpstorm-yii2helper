package com.yii2helper.view

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Creates a missing Yii2 view file (with a minimal view template) and opens it.
 *
 * The target location is recomputed from the highlighted string literal at fix time and the file
 * is created through the VFS, relative to an existing anchor directory, so it works regardless of
 * the underlying file system.
 */
class CreateViewFileQuickFix(private val displayPath: String) : LocalQuickFix {

    override fun getFamilyName(): String = "Create Yii2 view file"

    override fun getName(): String = "Create view file '${displayPath.substringAfterLast('/')}'"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val literal = descriptor.psiElement as? StringLiteralExpression ?: return
        val call = ViewReferenceContributor.renderCallFor(literal) ?: return
        val target = Yii2ViewResolver.resolve(call, literal.contents)
        val anchor = target.anchorDir ?: return
        val relative = target.relativePath ?: return

        val segments = relative.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return

        var dir: VirtualFile = anchor
        for (segment in segments.dropLast(1)) {
            dir = dir.findChild(segment) ?: dir.createChildDirectory(this, segment)
        }
        val fileName = segments.last()
        if (dir.findChild(fileName) != null) return
        val file = dir.createChildData(this, fileName)
        VfsUtil.saveText(file, VIEW_TEMPLATE)
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    private companion object {
        val VIEW_TEMPLATE = """
            <?php

            use yii\web\View;

            /** @var View ${"$"}this */
        """.trimIndent() + "\n"
    }
}
