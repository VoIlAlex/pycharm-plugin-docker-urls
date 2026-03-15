package com.voilalex.dockerurls

import javax.swing.table.AbstractTableModel

class DockerServicesTableModel : AbstractTableModel() {
    private val rows = mutableListOf<DockerServiceRow>()

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = 3

    override fun getColumnName(column: Int): String = when (column) {
        0 -> "Service"
        1 -> "Status"
        2 -> "Localhost URL"
        else -> ""
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        return when (columnIndex) {
            0 -> row.serviceName
            1 -> row.statusText
            2 -> row.urls.joinToString(", ")
            else -> ""
        }
    }

    fun setRows(newRows: List<DockerServiceRow>) {
        rows.clear()
        rows.addAll(newRows)
        fireTableDataChanged()
    }

    fun rowAt(index: Int): DockerServiceRow? = rows.getOrNull(index)
}
