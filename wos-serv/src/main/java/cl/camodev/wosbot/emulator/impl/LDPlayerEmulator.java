package cl.camodev.wosbot.emulator.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import cl.camodev.wosbot.emulator.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LDPlayerEmulator extends Emulator {

    private static final Logger logger = LoggerFactory.getLogger(LDPlayerEmulator.class);

    public LDPlayerEmulator(String consolePath) {
        super(consolePath);
    }

    @Override
    protected String getDeviceSerial(String emulatorNumber) {
        // LDPlayer usa el formato emulator-XXXX donde XXXX = 5554 + (emulatorNumber * 2)
        int targetPort = 5554 + (Integer.parseInt(emulatorNumber) * 2);
        return "emulator-" + targetPort;
    }

    @Override
    public void launchEmulator(String emulatorNumber) {
        String[] command = { consolePath + File.separator + "ldconsole.exe", "launch", "--index", emulatorNumber };
        executeCommand(command);
        logger.info("LDPlayer launched at index {}", emulatorNumber);
    }

    @Override
    public void closeEmulator(String emulatorNumber) {
        String[] command = { consolePath + File.separator + "ldconsole.exe", "quit", "--index", emulatorNumber };
        executeCommand(command);
        logger.info("LDPlayer closed at index {}", emulatorNumber);
    }

    @Override
    public boolean isRunning(String emulatorNumber) {
        try {
            String[] command = { consolePath + File.separator + "ldconsole.exe", "isrunning", "--index", emulatorNumber };
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(consolePath).getParentFile());

            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();

            process.waitFor();

            return line != null && line.trim().equalsIgnoreCase("running");
        } catch (IOException | InterruptedException e) {
            logger.error("Error checking if emulator is running", e);
        }
        return false;
    }

    private void executeCommand(String[] command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(consolePath).getParentFile());
            Process process = pb.start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            logger.error("Error executing command", e);
        }
    }
}
