package com.hotspotband;

import android.content.Context;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/** Runs {@link Helper} as the shell user through Shizuku and parses its output. */
final class Runner {
    static final String NOT_RUNNING = "Shizuku is not running. Open Shizuku and start it.";
    static final String NO_PERMISSION = "Allow Hotspot Band in Shizuku (open this app).";

    private Runner() {
    }

    static boolean waitForShizuku(long timeoutMillis) {
        long end = System.currentTimeMillis() + timeoutMillis;
        while (!Shizuku.pingBinder() && System.currentTimeMillis() < end) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return Shizuku.pingBinder();
    }

    static boolean hasPermission() {
        return Shizuku.pingBinder() && !Shizuku.isPreV11()
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    }

    /** Runs a raw shell command through Shizuku (fire and forget). Used for small self-grants. */
    static void shell(Context context, String rawCommand) {
        if (!hasPermission()) return;
        try {
            Method newProcess = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            newProcess.setAccessible(true);
            Process process = (Process) newProcess.invoke(null, new String[]{"sh", "-c", rawCommand}, null, null);
            process.waitFor();
        } catch (Exception ignored) {
        }
    }

    /**
     * Lists tethered interfaces (TETHERED=iface), their clients from the ARP table
     * (CLIENT=mac ip iface) and DHCP host names from the network stack log (HOST=mac name).
     */
    private static final String DETAILS_SCRIPT = ""
            + " ifs=$(dumpsys tethering 2>/dev/null | grep -E '^ *[A-Za-z0-9_]+ - TetheredState'"
            + " | sed 's/^ *//; s/ .*//' | sort -u);"
            + " for i in $ifs; do echo TETHERED=$i;"
            + " awk -v d=\"$i\" '$6==d && $3!=\"0x0\" {print \"CLIENT=\" $4 \" \" $1 \" \" $6}' /proc/net/arp; done;"
            + " dumpsys network_stack 2>/dev/null | grep 'hostname: '"
            + " | sed -n 's/.*hwAddr: \\([0-9a-f:]*\\),.*hostname: \\(.*\\)$/HOST=\\1 \\2/p' | sort -u";

    /** @param command one of: status, 5g CHANNEL, 2g, off, name HEX, usb on|off */
    static Status run(Context context, String command) {
        return run(context, command, false);
    }

    /** Like {@link #run(Context, String)}, and also reports USB tethering and connected devices. */
    static Status runWithDetails(Context context, String command) {
        return run(context, command, true);
    }

    /** Builds the "name HEX" command; the hex form keeps any characters away from the shell. */
    static String nameCommand(String name) {
        StringBuilder hex = new StringBuilder("name ");
        for (byte b : name.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
    }

    private static Status run(Context context, String command, boolean details) {
        if (!waitForShizuku(3000)) return Status.error(NOT_RUNNING);
        if (!hasPermission()) return Status.error(NO_PERMISSION);
        String apk = context.getApplicationInfo().sourceDir;
        String script = "app_process -Xhidden-api-policy:disabled -Djava.class.path=" + apk
                + " /system/bin " + Helper.class.getName() + " " + command + " 2>&1;"
                + (command.equals("status") ? "" : " sleep 2;") + " dumpsys wifi 2>/dev/null | grep mCurrentSoftApInfoMap"
                + " | grep -o 'frequency= *[1-9][0-9]*' | head -1;"
                + (details ? DETAILS_SCRIPT : "");
        StringBuilder output = new StringBuilder();
        try {
            Method newProcess = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            newProcess.setAccessible(true);
            Process process = (Process) newProcess.invoke(null, new String[]{"sh", "-c", script}, null, null);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append('\n');
            }
            process.waitFor();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return Status.error(cause.toString());
        }
        return Status.parse(output.toString());
    }
}
