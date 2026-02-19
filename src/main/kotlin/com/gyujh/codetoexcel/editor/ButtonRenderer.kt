package com.gyujh.codetoexcel.editor

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Component
import java.awt.Graphics
import java.awt.Rectangle

class ButtonRenderer(
    private val component: Component
) : EditorCustomElementRenderer {

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        return component.preferredSize.width
    }

    override fun paint(
        inlay: Inlay<*>,
        g: Graphics,
        targetRegion: Rectangle,
        textAttributes: TextAttributes
    ) {
        component.setBounds(targetRegion)
        component.paint(g)
    }
}
