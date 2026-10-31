package com.hotspotband;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hotspot state as reported by {@link Helper}, plus tethering details when requested. */
final class Status {
    private static final int AP_STATE_ENABLED = 13;
    private static final int AP_STATE_FAILED = 14;

    /** A device using the phone's internet over the hotspot or the USB cable. */
    static final class Client {
        String mac;
        String ip;
        String iface;
        String name;

        boolean isUsb() {
            return isUsbInterface(iface);
        }

        String describe() {
            String label = name != null && !name.isEmpty() ? name : "Unknown device";
            return label + "  (" + (isUsb() ? "USB" : "Wi-Fi") + ")\n" + ip + "  " + mac;
        }
    }

    int apState = -1;
    int band;
    int channel;
    int frequency;
    String ssid = "";
    String error;
    boolean usbTethered;
    final List<Client> clients = new ArrayList<>();
    /**
     * Bytes the connected devices downloaded and uploaded since sharing started, summed over all
     * tethered interfaces (the phone transmits what the devices download). -1 when not reported.
     */
    long downloaded = -1;
    long uploaded = -1;
    /** The hotspot password, only reported by the "pass" command. */
    String password;
    /** Hotspot channels the phone supports in its current country; null when not reported. */
    int[] channels24;
    int[] channels5;

    private static int[] channelList(String value) {
        List<Integer> channels = new ArrayList<>();
        for (String part : value.split("[, ]+")) {
            try {
                if (!part.isEmpty()) channels.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
            }
        }
        if (channels.isEmpty()) return null;
        int[] result = new int[channels.size()];
        for (int i = 0; i < result.length; i++) result[i] = channels.get(i);
        return result;
    }

    /** 5 GHz channels that need a radar check (DFS) before the hotspot can use them. */
    static boolean isDfs(int channel) {
        return channel >= 52 && channel <= 144;
    }

    static Status error(String message) {
        Status status = new Status();
        status.error = message;
        return status;
    }

    static Status parse(String output) {
        Status status = new Status();
        Map<String, String> hostNames = new HashMap<>();
        for (String line : output.split("\n")) {
            int split = line.indexOf('=');
            if (split < 0) continue;
            String key = line.substring(0, split).trim();
            String value = line.substring(split + 1).trim();
            try {
                switch (key) {
                    case "AP_STATE": status.apState = Integer.parseInt(value); break;
                    case "BAND": status.band = Integer.parseInt(value); break;
                    case "CHANNEL": status.channel = Integer.parseInt(value); break;
                    case "SSID": status.ssid = unquote(value); break;
                    case "ERROR": status.error = value; break;
                    case "TETHERED":
                        if (isUsbInterface(value)) status.usbTethered = true;
                        break;
                    case "CLIENT": {
                        String[] parts = value.split(" ");
                        if (parts.length < 3) break;
                        Client client = new Client();
                        client.mac = parts[0].toLowerCase();
                        client.ip = parts[1];
                        client.iface = parts[2];
                        status.clients.add(client);
                        break;
                    }
                    case "IFSTAT": {
                        String[] parts = value.split(" ");
                        if (parts.length < 3) break;
                        if (status.downloaded < 0) {
                            status.downloaded = 0;
                            status.uploaded = 0;
                        }
                        status.uploaded += Long.parseLong(parts[1]);
                        status.downloaded += Long.parseLong(parts[2]);
                        break;
                    }
                    case "CHANNELS_24":
                        status.channels24 = channelList(value);
                        break;
                    case "CHANNELS_5":
                        status.channels5 = channelList(value);
                        break;
                    case "PASSPHRASE_HEX":
                        status.password = fromHex(value);
                        break;
                    case "HOST": {
                        int space = value.indexOf(' ');
                        if (space > 0) hostNames.put(value.substring(0, space).toLowerCase(), value.substring(space + 1));
                        break;
                    }
                    default: break;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        for (Client client : status.clients) client.name = hostNames.get(client.mac);
        Matcher frequency = Pattern.compile("frequency=\\s*(\\d+)").matcher(output);
        if (frequency.find()) status.frequency = Integer.parseInt(frequency.group(1));
        if (status.error == null && status.apState < 0) {
            status.error = output.trim().isEmpty() ? "No response from helper" : output.trim();
        }
        return status;
    }

    private static String unquote(String value) {
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1) : value;
    }

    private static String fromHex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Formats a byte count as "12.3 MB". */
    static String bytes(long value) {
        if (value < 1024) return value + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double scaled = value;
        int unit = -1;
        while (scaled >= 1024 && unit < units.length - 1) {
            scaled /= 1024;
            unit++;
        }
        return String.format(java.util.Locale.US, scaled >= 100 ? "%.0f %s" : "%.1f %s", scaled, units[unit]);
    }

    static boolean isUsbInterface(String iface) {
        return iface != null && iface.matches("(rndis|ncm|usb)\\d+");
    }

    boolean isOn() {
        return error == null && apState == AP_STATE_ENABLED;
    }

    boolean isOn5GHz() {
        return isOn() && (frequency > 0 ? frequency > 4900 && frequency < 5900 : band == 2);
    }

    boolean isOn24GHz() {
        return isOn() && (frequency > 0 ? frequency < 2500 : band == 1);
    }

    String describe() {
        if (error != null) return error;
        String config = band == 1 ? "2.4 GHz" : band == 2 ? (channel != 0 ? "5 GHz ch " + channel : "5 GHz auto")
                : "band " + band;
        if (isOn()) {
            return "ON - " + ssid + " - " + (frequency > 0 ? frequency + " MHz" : config);
        }
        if (apState == AP_STATE_FAILED) return "FAILED to start (" + config + ")";
        return "OFF - saved setting: " + config;
    }
}
