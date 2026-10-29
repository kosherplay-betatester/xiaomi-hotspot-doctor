package com.hotspotband;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hotspot state as reported by {@link Helper}. */
final class Status {
    private static final int AP_STATE_ENABLED = 13;
    private static final int AP_STATE_FAILED = 14;

    int apState = -1;
    int band;
    int channel;
    int frequency;
    String ssid = "";
    String error;

    static Status error(String message) {
        Status status = new Status();
        status.error = message;
        return status;
    }

    static Status parse(String output) {
        Status status = new Status();
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
                    case "SSID": status.ssid = value.replace("\"", ""); break;
                    case "ERROR": status.error = value; break;
                    default: break;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        Matcher frequency = Pattern.compile("frequency=\\s*(\\d+)").matcher(output);
        if (frequency.find()) status.frequency = Integer.parseInt(frequency.group(1));
        if (status.error == null && status.apState < 0) {
            status.error = output.trim().isEmpty() ? "No response from helper" : output.trim();
        }
        return status;
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
