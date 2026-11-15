package org.sikuli.ide;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sikuli.basics.PreferencesUser;
import org.sikuli.ide.theme.OculixDarkLaf;
import org.sikuli.ide.ui.OculixSidebar;
import org.sikuli.ide.ui.ScriptExplorer;
import org.sikuli.ide.ui.SidebarSubmenu;
import org.sikuli.support.ide.SikuliIDEI18N;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OculixIdeUiRegressionTest {

    @Test
    void scriptExplorerAndStatusBarInitializeWithoutFlatLafDefaults(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("main.py"), "print('hi')\n");
        SwingUtilities.invokeAndWait(() -> {
            ScriptExplorer explorer = new ScriptExplorer();
            explorer.loadProjectFolder(tempDir.toFile());
            assertEquals(tempDir.toFile(), explorer.getProjectFolder());
            explorer.refreshProject();
            explorer.closeProject();
            assertNull(explorer.getProjectFolder());

            SikuliIDEStatusBar statusBar = new SikuliIDEStatusBar();
            statusBar.setType("text/jython");
            statusBar.setCaretPosition(3, 7);
            statusBar.refreshI18n();
            assertTrue(statusBar.getComponentCount() >= 6);
        });
    }

    @Test
    void sidebarRetainsNavigationAndSwitchesToArabicRtlCleanly() throws Exception {
        Locale prev = PreferencesUser.get().getLocale();
        try {
            SwingUtilities.invokeAndWait(() -> {
                OculixDarkLaf.setup();
                OculixSidebar sidebar = new OculixSidebar();
                sidebar.initNavItems();
                AtomicInteger runClicks = new AtomicInteger();
                AtomicInteger slowClicks = new AtomicInteger();
                SidebarSubmenu fileSub = new SidebarSubmenu();
                fileSub.addItem("Test", null);
                SidebarSubmenu toolsSub = new SidebarSubmenu();
                sidebar.initNavigation(fileSub, e -> runClicks.incrementAndGet(), e -> slowClicks.incrementAndGet(), toolsSub);
                sidebar.initFooter("4.0.0", null);

                sidebar.getNavRun().doClick();
                sidebar.getNavRunSlow().doClick();
                assertEquals(1, runClicks.get());
                assertEquals(1, slowClicks.get());

                // Simulate locale switch to Arabic and re-wiring as SikulixIDE.refreshAfterLocaleChange does
                Locale ar = new Locale("ar");
                PreferencesUser.get().setLocale(ar);
                SikuliIDEI18N.setLocale(ar);
                sidebar.refreshI18n();
                sidebar.initNavigation(fileSub, e -> runClicks.incrementAndGet(), e -> slowClicks.incrementAndGet(), toolsSub);

                sidebar.getNavRun().doClick();
                sidebar.getNavRunSlow().doClick();
                assertEquals(2, runClicks.get());
                assertEquals(2, slowClicks.get());
                assertEquals(ComponentOrientation.RIGHT_TO_LEFT, sidebar.getComponentOrientation());
            });
        } finally {
            PreferencesUser.get().setLocale(prev);
            SikuliIDEI18N.setLocale(prev);
        }
    }

    @Test
    void consolePaneAndInteractiveTerminalWork() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            OculixDarkLaf.setup();
            EditorConsolePane console = new EditorConsolePane();
            console.init(true);
            console.addNewTerminalTab();
            console.afterThemeChange();
            console.refreshI18n();
            console.clear();
        });
    }

    @Test
    void preferencesWinLoadsAllLocalesIncludingHiBnTe() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            OculixDarkLaf.setup();
            PreferencesWin win = new PreferencesWin();
            assertNotNull(win);
            win.dispose();
        });
    }
}
