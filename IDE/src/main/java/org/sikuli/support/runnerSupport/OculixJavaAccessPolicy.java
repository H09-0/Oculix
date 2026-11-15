/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.support.runnerSupport;

import org.example.jython3.security.AllowlistPolicy;
import org.example.jython3.security.JavaAccessPolicy;

import java.util.Set;

/**
 * Java access policy for trusted OculiX automation scripts running on Jython 3.
 * Grants access to OculiX automation packages ({@code org.sikuli.*}, {@code com.sikulix.*},
 * {@code jxgrabkey.*}, {@code org.oculix.*}) and safe AWT geometry/event types while
 * preserving the JVM reflection/process/unsafe guardrails of {@link AllowlistPolicy}.
 */
public final class OculixJavaAccessPolicy implements JavaAccessPolicy {

  private static final OculixJavaAccessPolicy INSTANCE = new OculixJavaAccessPolicy();

  private static final Set<String> OCULIX_ALLOWED_AWT_CLASSES = Set.of(
      "java.awt.Color",
      "java.awt.Dimension",
      "java.awt.Point",
      "java.awt.Rectangle",
      "java.awt.Toolkit",
      "java.awt.event.InputEvent",
      "java.awt.event.KeyEvent",
      "java.awt.event.MouseEvent",
      "java.awt.image.BufferedImage"
  );

  private OculixJavaAccessPolicy() {}

  public static OculixJavaAccessPolicy instance() {
    return INSTANCE;
  }

  private static boolean isOculixClass(String binaryName) {
    return binaryName.startsWith("org.sikuli.")
        || binaryName.startsWith("com.sikulix.")
        || binaryName.startsWith("jxgrabkey.")
        || binaryName.startsWith("org.oculix.")
        || OCULIX_ALLOWED_AWT_CLASSES.contains(binaryName);
  }

  @Override
  public boolean canAccessClass(String binaryName) {
    if (binaryName == null || binaryName.isEmpty()) {
      return false;
    }
    if (isOculixClass(binaryName)) {
      return true;
    }
    return AllowlistPolicy.trusted().canAccessClass(binaryName);
  }

  @Override
  public boolean canConstructClass(String binaryName, String descriptor) {
    if (isOculixClass(binaryName)) {
      return true;
    }
    return AllowlistPolicy.trusted().canConstructClass(binaryName, descriptor);
  }

  @Override
  public boolean canCallMethod(String owner, String methodName, String descriptor) {
    if (isOculixClass(owner)) {
      return true;
    }
    return AllowlistPolicy.trusted().canCallMethod(owner, methodName, descriptor);
  }

  @Override
  public boolean canReadField(String owner, String fieldName) {
    if (isOculixClass(owner)) {
      return true;
    }
    return AllowlistPolicy.trusted().canReadField(owner, fieldName);
  }

  @Override
  public boolean canWriteField(String owner, String fieldName) {
    if (isOculixClass(owner)) {
      return !"class".equals(fieldName);
    }
    return AllowlistPolicy.trusted().canWriteField(owner, fieldName);
  }
}
