/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;

import static org.sikuli.support.ide.SikuliIDEI18N._I;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Modern VS Code-style Project File Explorer and Workspace viewer.
 * Provides hierarchical project directory browsing, context actions (new file,
 * new folder, rename, delete, reveal), and smooth integration with the editor.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public class ScriptExplorer extends JPanel implements org.sikuli.ide.ThemeAware {

  private File projectFolder = null;
  private JLabel titleLabel;
  private JPanel headerActions;

  // Project Tree components (VS Code style)
  private JTree projectTree;
  private DefaultTreeModel treeModel;
  private JScrollPane treeScrollPane;

  // Script Cards (when scripts are open without an active project folder)
  private JPanel cardContainer;
  private JScrollPane scrollPane;
  private final List<ScriptCard> cards = new ArrayList<>();
  private int activeIndex = -1;

  // Empty state container
  private JPanel emptyPanel;

  // Callbacks
  private ActionListener onCardSelected;
  private ActionListener onCardRenamed;

  public ScriptExplorer() {
    setLayout(new BorderLayout());
    setMinimumSize(new Dimension(160, 0));
    setPreferredSize(new Dimension(240, 0));
    setOpaque(true);

    buildHeader();
    buildProjectTree();
    buildCardContainer();
    buildEmptyState();

    applyTheme();
    showEmptyState();
  }

  // ── Header ──

  private void buildHeader() {
    JPanel header = new JPanel(new BorderLayout());
    header.setOpaque(false);
    header.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 8));

    titleLabel = new JLabel(_I("projectExplorerTitle"));
    titleLabel.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD, 12f));
    header.add(titleLabel, BorderLayout.CENTER);

    headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
    headerActions.setOpaque(false);

    JButton btnNewFile = createHeaderButton("icons/menu/page.svg", _I("projectExplorerNewFile"),
        e -> createNewFileIn(getSelectedOrProjectDir()));
    JButton btnNewFolder = createHeaderButton("icons/sidebar/folder.svg", _I("projectExplorerNewFolder"),
        e -> createNewFolderIn(getSelectedOrProjectDir()));
    JButton btnRefresh = createHeaderButton("icons/menu/up-arrow.svg", _I("projectExplorerRefresh"),
        e -> refreshProject());
    JButton btnClose = createHeaderButton("icons/menu/clear.svg", _I("projectExplorerCollapse"),
        e -> closeProject());

    headerActions.add(btnNewFile);
    headerActions.add(btnNewFolder);
    headerActions.add(btnRefresh);
    headerActions.add(btnClose);
    headerActions.setVisible(false);

    header.add(headerActions, BorderLayout.LINE_END);
    add(header, BorderLayout.NORTH);
  }

  private JButton createHeaderButton(String iconPath, String tooltip, ActionListener action) {
    JButton btn = new JButton(new FlatSVGIcon(iconPath, 14, 14));
    btn.setToolTipText(tooltip);
    btn.setFocusable(false);
    btn.setContentAreaFilled(false);
    btn.setBorderPainted(false);
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btn.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
    btn.addActionListener(action);
    return btn;
  }

  // ── VS Code-style Project File Tree ──

  private void buildProjectTree() {
    projectTree = new JTree();
    projectTree.setOpaque(false);
    projectTree.setRootVisible(true);
    projectTree.setShowsRootHandles(true);
    projectTree.setRowHeight(24);
    projectTree.putClientProperty(FlatClientProperties.TREE_WIDE_SELECTION, true);
    projectTree.setCellRenderer(new ProjectTreeCellRenderer());

    projectTree.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
          openSelectedTreeNode();
        } else if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
          showTreeContextMenu(e);
        }
      }
    });

    projectTree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          openSelectedTreeNode();
        } else if (e.getKeyCode() == KeyEvent.VK_F5) {
          refreshProject();
        }
      }
    });

    treeScrollPane = new JScrollPane(projectTree);
    treeScrollPane.setBorder(BorderFactory.createEmptyBorder());
    treeScrollPane.setOpaque(false);
    treeScrollPane.getViewport().setOpaque(false);
    treeScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    treeScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    treeScrollPane.getVerticalScrollBar().setUnitIncrement(16);

    addMouseWheelListener(e -> treeScrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, treeScrollPane)));
    projectTree.addMouseWheelListener(e -> treeScrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(projectTree, e, treeScrollPane)));
  }

  // ── Card container (fallback for unparented scripts) ──

  private void buildCardContainer() {
    cardContainer = new JPanel(new MigLayout("wrap 1, insets 4 6 4 6, gap 4", "[fill, grow]", ""));
    cardContainer.setOpaque(false);

    scrollPane = new JScrollPane(cardContainer);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.getVerticalScrollBar().setUnitIncrement(16);
    scrollPane.setOpaque(false);
    scrollPane.getViewport().setOpaque(false);
  }

  // ── Empty State ──

  private void buildEmptyState() {
    emptyPanel = new JPanel(new MigLayout("wrap 1, insets 20 16 20 16, align center", "[grow, fill]", "push[]12[]16[]8[]8[]push"));
    emptyPanel.setOpaque(false);

    JLabel emptyIcon = new JLabel(new FlatSVGIcon("icons/sidebar/folder.svg", 36, 36));
    emptyIcon.setHorizontalAlignment(SwingConstants.CENTER);
    emptyPanel.add(emptyIcon, "align center");

    JLabel emptyLabel = new JLabel(_I("projectExplorerNoProject"));
    emptyLabel.setFont(UIManager.getFont("defaultFont").deriveFont(12f));
    emptyLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
    emptyPanel.add(emptyLabel, "align center");

    JButton btnOpenProject = new JButton(_I("projectExplorerOpenProject"));
    btnOpenProject.setFont(UIManager.getFont("defaultFont").deriveFont(11f));
    btnOpenProject.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btnOpenProject.addActionListener(e -> {
      org.sikuli.ide.SikulixIDE ide = org.sikuli.ide.SikulixIDE.get();
      if (ide != null) ide.openExistingWorkspace();
    });
    emptyPanel.add(btnOpenProject);

    JButton btnNewProject = new JButton(_I("projectExplorerNewProject"));
    btnNewProject.setFont(UIManager.getFont("defaultFont").deriveFont(11f));
    btnNewProject.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btnNewProject.addActionListener(e -> {
      org.sikuli.ide.SikulixIDE ide = org.sikuli.ide.SikulixIDE.get();
      if (ide != null) ide.openNewWorkspaceDialog();
    });
    emptyPanel.add(btnNewProject);

    JButton btnNewFile = new JButton(_I("menuFileNewFile"));
    btnNewFile.setFont(UIManager.getFont("defaultFont").deriveFont(11f));
    btnNewFile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btnNewFile.addActionListener(e -> {
      org.sikuli.ide.SikulixIDE ide = org.sikuli.ide.SikulixIDE.get();
      if (ide != null) ide.createEmptyScriptContext();
    });
    emptyPanel.add(btnNewFile);
  }

  // ── Project Tree Lifecycle & Population ──

  public void loadProjectFolder(File folder) {
    this.projectFolder = folder;
    if (folder == null || !folder.isDirectory()) {
      showEmptyState();
      return;
    }

    titleLabel.setText(folder.getName());
    titleLabel.setIcon(new FlatSVGIcon("icons/sidebar/folder.svg", 15, 15));
    headerActions.setVisible(true);

    FileNode root = new FileNode(folder);
    buildTree(root, folder, 0);
    treeModel = new DefaultTreeModel(root);
    projectTree.setModel(treeModel);

    // Expand top-level project folder
    projectTree.expandRow(0);

    removeAllCenterViews();
    add(treeScrollPane, BorderLayout.CENTER);

    applyTheme();
    revalidate();
    repaint();
  }

  public File getProjectFolder() {
    return projectFolder;
  }

  public void refreshProject() {
    if (projectFolder != null && projectFolder.isDirectory()) {
      loadProjectFolder(projectFolder);
    }
  }

  public void closeProject() {
    projectFolder = null;
    titleLabel.setText(_I("projectExplorerTitle"));
    titleLabel.setIcon(null);
    headerActions.setVisible(false);
    showEmptyState();

    org.sikuli.ide.SikulixIDE ide = org.sikuli.ide.SikulixIDE.get();
    if (ide != null) {
      ide.setWorkspaceDir(null);
      ide.collapseExplorer();
    }
  }

  private void removeAllCenterViews() {
    remove(treeScrollPane);
    remove(scrollPane);
    remove(emptyPanel);
  }

  private void showEmptyState() {
    removeAllCenterViews();
    if (cards.isEmpty()) {
      add(emptyPanel, BorderLayout.CENTER);
    } else {
      add(scrollPane, BorderLayout.CENTER);
    }
    revalidate();
    repaint();
  }

  private void buildTree(FileNode parent, File dir, int depth) {
    if (depth > 7 || dir == null || !dir.isDirectory()) return;
    File[] list = dir.listFiles();
    if (list == null) return;

    Arrays.sort(list, (a, b) -> {
      boolean aDir = a.isDirectory() && !a.getName().endsWith(".sikuli");
      boolean bDir = b.isDirectory() && !b.getName().endsWith(".sikuli");
      if (aDir != bDir) {
        return aDir ? -1 : 1;
      }
      return a.getName().compareToIgnoreCase(b.getName());
    });

    for (File f : list) {
      String name = f.getName();
      if (name.startsWith(".") || name.equals("target") || name.equals("__pycache__")) continue;
      FileNode child = new FileNode(f);
      parent.add(child);
      if (child.isDirectory()) {
        buildTree(child, f, depth + 1);
      }
    }
  }

  private void openSelectedTreeNode() {
    TreePath path = projectTree.getSelectionPath();
    if (path == null) return;
    Object obj = path.getLastPathComponent();
    if (obj instanceof FileNode) {
      File f = ((FileNode) obj).getFile();
      if (f != null && (!f.isDirectory() || f.getName().endsWith(".sikuli"))) {
        org.sikuli.ide.SikulixIDE ide = org.sikuli.ide.SikulixIDE.get();
        if (ide != null) {
          ide.createFileContext(f);
        }
      }
    }
  }

  private File getSelectedOrProjectDir() {
    TreePath path = projectTree.getSelectionPath();
    if (path != null && path.getLastPathComponent() instanceof FileNode) {
      File f = ((FileNode) path.getLastPathComponent()).getFile();
      if (f != null && f.isDirectory() && !f.getName().endsWith(".sikuli")) {
        return f;
      } else if (f != null && f.getParentFile() != null) {
        return f.getParentFile();
      }
    }
    return projectFolder != null ? projectFolder : new File(System.getProperty("user.dir", "."));
  }

  private void createNewFileIn(File dir) {
    if (dir == null) return;
    String name = JOptionPane.showInputDialog(this,
        _I("projectExplorerNewFile"), "script.py");
    if (name != null && !name.trim().isEmpty()) {
      name = name.trim();
      if (!name.contains(".")) {
        name += ".py";
      }
      File newFile = new File(dir, name);
      try {
        if (newFile.createNewFile()) {
          refreshProject();
          org.sikuli.ide.SikulixIDE ide = org.sikuli.ide.SikulixIDE.get();
          if (ide != null) {
            ide.createFileContext(newFile);
          }
        }
      } catch (Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  private void createNewFolderIn(File dir) {
    if (dir == null) return;
    String name = JOptionPane.showInputDialog(this,
        _I("projectExplorerNewFolder"), "folder");
    if (name != null && !name.trim().isEmpty()) {
      File newFolder = new File(dir, name.trim());
      if (newFolder.mkdirs()) {
        refreshProject();
      }
    }
  }

  private void renameFile(File f) {
    if (f == null) return;
    String newName = JOptionPane.showInputDialog(this,
        _I("scriptExplorerRenamePrompt"), f.getName());
    if (newName != null && !newName.trim().isEmpty()) {
      File dest = new File(f.getParentFile(), newName.trim());
      if (f.renameTo(dest)) {
        refreshProject();
      }
    }
  }

  private void deleteFile(File f) {
    if (f == null) return;
    int choice = JOptionPane.showConfirmDialog(this,
        _I("projectExplorerDeleteConfirm", f.getName()),
        _I("projectExplorerDelete"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
    if (choice == JOptionPane.YES_OPTION) {
      deleteRecursive(f);
      refreshProject();
    }
  }

  private void deleteRecursive(File f) {
    if (f.isDirectory()) {
      File[] subs = f.listFiles();
      if (subs != null) {
        for (File sub : subs) deleteRecursive(sub);
      }
    }
    f.delete();
  }

  private void revealInFileManager(File f) {
    if (f == null) return;
    try {
      File target = f.isDirectory() ? f : f.getParentFile();
      Desktop.getDesktop().open(target);
    } catch (Exception ignored) {}
  }

  private void showTreeContextMenu(MouseEvent e) {
    int row = projectTree.getRowForLocation(e.getX(), e.getY());
    if (row >= 0) {
      projectTree.setSelectionRow(row);
    }
    TreePath path = projectTree.getSelectionPath();
    File targetDir = projectFolder;
    File targetFile = null;
    if (path != null && path.getLastPathComponent() instanceof FileNode) {
      File f = ((FileNode) path.getLastPathComponent()).getFile();
      targetFile = f;
      targetDir = (f != null && f.isDirectory() && !f.getName().endsWith(".sikuli")) ? f : (f != null ? f.getParentFile() : projectFolder);
    }
    final File dirForCreation = (targetDir != null && targetDir.isDirectory()) ? targetDir : projectFolder;
    final File fileForAction = targetFile;

    JPopupMenu popup = new JPopupMenu();
    org.sikuli.support.ide.SikuliIDEI18N.applyOrientation(popup);

    JMenuItem newFileItem = new JMenuItem(_I("projectExplorerNewFile"), new FlatSVGIcon("icons/menu/page.svg", 14, 14));
    newFileItem.addActionListener(ev -> createNewFileIn(dirForCreation));
    popup.add(newFileItem);

    JMenuItem newFolderItem = new JMenuItem(_I("projectExplorerNewFolder"), new FlatSVGIcon("icons/sidebar/folder.svg", 14, 14));
    newFolderItem.addActionListener(ev -> createNewFolderIn(dirForCreation));
    popup.add(newFolderItem);

    popup.addSeparator();

    if (fileForAction != null && !fileForAction.equals(projectFolder)) {
      JMenuItem renameItem = new JMenuItem(_I("projectExplorerRename"));
      renameItem.addActionListener(ev -> renameFile(fileForAction));
      popup.add(renameItem);

      JMenuItem deleteItem = new JMenuItem(_I("projectExplorerDelete"));
      deleteItem.addActionListener(ev -> deleteFile(fileForAction));
      popup.add(deleteItem);

      popup.addSeparator();
    }

    JMenuItem revealItem = new JMenuItem(_I("projectExplorerRevealInFileManager"));
    revealItem.addActionListener(ev -> revealInFileManager(dirForCreation));
    popup.add(revealItem);

    JMenuItem refreshItem = new JMenuItem(_I("projectExplorerRefresh"));
    refreshItem.addActionListener(ev -> refreshProject());
    popup.add(refreshItem);

    popup.show(projectTree, e.getX(), e.getY());
  }

  // ── Node & Renderer ──

  static class FileNode extends DefaultMutableTreeNode {
    private final File file;
    FileNode(File file) {
      super(file);
      this.file = file;
    }
    public File getFile() { return file; }
    public boolean isDirectory() { return file != null && file.isDirectory() && !file.getName().endsWith(".sikuli"); }
    @Override
    public String toString() {
      if (file == null) return "";
      String n = file.getName();
      return n.isEmpty() ? file.getPath() : n;
    }
  }

  static class ProjectTreeCellRenderer extends DefaultTreeCellRenderer {
    private final Icon folderIcon = new FlatSVGIcon("icons/sidebar/folder.svg", 14, 14);
    private final Icon scriptIcon = new FlatSVGIcon("icons/menu/page.svg", 14, 14);
    private final Icon imageIcon = new FlatSVGIcon("icons/menu/picture.svg", 14, 14);

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                  boolean expanded, boolean leaf, int row, boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      if (value instanceof FileNode) {
        File f = ((FileNode) value).getFile();
        if (f != null) {
          String name = f.getName().isEmpty() ? f.getPath() : f.getName();
          setText(name);
          if (f.isDirectory() && !name.endsWith(".sikuli")) {
            setIcon(folderIcon);
          } else if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif")) {
            setIcon(imageIcon);
          } else {
            setIcon(scriptIcon);
          }
        }
      }
      return this;
    }
  }

  // ── Compatibility methods for tab card synchronization ──

  public void setOnCardSelected(ActionListener listener) {
    this.onCardSelected = listener;
  }

  public void setWorkspaceName(String name) {
    if (projectFolder == null) {
      titleLabel.setText(name != null ? name : _I("projectExplorerTitle"));
    }
  }

  public void setOnCardRenamed(ActionListener listener) {
    this.onCardRenamed = listener;
  }

  public void updateScripts(List<ScriptInfo> scripts, int selectedIndex) {
    cards.clear();
    cardContainer.removeAll();

    if (projectFolder != null) {
      // Tree mode is active, cards not needed
      return;
    }

    if (scripts.isEmpty()) {
      showEmptyState();
      activeIndex = -1;
      return;
    }

    removeAllCenterViews();

    for (int i = 0; i < scripts.size(); i++) {
      ScriptInfo info = scripts.get(i);
      ScriptCard card = new ScriptCard(info, i);
      cards.add(card);
      cardContainer.add(card, "growx");
    }

    setActiveCard(selectedIndex);
    add(scrollPane, BorderLayout.CENTER);
    revalidate();
    repaint();
  }

  public void setActiveCard(int index) {
    activeIndex = index;
    for (int i = 0; i < cards.size(); i++) {
      cards.get(i).setActive(i == index);
    }
  }

  public void updateCard(int index, ScriptInfo info) {
    if (index >= 0 && index < cards.size()) {
      cards.get(index).updateInfo(info);
    }
  }

  // ── Theme & Lifecycle ──

  private void applyTheme() {
    boolean dark = isDarkTheme();
    Color bg = dark ? new Color(0x0A, 0x10, 0x28) : new Color(0xF8, 0xFA, 0xFD);
    setBackground(bg);
    if (projectTree != null) {
      projectTree.setBackground(bg);
      projectTree.setForeground(dark ? new Color(0xE6, 0xEA, 0xFB) : Color.BLACK);
    }
    if (scrollPane != null) {
      scrollPane.getViewport().setBackground(bg);
    }
    if (treeScrollPane != null) {
      treeScrollPane.getViewport().setBackground(bg);
    }
    for (ScriptCard card : cards) {
      card.refreshTheme();
    }
  }

  private static boolean isDarkTheme() {
    String theme = org.sikuli.basics.PreferencesUser.get().getIdeTheme();
    return !org.sikuli.basics.PreferencesUser.THEME_LIGHT.equals(theme);
  }

  @Override
  public void beforeThemeChange() {}

  @Override
  public void afterThemeChange() {
    applyTheme();
    revalidate();
    repaint();
  }

  public void refreshI18n() {
    if (projectFolder == null) {
      titleLabel.setText(_I("projectExplorerTitle"));
    }
    org.sikuli.support.ide.SikuliIDEI18N.applyOrientation(this);
    if (projectTree != null) {
      org.sikuli.support.ide.SikuliIDEI18N.applyOrientation(projectTree);
    }
    revalidate();
    repaint();
  }

  // ── ScriptCard ──

  class ScriptCard extends JPanel {
    private final int index;
    private JLabel nameLabel;
    private JLabel infoLabel;
    private JLabel statusLabel;
    private boolean active = false;
    private Color hoverBg;
    private Color activeBg;
    private Color normalBg;

    ScriptCard(ScriptInfo info, int index) {
      this.index = index;
      setLayout(new MigLayout("insets 8 10 8 10, gap 4", "[grow][]", "[]2[]"));
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

      addMouseWheelListener(e -> {
        scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, scrollPane));
      });

      normalBg = UIManager.getColor("Panel.background");
      hoverBg = UIManager.getColor("List.selectionInactiveBackground");
      activeBg = UIManager.getColor("List.selectionBackground");

      setOpaque(true);
      setBackground(normalBg);
      setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1),
          BorderFactory.createEmptyBorder(0, 0, 0, 0)));

      // Script icon + name
      nameLabel = new JLabel();
      nameLabel.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD, 12f));
      add(nameLabel, "growx");

      // Save status
      statusLabel = new JLabel();
      statusLabel.setFont(UIManager.getFont("small.font"));
      add(statusLabel, "wrap");

      // Details
      infoLabel = new JLabel();
      infoLabel.setFont(UIManager.getFont("small.font"));
      infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
      add(infoLabel, "span 2, growx");

      updateInfo(info);

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.getButton() == MouseEvent.BUTTON1 && onCardSelected != null) {
            onCardSelected.actionPerformed(new ActionEvent(ScriptCard.this, index, "selected"));
          } else if (e.getButton() == MouseEvent.BUTTON3) {
            JPopupMenu popup = new JPopupMenu();
            JMenuItem renameItem = new JMenuItem(_I("scriptExplorerCtxRename"));
            renameItem.addActionListener(ev -> {
              String newName = JOptionPane.showInputDialog(ScriptExplorer.this,
                  _I("scriptExplorerRenamePrompt"), info.name);
              if (newName != null && !newName.trim().isEmpty() && onCardRenamed != null) {
                onCardRenamed.actionPerformed(new ActionEvent(ScriptCard.this, index, newName.trim()));
              }
            });
            popup.add(renameItem);
            popup.show(ScriptCard.this, e.getX(), e.getY());
          }
        }

        @Override
        public void mouseEntered(MouseEvent e) {
          if (!active) {
            setBackground(hoverBg);
          }
        }

        @Override
        public void mouseExited(MouseEvent e) {
          setBackground(active ? activeBg : normalBg);
        }
      });
    }

    void updateInfo(ScriptInfo info) {
      nameLabel.setIcon(new FlatSVGIcon("icons/menu/page.svg", 14, 14));
      nameLabel.setText(info.name);
      nameLabel.setToolTipText(info.path);

      if (info.saved) {
        statusLabel.setIcon(new FlatSVGIcon("icons/menu/check.svg", 12, 12));
        statusLabel.setText("");
        statusLabel.setToolTipText("Saved");
      } else {
        statusLabel.setIcon(null);
        statusLabel.setText("*");
        statusLabel.setForeground(new Color(0xFF, 0xAA, 0x33));
        statusLabel.setToolTipText(_I("workspaceCardUnsaved"));
      }

      String imgText = info.imageCount + " " + (info.imageCount != 1
          ? _I("workspaceCardImagesPlural")
          : _I("workspaceCardImagesSingular"));
      infoLabel.setIcon(new FlatSVGIcon("icons/menu/picture.svg", 12, 12));
      infoLabel.setText(imgText);
    }

    void setActive(boolean active) {
      this.active = active;
      setBackground(active ? activeBg : normalBg);
      if (active) {
        nameLabel.setForeground(UIManager.getColor("List.selectionForeground"));
        infoLabel.setForeground(UIManager.getColor("List.selectionForeground"));
      } else {
        nameLabel.setForeground(UIManager.getColor("Label.foreground"));
        infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
      }
    }

    void refreshTheme() {
      normalBg = UIManager.getColor("Panel.background");
      hoverBg = UIManager.getColor("List.selectionInactiveBackground");
      activeBg = UIManager.getColor("List.selectionBackground");
      setBackground(active ? activeBg : normalBg);
      setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1),
          BorderFactory.createEmptyBorder(0, 0, 0, 0)));
      if (active) {
        nameLabel.setForeground(UIManager.getColor("List.selectionForeground"));
        infoLabel.setForeground(UIManager.getColor("List.selectionForeground"));
      } else {
        nameLabel.setForeground(UIManager.getColor("Label.foreground"));
        infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
      }
    }
  }

  // ── ScriptInfo (data class) ──

  public static class ScriptInfo {
    public final String name;
    public final String path;
    public final int imageCount;
    public final boolean saved;

    public ScriptInfo(String name, String path, int imageCount, boolean saved) {
      this.name = name;
      this.path = path;
      this.imageCount = imageCount;
      this.saved = saved;
    }

    public static ScriptInfo fromFolder(String name, File folder, boolean isDirty) {
      int count = 0;
      if (folder != null && folder.exists()) {
        File[] images = folder.listFiles((d, f) -> f.endsWith(".png"));
        if (images != null) count = images.length;
      }
      return new ScriptInfo(name, folder != null ? folder.getAbsolutePath() : "", count, !isDirty);
    }
  }
}
