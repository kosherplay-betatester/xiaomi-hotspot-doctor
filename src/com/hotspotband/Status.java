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
