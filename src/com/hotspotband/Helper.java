package com.hotspotband;

import android.net.wifi.SoftApConfiguration;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Runs as the shell user (through Shizuku or adb) via app_process, with the APK as class path.
 *
 * Usage: Helper status | 5g [channel] | 2g | off
 *   5g/2g stop the hotspot if it is running, apply the band, then start it again.
 */
public final class Helper {
    private static final String CALLER = "com.android.shell";
    private static final int BAND_2GHZ = 1;
    private static final int BAND_5GHZ = 2;
    private static final int TETHERING_WIFI = 0;
    private static final int AP_STATE_DISABLED = 11;
    private static final int AP_STATE_ENABLED = 13;
    private static final int AP_STATE_FAILED = 14;

    private static Object wifi;

    public static void main(String[] args) {
        try {
            wifi = asInterface("android.net.wifi.IWifiManager", getService("wifi"));
            String command = args.length > 0 ? args[0] : "status";
            switch (command) {
                case "status":
                    printStatus();
                    break;
                case "5g":
                    int channel = args.length > 1 ? Integer.parseInt(args[1]) : 149;
                    switchBand(BAND_5GHZ, channel);
                    break;
                case "2g":
                    switchBand(BAND_2GHZ, 0);
                    break;
                case "off":
                    stopHotspot();
                    printStatus();
                    break;
                default:
                    System.out.println("ERROR=unknown command " + command);
            }
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            System.out.println("ERROR=" + cause);
            cause.printStackTrace(System.out);
        }
        System.exit(0);
    }

    private static void switchBand(int band, int channel) throws Exception {
        if (apState() != AP_STATE_DISABLED) {
            stopHotspot();
        }
        SoftApConfiguration current = (SoftApConfiguration) call(wifi, "getSoftApConfiguration");
        Class<?> builderClass = Class.forName("android.net.wifi.SoftApConfiguration$Builder");
        Object builder = builderClass.getConstructor(SoftApConfiguration.class).newInstance(current);
        if (channel != 0) {
            builder = builderClass.getMethod("setChannel", int.class, int.class).invoke(builder, channel, band);
        } else {
            builder = builderClass.getMethod("setBand", int.class).invoke(builder, band);
        }
        try {
            builder = builderClass.getMethod("setBandOptimizationEnabled", boolean.class).invoke(builder, false);
        } catch (NoSuchMethodException ignored) {
            // Only present on some builds.
        }
        SoftApConfiguration updated = (SoftApConfiguration) builderClass.getMethod("build").invoke(builder);
        Object saved = call(wifi, "setSoftApConfiguration", updated);
        System.out.println("CONFIG_SAVED=" + saved);
        startHotspot();
        printStatus();
    }

    private static void stopHotspot() throws Exception {
        Object tethering = asInterface("android.net.ITetheringConnector", getService("tethering"));
        call(tethering, "stopTethering", TETHERING_WIFI);
        waitForState(AP_STATE_DISABLED, 10000);
    }

    private static void startHotspot() throws Exception {
        Object tethering = asInterface("android.net.ITetheringConnector", getService("tethering"));
        call(tethering, "startTethering", TETHERING_WIFI);
        waitForState(AP_STATE_ENABLED, 15000);
    }

    private static void waitForState(int wanted, long timeoutMillis) throws Exception {
        long end = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < end) {
            int state = apState();
            if (state == wanted || (wanted == AP_STATE_ENABLED && state == AP_STATE_FAILED)) return;
            Thread.sleep(250);
        }
    }

    private static int apState() throws Exception {
        return (Integer) call(wifi, "getWifiApEnabledState");
    }

    private static void printStatus() throws Exception {
        SoftApConfiguration config = (SoftApConfiguration) call(wifi, "getSoftApConfiguration");
        int band = (Integer) SoftApConfiguration.class.getMethod("getBand").invoke(config);
        int channel = (Integer) SoftApConfiguration.class.getMethod("getChannel").invoke(config);
        System.out.println("AP_STATE=" + apState());
        System.out.println("SSID=" + config.getWifiSsid());
        System.out.println("BAND=" + band);
        System.out.println("CHANNEL=" + channel);
    }

    // ---- reflection helpers ----

    private static IBinder getService(String name) throws Exception {
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, name);
        if (binder == null) throw new IllegalStateException("service not found: " + name);
        return binder;
    }

    private static Object asInterface(String aidl, IBinder binder) throws Exception {
        return Class.forName(aidl + "$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
    }

    /**
     * Calls an AIDL method by name, filling parameters by type, so that small signature
     * changes between Android versions do not break the helper.
     */
    private static Object call(Object target, String name, Object... values) throws Exception {
        Method best = null;
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && (best == null
                    || method.getParameterTypes().length < best.getParameterTypes().length)) {
                best = method;
            }
        }
        if (best == null) throw new NoSuchMethodException(name);
        Class<?>[] types = best.getParameterTypes();
        Object[] args = new Object[types.length];
        int valueIndex = 0;
        boolean callerFilled = false;
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (valueIndex < values.length && wraps(type, values[valueIndex])) {
                args[i] = values[valueIndex++];
            } else if (type == String.class) {
                args[i] = callerFilled ? null : CALLER;
                callerFilled = true;
            } else if (type.getName().endsWith("IIntResultListener")) {
                args[i] = asInterface(type.getName(), new IgnoringBinder());
            } else if (type.getName().endsWith("TetheringRequestParcel")) {
                args[i] = tetheringRequestParcel(type);
            } else if (type.getName().equals("android.net.TetheringManager$TetheringRequest")) {
                args[i] = tetheringRequest();
            } else if (type == int.class) {
                args[i] = 0;
            } else if (type == boolean.class) {
                args[i] = false;
            } else if (type == long.class) {
                args[i] = 0L;
            }
        }
        return best.invoke(target, args);
    }

    private static boolean wraps(Class<?> type, Object value) {
        if (value == null) return !type.isPrimitive();
        if (type == int.class) return value instanceof Integer;
        if (type == boolean.class) return value instanceof Boolean;
        return type.isInstance(value);
    }

    private static Object tetheringRequestParcel(Class<?> type) throws Exception {
        Object parcel = type.getConstructor().newInstance();
        setField(parcel, "tetheringType", TETHERING_WIFI);
        setField(parcel, "exemptFromEntitlementCheck", true);
        setField(parcel, "showProvisioningUi", false);
        setField(parcel, "connectivityScope", 1);
        return parcel;
    }

    private static Object tetheringRequest() throws Exception {
        Class<?> builderClass = Class.forName("android.net.TetheringManager$TetheringRequest$Builder");
        Object builder = builderClass.getConstructor(int.class).newInstance(TETHERING_WIFI);
        try {
            builderClass.getMethod("setExemptFromEntitlementCheck", boolean.class).invoke(builder, true);
            builderClass.getMethod("setShouldShowEntitlementUi", boolean.class).invoke(builder, false);
        } catch (NoSuchMethodException ignored) {
        }
        return builderClass.getMethod("build").invoke(builder);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getField(name);
            field.set(target, value);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    /** Receives the oneway result callback from the tethering service; the result is not needed. */
    private static final class IgnoringBinder extends Binder {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            return true;
        }
    }
}
