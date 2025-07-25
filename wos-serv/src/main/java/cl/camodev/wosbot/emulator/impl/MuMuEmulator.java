package cl.camodev.wosbot.emulator.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import cl.camodev.wosbot.emulator.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MuMuEmulator extends Emulator {
	private static final Logger logger = LoggerFactory.getLogger(MuMuEmulator.class);

	public MuMuEmulator(String consolePath) {
		super(consolePath);
	}

	@Override
	protected String getDeviceSerial(String emulatorNumber) {
		//MuMu uses this format for device serial: 16384 + (instanceNumber * 32)
		int port = 16384 + (Integer.parseInt(emulatorNumber) * 32);
		return "127.0.0.1:" + port;
	}

	@Override
	public void launchEmulator(String emulatorNumber) {
		String[] command = { consolePath + File.separator + "MuMuManager.exe", "api", "-v", emulatorNumber, "launch_player" };
		executeCommand(command);
        logger.info("MuMu launched at index {}", emulatorNumber);
	}

	@Override
	public void closeEmulator(String emulatorNumber) {
		String[] command = { consolePath + File.separator + "MuMuManager.exe", "api", "-v", emulatorNumber, "shutdown_player" };
		executeCommand(command);
        logger.info("MuMu closed at index {}", emulatorNumber);
	}

	@Override
	public boolean isRunning(String emulatorNumber) {
		try {
			String[] command = { consolePath + File.separator + "MuMuManager.exe", "api", "-v", emulatorNumber, "player_state" };
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(new File(consolePath).getParentFile());

			Process process = pb.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;

			while ((line = reader.readLine()) != null) {
				if (line.contains("state=start_finished")) {
					return true;
				}
			}

			process.waitFor();
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
