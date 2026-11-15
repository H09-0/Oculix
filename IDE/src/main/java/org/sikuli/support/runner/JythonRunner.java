/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.support.runner;

import org.example.jython3.runtime.InterpreterException;
import org.example.jython3.runtime.SystemExit;
import org.sikuli.basics.Debug;
import org.sikuli.ide.Sikulix;
import org.sikuli.ide.SikulixIDE;
import org.sikuli.script.SX;
import org.sikuli.support.Commons;
import org.sikuli.support.runnerSupport.JythonSupport;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.PrintStream;

import static org.sikuli.idesupport.CommandArgsEnum.RUN;

/**
 * Executes OculiX scripts written in Python 3 using Jython 3.
 */
public class JythonRunner extends AbstractLocalFileScriptRunner {

  public static final String NAME = "Jython";
  public static final String TYPE = "text/jython";
  public static final String[] EXTENSIONS = new String[]{"py"};

  private int lvl = 3;

  @Override
  public boolean isSupported() {
    return JythonSupport.isSupported();
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String[] getExtensions() {
    return EXTENSIONS.clone();
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public boolean isAbortSupported() {
    return true;
  }

  @Override
  protected void doAbort() {
    if (jythonSupport != null) {
      jythonSupport.cancel();
    }
    super.doAbort();
  }

  @Override
  protected void doInit(String[] param) {
    synchronized (JythonRunner.class) {
      Commons.startLog(1, "JythonRunner: starting Jython 3 initialization (%4.1f sec)", Commons.getSinceStart());

      jythonSupport = JythonSupport.get();
      jythonSupport.getSysPath();
      if (!Commons.isRunningFromJar()) {
        jythonSupport.putSysPath(new File(Commons.getMainClassLocation(), "LIB").getAbsolutePath(), 0);
      }
      jythonSupport.addSitePackages();
      jythonSupport.showSysPath();
      jythonSupport.interpreterExecString("import sys");
      jythonSupport.interpreterExecString("import org.sikuli.support.runner.Runner as Runner");
      Object evalVer = jythonSupport.interpreterEval("sys.version.split(\"(\")[0]");
      String interpreterVersion = evalVer != null ? evalVer.toString().trim() : "";
      if (interpreterVersion.isEmpty()) {
        interpreterVersion = "3.x";
      }
      Commons.startLog(3, "Jython 3 ready: version %s (%4.1f sec)", interpreterVersion, Commons.getSinceStart());
      if (!Sikulix.hasArg(RUN.shortname()) && SikulixIDE.isIdeStarted()) {
        SwingUtilities.invokeLater(() -> SikulixIDE.showAfterStart());
      }
    }
  }

  static JythonSupport jythonSupport = null;

  @Override
  public void doClose() {
    if (jythonSupport != null) {
      jythonSupport.interpreterClose();
    }
    redirected = false;
  }

  @Override
  protected int doEvalScript(String script, IRunner.Options options) {
    synchronized (JythonRunner.class) {
      if (jythonSupport == null) {
        jythonSupport = JythonSupport.get();
      }
      redirectToCurrentConsole();
      try {
        jythonSupport.interpreterExecString(script);
        return 0;
      } catch (SystemExit se) {
        return se.code;
      } catch (InterpreterException ie) {
        if (isAborted() || "KeyboardInterrupt".equals(ie.typeName())) {
          return 0;
        }
        int errLine = jythonSupport.findErrorSource(ie, "<eval>");
        if (options != null && errLine > 0) {
          options.setErrorLine(errLine);
        }
        return 1;
      }
    }
  }

  @Override
  protected int doRunScript(String scriptFile, String[] argv, IRunner.Options options) {
    synchronized (JythonRunner.class) {
      if (jythonSupport == null) {
        jythonSupport = JythonSupport.get();
      }
      redirectToCurrentConsole();

      File pyFile = new File(scriptFile);
      File parentDir = pyFile.getParentFile();
      if (parentDir != null) {
        jythonSupport.addSysPath(parentDir);
      }

      jythonSupport.interpreterFillSysArgv(pyFile, argv);
      jythonSupport.executeScriptHeader(codeBefore);

      int exitCode = 0;

      try {
        jythonSupport.interpreterExecFile(pyFile.getAbsolutePath());
      } catch (SystemExit se) {
        exitCode = se.code;
        Debug.info("Exit code: " + exitCode);
      } catch (Throwable scriptException) {
        if (!isAborted()) {
          if (scriptException instanceof InterpreterException ie
              && "KeyboardInterrupt".equals(ie.typeName())) {
            exitCode = 0;
          } else {
            exitCode = 1;
            int errorExit = jythonSupport.findErrorSource(scriptException, pyFile.getAbsolutePath());
            if (null != options) {
              options.setErrorLine(errorExit);
            }
          }
        }
      } finally {
        jythonSupport.interpreterCleanup();
      }

      if (System.out.checkError()) {
        SX.popError("System.out is broken (console output)!" + "\nYou will not see any messages anymore!"
                + "\nSave your work and restart the IDE!", "Fatal Error");
      }

      return exitCode;
    }
  }

  @Override
  protected void doRunLines(String lines, IRunner.Options options) {
    synchronized (JythonRunner.class) {
      if (jythonSupport == null) {
        jythonSupport = JythonSupport.get();
      }
      redirectToCurrentConsole();
      jythonSupport.executeScriptHeader(codeBefore);

      try {
        jythonSupport.interpreterExecString(lines);
      } catch (SystemExit se) {
        Debug.info("Exit code: " + se.code);
      } catch (Exception ex) {
        if (!isAborted()) {
          log(-1, "runPython: (%s) raised: %s", "\n" + lines, ex);
        }
      }
    }
  }

  private static boolean redirected = false;
  private static PrintStream customStdout = null;
  private static PrintStream customStderr = null;

  @Override
  protected boolean doRedirect(PrintStream stdout, PrintStream stderr) {
    synchronized (JythonRunner.class) {
      customStdout = stdout;
      customStderr = stderr;
      redirected = true;
      if (jythonSupport != null) {
        return jythonSupport.interpreterRedirect(stdout, stderr);
      }
      return true;
    }
  }

  private void redirectToCurrentConsole() {
    if (jythonSupport != null) {
      PrintStream out = customStdout != null ? customStdout : System.out;
      PrintStream err = customStderr != null ? customStderr : System.err;
      jythonSupport.interpreterRedirect(out, err);
    }
  }
}
