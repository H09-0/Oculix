/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * A responsive popup menu used for sidebar navigation sub-items (File, Edit, Tools, Help).
 * Integrates natively with Swing's MenuSelectionManager and FlatLaf, ensuring items never
 * disappear prematurely on mouse hover, with automatic screen boundary and RTL placement.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public class SidebarSubmenu extends JPopupMenu {

  private java.awt.event.MouseListener hoverListener;

  public SidebarSubmenu() {
    setLightWeightPopupEnabled(true);
  }

  public void attachHoverListener(java.awt.event.MouseListener listener) {
    this.hoverListener = listener;
    addMouseListener(listener);
    for (Component c : getComponents()) {
      c.addMouseListener(listener);
    }
  }

  @Override
  public JMenuItem add(JMenuItem item) {
    if (item != null) {
      if (hoverListener != null) {
        item.addMouseListener(hoverListener);
      }
      super.add(item);
    }
    return item;
  }

  public JMenuItem addItem(String text, Icon icon, KeyStroke accelerator, ActionListener action) {
    JMenuItem item = new JMenuItem(text, icon);
    if (accelerator != null) {
      item.setAccelerator(accelerator);
    }
    if (action != null) {
      item.addActionListener(action);
    }
    add(item);
    return item;
  }

  public JMenuItem addItem(String text, KeyStroke accelerator, ActionListener action) {
    return addItem(text, null, accelerator, action);
  }

  public JMenuItem addItem(String text, ActionListener action) {
    return addItem(text, null, null, action);
  }

  /**
   * Displays the submenu positioned proportionally relative to the invoker button,
   * adjusting dynamically for LTR/RTL reading direction and keeping all items fully
   * within available screen bounds.
   */
  public void showBelow(Component invoker) {
    if (invoker == null || !invoker.isShowing()) return;

    org.sikuli.support.ide.SikuliIDEI18N.applyOrientation(this);
    boolean rtl = org.sikuli.support.ide.SikuliIDEI18N.isRTL();

    pack();
    Dimension pref = getPreferredSize();

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

    int xOffset;
    if (rtl) {
      // In RTL, sidebar is on the right; submenu opens to the left of invoker
      xOffset = -pref.width - 2;
      if (invokerScreen.x + xOffset < screenBounds.x) {
        xOffset = screenBounds.x - invokerScreen.x + 4;
      }
    } else {
      // In LTR, sidebar is on the left; submenu opens to the right of invoker
      xOffset = invoker.getWidth() + 2;
      if (invokerScreen.x + xOffset + pref.width > screenBounds.x + screenBounds.width) {
        xOffset = (screenBounds.x + screenBounds.width) - (invokerScreen.x + pref.width) - 4;
      }
    }

    int yOffset = 0;
    if (invokerScreen.y + pref.height > screenBounds.y + screenBounds.height - 10) {
      yOffset = (screenBounds.y + screenBounds.height - 10) - (invokerScreen.y + pref.height);
    }

    show(invoker, xOffset, yOffset);
  }
}
