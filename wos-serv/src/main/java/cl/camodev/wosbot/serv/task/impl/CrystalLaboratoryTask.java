package cl.camodev.wosbot.serv.task.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.task.DelayedTask;

import cl.camodev.wosbot.serv.task.EnumStartLocation;

public class CrystalLaboratoryTask extends DelayedTask {

	public CrystalLaboratoryTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	/**
	 * Parses a String with the format "Remaining today: <number>" and returns the found number. Extra spaces are considered and it is case-insensitive.
	 *
	 * @param input The string to parse, for example: " Remaining today: 7 "
	 * @return The number extracted from the String.
	 * @throws IllegalArgumentException if the text format is invalid.
	 */
	public static int parseRemainingToday(String input) {
		// Compiles the regular expression with CASE_INSENSITIVE flag to ignore case.
		Pattern pattern = Pattern.compile("^\\s*remaining\\s*(today)?\\s*:\\s*(\\d+|—)\\s*$", Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(input);

		if (matcher.matches()) {
			String value = matcher.group(2);
			// If the captured value is "—", return 0
			return "—".equals(value) ? 0 : Integer.parseInt(value);
		} else {
			throw new IllegalArgumentException("The text format is invalid: " + input);
		}
	}

	@Override
	protected void execute() {
		logInfo("Starting crystal laboratory task.");
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(1, 500), new DTOPoint(25, 590));
		sleepTask(2000);
		emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(110, 270));
		sleepTask(1000);
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(391, 618), new DTOPoint(417, 644));
		sleepTask(5000);
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(530, 860), new DTOPoint(600, 1000));
		sleepTask(3000);

		DTOImageSearchResult claim = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.CRYSTAL_LAB_FC_BUTTON,  90);
		if (!claim.isFound()) {
			logInfo("No crystals available to claim.");
		}
		while (claim.isFound()) {
			logInfo("Claiming crystal...");
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, claim.getPoint(), claim.getPoint());
			sleepTask(100);
			claim = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.CRYSTAL_LAB_FC_BUTTON, 90);
		}

		reschedule(UtilTime.getGameReset());
		ServScheduler.getServices().updateDailyTaskStatus(profile, TpDailyTaskEnum.CRYSTAL_LABORATORY, UtilTime.getGameReset());
		logInfo("Crystal laboratory task completed. Rescheduled for the next game reset.");
		emuManager.tapBackButton(EMULATOR_NUMBER);
	}

	@Override
	protected EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.HOME;
	}

}
