/*
 * Copyright (c) 2010-2020, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.support.ide;

import org.sikuli.basics.PreferencesUser;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.text.MessageFormat;
import java.util.*;
import org.sikuli.basics.Debug;

public class SikuliIDEI18N {
   static ResourceBundle i18nRB = null;
   static ResourceBundle i18nRB_en;
   static Locale curLocale;

   static {
      Locale locale_en = new Locale("en","US");
      try {
         i18nRB_en = ResourceBundle.getBundle("i18n/IDE", locale_en);
      } catch (Exception e) {
         Debug.error("SikuliIDEI18N: failed to load default en_US bundle: " + e.getMessage());
      }
      Locale locale = PreferencesUser.get().getLocale();
      if (!setLocale(locale)) {
         locale = locale_en;
         setLocale(locale);
         PreferencesUser.get().setLocale(locale);
      }
   }

   public static boolean setLocale(Locale locale){
      if (locale == null) return false;
      try{
         i18nRB = ResourceBundle.getBundle("i18n/IDE", locale);
         curLocale = locale;
      }
      catch(MissingResourceException e){
         Debug.error("SikuliIDEI18N: no locale for " + locale);
         return false;
      }
      return true;
   }

   public static Locale getLocale() {
      return curLocale != null ? curLocale : Locale.getDefault();
   }

   public static boolean isRTL() {
      return isRTL(curLocale);
   }

   public static boolean isRTL(Locale locale) {
      if (locale == null) return false;
      String lang = locale.getLanguage().toLowerCase(Locale.ROOT);
      return "ar".equals(lang) || "he".equals(lang) || "fa".equals(lang) || "ur".equals(lang);
   }

   public static ComponentOrientation getComponentOrientation() {
      return isRTL() ? ComponentOrientation.RIGHT_TO_LEFT : ComponentOrientation.LEFT_TO_RIGHT;
   }

   public static void applyOrientation(Component component) {
      if (component != null) {
         component.applyComponentOrientation(getComponentOrientation());
      }
   }

   public static String getLocaleShow() {
     String ret = curLocale != null ? curLocale.toString() : "unknown";
     if (i18nRB == null) ret += " (using en_US)";
     return ret;
   }

   public static String _I(String key, Object... args){
      String ret = null;
      if (i18nRB != null) {
         try {
            ret = i18nRB.getString(key);
         } catch (MissingResourceException e) {
            // fallback to English
         }
      }
      if (ret == null && i18nRB_en != null) {
         try {
            ret = i18nRB_en.getString(key);
         } catch (MissingResourceException e) {
            ret = key;
         }
      }
      if (ret == null) {
         ret = key;
      }

      if (args != null && args.length > 0) {
         try {
            MessageFormat formatter = new MessageFormat("");
            formatter.setLocale(curLocale != null ? curLocale : Locale.getDefault());
            formatter.applyPattern(ret);
            ret = formatter.format(args);
         } catch (Exception ex) {
            // If message formatting fails, return raw string rather than crashing
         }
      }
      return ret;
   }

}
