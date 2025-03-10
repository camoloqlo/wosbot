package cl.camodev.wosbot.emulator.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import cl.camodev.wosbot.emulator.Emulator;

public class LDPlayerEmulator extends Emulator {
	public LDPlayerEmulator(String consolePath) {
		super(consolePath);
	}

	@Override
	protected String[] buildAdbCommand(String emulatorNumber, String command) {
		return new String[] { consolePath + File.separator + "ldconsole.exe", "adb", "--index", emulatorNumber, "--command", command };
	}

	@Override
	public void launchEmulator(String emulatorNumber) {
		String[] command = { consolePath + File.separator + "ldconsole.exe", "launch", "--index", emulatorNumber };
		executeCommand(command);
		System.out.println("🚀 LDPlayer lanzado en el índice " + emulatorNumber);

	}

	@Override
	public void closeEmulator(String emulatorNumber) {
		String[] command = { consolePath + File.separator + "ldconsole.exe", "quit", "--index", emulatorNumber };
		executeCommand(command);
		System.out.println("🛑 LDPlayer cerrado en el índice " + emulatorNumber);

	}

	private void executeCommand(String[] command) {
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(new File(consolePath).getParentFile());
			Process process = pb.start();
			process.waitFor();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
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
			e.printStackTrace();
		}
		return false;
	}
}
