/*
 * Copyright (c) 2017 大前良介 (OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */
package net.mm2d.cdsextractor

import io.reactivex.schedulers.Schedulers
import net.mm2d.android.upnp.AvControlPointManager
import net.mm2d.android.upnp.cds.MediaServer
import net.mm2d.android.upnp.cds.MsControlPoint
import net.mm2d.android.upnp.cds.MsControlPoint.MsDiscoveryListener
import net.mm2d.log.Logger
import net.mm2d.log.Senders
import java.awt.BorderLayout
import java.awt.Color
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

/**
 * @author [大前良介(OHMAE Ryosuke)](mailto:ryo@mm2d.net)
 */
class MainWindow private constructor() : JFrame() {
    private var currentDirectory: File? = null
    private lateinit var tree: JTree
    private lateinit var textField: JTextField
    private lateinit var button: JButton
    private lateinit var rootNode: DefaultMutableTreeNode
    private lateinit var controlPointManager: AvControlPointManager
    private lateinit var msControlPoint: MsControlPoint
    private var loading: Boolean = false
    private var cancel: Boolean = false
    private val zipEntrySet = HashSet<String>()
    private val executor: ExecutorService = Executors.newCachedThreadPool()

    private val selectedServer: MediaServer?
        get() = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? MediaServer

    init {
        title = "CDS Extractor"
        setSize(300, 500)
        defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        setUpUi()
        isVisible = true
        setUpControlPoint()
    }

    private fun setUpUi() {
        rootNode = DefaultMutableTreeNode("Device")
        tree = JTree(rootNode, true)
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        val scrollPane = JScrollPane(tree)
        contentPane.add(scrollPane, BorderLayout.CENTER)
        tree.addTreeSelectionListener {
            val server = selectedServer
            button.isEnabled = server != null
            textField.text = server?.friendlyName ?: ""
        }
        textField = JTextField()
        textField.horizontalAlignment = JTextField.CENTER
        textField.background = Color.WHITE
        contentPane.add(textField, BorderLayout.SOUTH)
        button = JButton("保存")
        button.isEnabled = false
        button.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                onClick()
            }
        })
        contentPane.add(button, BorderLayout.NORTH)
    }

    private fun setUpControlPoint() {
        controlPointManager = AvControlPointManager()
        msControlPoint = controlPointManager.msControlPoint
        msControlPoint.setMsDiscoveryListener(object : MsDiscoveryListener {
            override fun onDiscover(server: MediaServer) {
                updateTree()
            }

            override fun onLost(server: MediaServer) {
                updateTree()
            }

            private fun updateTree() {
                rootNode.removeAllChildren()
                for (server in msControlPoint.deviceList) {
                    rootNode.add(DefaultMutableTreeNode(server).also { it.allowsChildren = false })
                }
                (tree.model as DefaultTreeModel).reload()
            }
        })
        controlPointManager.initialize(emptyList())
        controlPointManager.start()
        executor.execute {
            while (!executor.isShutdown) {
                controlPointManager.search()
                Thread.sleep(3000)
            }
        }
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                executor.shutdown()
                controlPointManager.stop()
                controlPointManager.terminate()
            }
        })
    }

    private fun onClick() {
        if (loading) {
            cancel = true
            return
        }
        val server = selectedServer ?: return
        currentDirectory = selectSaveDirectory(server.friendlyName)
        if (currentDirectory == null) {
            return
        }
        cancel = false
        loading = true
        button.text = "キャンセル"
        textField.background = Color.YELLOW
        tree.isEnabled = false
        executor.execute {
            zipEntrySet.clear()
            val fineName = File(currentDirectory, toFileNameString(server.friendlyName) + ".zip")
            ZipOutputStream(FileOutputStream(fineName)).use { zos ->
                saveDescription(zos, server)
                dumpAllDir(zos, server)
            }
            zipEntrySet.clear()
            textField.background = Color.WHITE
            textField.text = "完了"
            button.text = "保存"
            tree.isEnabled = true
            loading = false
        }
    }

    private fun selectSaveDirectory(title: String): File? {
        val chooser = JFileChooser(currentDirectory).also {
            it.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            it.dialogTitle = "「$title」の保存先フォルダを選択"
        }
        val selected = chooser.showSaveDialog(this)
        return if (selected == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    private fun saveDescription(
        zos: ZipOutputStream,
        server: MediaServer
    ) {
        val device = server.device
        val base = toFileNameString(server.friendlyName) + "/description"
        writeZipEntry(zos, makePath(base, device.friendlyName), device.description)
        for (service in device.serviceList) {
            writeZipEntry(zos, makePath(base, service.serviceId), service.description)
        }
    }

    @Throws(IOException::class)
    private fun makePath(
        base: String,
        name: String?
    ): String = makeUniquePath(base + "/" + toFileNameString(name!!), ".xml")

    @Throws(IOException::class)
    private fun makePath(
        base: String,
        name: String?,
        suffix: String
    ): String = makeUniquePath(base + "/" + toFileNameString(name!!), "$suffix.xml")

    @Throws(IOException::class)
    private fun makeUniquePath(
        body: String,
        suffix: String
    ): String {
        val defaultPath = body + suffix
        if (zipEntrySet.add(defaultPath)) {
            return defaultPath
        }
        for (i in 0 until Integer.MAX_VALUE) {
            val path = "$body$$$i$suffix"
            if (zipEntrySet.add(path)) {
                return path
            }
        }
        throw IOException()
    }

    @Throws(IOException::class)
    private fun writeZipEntry(
        zos: ZipOutputStream,
        path: String,
        data: String
    ) {
        kotlin.runCatching {
            zos.putNextEntry(ZipEntry(path))
            zos.write(data.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }
    }

    private fun toFileNameString(name: String): String {
        return name.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
    }

    private fun dumpAllDir(
        zos: ZipOutputStream,
        server: MediaServer
    ) {
        val base = toFileNameString(server.friendlyName) + "/cds"
        val idList = LinkedList<String>()
        var count = 1
        idList.addFirst("0")
        while (!cancel) {
            val id = idList.pollFirst() ?: return
            server.browse(id)
                .subscribeOn(Schedulers.trampoline())
                .subscribe({ result ->
                    result.list.forEach {
                        if (it.isContainer) {
                            idList.addLast(it.objectId)
                            count++
                        }
                    }
                    textField.text = (count - idList.size).toString() + "/" + count
                    val start = result.start
                    val end = start + result.number - 1
                    if (start == 0 && result.number == result.total) {
                        writeZipEntry(zos, makePath(base, id), result.description)
                    } else {
                        writeZipEntry(zos, makePath(base, id, "(${start}-${end})"), result.description)
                    }
                }, {})
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Logger.setLogLevel(Logger.VERBOSE)
            Logger.setSender(Senders.create())
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            MainWindow()
        }
    }
}
