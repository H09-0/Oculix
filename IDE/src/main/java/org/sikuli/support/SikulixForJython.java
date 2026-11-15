/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.support;

import java.io.File;
import java.net.URL;

import org.sikuli.script.SikuliXception;
import org.sikuli.support.runnerSupport.JythonSupport;

/**
 * Can be used in pure Jython environments to add the Sikuli Python API to sys.path<br>
 * Usage: (before any Sikuli features are used)<br>
 * import org.sikuli.script.SikulixForJython<br>
 * from sikuli import *
 */
public class SikulixForJython {

  private static SikulixForJython instance = null;
  private static final int lvl = 3;

  static {
    staticInit();
  }

  static void staticInit() {
    JythonSupport helper = JythonSupport.get();
    helper.log(lvl, "SikulixForJython: init: starting");
    String sikuliStuff = "sikuli/Sikuli";
    File fSikuliStuff = helper.existsSysPathModule(sikuliStuff);
    String fpSikuliStuff = Commons.getLibFolder().getAbsolutePath();
    if (null == fSikuliStuff) {
      if (!new File(Commons.getLibFolder(), "sikuli/Sikuli.py").exists()) {
        throw new SikuliXception("fatal: Jython3: Lib/sikuli/Sikuli.py not available");
      }
      if (!helper.hasSysPath(fpSikuliStuff)) {
        helper.log(lvl, "sikuli/*.py not found on current Jython3::sys.path");
        helper.addSysPath(fpSikuliStuff);
        if (!helper.hasSysPath(fpSikuliStuff)) {
          throw new SikuliXception(String.format("fatal: " + "Jython3: " +
                  "not possible to add to Jython3::sys.path: %s", fpSikuliStuff));
        }
        helper.log(lvl, "added as Jython3::sys.path[0]:\n%s", fpSikuliStuff);
      } else {
        helper.log(lvl, "sikuli/*.py is on Jython3::sys.path at:\n%s", fpSikuliStuff);
      }
    }
    helper.addSitePackages();
//TODO default ImagePath???
/*
    List<String> sysArgv = helper.getSysArgv();
    if (sysArgv.size() > 0) {
      String path = sysArgv.get(0);
      Settings.BundlePath = new File(path).getParent();
      helper.log(lvl, "Default ImagePath: %s", Settings.BundlePath);
    }
*/
    helper.log(lvl, "SikulixForJython: init: success");
  }

  SikulixForJython() {
  }

  public static SikulixForJython get() {
    if (null == instance) {
      instance = new SikulixForJython();
    }
    return instance;
  }
}
