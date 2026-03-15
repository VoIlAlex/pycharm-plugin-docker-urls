package com.voilalex.dockerurls

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer

class DockerUrlsToolWindowPanel(
    private val project: Project
) : Disposable {
    val component: JComponent

    private val settings = project.service<DockerUrlsProjectSettings>()
    private val dockerComposeClient = DockerComposeClient()
    private val tableModel = DockerServicesTableModel()
    private val composeProjectsModel = DefaultComboBoxModel<DockerComposeProject>()
    private val selectionProjectsModel = DefaultComboBoxModel<DockerComposeProject>()
    private val refreshInProgress = AtomicBoolean(false)
    private val cardLayout = CardLayout()
    private val rootPanel = JPanel(cardLayout)
    private val selectedProjectLabel = JBLabel("No Docker Compose project selected")
    private val footerLabel = JBLabel(" ")
    private val selectionHint = JBTextArea().apply {
        isEditable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        text = "Select the Docker Compose project to inspect for this PyCharm project."
    }
    private val projectComboBox = ComboBox<DockerComposeProject>(composeProjectsModel)
    private val selectionComboBox = ComboBox<DockerComposeProject>(selectionProjectsModel)
    private val table = JTable(tableModel)
    private var selectedComposeProject: DockerComposeProject? = null
    private var refreshFuture: ScheduledFuture<*>? = null

    init {
        component = JPanel(BorderLayout())
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.fillsViewportHeight = true
        table.setDefaultRenderer(Any::class.java, ServicesTableRenderer(tableModel))
        installUrlHandler()

        rootPanel.add(buildSelectionPanel(), "selection")
        rootPanel.add(buildMainPanel(), "main")
        component.add(rootPanel, BorderLayout.CENTER)

        rescanComposeProjects()
        refreshFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            { triggerRefresh() },
            5,
            5,
            TimeUnit.SECONDS
        )
    }

    private fun buildSelectionPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val controls = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(selectionComboBox)
            add(JButton("Open").apply {
                addActionListener {
                    val selected = selectionComboBox.selectedItem as? DockerComposeProject
                    if (selected != null) {
                        selectComposeProject(selected)
                    }
                }
            })
            add(JButton("Rescan").apply {
                addActionListener { rescanComposeProjects() }
            })
        }

        panel.add(selectionHint, BorderLayout.NORTH)
        panel.add(controls, BorderLayout.CENTER)
        return panel
    }

    private fun buildMainPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(selectedProjectLabel)
            add(projectComboBox.apply {
                addActionListener {
                    val selected = selectedItem as? DockerComposeProject ?: return@addActionListener
                    if (selected.composeFile != selectedComposeProject?.composeFile) {
                        selectComposeProject(selected)
                    }
                }
            })
            add(JButton("Change Project").apply {
                addActionListener { showSelectionCard() }
            })
            add(JButton("Rescan Projects").apply {
                addActionListener { rescanComposeProjects() }
            })
            add(JButton("Reload Now").apply {
                addActionListener { triggerRefresh(force = true) }
            })
        }

        footerLabel.horizontalAlignment = SwingConstants.LEFT

        panel.add(toolbar, BorderLayout.NORTH)
        panel.add(JBScrollPane(table), BorderLayout.CENTER)
        panel.add(footerLabel, BorderLayout.SOUTH)
        return panel
    }

    private fun rescanComposeProjects() {
        val discovered = DockerComposeLocator.findComposeProjects(project)
        updateComboModels(discovered)

        val savedSelection = settings.selectedComposeFile()
        val restored = discovered.firstOrNull { it.composeFile.toString() == savedSelection }
        val fallback = discovered.singleOrNull()
        val target = when {
            selectedComposeProject != null && discovered.any { it.composeFile == selectedComposeProject?.composeFile } ->
                discovered.first { it.composeFile == selectedComposeProject?.composeFile }
            restored != null -> restored
            fallback != null -> fallback
            else -> null
        }

        when {
            target != null -> selectComposeProject(target, triggerRefresh = true)
            discovered.isEmpty() -> {
                selectedComposeProject = null
                settings.setSelectedComposeFile(null)
                selectedProjectLabel.text = "No Docker Compose project selected"
                footerLabel.text = "No docker-compose.yml or compose.yml files were found under ${project.basePath ?: "this project"}."
                tableModel.setRows(emptyList())
                showSelectionCard(emptyState = true)
            }
            else -> showSelectionCard()
        }
    }

    private fun updateComboModels(projects: List<DockerComposeProject>) {
        composeProjectsModel.removeAllElements()
        selectionProjectsModel.removeAllElements()
        projects.forEach {
            composeProjectsModel.addElement(it)
            selectionProjectsModel.addElement(it)
        }
        val renderer = ComposeProjectRenderer()
        projectComboBox.renderer = renderer
        selectionComboBox.renderer = renderer
    }

    private fun showSelectionCard(emptyState: Boolean = false) {
        selectionHint.text = if (emptyState) {
            "No Docker Compose projects were found in the current PyCharm project path. Use Rescan after adding one."
        } else {
            "Select the Docker Compose project to inspect for this PyCharm project."
        }
        cardLayout.show(rootPanel, "selection")
    }

    private fun showMainCard() {
        cardLayout.show(rootPanel, "main")
    }

    private fun selectComposeProject(
        composeProject: DockerComposeProject,
        triggerRefresh: Boolean = true
    ) {
        selectedComposeProject = composeProject
        settings.setSelectedComposeFile(composeProject.composeFile.toString())
        selectedProjectLabel.text = "Compose project:"
        projectComboBox.selectedItem = composeProject
        selectionComboBox.selectedItem = composeProject
        footerLabel.text = "Selected ${composeProject.displayName}"
        showMainCard()
        if (triggerRefresh) {
            triggerRefresh(force = true)
        }
    }

    private fun triggerRefresh(force: Boolean = false) {
        val composeProject = selectedComposeProject ?: return
        if (!force && refreshInProgress.get()) {
            return
        }
        if (!refreshInProgress.compareAndSet(false, true)) {
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = dockerComposeClient.listServices(composeProject)
            ApplicationManager.getApplication().invokeLater {
                try {
                    result.onSuccess { rows ->
                        tableModel.setRows(rows)
                        footerLabel.text = "Last updated ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}"
                    }.onFailure { error ->
                        tableModel.setRows(emptyList())
                        footerLabel.text = error.message ?: "Failed to load docker compose status."
                    }
                } finally {
                    refreshInProgress.set(false)
                }
            }
        }
    }

    private fun installUrlHandler() {
        table.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(event: MouseEvent) {
                val row = table.rowAtPoint(event.point)
                val column = table.columnAtPoint(event.point)
                val hasUrl = column == 2 && tableModel.rowAt(row)?.urls?.isNotEmpty() == true
                table.cursor = if (hasUrl) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
            }
        })

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                val row = table.rowAtPoint(event.point)
                val column = table.columnAtPoint(event.point)
                if (row >= 0 && column == 2) {
                    openUrls(row, event)
                }
            }
        })
    }

    private fun openUrls(rowIndex: Int, event: MouseEvent) {
        val row = tableModel.rowAt(rowIndex) ?: return
        when (row.urls.size) {
            0 -> Messages.showInfoMessage(project, "This service does not publish any localhost ports.", "Docker URLs")
            1 -> BrowserUtil.browse(row.urls.first())
            else -> {
                val popup = JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(row.urls)
                    .setTitle("Open localhost URL")
                    .setItemChosenCallback { url -> BrowserUtil.browse(url) }
                    .createPopup()
                popup.show(RelativePoint(event))
            }
        }
    }

    override fun dispose() {
        refreshFuture?.cancel(true)
    }
}

private class ComposeProjectRenderer : SimpleListCellRenderer<DockerComposeProject>() {
    override fun customize(
        list: javax.swing.JList<out DockerComposeProject>,
        value: DockerComposeProject?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        text = value?.displayName ?: ""
    }
}

private class ServicesTableRenderer(
    private val model: DockerServicesTableModel
) : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ) = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column).also {
        foreground = if (!isSelected && column == 2 && model.rowAt(row)?.urls?.isNotEmpty() == true) {
            JBColor.BLUE
        } else {
            if (isSelected) table.selectionForeground else table.foreground
        }
    }

    override fun setValue(value: Any?) {
        text = when {
            value == null -> ""
            value.toString().isBlank() -> "No published ports"
            else -> value.toString()
        }
    }
}
