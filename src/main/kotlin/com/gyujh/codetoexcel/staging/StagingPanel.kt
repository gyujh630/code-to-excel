package com.gyujh.codetoexcel.staging

import com.gyujh.codetoexcel.settings.ExcelSettingsState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.icons.AllIcons
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities

class StagingPanel(
    private val project: Project,
    private val service: StagingService = StagingService(),
    private val storage: StagingStorage = StagingStorage()
) : JPanel(BorderLayout()), StagingListener {

    private val contentPanel = JBPanel<JBPanel<*>>()
    private val emptyLabel = JBLabel("엑셀 파일을 선택하면 스테이징 목록이 표시됩니다.")
    private var selectedRowKey: String? = null
    private val expandedState = mutableMapOf<String, Boolean>()

    init {
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.border = JBUI.Borders.empty(8, 12)

        val toolbar = JPanel(BorderLayout())
        val leftBar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4))
        val rightBar = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 4))

        val refreshButton = JButton(AllIcons.Actions.Refresh)
        refreshButton.toolTipText = "동기화"
        refreshButton.preferredSize = Dimension(28, 28)
        refreshButton.minimumSize = Dimension(28, 28)
        refreshButton.maximumSize = Dimension(28, 28)
        refreshButton.margin = JBUI.insets(0)
        refreshButton.addActionListener { refresh() }
        leftBar.add(refreshButton)

        val applyButton = JButton("Apply to Excel")
        applyButton.addActionListener { applyToExcel() }
        rightBar.add(applyButton)

        toolbar.add(leftBar, BorderLayout.WEST)
        toolbar.add(rightBar, BorderLayout.EAST)

        val scroll = ScrollPaneFactory.createScrollPane(contentPanel, true)
        add(toolbar, BorderLayout.NORTH)
        add(scroll, BorderLayout.CENTER)

        val connection = project.messageBus.connect()
        connection.subscribe(STAGING_TOPIC, this)

        refresh()
    }

    override fun onStagingUpdated(excelPath: String) {
        val currentPath = ExcelSettingsState.getInstance().excelPath.trim()
        if (currentPath == excelPath) {
            SwingUtilities.invokeLater { refresh() }
        }
    }

    fun refresh() {
        contentPanel.removeAll()
        val excelPath = ExcelSettingsState.getInstance().excelPath.trim()
        if (excelPath.isEmpty()) {
            emptyLabel.foreground = UIUtil.getContextHelpForeground()
            contentPanel.add(emptyLabel)
            revalidate()
            repaint()
            return
        }

        val index = storage.loadExcelIndex(excelPath)
        val rows = index.rows
            .filterValues { it.items.isNotEmpty() }
            .toSortedMap(compareBy { it.toIntOrNull() ?: 0 })
        if (rows.isEmpty()) {
            val label = JBLabel("저장된 이미지가 없습니다.")
            label.foreground = UIUtil.getContextHelpForeground()
            contentPanel.add(label)
        } else {
            rows.forEach { (rowKey, rowEntry) ->
                val section = RowSectionPanel(
                    project = project,
                    excelPath = excelPath,
                    rowKey = rowKey,
                    rowEntry = rowEntry,
                    service = service,
                    storage = storage,
                    onSelected = { selectRow(rowKey) },
                    isSelected = rowKey == selectedRowKey,
                    isExpanded = expandedState[rowKey] ?: true,
                    onToggle = { expanded -> expandedState[rowKey] = expanded }
                )
                contentPanel.add(section)
                contentPanel.add(Box.createVerticalStrut(8))
            }
        }

        revalidate()
        repaint()
    }

    private fun applyToExcel() {
        ApplicationManager.getApplication().executeOnPooledThread {
            service.applyAll(project)
        }
    }

    private fun selectRow(rowKey: String) {
        selectedRowKey = rowKey
        refresh()
    }
}

private class RowSectionPanel(
    private val project: Project,
    private val excelPath: String,
    private val rowKey: String,
    private val rowEntry: RowEntry,
    private val service: StagingService,
    private val storage: StagingStorage,
    private val onSelected: () -> Unit,
    private val isSelected: Boolean,
    private val isExpanded: Boolean,
    private val onToggle: (Boolean) -> Unit
) : JPanel(BorderLayout()) {

    private val bodyPanel = JBPanel<JBPanel<*>>()
    private var expanded = isExpanded
    private val header = JPanel(BorderLayout())

    init {
        border = JBUI.Borders.customLine(UIUtil.getPanelBackground().darker(), 1, 1, 1, 1)
        background = if (isSelected) UIUtil.getListSelectionBackground(false) else UIUtil.getPanelBackground()
        isOpaque = true

        header.border = JBUI.Borders.empty(4, 8)
        header.background = background
        header.isOpaque = true
        val toggleIcon = JBLabel(if (expanded) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight)
        val titleLabel = JBLabel("TC$rowKey")
        val countLabel = JBLabel("(${rowEntry.items.size})")
        countLabel.foreground = UIUtil.getContextHelpForeground()
        val togglePanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        togglePanel.isOpaque = false
        togglePanel.add(toggleIcon)
        togglePanel.add(titleLabel)
        togglePanel.add(countLabel)
        header.add(togglePanel, BorderLayout.WEST)

        val toggleHandler = {
            expanded = !expanded
            if (expanded) {
                bodyPanel.isVisible = true
                if (bodyPanel.parent == null) {
                    add(bodyPanel, BorderLayout.CENTER)
                }
                toggleIcon.icon = AllIcons.General.ArrowDown
            } else {
                bodyPanel.isVisible = false
                remove(bodyPanel)
                toggleIcon.icon = AllIcons.General.ArrowRight
            }
            onToggle(expanded)
            updateSizing()
            revalidate()
            repaint()
        }

        bodyPanel.layout = BorderLayout()
        bodyPanel.border = JBUI.Borders.empty(6, 8, 8, 8)
        bodyPanel.isVisible = expanded
        bodyPanel.add(buildListPanel(), BorderLayout.CENTER)

        val toggleListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1 && e.button == MouseEvent.BUTTON1) {
                    toggleHandler()
                }
            }
        }
        toggleIcon.addMouseListener(toggleListener)

        val selectListener = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                onSelected()
            }
        }
        addMouseListener(selectListener)
        bodyPanel.addMouseListener(selectListener)

        add(header, BorderLayout.NORTH)
        if (expanded) {
            add(bodyPanel, BorderLayout.CENTER)
        }

        updateSizing()

        val rowNumber = rowKey.toIntOrNull()
        if (rowNumber != null) {
            val inputMap = this.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            val actionMap = this.actionMap
            inputMap.put(KeyStroke.getKeyStroke("ctrl V"), "pasteImage")
            inputMap.put(KeyStroke.getKeyStroke("meta V"), "pasteImage")
            actionMap.put("pasteImage", object : javax.swing.AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    if (!isSelected) return
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    val image = clipboard.getData(DataFlavor.imageFlavor) as? java.awt.image.BufferedImage ?: return
                    service.saveImageToRow(
                        project = project,
                        excelPath = excelPath,
                        rowNumber = rowNumber,
                        image = image,
                        title = "Pasted Image",
                        lineCount = 1
                    )
                }
            })
        }
    }

    private fun buildListPanel(): JPanel {
        val model = DefaultListModel<StagedItem>()
        rowEntry.items.sortedBy { it.order }.forEach { model.addElement(it) }

        val list = JBList(model)
        list.visibleRowCount = model.size().coerceAtLeast(1)
        list.fixedCellHeight = 76
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        val rowNumber = rowKey.toIntOrNull() ?: 0
        val rowFolder = storage.ensureRowFolder(excelPath, rowNumber)
        list.cellRenderer = StagedItemRenderer(rowFolder)
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                onSelected()
                val index = list.locationToIndex(e.point)
                if (index < 0) return
                val bounds = list.getCellBounds(index, index) ?: return
                val removeAreaMinX = bounds.x + bounds.width - 24
                if (e.x >= removeAreaMinX) {
                    val item = model.getElementAt(index)
                    service.deleteItem(project, excelPath, rowKey, item.id)
                    return
                }
                val iconAreaMaxX = bounds.x + 100
                if (e.x <= iconAreaMaxX) {
                    val item = model.getElementAt(index)
                    showPreview(list, rowFolder.resolve(item.file))
                }
            }
        })

        StagingListTransferHandler.install(list) { newOrder ->
            service.reorderRow(project, excelPath, rowKey, newOrder.map { it.id })
        }

        val decorator = ToolbarDecorator.createDecorator(list)
            .disableAddAction()
            .disableRemoveAction()
            .disableUpDownActions()

        val panel = decorator.createPanel()
        panel.border = BorderFactory.createEmptyBorder()
        val height = list.fixedCellHeight * list.visibleRowCount + 8
        panel.preferredSize = Dimension(100, height)
        return panel
    }

    private fun updateSizing() {
        val headerHeight = header.preferredSize.height.coerceAtLeast(28)
        if (expanded) {
            val bodyHeight = bodyPanel.preferredSize.height
            val totalHeight = headerHeight + bodyHeight
            preferredSize = Dimension(preferredSize.width, totalHeight)
            maximumSize = Dimension(Int.MAX_VALUE, totalHeight)
        } else {
            maximumSize = Dimension(Int.MAX_VALUE, headerHeight)
            preferredSize = Dimension(preferredSize.width, headerHeight)
        }
        alignmentX = LEFT_ALIGNMENT
    }

    private fun showPreview(parent: JComponent, path: Path) {
        if (!Files.exists(path)) return
        val image = ImageIO.read(path.toFile())
        val iconLabel = JBLabel(javax.swing.ImageIcon(image))

        val dialog = JDialog(SwingUtilities.getWindowAncestor(parent))
        dialog.title = "Preview"
        dialog.layout = BorderLayout()
        val scroll = JBScrollPane(iconLabel)
        scroll.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        scroll.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        dialog.add(scroll, BorderLayout.CENTER)

        val closeButton = JButton("Close")
        closeButton.addActionListener { dialog.dispose() }
        val footer = JPanel(FlowLayout(FlowLayout.RIGHT))
        footer.add(closeButton)
        dialog.add(footer, BorderLayout.SOUTH)

        dialog.setSize(800, 600)
        dialog.setLocationRelativeTo(parent)
        dialog.isModal = true
        dialog.isVisible = true
    }
}

private class StagedItemRenderer(
    private val rowFolder: Path
) : ListCellRenderer<StagedItem> {

    private val cache = mutableMapOf<String, javax.swing.ImageIcon>()

    override fun getListCellRendererComponent(
        list: javax.swing.JList<out StagedItem>,
        value: StagedItem,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): java.awt.Component {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        panel.border = JBUI.Borders.empty(4, 4)

        val removeLabel = JBLabel(AllIcons.Actions.Close)
        removeLabel.foreground = UIUtil.getContextHelpForeground()
        removeLabel.border = JBUI.Borders.empty(0, 6, 0, 6)

        val icon = loadIcon(value.file)
        val iconLabel = JBLabel(icon)
        iconLabel.border = JBUI.Borders.empty(0, 6, 0, 8)
        val orderLabel = JBLabel(value.order.toString())
        orderLabel.border = JBUI.Borders.empty(0, 4, 0, 4)
        val leftPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0))
        leftPanel.isOpaque = false
        leftPanel.add(orderLabel)
        leftPanel.add(iconLabel)
        panel.add(leftPanel, BorderLayout.WEST)

        val textPanel = JBPanel<JBPanel<*>>()
        textPanel.layout = BoxLayout(textPanel, BoxLayout.Y_AXIS)
        textPanel.add(JBLabel(buildTitle(value, list)))
        if (value.desc.isNotBlank()) {
            val descLabel = JBLabel(value.desc)
            descLabel.foreground = UIUtil.getContextHelpForeground()
            textPanel.add(descLabel)
        }
        panel.add(textPanel, BorderLayout.CENTER)
        panel.add(removeLabel, BorderLayout.EAST)

        if (isSelected) {
            panel.background = UIUtil.getListSelectionBackground(true)
            panel.foreground = UIUtil.getListSelectionForeground(true)
        } else {
            panel.background = UIUtil.getListBackground()
            panel.foreground = UIUtil.getListForeground()
        }
        panel.isOpaque = true

        return panel
    }

    private fun buildTitle(item: StagedItem, list: javax.swing.JList<out StagedItem>): String {
        val displayName = java.nio.file.Path.of(item.title).fileName.toString()
        val effectiveStart = if (item.startLine > 0) item.startLine else 0
        val effectiveEnd = when {
            item.endLine > 0 -> item.endLine
            item.startLine > 0 && item.lineCount > 0 -> item.startLine + item.lineCount - 1
            else -> 0
        }
        val lineText = when {
            effectiveStart > 0 && effectiveEnd > 0 && effectiveStart == effectiveEnd ->
                "(Lines: $effectiveStart)"
            effectiveStart > 0 && effectiveEnd > 0 ->
                "(Lines: $effectiveStart-$effectiveEnd)"
            item.lineCount > 0 ->
                "(Lines: ${item.lineCount})"
            else -> ""
        }
        val base = if (lineText.isEmpty()) displayName else "$displayName $lineText"
        val fm = list.getFontMetrics(list.font)
        val maxWidth = 220
        if (fm.stringWidth(base) <= maxWidth) return base
        val ellipsis = "..."
        val budget = maxWidth - fm.stringWidth(ellipsis)
        var lo = 0
        var hi = base.length
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            val head = base.take(mid)
            if (fm.stringWidth(head) <= budget) lo = mid else hi = mid - 1
        }
        return base.take(lo) + ellipsis
    }

    private fun loadIcon(fileName: String): javax.swing.ImageIcon {
        return cache.getOrPut(fileName) {
            val path = rowFolder.resolve(fileName)
            val image = if (Files.exists(path)) {
                ImageIO.read(path.toFile())
            } else {
                java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            }
            val square = centerSquare(image)
            val scaled = square.getScaledInstance(64, 64, Image.SCALE_SMOOTH)
            javax.swing.ImageIcon(scaled)
        }
    }

    private fun centerSquare(image: java.awt.image.BufferedImage): java.awt.image.BufferedImage {
        val size = minOf(image.width, image.height).coerceAtLeast(1)
        val x = (image.width - size) / 2
        val y = (image.height - size) / 2
        return image.getSubimage(x, y, size, size)
    }
}
