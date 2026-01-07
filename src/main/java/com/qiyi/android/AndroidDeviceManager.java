package com.qiyi.android;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class AndroidDeviceManager {
    private static final AndroidDeviceManager INSTANCE = new AndroidDeviceManager();
    private String adbPath = "adb"; // Assume in PATH

    private AndroidDeviceManager() {
    }

    public static AndroidDeviceManager getInstance() {
        return INSTANCE;
    }

    public List<String> getDevices() throws IOException {
        List<String> devices = new ArrayList<>();
        Process process = new ProcessBuilder(adbPath, "devices").start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.endsWith("device")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length > 0) {
                        devices.add(parts[0]);
                    }
                }
            }
        }
        return devices;
    }
    
    public String executeShell(String serial, String command) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(adbPath);
        if (serial != null && !serial.isEmpty()) {
            cmd.add("-s");
            cmd.add(serial);
        }
        cmd.add("shell");
        
        // Simple command parsing: split by space. 
        // For more complex commands, we might need a better parser.
        // But for "am start" and "monkey", this is sufficient.
        String[] parts = command.split("\\s+");
        for (String part : parts) {
            cmd.add(part);
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }
}
