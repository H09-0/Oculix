/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * A responsive, scrollable popup menu used for sidebar sub-items (File, Edit, Tools, Help).
 * Supports dynamic content-proportional sizing, automatic vertical scrolling when screen
 * height is constrained, mouse-wheel scrolling, and bidirectional (LTR/RTL) placement.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public class SidebarSubmenu extends JPopupMenu {

  private final JPanel menuPanel;
  private final JScrollPane scrollPane;

  public SidebarSubmenu() {
    setLightWeightPopupEnabled(true);
    super.setLayout(new BorderLayout());

    menuPanel = new JPanel();
    menuPanel.setLayout(new BoxLayout(menuPanel, BoxLayout.Y_AXIS));
    menuPanel.setOpaque(false);

    scrollPane = new JScrollPane(menuPanel);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    scrollPane.setOpaque(false);
    scrollPane.getViewport().setOpaque(false);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.getVerticalScrollBar().setUnitIncrement(16);

    // Forward wheel scrolling from panel
    menuPanel.addMouseWheelListener(e -> {
      scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(menuPanel, e, scrollPane));
    });

    super.add(scrollPane, BorderLayout.CENTER);
  }

  @Override
  public JMenuItem add(JMenuItem item) {
    if (item != null) {
      if (item.isEnabled()) {
        item.addActionListener(e -> setVisible(false));
      }
      item.addMouseWheelListener(e -> {
        scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(item, e, scrollPane));
      });
      menuPanel.add(item);
    }
    return item;
  }

  @Override
  public Component add(Component comp) {
    if (comp instanceof JMenuItem) {
      return add((JMenuItem) comp);
    }
    if (comp != null) {
      comp.addMouseWheelListener(e -> {
        scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(comp, e, scrollPane));
      });
      menuPanel.add(comp);
    }
    return comp;
  }

  @Override
  public void addSeparator() {
    JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
    menuPanel.add(sep);
  }

  @Override
  public void removeAll() {
    menuPanel.removeAll();
  }

  @Override
  public int getComponentCount() {
    return menuPanel != null ? menuPanel.getComponentCount() : 0;
  }

  @Override
  public Component getComponent(int n) {
    return menuPanel.getComponent(n);
  }

  @Override
  public Component[] getComponents() {
    return menuPanel != null ? menuPanel.getComponents() : new Component[0];
  }

  public JMenuItem addItem(String text, Icon icon, KeyStroke accelerator, ActionListener action) {
    JMenuItem item = new JMenuItem(text, icon);
    if (accelerator != null) {
      item.setAccelerator(accelerator);
    }
    if (action != null) {
      item.addActionListener(action);
    }
    return add(item);
  }

  public JMenuItem addItem(String text, KeyStroke accelerator, ActionListener action) {
    return addItem(text, null, accelerator, action);
  }

  public JMenuItem addItem(String text, ActionListener action) {
    return addItem(text, null, null, action);
  }

  /**
   * Displays the submenu positioned proportionally relative to the invoker button,
   * adjusting dynamically for LTR/RTL reading direction and ensuring all items remain
   * accessible via scrolling if available screen height is exceeded.
   */
  public void showBelow(Component invoker) {
    if (invoker == null || !invoker.isShowing()) return;

    // Apply orientation
    org.sikuli.support.ide.SikuliIDEI18N.applyOrientation(this);
    org.sikuli.support.ide.SikuliIDEI18N.applyOrientation(menuPanel);
    boolean rtl = org.sikuli.support.ide.SikuliIDEI18N.isRTL();

    menuPanel.revalidate();
    Dimension pref = menuPanel.getPreferredSize();

    // Determine available screen space
    GraphicsConfiguration gc = invoker.getGraphicsConfiguration();
    Rectangle screenBounds;
    if (gc != null) {
      screenBounds = gc.getBounds();
      Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
      screenBounds.x += insets.left;
      screenBounds.y += insets.top;
      screenBounds.width -= (insets.left + insets.right);
      screenBounds.height -= (insets.top + insets.bottom);
    } else {
      Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
      screenBounds = new Rectangle(0, 0, scr.width, scr.height);
    }

    Point invokerScreen = new Point(0, 0);
    try {
      invokerScreen = invoker.getLocationOnScreen();
    } catch (Exception ignored) {}

    // Dynamic proportional width to fit content with sane bounds
    int targetW = Math.max(200, Math.min(pref.width + 16, 420));

    // Vertical space calculations
    int spaceBelow = screenBounds.y + screenBounds.height - invokerScreen.y - 12;
    int spaceAbove = invokerScreen.y - screenBounds.y - 12;

    int targetH;
    int yOffset;

    if (pref.height + 8 <= spaceBelow) {
      targetH = pref.height + 8;
      yOffset = 0;
    } else if (pref.height + 8 <= spaceAbove && spaceAbove > spaceBelow) {
      targetH = pref.height + 8;
      yOffset = -(pref.height + 8) + invoker.getHeight();
    } else {
      int maxAvailable = Math.max(spaceBelow, spaceAbove);
      targetH = Math.max(220, Math.min(pref.height + 8, maxAvailable));
      yOffset = (spaceBelow >= spaceAbove) ? 0 : -(targetH) + invoker.getHeight();
      targetW += 18; // reserve room for visible scrollbar
    }

    scrollPane.setPreferredSize(new Dimension(targetW, targetH));
    pack();

    // Horizontal placement with RTL and screen edge checks
    int xOffset;
    if (rtl) {
      // In RTL, the sidebar is on the right; submenu opens to the left of invoker
      xOffset = -targetW - 2;
      if (invokerScreen.x + xOffset < screenBounds.x) {
        xOffset = screenBounds.x - invokerScreen.x + 4;
      }
    } else {
      // In LTR, the sidebar is on the left; submenu opens to the right of invoker
      xOffset = invoker.getWidth() + 2;
      if (invokerScreen.x + xOffset + targetW > screenBounds.x + screenBounds.width) {
        xOffset = (screenBounds.x + screenBounds.width) - (invokerScreen.x + targetW) - 4;
      }
    }

    show(invoker, xOffset, yOffset);
  }
}
