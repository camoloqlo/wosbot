package cl.camodev.wosbot.emulator.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import cl.camodev.wosbot.emulator.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MEmuEmulator extends Emulator {
	private static final Logger logger = LoggerFactory.getLogger(MEmuEmulator.class);

	public MEmuEmulator(String consolePath) {
		super(consolePath);
	}

	@Override
	protected String getDeviceSerial(String emulatorNumber) {
		// MEmu uses the format 127.0.0.1:XXXX where XXXX = 21503 + (emulatorNumber * 10)
		return "127.0.0.1:" + (21503 + Integer.parseInt(emulatorNumber) * 10);
	}


	@Override
	public void launchEmulator(String emulatorNumber) {
		String[] command = { consolePath + File.separator + "memuc", "start", "-i", emulatorNumber };
		executeCommand(command);
		logger.info("MEmu launched at index " + emulatorNumber);
	}

	@Override
	public void closeEmulator(String emulatorNumber) {
		String[] command = { consolePath + File.separator + "memuc", "stop", "-i", emulatorNumber };
		executeCommand(command);
		logger.info("MEmu closed at index " + emulatorNumber);
	}

	@Override
	public boolean isRunning(String emulatorNumber) {
		try {
			String[] command = { consolePath + File.separator + "memuc", "isvmrunning", "-i", emulatorNumber };
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(new File(consolePath).getParentFile());

			Process process = pb.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;

			while ((line = reader.readLine()) != null) {
				if (!line.equals("Not Running")) {
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
