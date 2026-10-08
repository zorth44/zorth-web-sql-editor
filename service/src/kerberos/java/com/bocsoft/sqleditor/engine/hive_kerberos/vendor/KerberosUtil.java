package com.bocsoft.sqleditor.engine.hive_kerberos.vendor;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.apache.hadoop.security.authentication.util.KerberosName;

public final class KerberosUtil {
    private static final String JAVA_VENDOR = "java.vendor";
    private static final String IBM_FLAG = "IBM";
    private static final String CONFIG_CLASS_FOR_IBM = "com.ibm.security.krb5.internal.Config";
    private static final String CONFIG_CLASS_FOR_SUN = "sun.security.krb5.Config";
    private static final String METHOD_GET_INSTANCE = "getInstance";
    private static final String METHOD_GET_DEFAULT_REALM = "getDefaultRealm";
    private static final String METHOD_REFRESH = "refresh";
    private static final String DEFAULT_REALM = "HADOOP.COM";

    private KerberosUtil() { }

    public static String getKrb5DomainRealm() {
        String peerRealm;
        try {
            Class<?> krb5ConfClass = configClass();
            Method getInstanceMethod = krb5ConfClass.getMethod(METHOD_GET_INSTANCE);
            Object kerbConf = getInstanceMethod.invoke(krb5ConfClass);
            Method getDefaultRealmMethod = krb5ConfClass.getDeclaredMethod(METHOD_GET_DEFAULT_REALM);
            Object realm = getDefaultRealmMethod.invoke(kerbConf);
            peerRealm = realm instanceof String ? (String) realm : DEFAULT_REALM;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            peerRealm = DEFAULT_REALM;
        }
        return peerRealm;
    }

    public static void setDefaultRealm(String serverRealm) {
        try {
            Field defaultRealm = KerberosName.class.getDeclaredField("defaultRealm");
            defaultRealm.setAccessible(true);
            String value = (String) defaultRealm.get(null);
            if (value != null && !value.trim().isEmpty()) {
                defaultRealm.set(null, serverRealm);
            }
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            // Keep the existing default realm when the field cannot be updated.
        }
    }

    public static void refreshKerberosConfig() {
        try {
            Class<?> krb5ConfClass = configClass();
            Method refreshMethod = krb5ConfClass.getDeclaredMethod(METHOD_REFRESH);
            refreshMethod.setAccessible(true);
            refreshMethod.invoke(null);
        } catch (Exception ignored) {
            // Refreshing is best-effort; a later login will surface a real configuration error.
        }
    }

    private static Class<?> configClass() throws ClassNotFoundException {
        String vendor = System.getProperty(JAVA_VENDOR);
        if (vendor != null && vendor.contains(IBM_FLAG)) return Class.forName(CONFIG_CLASS_FOR_IBM);
        return Class.forName(CONFIG_CLASS_FOR_SUN);
    }
}
