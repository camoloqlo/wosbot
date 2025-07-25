package cl.camodev.wosbot.serv.task.impl;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.task.DelayedTask;
import net.sourceforge.tess4j.TesseractException;

public class WarAcademyTask extends DelayedTask {

	public WarAcademyTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	/**
	 * Parsea un String con el formato "Remaining : <number>" y retorna el número encontrado. Se consideran espacios extra y no distingue entre
	 * mayúsculas y minúsculas.
	 *
	 * @param input La cadena a parsear, por ejemplo: " Remaining today: 7 "
	 * @return El número extraído del String.
	 * @throws IllegalArgumentException si el formato del texto no es válido.
	 */
	public static int parseRemaining(String input) {
		Pattern pattern = Pattern.compile("^\\s*remaining\\s*:\\s*(\\d+|O)\\s*$", Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(input);

		if (matcher.matches()) {
			String value = matcher.group(1);
			// Si es "O", lo interpretamos como 0
			return value.equalsIgnoreCase("O") ? 0 : Integer.parseInt(value);
		} else {
			throw new IllegalArgumentException("El formato del texto no es válido: " + input);
		}
	}

	@Override
	protected void execute() {

		if (isHomeOrWorldScreenFound()) {
			performWarAcademyTask();
		} else {
			logAndExit("Home not found");
		}
	}

	private boolean isHomeOrWorldScreenFound() {
		DTOImageSearchResult homeResult = EmulatorManager.getInstance().searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_FURNACE.getTemplate(),  90);
		DTOImageSearchResult worldResult = EmulatorManager.getInstance().searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_WORLD.getTemplate(),  90);
		return homeResult.isFound() || worldResult.isFound();
	}

	private void performWarAcademyTask() {
		ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Going to war academy");

		navigateToWarAcademy();
		int totalShards = checkRemainingShards();

		if (totalShards > 0) {
			claimCrystalsShards();
		}

		this.reschedule(UtilTime.getGameReset());
		ServScheduler.getServices().updateDailyTaskStatus(profile, TpDailyTaskEnum.WAR_ACADEMY_SHARDS, UtilTime.getGameReset());
		EmulatorManager.getInstance().tapBackButton(EMULATOR_NUMBER);

	}

	private void navigateToWarAcademy() {
		EmulatorManager.getInstance().tapAtPoint(EMULATOR_NUMBER, new DTOPoint(1, 550));
		sleepTask(2000);
		EmulatorManager.getInstance().tapAtPoint(EMULATOR_NUMBER, new DTOPoint(110, 270));
		sleepTask(1000);
		EmulatorManager.getInstance().tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(186, 533), new DTOPoint(265, 555));
		sleepTask(5000);
		EmulatorManager.getInstance().tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(1, 787), new DTOPoint(38, 842));
		sleepTask(3000);
		EmulatorManager.getInstance().tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(605, 139), new DTOPoint(622, 198));
	}

	private int checkRemainingShards() {
		String remainingText = "";
                try {
                        remainingText = EmulatorManager.getInstance().ocrRegionText(EMULATOR_NUMBER, new DTOPoint(463, 452), new DTOPoint(624, 483));
                } catch (IOException | TesseractException e) {
                        ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), "Failed to read shards count: " + e.getMessage());
                }
		ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), remainingText);
		return parseRemaining(remainingText);
	}

	private void claimCrystalsShards() {
		ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Claiming crystals shards");
		EmulatorManager.getInstance().tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(483, 500), new DTOPoint(605, 540));
		sleepTask(2000);
		EmulatorManager.getInstance().tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(592, 685), new DTOPoint(629, 722));
		sleepTask(2000);
		EmulatorManager.getInstance().tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(241, 805), new DTOPoint(483, 857));
	}

	private void logAndExit(String message) {
		ServLogs.getServices().appendLog(EnumTpMessageSeverity.WARNING, taskName, profile.getName(), message);
		EmulatorManager.getInstance().tapBackButton(EMULATOR_NUMBER);
	}

}
