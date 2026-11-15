/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.support.runnerSupport;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.example.jython3.Jython3Interpreter;
import org.example.jython3.bridge.JavaBridge;
import org.example.jython3.runtime.InterpreterException;
import org.example.jython3.runtime.PyBoundMethod;
import org.example.jython3.runtime.PyBuiltinFunction;
import org.example.jython3.runtime.PyFunction;
import org.example.jython3.runtime.PyInstance;
import org.example.jython3.runtime.PyList;
import org.example.jython3.runtime.PyObject;
import org.example.jython3.runtime.PyStr;
import org.example.jython3.security.ExecutionLimits;
import org.example.jython3.security.HostAccessPolicy;
import org.sikuli.basics.Debug;
import org.sikuli.basics.Settings;
import org.sikuli.script.ImagePath;
import org.sikuli.support.Commons;
import org.sikuli.support.FileManager;
import org.sikuli.support.SikulixForJython;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JythonSupport implements IRunnerSupport {

  private static final String me = "Jython3: ";
  private static final int lvl = 3;

  public void log(int level, String message, Object... args) {
    Debug.logx(level, me + message, args);
  }

  private void logp(String message, Object... args) {
    Debug.logx(-3, message, args);
  }

  private void logp(int level, String message, Object... args) {
    if (level <= Debug.getDebugLevel()) {
      logp(message, args);
    }
  }

  private static JythonSupport instance = null;
  private static Jython3Interpreter interpreter = null;

  private JythonSupport() {
  }

  public static synchronized JythonSupport get() {
    if (null == instance) {
      instance = new JythonSupport();
      init();
    }
    return instance;
  }

  public static boolean isSupported() {
    try {
      Class.forName("org.example.jython3.Jython3Interpreter");
      return true;
    } catch (ClassNotFoundException ex) {
      Debug.log(-1, "no Jython3 on classpath --- consult the docs for a solution, if needed");
      return false;
    }
  }

  private static void ensureSikuliLibExtracted() {
    File libFolder = Commons.getLibFolder();
    if (!libFolder.exists()) {
      libFolder.mkdirs();
    }
    Commons.extractResourcesToFolder("/Lib", libFolder, null);
    String[] coreFiles = new String[]{
        "__init__.py", "Sikuli.py", "Region.py", "Screen.py", "Env.py", "Finder.py"
    };
    File sikuliDir = new File(libFolder, "sikuli");
    if (!sikuliDir.exists()) {
      sikuliDir.mkdirs();
    }
    for (String name : coreFiles) {
      String res = "/Lib/sikuli/" + name;
      try (InputStream is = JythonSupport.class.getResourceAsStream(res)) {
        if (is != null) {
          Files.copy(is, new File(sikuliDir, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
      } catch (IOException ex) {
        if (instance != null) {
          instance.log(-1, "failed extracting %s: %s", res, ex.getMessage());
        }
      }
    }
  }

  private static void init() {
    try {
      Class.forName("org.example.jython3.Jython3Interpreter");
    } catch (Exception ex) {
      Debug.log("Jython3: not found on classpath");
      return;
    }

    try {
      ensureSikuliLibExtracted();
      interpreter = new Jython3Interpreter(
          OculixJavaAccessPolicy.instance(),
          HostAccessPolicy.PERMISSIVE,
          ExecutionLimits.TRUSTED);
      Path libPath = Commons.getLibFolder().toPath();
      interpreter.insertModulePath(0, libPath);
      interpreter.addImportListener(importedFile -> {
        Path parent = importedFile.getParent();
        if (parent != null && parent.getFileName() != null
            && parent.getFileName().toString().endsWith(".sikuli")) {
          File bundleDir = parent.toFile();
          if (!instance.importedScripts.contains(bundleDir)) {
            instance.importedScripts.add(bundleDir);
          }
          ImagePath.appendFromImport(bundleDir.getAbsolutePath());
        }
      });
    } catch (Exception ex) {
      if (instance != null) {
        instance.log(-1, "Jython3 init problem: %s", ex.getMessage());
      }
      interpreter = null;
      return;
    }
    Commons.setJythonReady();
  }

  public static boolean isReady() {
    return interpreter != null && !interpreter.isClosed();
  }

  public Jython3Interpreter interpreterGet() {
    return interpreter;
  }

  public void cancel() {
    if (interpreter != null) {
      interpreter.cancel();
    }
  }

  public void interpreterCleanup() {
    // Jython3Interpreter resets frame state between eval/evalFile calls automatically.
  }

  public void interpreterClose() {
    if (null != interpreter) {
      interpreter.close();
      interpreter = null;
      instance = null;
    }
  }

  public boolean interpreterRedirect(PrintStream stdout, PrintStream stderr) {
    if (interpreter == null) {
      return false;
    }
    interpreter.setOut(stdout);
    interpreter.setErr(stderr);
    return true;
  }

  public Object interpreterEval(String expression) {
    if (interpreter == null || expression == null) {
      return "";
    }
    String trimmed = expression.trim();
    if (trimmed.isEmpty()) {
      return "";
    }
    String tempVar = "__oculix_eval_res__";
    interpreter.eval(tempVar + " = (" + trimmed + ")");
    PyObject val = interpreter.getGlobal(tempVar);
    return val != null ? val.toString() : "";
  }

  public boolean interpreterExecString(String script) {
    if (interpreter == null) {
      return false;
    }
    interpreter.eval(script);
    return true;
  }

  public void interpreterExecFile(String script) {
    if (interpreter == null) {
      return;
    }
    interpreter.evalFile(Path.of(script));
  }

  public void executeScriptHeader(List<String> codeBefore) {
    for (String line : SCRIPT_HEADER) {
      log(lvl + 1, "executeScriptHeader: %s", line);
      interpreterExecString(line);
    }
    if (codeBefore != null) {
      for (String line : codeBefore) {
        interpreterExecString(line);
      }
    }
  }

  private static final String[] SCRIPT_HEADER = new String[]{
      "import time; start = time.time()",
      "from sikuli import *",
      "try:\n  resetBeforeScriptStart()\nexcept Exception:\n  pass",
      "Debug.log(3, 'Jython3: BeforeScript: %s (%f)', SCREEN, time.time() - start)",
  };

  // sys.path handling
  final List<String> sysPath = new ArrayList<>();
  int nPathAdded = 0;
  int nPathSaved = -1;
  private final List<File> importedScripts = new ArrayList<>();
  private long lastRun = 0;

  public void getSysPath() {
    synchronized (sysPath) {
      if (null == interpreter) {
        return;
      }
      sysPath.clear();
      PyList pyPath = interpreter.sysPath();
      for (PyObject item : pyPath.items()) {
        sysPath.add(item instanceof PyStr s ? s.value() : item.toString());
      }
    }
  }

  public void setSysPath() {
    synchronized (sysPath) {
      if (null == interpreter) {
        return;
      }
      List<String> copy = new ArrayList<>(sysPath);
      List<Path> existing = new ArrayList<>(interpreter.moduleSearchPaths());
      for (Path p : existing) {
        interpreter.removeModulePath(p);
      }
      for (String entry : copy) {
        if (entry != null && !entry.isEmpty()) {
          interpreter.addModulePath(Path.of(entry));
        }
      }
    }
  }

  public void addSysPath(File fFolder) {
    if (fFolder != null) {
      addSysPath(fFolder.getAbsolutePath());
    }
  }

  public void addSysPath(String fpFolder) {
    if (fpFolder == null || fpFolder.isEmpty()) {
      return;
    }
    synchronized (sysPath) {
      if (!hasSysPath(fpFolder)) {
        interpreter.insertModulePath(0, Path.of(fpFolder));
        getSysPath();
        nPathAdded++;
      }
    }
  }

  public void appendSysPath(String fpFolder) {
    if (fpFolder == null || fpFolder.isEmpty()) {
      return;
    }
    synchronized (sysPath) {
      if (!hasSysPath(fpFolder)) {
        interpreter.addModulePath(Path.of(fpFolder));
        getSysPath();
        nPathAdded++;
      }
    }
  }

  public void putSysPath(String fpFolder, int n) {
    if (fpFolder == null || fpFolder.isEmpty()) {
      return;
    }
    synchronized (sysPath) {
      getSysPath();
      if (n < 0 || n > sysPath.size()) {
        appendSysPath(fpFolder);
      } else {
        interpreter.insertModulePath(n, Path.of(fpFolder));
        getSysPath();
        nPathAdded++;
      }
    }
  }

  public void insertSysPath(File fFolder) {
    if (fFolder == null) {
      return;
    }
    synchronized (sysPath) {
      int idx = nPathSaved > -1 ? nPathSaved : 0;
      interpreter.insertModulePath(idx, fFolder.toPath());
      getSysPath();
      nPathSaved = -1;
    }
  }

  public void removeSysPath(File fFolder) {
    if (fFolder == null) {
      return;
    }
    synchronized (sysPath) {
      int n = getSysPathEntry(fFolder);
      if (n > -1) {
        interpreter.removeModulePath(fFolder.toPath());
        getSysPath();
        nPathSaved = n;
        if (nPathAdded > 0) nPathAdded--;
      }
    }
  }

  public boolean hasSysPath(String fpFolder) {
    if (fpFolder == null) {
      return false;
    }
    synchronized (sysPath) {
      getSysPath();
      for (String fpPath : sysPath) {
        if (FileManager.pathEquals(fpPath, fpFolder)) {
          return true;
        }
      }
      return false;
    }
  }

  public int getSysPathEntry(File fFolder) {
    synchronized (sysPath) {
      getSysPath();
      int n = 0;
      for (String fpPath : sysPath) {
        if (FileManager.pathEquals(fpPath, fFolder.getAbsolutePath())) {
          return n;
        }
        n++;
      }
      return -1;
    }
  }

  public void showSysPath() {
    synchronized (sysPath) {
      if (Debug.is(lvl)) {
        getSysPath();
        log(lvl, "***** sys.path");
        for (int i = 0; i < sysPath.size(); i++) {
          logp(lvl, "%2d: %s", i, sysPath.get(i));
        }
      }
    }
  }

  public void addSitePackages() {
    synchronized (sysPath) {
      File fLibFolder = Commons.getLibFolder();
      if (fLibFolder.exists()) {
        addSysPath(fLibFolder);
      }
      File fSitePackages = new File(fLibFolder, "site-packages");
      if (fSitePackages.exists()) {
        addSysPath(fSitePackages);
        if (hasSysPath(fSitePackages.getAbsolutePath())) {
          log(lvl, "added as Jython3::sys.path[0]:\n%s", fSitePackages);
        }
        File fSites = new File(fSitePackages, "sites.txt");
        if (fSites.exists()) {
          String sSites = FileManager.readFileToString(fSites);
          if (!sSites.isEmpty()) {
            log(lvl, "found Lib/site-packages/sites.txt");
            for (String site : sSites.split("\n")) {
              String path = site.trim();
              if (path.startsWith("#") || path.isEmpty()) {
                continue;
              }
              addSysPath(path);
              log(lvl, "added as Jython3::sys.path[0] from sites.txt:\n%s", path);
            }
          }
        }
      }
      String fpBundle = ImagePath.getBundlePath();
      if (fpBundle != null) {
        addSysPath(fpBundle);
      }
    }
  }

  // import handling
  public void reloadImported() {
    if (lastRun > 0 && interpreter != null) {
      for (File fMod : importedScripts) {
        String modName = getPyName(fMod);
        if (new File(fMod, modName + ".py").lastModified() > lastRun) {
          log(lvl, "reload: %s", fMod);
          try {
            interpreter.reloadModule(modName);
          } catch (Exception ex) {
            log(-1, "reload failed for %s: %s", modName, ex.getMessage());
          }
        }
      }
    }
    lastRun = new Date().getTime();
  }

  private String getPyName(File fMod) {
    String ending = ".sikuli";
    String name = fMod.getName();
    if (name.endsWith(ending)) {
      name = name.substring(0, name.length() - ending.length());
    }
    return name;
  }

  public String findModule(String modName, Object packPath, Object sysPathObj) {
    if (modName == null || modName.endsWith(".*") || packPath != null) {
      return null;
    }
    int nDot = modName.lastIndexOf('.');
    if (nDot > -1) {
      modName = modName.substring(nDot + 1);
    }
    File fModule = null;
    if (ImagePath.getBundlePath() != null) {
      File fParentBundle = new File(ImagePath.getBundlePath()).getParentFile();
      fModule = existsModule(modName, fParentBundle);
    }
    if (fModule == null) {
      fModule = existsSysPathModule(modName);
    }
    if (fModule != null && fModule.getName().endsWith(".sikuli")) {
      if (!importedScripts.contains(fModule)) {
        importedScripts.add(fModule);
      }
      return fModule.getAbsolutePath();
    }
    return null;
  }

  public String loadModulePrepare(String modName, String modPath) {
    log(lvl + 1, "loadModulePrepare: %s in %s", modName, modPath);
    int nDot = modName.lastIndexOf('.');
    if (nDot > -1) {
      modName = modName.substring(nDot + 1);
    }
    addSysPath(modPath);
    if (modPath.endsWith(".sikuli")) {
      ImagePath.appendFromImport(modPath);
    }
    return modName;
  }

  private File existsModule(String mName, File fFolder) {
    if (fFolder == null || mName.endsWith(".sikuli") || mName.endsWith(".py")) {
      return null;
    }
    File fSikuli = new File(fFolder, mName + ".sikuli");
    if (fSikuli.exists()) {
      return fSikuli;
    }
    File fPython = new File(fFolder, mName + ".py");
    if (fPython.exists()) {
      return fPython;
    }
    return null;
  }

  public File existsSysPathModule(String modname) {
    synchronized (sysPath) {
      getSysPath();
      for (String fpPath : sysPath) {
        File fModule = existsModule(modname, new File(fpPath));
        if (null != fModule) {
          return fModule;
        }
      }
      return null;
    }
  }

  public File existsSysPathJar(String fpJar) {
    synchronized (sysPath) {
      getSysPath();
      for (String fpPath : sysPath) {
        File fJar = new File(fpPath, fpJar);
        if (fJar.exists()) {
          return fJar;
        }
      }
      return null;
    }
  }

  // sys.argv handling
  public void interpreterFillSysArgv(File pyFile, String[] argv) {
    List<String> jyargv = new ArrayList<>();
    jyargv.add(pyFile.getAbsolutePath());
    if (argv != null) {
      jyargv.addAll(Arrays.asList(argv));
    }
    setSysArgv(jyargv);
  }

  public List<String> getSysArgv() {
    if (null == interpreter) {
      return null;
    }
    List<String> result = new ArrayList<>();
    Object sysObj = interpreter.sysModules().get(new PyStr("sys"));
    if (sysObj instanceof org.example.jython3.runtime.PyModule sysMod
        && sysMod.attrs().get("argv") instanceof PyList pyArgv) {
      for (PyObject item : pyArgv.items()) {
        result.add(item instanceof PyStr s ? s.value() : item.toString());
      }
    }
    return result;
  }

  public void setSysArgv(List<String> args) {
    if (null != interpreter) {
      interpreter.setArgv(args);
    }
  }

  // RobotFramework support
  public boolean prepareRobot() {
    if (Commons.isRunningFromJar()) {
      final File libFolder = Commons.getLibFolder();
      File fLibRobot = new File(libFolder, "robot");
      if (!fLibRobot.exists()) {
        log(-1, "prepareRobot: not available: %s", fLibRobot);
        return false;
      }
      if (!hasSysPath(libFolder.getAbsolutePath())) {
        insertSysPath(libFolder);
      }
    }
    if (Settings.BundlePath != null) {
      String parent = new File(Settings.BundlePath).getParent();
      if (parent != null && !hasSysPath(parent)) {
        appendSysPath(parent);
      }
    }
    interpreterExecString("import robot");
    return true;
  }

  // error/exception handling
  public String getCurrentLine() {
    return "Jython3: runtime active";
  }

  private int errorLine;

  public int findErrorSource(Throwable throwable, String filename) {
    log(lvl + 1, "Run Script Error: %s", throwable);
    errorLine = -1;
    String shortName = new File(filename).getName().replace(".py", "");

    if (throwable instanceof InterpreterException ie) {
      if ("KeyboardInterrupt".equals(ie.typeName())) {
        log(lvl, "script [ %s ] aborted by user", shortName);
        return -1;
      }
      errorLine = ie.line();
      if (errorLine <= 0 && !ie.traceback().isEmpty()) {
        for (InterpreterException.TraceEntry entry : ie.traceback()) {
          if (entry.line() > 0 && (entry.file() == null || entry.file().equals(filename))) {
            errorLine = entry.line();
            break;
          }
        }
      }
      String msg = String.format("script [ %s ] stopped with error in line %s",
          shortName, errorLine > 0 ? String.valueOf(errorLine) : "--unknown--");
      Debug.error(msg);

      String source = "";
      try {
        File f = new File(filename);
        if (f.isFile()) {
          source = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        }
      } catch (IOException ignored) {
      }
      String tb = interpreter != null
          ? interpreter.formatTraceback(ie, filename, source)
          : ie.getMessage();
      Debug.error("%s", tb);
      return errorLine;
    }

    String err = throwable.toString();
    Matcher m = Pattern.compile("at (\\d+):(\\d+)").matcher(err);
    if (m.find()) {
      errorLine = Integer.parseInt(m.group(1));
    }
    String msg = String.format("script [ %s ] stopped with error at line %s",
        shortName, errorLine > 0 ? String.valueOf(errorLine) : "--unknown--");
    Debug.error(msg);
    Debug.error("Error caused by: %s", err);
    return errorLine;
  }

  // load / runJar
  public int runJar(String fpJarOrFolder) {
    return runJar(fpJarOrFolder, "");
  }

  public int runJar(String fpJarOrFolder, String imagePath) {
    SikulixForJython.get();
    String fpJar = load(fpJarOrFolder, true);
    ImagePath.addJar(fpJar, imagePath);
    String scriptName = new File(fpJar).getName().replace("_sikuli.jar", "");
    try {
      interpreterExecString("import importlib\ntry:\n  importlib.reload(" + scriptName + ")\nexcept Exception:\n  import " + scriptName);
      return 0;
    } catch (Exception ex) {
      return -1;
    }
  }

  public String load(String fpJarOrFolder) {
    return load(fpJarOrFolder, false);
  }

  public String load(String fpJar, boolean scriptOnly) {
    log(lvl, "load: %s", fpJar);
    if (!fpJar.endsWith(".jar")) {
      fpJar += ".jar";
    }
    File fJar = new File(FileManager.normalizeAbsolute(fpJar));
    if (!fJar.exists()) {
      String fpBundle = ImagePath.getBundlePath();
      fJar = null;
      if (null != fpBundle) {
        fJar = new File(fpBundle, fpJar);
        if (!fJar.exists()) {
          fJar = new File(new File(fpBundle).getParentFile(), fpJar);
          if (!fJar.exists()) {
            fJar = null;
          }
        }
      }
      if (fJar == null) {
        fJar = new File(Commons.getExtensionsFolder(), fpJar);
        if (!fJar.exists()) {
          fJar = new File(Commons.getLibFolder(), fpJar);
          if (!fJar.exists()) {
            fJar = null;
          }
        }
      }
    }
    if (fJar == null) {
      log(-1, "load: not found: %s", fpJar);
      return fpJar;
    } else {
      if (!hasSysPath(fJar.getPath())) {
        insertSysPath(fJar);
      }
      return fJar.getAbsolutePath();
    }
  }

  // Callbacks from API (Debug logger, Region observers, FindFailed handlers)
  public boolean checkCallback(Object[] args) {
    if (args == null || args.length < 2 || !(args[0] instanceof PyInstance inst)) {
      return false;
    }
    String mName = (String) args[1];
    return inst.hasField(mName);
  }

  public boolean runLoggerCallback(Object[] args) {
    if (interpreter == null || args == null || args.length < 3 || !(args[0] instanceof PyInstance inst)) {
      return false;
    }
    String mName = (String) args[1];
    String msg = (String) args[2];
    try {
      PyObject method = inst.getField(mName);
      interpreter.invokeHostCallback(method, new PyStr(msg));
      return true;
    } catch (Exception ex) {
      log(-100, "runLoggerCallback: invoke: %s", ex.getMessage());
      return false;
    }
  }

  @Override
  public boolean runObserveCallback(Object[] args) {
    if (interpreter == null || args == null || args.length < 2) {
      return false;
    }
    Object rawCb = args[0];
    if (!(rawCb instanceof PyFunction || rawCb instanceof PyBuiltinFunction || rawCb instanceof PyBoundMethod)) {
      log(-1, "runObserveCallback: unsupported callback type: %s", rawCb == null ? "null" : rawCb.getClass().getName());
      return false;
    }
    PyObject callable = (PyObject) rawCb;
    PyObject pyEvent = JavaBridge.toPyObject(args[1], OculixJavaAccessPolicy.instance());
    try {
      interpreter.invokeHostCallback(callable, pyEvent);
      return true;
    } catch (InterpreterException ex) {
      // Zero-arg lambda / callback fallback
      if ("TypeError".equals(ex.typeName()) && ex.getMessage() != null && ex.getMessage().contains("argument")) {
        try {
          interpreter.invokeHostCallback(callable);
          return true;
        } catch (Exception ex2) {
          log(-1, "runObserveCallback: jython3 invoke 0-arg: %s", ex2.getMessage());
          return false;
        }
      }
      log(-1, "runObserveCallback: jython3 invoke: %s", ex.getMessage());
      return false;
    } catch (Exception ex) {
      log(-1, "runObserveCallback: jython3 invoke: %s", ex.getMessage());
      return false;
    }
  }

  public boolean runCallback(Object[] args) {
    if (interpreter == null || args == null || args.length < 2 || !(args[0] instanceof PyInstance inst)) {
      return false;
    }
    String mName = (String) args[1];
    try {
      PyObject method = inst.getField(mName);
      interpreter.invokeHostCallback(method, new PyStr("not yet supported"));
      return true;
    } catch (Exception ex) {
      log(-1, "runCallback: invoke: %s", ex.getMessage());
      return false;
    }
  }

  /**
   * Validates and copies a folder of Python 3 scripts to a target folder.
   */
  public static boolean compileJythonFolder(Object[] parms) {
    if (parms == null || parms.length < 2) {
      return false;
    }
    String fpSource = parms[0].toString();
    String fpTarget = parms[1].toString();
    JythonSupport jython = JythonSupport.get();
    if (jython != null && jython.interpreterGet() != null) {
      File fTarget = new File(fpTarget);
      FileManager.deleteFileOrFolder(fTarget);
      fTarget.mkdirs();
      if (!fTarget.exists()) {
        Debug.log(-1, "compileJythonFolder: target folder not available\n%s", fTarget);
        return false;
      }
      File fSource = new File(fpSource);
      if (!fSource.exists()) {
        Debug.log(-1, "compileJythonFolder: source folder not available\n%s", fSource);
        return false;
      }
      if (fTarget.equals(fSource)) {
        Debug.log(-1, "compileJythonFolder: target folder cannot be the same as the source folder");
        return false;
      }
      FileManager.xcopy(fSource, fTarget);
      try {
        validatePythonFilesRecursive(jython.interpreterGet(), fTarget);
        return true;
      } catch (Exception ex) {
        Debug.log(-1, "compileJythonFolder: compile check failed: %s", ex.getMessage());
        return false;
      }
    }
    return false;
  }

  private static void validatePythonFilesRecursive(Jython3Interpreter interp, File dir) throws IOException {
    File[] entries = dir.listFiles();
    if (entries == null) return;
    for (File f : entries) {
      if (f.isDirectory()) {
        validatePythonFilesRecursive(interp, f);
      } else if (f.getName().endsWith(".py")) {
        String src = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
        interp.compileToCode(src);
      }
    }
  }
}
