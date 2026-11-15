/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Modern VS Code-style auto-completion popup for OculiX EditorPane.
 * Filters matching methods and keywords as the user types, supports arrow-key navigation,
 * and completes full signatures with default arguments upon pressing Tab or Enter.
 */
public class EditorCodeCompletion {

  public static class Item {
    public final String name;
    public final String snippet;
    public final String signature;
    public final String doc;
    public final String kind; // "method", "keyword", "class"

    public Item(String name, String snippet, String signature, String doc, String kind) {
      this.name = name;
      this.snippet = snippet;
      this.signature = signature;
      this.doc = doc;
      this.kind = kind;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private static final List<Item> COMPLETION_DATABASE = new ArrayList<>();

  static {
    // SikuliX & OculiX Visual Automation API
    add("click", "click(\"image.png\")", "(pattern)", "Click on visual pattern or region", "method");
    add("doubleClick", "doubleClick(\"image.png\")", "(pattern)", "Double-click visual pattern or region", "method");
    add("rightClick", "rightClick(\"image.png\")", "(pattern)", "Right-click visual pattern or region", "method");
    add("find", "find(\"image.png\")", "(pattern)", "Find match on screen", "method");
    add("findAll", "findAll(\"image.png\")", "(pattern)", "Find all occurrences on screen", "method");
    add("wait", "wait(\"image.png\", 10)", "(pattern, [timeout])", "Wait for pattern to appear", "method");
    add("waitVanish", "waitVanish(\"image.png\", 10)", "(pattern, [timeout])", "Wait for pattern to vanish", "method");
    add("exists", "exists(\"image.png\", 5)", "(pattern, [timeout])", "Check if pattern exists", "method");
    add("hover", "hover(\"image.png\")", "(pattern)", "Move mouse hover over pattern", "method");
    add("dragDrop", "dragDrop(\"source.png\", \"target.png\")", "(source, target)", "Drag from source and drop on target", "method");
    add("type", "type(\"text\")", "(text, [modifiers])", "Type keystrokes or text", "method");
    add("paste", "paste(\"text\")", "(text)", "Paste string from clipboard", "method");
    add("sleep", "sleep(1)", "(seconds)", "Pause execution for N seconds", "method");
    add("wheel", "wheel(WHEEL_DOWN, 3)", "(direction, steps)", "Scroll mouse wheel", "method");
    add("mouseMove", "mouseMove(Location(x, y))", "(location)", "Move mouse to coordinates", "method");
    add("mouseDown", "mouseDown(Button.LEFT)", "(button)", "Press and hold mouse button", "method");
    add("mouseUp", "mouseUp(Button.LEFT)", "(button)", "Release mouse button", "method");
    add("keyDown", "keyDown(Key.CTRL)", "(key)", "Press and hold keyboard key", "method");
    add("keyUp", "keyUp(Key.CTRL)", "(key)", "Release keyboard key", "method");

    // Constructors & Classes
    add("Pattern", "Pattern(\"image.png\").similar(0.7)", "(path)", "Visual target with similarity/offset", "class");
    add("Region", "Region(0, 0, 100, 100)", "(x, y, w, h)", "Restricted rectangular screen area", "class");
    add("Location", "Location(0, 0)", "(x, y)", "Point coordinates on screen", "class");
    add("Screen", "Screen()", "()", "Physical display monitor", "class");
    add("App", "App.open(\"app_name\")", "(name)", "Application controller", "class");
    add("Settings", "Settings.ActionLogs = True", "", "OculiX runtime configuration", "class");

    // Keys
    add("Key.ENTER", "Key.ENTER", "", "Enter / Return key", "keyword");
    add("Key.TAB", "Key.TAB", "", "Tab key", "keyword");
    add("Key.ESC", "Key.ESC", "", "Escape key", "keyword");
    add("Key.BACKSPACE", "Key.BACKSPACE", "", "Backspace key", "keyword");
    add("Key.DELETE", "Key.DELETE", "", "Delete key", "keyword");
    add("Key.UP", "Key.UP", "", "Arrow Up key", "keyword");
    add("Key.DOWN", "Key.DOWN", "", "Arrow Down key", "keyword");
    add("Key.LEFT", "Key.LEFT", "", "Arrow Left key", "keyword");
    add("Key.RIGHT", "Key.RIGHT", "", "Arrow Right key", "keyword");
    add("Key.CTRL", "Key.CTRL", "", "Control modifier key", "keyword");
    add("Key.ALT", "Key.ALT", "", "Alt modifier key", "keyword");
    add("Key.SHIFT", "Key.SHIFT", "", "Shift modifier key", "keyword");

    // Python syntax snippets
    add("print", "print(\"message\")", "(msg)", "Print output to console", "method");
    add("def", "def function_name():\n    pass", "()", "Define a new function", "keyword");
    add("class", "class ClassName:\n    def __init__(self):\n        pass", "", "Define a new class", "keyword");
    add("import", "import ", "", "Import a module", "keyword");
    add("from", "from  import ", "", "Import specific symbols", "keyword");
    add("for", "for item in items:\n    pass", "", "Loop over iterable", "keyword");
    add("while", "while condition:\n    pass", "", "While loop", "keyword");
    add("if", "if condition:\n    pass", "", "Conditional branch", "keyword");
    add("elif", "elif condition:\n    pass", "", "Else-if branch", "keyword");
    add("else", "else:\n    pass", "", "Else branch", "keyword");
    add("try", "try:\n    pass\nexcept Exception as e:\n    print(e)", "", "Try-except block", "keyword");
    add("return", "return ", "", "Return statement", "keyword");
    add("range", "range(0, 10)", "(start, stop)", "Sequence of numbers", "method");
    add("len", "len(items)", "(object)", "Return length of object", "method");
    add("str", "str(val)", "(val)", "Convert to string", "method");
    add("int", "int(val)", "(val)", "Convert to integer", "method");
    add("True", "True", "", "Boolean true", "keyword");
    add("False", "False", "", "Boolean false", "keyword");
    add("None", "None", "", "Null/None value", "keyword");
  }

  private static void add(String name, String snippet, String sig, String doc, String kind) {
    COMPLETION_DATABASE.add(new Item(name, snippet, sig, doc, kind));
  }

  private final EditorPane editor;
  private JWindow popupWindow;
  private Window popupOwner;
  private JScrollPane scrollPane;
  private JList<Item> completionList;
  private DefaultListModel<Item> listModel;
  private int prefixStartOffset = -1;
  private String currentPrefix = "";
  private boolean justCommitted = false;

  public EditorCodeCompletion(EditorPane editor) {
    this.editor = editor;
    initUI();
    wireListeners();
  }

  private void initUI() {
    listModel = new DefaultListModel<>();
    completionList = new JList<>(listModel);
    completionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    completionList.setCellRenderer(new CompletionRenderer());
    completionList.setFocusable(false);
    completionList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        int idx = completionList.locationToIndex(e.getPoint());
        if (idx >= 0) {
          completionList.setSelectedIndex(idx);
          if (e.getClickCount() >= 1 && SwingUtilities.isLeftMouseButton(e)) {
            Item item = completionList.getSelectedValue();
            if (item != null) {
              commitCompletion(item);
              editor.requestFocusInWindow();
            }
          }
        }
      }
    });

    scrollPane = new JScrollPane(completionList);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    rebuildPopupWindow(SwingUtilities.getWindowAncestor(editor));
  }

  private void rebuildPopupWindow(Window owner) {
    if (popupWindow != null) {
      popupWindow.setVisible(false);
      popupWindow.dispose();
    }
    popupOwner = owner;
    popupWindow = new JWindow(owner);
    popupWindow.setFocusableWindowState(false);
    popupWindow.getContentPane().setLayout(new BorderLayout());
    popupWindow.getContentPane().add(scrollPane, BorderLayout.CENTER);
    popupWindow.setSize(360, 195);
  }

  private void ensurePopupOwner() {
    Window currentOwner = SwingUtilities.getWindowAncestor(editor);
    if (currentOwner != null && currentOwner != popupOwner) {
      rebuildPopupWindow(currentOwner);
    }
  }

  private void wireListeners() {
    editor.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (popupWindow.isVisible()) {
          int code = e.getKeyCode();
          if (code == KeyEvent.VK_UP) {
            int sel = completionList.getSelectedIndex();
            if (sel > 0) {
              completionList.setSelectedIndex(sel - 1);
              completionList.ensureIndexIsVisible(sel - 1);
            }
            e.consume();
            return;
          } else if (code == KeyEvent.VK_DOWN) {
            int sel = completionList.getSelectedIndex();
            if (sel < listModel.size() - 1) {
              completionList.setSelectedIndex(sel + 1);
              completionList.ensureIndexIsVisible(sel + 1);
            }
            e.consume();
            return;
          } else if (code == KeyEvent.VK_TAB || code == KeyEvent.VK_ENTER) {
            Item item = completionList.getSelectedValue();
            if (item != null) {
              justCommitted = true;
              commitCompletion(item);
              e.consume();
              return;
            }
          } else if (code == KeyEvent.VK_ESCAPE) {
            hide();
            e.consume();
            return;
          }
        }

        // Ctrl+Space triggers completion
        if (e.getKeyCode() == KeyEvent.VK_SPACE && (e.isControlDown() || e.isMetaDown())) {
          showCompletion(true);
          e.consume();
        }
      }

      @Override
      public void keyReleased(KeyEvent e) {
        if (justCommitted) {
          justCommitted = false;
          return;
        }
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN ||
            code == KeyEvent.VK_TAB || code == KeyEvent.VK_ENTER ||
            code == KeyEvent.VK_ESCAPE || code == KeyEvent.VK_CONTROL ||
            code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_ALT ||
            code == KeyEvent.VK_META) {
          return;
        }
        if ((e.isControlDown() || e.isMetaDown()) && code != KeyEvent.VK_SPACE) {
          return;
        }
        showCompletion(false);
      }
    });

    editor.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        hide();
      }
    });

    editor.addHierarchyListener(e -> {
      if (!editor.isShowing()) {
        hide();
      }
    });
  }

  private void showCompletion(boolean force) {
    if (!editor.isShowing()) {
      hide();
      return;
    }
    ensurePopupOwner();
    int caret = editor.getCaretPosition();
    Document doc = editor.getDocument();
    try {
      int start = caret;
      while (start > 0) {
        char ch = doc.getText(start - 1, 1).charAt(0);
        if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '.') {
          start--;
        } else {
          break;
        }
      }

      String prefix = doc.getText(start, caret - start);
      if (!force && prefix.isEmpty()) {
        hide();
        return;
      }

      this.prefixStartOffset = start;
      this.currentPrefix = prefix;

      listModel.clear();
      String lower = prefix.toLowerCase(java.util.Locale.ROOT);
      // Prioritize prefix matches before substring matches
      for (Item item : COMPLETION_DATABASE) {
        if (item.name.toLowerCase(java.util.Locale.ROOT).startsWith(lower)) {
          listModel.addElement(item);
        }
      }
      for (Item item : COMPLETION_DATABASE) {
        String itemLower = item.name.toLowerCase(java.util.Locale.ROOT);
        if (!itemLower.startsWith(lower) && itemLower.contains(lower)) {
          listModel.addElement(item);
        }
      }

      if (listModel.isEmpty()) {
        hide();
        return;
      }

      completionList.setSelectedIndex(0);
      completionList.ensureIndexIsVisible(0);

      // Positioning popup beneath caret, clamped within screen bounds
      java.awt.geom.Rectangle2D caretRect = editor.modelToView2D(caret);
      if (caretRect != null) {
        Point pt = new Point((int) caretRect.getX(), (int) (caretRect.getY() + caretRect.getHeight() + 2));
        SwingUtilities.convertPointToScreen(pt, editor);

        GraphicsConfiguration gc = editor.getGraphicsConfiguration();
        if (gc != null) {
          Rectangle scr = gc.getBounds();
          Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
          int maxX = scr.x + scr.width - insets.right - popupWindow.getWidth() - 4;
          int maxY = scr.y + scr.height - insets.bottom - popupWindow.getHeight() - 4;
          if (pt.x > maxX) pt.x = Math.max(scr.x + insets.left, maxX);
          if (pt.y > maxY) {
            // Flip above caret line when near bottom of screen
            int aboveY = pt.y - (int) caretRect.getHeight() - popupWindow.getHeight() - 4;
            pt.y = Math.max(scr.y + insets.top, aboveY);
          }
        }

        boolean dark = !org.sikuli.basics.PreferencesUser.THEME_LIGHT.equals(
            org.sikuli.basics.PreferencesUser.get().getIdeTheme());
        Color bg = dark ? new Color(0x0C, 0x14, 0x30) : new Color(0xF3, 0xF5, 0xFA);
        Color border = dark ? new Color(0x2B, 0x3A, 0x72) : new Color(0xC0, 0xC8, 0xDD);
        popupWindow.getContentPane().setBackground(bg);
        ((JComponent) popupWindow.getContentPane()).setBorder(new LineBorder(border, 1, true));

        popupWindow.setLocation(pt.x, pt.y);
        popupWindow.setVisible(true);
      }
    } catch (Exception ex) {
      hide();
    }
  }

  public void hide() {
    if (popupWindow != null && popupWindow.isVisible()) {
      popupWindow.setVisible(false);
    }
  }

  private void commitCompletion(Item item) {
    hide();
    int caret = editor.getCaretPosition();
    if (prefixStartOffset >= 0 && prefixStartOffset <= caret) {
      try {
        Document doc = editor.getDocument();
        int len = caret - prefixStartOffset;
        doc.remove(prefixStartOffset, len);
        doc.insertString(prefixStartOffset, item.snippet, null);
        editor.setCaretPosition(prefixStartOffset + item.snippet.length());
      } catch (BadLocationException ignored) {}
    }
  }

  static class CompletionRenderer extends JPanel implements ListCellRenderer<Item> {
    private final JLabel nameLabel = new JLabel();
    private final JLabel sigLabel = new JLabel();
    private final JLabel docLabel = new JLabel();

    CompletionRenderer() {
      setLayout(new BorderLayout(8, 0));
      setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

      JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
      left.setOpaque(false);
      Font defFont = UIManager.getFont("defaultFont");
      if (defFont == null) defFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
      Font smFont = UIManager.getFont("small.font");
      if (smFont == null) smFont = new Font(Font.SANS_SERIF, Font.PLAIN, 11);

      nameLabel.setFont(defFont.deriveFont(Font.BOLD, 12f));
      sigLabel.setFont(smFont);
      left.add(nameLabel);
      left.add(sigLabel);

      docLabel.setFont(smFont);
      docLabel.setHorizontalAlignment(SwingConstants.RIGHT);

      add(left, BorderLayout.WEST);
      add(docLabel, BorderLayout.EAST);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends Item> list, Item item,
                                                  int index, boolean isSelected, boolean cellHasFocus) {
      nameLabel.setText(item.name);
      sigLabel.setText(item.signature);
      docLabel.setText(item.doc);

      boolean dark = !org.sikuli.basics.PreferencesUser.THEME_LIGHT.equals(
          org.sikuli.basics.PreferencesUser.get().getIdeTheme());

      if ("class".equals(item.kind)) {
        nameLabel.setIcon(new FlatSVGIcon("icons/sidebar/folder.svg", 14, 14));
      } else if ("keyword".equals(item.kind)) {
        nameLabel.setIcon(new FlatSVGIcon("icons/sidebar/play.svg", 14, 14));
      } else {
        nameLabel.setIcon(new FlatSVGIcon("icons/menu/page.svg", 14, 14));
      }

      if (isSelected) {
        setBackground(dark ? new Color(0x1C, 0x27, 0x53) : new Color(0xD8, 0xE4, 0xFC));
        nameLabel.setForeground(dark ? new Color(0x7C, 0xCB, 0xFF) : new Color(0x00, 0x44, 0xCC));
        sigLabel.setForeground(dark ? new Color(0xB9, 0xC2, 0xE8) : Color.DARK_GRAY);
        docLabel.setForeground(dark ? new Color(0x7D, 0x8B, 0xC9) : Color.GRAY);
      } else {
        setBackground(dark ? new Color(0x0C, 0x14, 0x30) : new Color(0xF3, 0xF5, 0xFA));
        nameLabel.setForeground(dark ? new Color(0xE6, 0xEA, 0xFB) : Color.BLACK);
        sigLabel.setForeground(dark ? new Color(0x7D, 0x8B, 0xC9) : Color.GRAY);
        docLabel.setForeground(dark ? new Color(0x44, 0x56, 0xA0) : new Color(0x88, 0x88, 0x88));
      }
      return this;
    }
  }
}
