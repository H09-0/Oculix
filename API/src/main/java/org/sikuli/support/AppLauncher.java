/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.support;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Cross-platform application launcher for local and remote (VNC via SSH) apps.
 * Handles X Server lifecycle, DISPLAY resolution, and SSH+VNC command building.
 */
public class AppLauncher {

  private static final String VCXSRV_PROCESS = "vcxsrv.exe";
  private static final String VCXSRV_PATH = "C:\\Program Files\\VcXsrv\\vcxsrv.exe";
  private static final int XSERVER_STARTUP_DELAY = 5000;
  private static final int LAUNCH_TIMEOUT_SECONDS = 120;

  private static final String DEFAULT_SSH_OPTIONS =
      "-Y -C -o Ciphers=aes128-ctr -o MACs=hmac-sha2-256,hmac-sha1 " +
      "-o TCPKeepAlive=no -o ServerAliveInterval=30 -o ServerAliveCountMax=3";

  // ═══════════════════════════════════════════════════════════════════════
  // PUBLIC API
  // ═══════════════════════════════════════════════════════════════════════

  /**
   * Launch a local application.
   */
  public static void launchLocal(String appPath) throws IOException {
    if (Commons.runningWindows()) {
      CommandExecutor.execFireAndForget(new String[]{"cmd", "/c", "start", "", appPath});
    } else if (Commons.runningMac()) {
      CommandExecutor.execFireAndForget(new String[]{"open", "-a", appPath});
    } else {
      CommandExecutor.execFireAndForget(appPath);
    }
  }

  /**
   * Launch a remote app via VNC over SSH with X11 forwarding: ssh runs vncviewer on the
   * target against the target's own VNC server, and the viewer window comes back over X11.
   * The SSH password follows {@link RemoteMode}: environment variable by default, command
   * line only in YOLO mode. Returns once the session has survived its first 5 seconds.
   *
   * @throws RuntimeException with a readable reason if ssh exits within those 5 seconds
   */
  public static void launchRemoteVNC(String host, String user, String password,
                                      String vncPort) throws Exception {
    ensureXServerRunning();

    RemoteMode mode = RemoteMode.fromEnvOrDefault();
    org.sikuli.basics.Debug.log(3, "AppLauncher: remote launch to %s in %s mode", host, mode);

    String displayIp = resolveDisplayIp();
    ProcessBuilder builder = new VncCommandBuilder()
        .displayIp(displayIp)
        .credentials(user, password)
        .targetIp(host)
        .vncPort(vncPort != null ? vncPort : "5900")
        .sshOptions(DEFAULT_SSH_OPTIONS)
        .remoteMode(mode)
        .build();
    builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

    Process process = builder.start();

    StringBuilder errorOutput = new StringBuilder();
    Thread errorReader = new Thread(() -> {
      try (java.io.BufferedReader err = new java.io.BufferedReader(
          new java.io.InputStreamReader(process.getErrorStream(), "UTF-8"))) {
        String line;
        while ((line = err.readLine()) != null) {
          synchronized (errorOutput) {
            errorOutput.append(line).append("\n");
          }
        }
      } catch (Exception ignored) {}
    }, "VNC-ErrorReader");
    errorReader.setDaemon(true);
    errorReader.start();

    if (process.waitFor(5, TimeUnit.SECONDS)) {
      errorReader.join(1000);
      String errors;
      synchronized (errorOutput) {
        errors = errorOutput.toString().trim();
      }
      throw new RuntimeException(errors.isEmpty()
          ? "SSH/VNC session ended at once (exit code " + process.exitValue() + ")"
          : parseSSHError(errors));
    }

    String warnings;
    synchronized (errorOutput) {
      warnings = errorOutput.toString().trim();
    }
    if (!warnings.isEmpty()) {
      org.sikuli.basics.Debug.log(3, "AppLauncher: ssh is running, it said: %s", warnings);
    }
  }

  private static String parseSSHError(String stderr) {
    String lower = stderr.toLowerCase();
    if (lower.contains("permission denied")) return "SSH authentication failed — check password.";
    if (lower.contains("connection refused")) return "Connection refused — check host IP and SSH service.";
    if (lower.contains("connection timed out")) return "Connection timed out — host unreachable.";
    if (lower.contains("no route to host")) return "No route to host — check network and IP.";
    if (lower.contains("host key verification failed")) return "SSH host key verification failed — add fingerprint first.";
    if (lower.contains("name or service not known")) return "Unknown host — check IP/hostname.";
    if (lower.contains("unable to open display")) return "Cannot open display — check X Server (VcXsrv/XQuartz).";
    if (lower.contains("unable connect to socket")) return "VNC connection failed — check VNC port and target.";
    return "SSH/VNC error: " + stderr;
  }

  /**
   * Launch remote VNC with default port 5900.
   */
  public static void launchRemoteVNC(String host, String user, String password) throws Exception {
    launchRemoteVNC(host, user, password, "5900");
  }

  // ═══════════════════════════════════════════════════════════════════════
  // X SERVER
  // ═══════════════════════════════════════════════════════════════════════

  static void ensureXServerRunning() throws IOException {
    if (Commons.runningLinux()) return;

    if (Commons.runningWindows()) {
      if (!CommandExecutor.isProcessRunning(VCXSRV_PROCESS)) {
        CommandExecutor.execFireAndForget(
            String.format("\"%s\" -ac -multiwindow -clipboard -wgl", VCXSRV_PATH));
        sleep(XSERVER_STARTUP_DELAY);
      }
    } else if (Commons.runningMac()) {
      if (!CommandExecutor.isProcessRunning("XQuartz")) {
        CommandExecutor.execFireAndForget(new String[]{"open", "-a", "XQuartz"});
        sleep(XSERVER_STARTUP_DELAY);
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════
  // DISPLAY IP RESOLUTION
  // ═══════════════════════════════════════════════════════════════════════

  static String resolveDisplayIp() throws IOException {
    if (!Commons.runningWindows()) return "";

    // Try 1: WSL adapter in ipconfig (classic NAT mode)
    Optional<String> wslIp = getWslIpFromIpconfig();
    if (wslIp.isPresent()) return wslIp.get();

    // virtioproxy / mirrored: DISPLAY=:0 (no IP needed)
    return "";
  }

  private static Optional<String> getWslIpFromIpconfig() throws IOException {
    Pattern ipPattern = Pattern.compile("IPv4.*?:\\s*(\\d+\\.\\d+\\.\\d+\\.\\d+)");
    boolean inWslSection = false;

    try (Stream<String> lines = CommandExecutor.execLocal("ipconfig")) {
      for (String line : (Iterable<String>) lines::iterator) {
        if (line.contains("WSL")) inWslSection = true;
        if (inWslSection) {
          Matcher m = ipPattern.matcher(line);
          if (m.find()) return Optional.of(m.group(1));
        }
      }
    }
    return Optional.empty();
  }

  private static Optional<String> getWslIpFromResolvConf() {
    try (Stream<String> lines = CommandExecutor.execWsl("cat /etc/resolv.conf")) {
      Pattern nsPattern = Pattern.compile("nameserver\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)");
      for (String line : (Iterable<String>) lines::iterator) {
        Matcher m = nsPattern.matcher(line);
        if (m.find()) return Optional.of(m.group(1));
      }
    } catch (Exception ignored) {
    }
    return Optional.empty();
  }

  // ═══════════════════════════════════════════════════════════════════════
  // VNC WINDOW DETECTION WITH TIMEOUT
  // ═══════════════════════════════════════════════════════════════════════

  private static void waitForVncWindow() throws TimeoutException {
    ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "VncWindowWait");
      t.setDaemon(true);
      return t;
    });

    Future<?> future = executor.submit(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        if (CommandExecutor.isProcessRunning("vncviewer")) return;
        sleep(1000);
      }
    });

    try {
      future.get(LAUNCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new TimeoutException(
          "VNC window did not appear within " + LAUNCH_TIMEOUT_SECONDS + " seconds. "
          + "Check SSH credentials, network, and X Server.");
    } catch (Exception e) {
      throw new RuntimeException("VNC launch failed: " + e.getMessage(), e);
    } finally {
      executor.shutdownNow();
    }
  }

  // ═══════════════════════════════════════════════════════════════════════
  // VNC COMMAND BUILDER
  // ═══════════════════════════════════════════════════════════════════════

  public static class VncCommandBuilder {
    private String displayIp;
    private String sshUser;
    private String sshPassword;
    private String targetIp;
    private String vncPort = "5900";
    private String sshOptions = "";
    private RemoteMode remoteMode = RemoteMode.CONFIDENTIAL;

    public VncCommandBuilder displayIp(String ip) { this.displayIp = ip; return this; }
    public VncCommandBuilder credentials(String user, String pass) {
      this.sshUser = user; this.sshPassword = pass; return this;
    }
    public VncCommandBuilder targetIp(String ip) { this.targetIp = ip; return this; }
    public VncCommandBuilder vncPort(String port) { this.vncPort = port; return this; }
    public VncCommandBuilder sshOptions(String opts) { this.sshOptions = opts; return this; }
    public VncCommandBuilder remoteMode(RemoteMode mode) { this.remoteMode = mode; return this; }

    /**
     * Builds the ssh launch as an argument list, never as a string a shell would have to
     * re-parse. CONFIDENTIAL: password in the SSHPASS variable ({@code sshpass -e}),
     * unknown host keys refused. YOLO: password on the command line, unknown host keys
     * accepted. On Windows the command runs in WSL through {@code wsl.exe --exec}, which
     * also receives SSHPASS and DISPLAY through WSLENV.
     */
    public ProcessBuilder build() {
      String password = sshPassword == null ? "" : sshPassword;
      boolean yolo = remoteMode.isYolo();

      List<String> ssh = new ArrayList<>();
      ssh.add("sshpass");
      if (yolo) {
        ssh.add("-p");
        ssh.add(password);
      } else {
        ssh.add("-e");
      }
      ssh.add("ssh");
      if (sshOptions != null && !sshOptions.isBlank()) {
        ssh.addAll(Arrays.asList(sshOptions.trim().split("\\s+")));
      }
      ssh.add("-o");
      ssh.add(yolo ? "StrictHostKeyChecking=no" : "StrictHostKeyChecking=yes");
      ssh.add(sshUser + "@" + targetIp);
      ssh.add("vncviewer -FullColor localhost:" + vncPort);

      List<String> command = new ArrayList<>();
      if (Commons.runningWindows()) {
        command.add("C:\\Windows\\System32\\wsl.exe");
        command.add("--exec");
      }
      command.addAll(ssh);

      ProcessBuilder builder = new ProcessBuilder(command);
      Map<String, String> env = builder.environment();
      List<String> shared = new ArrayList<>();
      if (!yolo) {
        env.put("SSHPASS", password);
        shared.add("SSHPASS/u");
      }
      if (Commons.runningWindows()) {
        if (env.get("DISPLAY") == null) {
          env.put("DISPLAY", (displayIp == null ? "" : displayIp) + ":0");
        }
        shared.add("DISPLAY/u");
        String wslenv = env.get("WSLENV");
        String added = String.join(":", shared);
        env.put("WSLENV", wslenv == null || wslenv.isBlank() ? added : wslenv + ":" + added);
      }
      return builder;
    }
  }

  // ═══════════════════════════════════════════════════════════════════════
  // UTIL
  // ═══════════════════════════════════════════════════════════════════════

  private static void sleep(int millis) {
    try { Thread.sleep(millis); } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
