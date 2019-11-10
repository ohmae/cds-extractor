/*
 * Copyright (c) 2017 大前良介 (OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */
package net.mm2d.cdsextractor;

import net.mm2d.android.upnp.AvControlPointManager;
import net.mm2d.android.upnp.cds.BrowseResult;
import net.mm2d.android.upnp.cds.CdsObject;
import net.mm2d.android.upnp.cds.Description;
import net.mm2d.android.upnp.cds.MediaServer;
import net.mm2d.android.upnp.cds.MsControlPoint;
import net.mm2d.android.upnp.cds.MsControlPoint.MsDiscoveryListener;
import net.mm2d.log.Logger;
import net.mm2d.log.Senders;
import net.mm2d.upnp.Device;
import net.mm2d.upnp.Service;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;

/**
 * @author <a href="mailto:ryo@mm2d.net">大前良介(OHMAE Ryosuke)</a>
 */
public class MainWindow extends JFrame {
    public static void main(String[] args) {
        Logger.setLogLevel(Logger.VERBOSE);
        Logger.setSender(Senders.create());
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                | UnsupportedLookAndFeelException e) {
            Logger.w(e);
        }
        new MainWindow();
    }

    private File mCurrentDirectory;
    private JTree mTree;
    private JTextField mText;
    private JButton mButton;
    private DefaultMutableTreeNode mRootNode;
    private AvControlPointManager mControlPointManager;
    private MsControlPoint mMsControlPoint;
    private Thread mThread;
    private boolean mLoading;
    private boolean mCancel;
    private final Set<String> mZipEntrySet = new HashSet<>();

    private MainWindow() {
        setTitle("CDS Extractor");
        setSize(300, 500);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setUpUi();
        setVisible(true);
        setUpControlPoint();
    }

    private void setUpControlPoint() {
        mControlPointManager = new AvControlPointManager();
        mMsControlPoint = mControlPointManager.getMsControlPoint();
        mMsControlPoint.setMsDiscoveryListener(new MsDiscoveryListener() {
            @Override
            public void onDiscover(@Nonnull final MediaServer server) {
                updateTree();
            }

            @Override
            public void onLost(@Nonnull final MediaServer server) {
                updateTree();
            }

            private void updateTree() {
                mRootNode.removeAllChildren();
                List<MediaServer> list = mMsControlPoint.getDeviceList();
                for (MediaServer server : list) {
                    DefaultMutableTreeNode node = new DefaultMutableTreeNode(server);
                    node.setAllowsChildren(false);
                    mRootNode.add(node);
                }
                ((DefaultTreeModel) mTree.getModel()).reload();
            }
        });
        mControlPointManager.initialize();
        mControlPointManager.start();
        mThread = new Thread(() -> {
            try {
                while (!Thread.interrupted()) {
                    mControlPointManager.search();
                    Thread.sleep(3000);
                }
            } catch (InterruptedException ignored) {
            }
        });
        mThread.start();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                mThread.interrupt();
                mControlPointManager.stop();
                mControlPointManager.terminate();
            }
        });
    }

    private void setUpUi() {
        mRootNode = new DefaultMutableTreeNode("Device");
        mTree = new JTree(mRootNode, true);
        mTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        final JScrollPane scrollPane = new JScrollPane(mTree);
        getContentPane().add(scrollPane, BorderLayout.CENTER);
        mTree.addTreeSelectionListener(e -> {
            MediaServer server = getSelectedServer();
            mButton.setEnabled(server != null);
            if (server != null) {
                mText.setText(server.getFriendlyName());
            } else {
                mText.setText("");
            }
        });
        mText = new JTextField();
        mText.setHorizontalAlignment(JTextField.CENTER);
        mText.setBackground(Color.WHITE);
        getContentPane().add(mText, BorderLayout.SOUTH);
        mButton = new JButton("保存");
        mButton.setEnabled(false);
        mButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                onClick();
            }
        });
        getContentPane().add(mButton, BorderLayout.NORTH);
    }

    private void onClick() {
        if (mLoading) {
            mCancel = true;
            return;
        }
        final MediaServer server = getSelectedServer();
        if (server == null) {
            return;
        }
        mCurrentDirectory = selectSaveDirectory(server.getFriendlyName());
        if (mCurrentDirectory == null) {
            return;
        }
        mCancel = false;
        mLoading = true;
        mButton.setText("キャンセル");
        mText.setBackground(Color.YELLOW);
        mTree.setEnabled(false);
        new Thread(() -> {
            mZipEntrySet.clear();
            final File fineName = new File(mCurrentDirectory, toFileNameString(server.getFriendlyName()) + ".zip");
            try (final ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(fineName))) {
                saveDescription(zos, server);
                dumpAllDir(zos, server);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mZipEntrySet.clear();
            mText.setBackground(Color.WHITE);
            mText.setText("完了");
            mButton.setText("保存");
            mTree.setEnabled(true);
            mLoading = false;
        }).start();
    }

    @Nullable
    private MediaServer getSelectedServer() {
        final Object selected = mTree.getLastSelectedPathComponent();
        if (!(selected instanceof DefaultMutableTreeNode)) {
            return null;
        }
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode) selected;
        final Object object = node.getUserObject();
        if (!(object instanceof MediaServer)) {
            return null;
        }
        return (MediaServer) object;
    }

    @Nullable
    private File selectSaveDirectory(String title) {
        final JFileChooser chooser = new JFileChooser(mCurrentDirectory);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("「" + title + "」の保存先フォルダを選択");
        final int selected = chooser.showSaveDialog(this);
        if (selected == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
    }

    private void saveDescription(
            ZipOutputStream zos,
            MediaServer server) throws IOException {
        final Device device = server.getDevice();
        final String base = toFileNameString(server.getFriendlyName()) + "/description";
        writeZipEntry(zos, makePath(base, device.getFriendlyName()), device.getDescription());
        for (Service service : device.getServiceList()) {
            writeZipEntry(zos, makePath(base, service.getServiceId()), service.getDescription());
        }
    }

    @Nonnull
    private String makePath(
            String base,
            String name) throws IOException {
        return makeUniquePath(base + "/" + toFileNameString(name), ".xml");
    }

    @Nonnull
    private String makePath(
            String base,
            String name,
            String suffix) throws IOException {
        return makeUniquePath(base + "/" + toFileNameString(name), suffix + ".xml");
    }

    @Nonnull
    private String makeUniquePath(
            String body,
            String suffix) throws IOException {
        String path = body + suffix;
        if (!mZipEntrySet.contains(path)) {
            mZipEntrySet.add(path);
            return path;
        }
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            path = body + "$$" + i + suffix;
            if (!mZipEntrySet.contains(path)) {
                mZipEntrySet.add(path);
                return path;
            }
        }
        throw new IOException();
    }

    private void writeZipEntry(
            ZipOutputStream zos,
            String path,
            String data) throws IOException {
        zos.putNextEntry(new ZipEntry(path));
        zos.write(data.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String toFileNameString(final String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private void dumpAllDir(
            ZipOutputStream zos,
            MediaServer server) throws IOException {
        try {
            final String base = toFileNameString(server.getFriendlyName()) + "/cds";
            final LinkedList<String> idList = new LinkedList<>();
            int count = 1;
            idList.addFirst("0");
            while (idList.size() > 0 && !mCancel) {
                final String id = idList.pollFirst();
                final BrowseResult result = server.browse(id);
                final List<CdsObject> objects = result.get();
                if (objects == null) {
                    continue;
                }
                for (CdsObject object : objects) {
                    if (object.isContainer()) {
                        idList.addLast(object.getObjectId());
                        count++;
                    }
                }
                mText.setText((count - idList.size()) + "/" + count);
                final List<Description> descriptions = result.getDescriptionList();
                for (Description description : descriptions) {
                    int start = description.getStart();
                    int end = description.getNumber() + start - 1;
                    if (descriptions.size() == 1) {
                        writeZipEntry(zos, makePath(base, id), description.getXml());
                    } else {
                        writeZipEntry(zos, makePath(base, id, "(" + start + "-" + end + ")"), description.getXml());
                    }
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
