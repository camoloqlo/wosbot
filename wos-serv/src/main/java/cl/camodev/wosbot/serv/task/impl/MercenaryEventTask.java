package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import net.sourceforge.tess4j.TesseractException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MercenaryEventTask extends DelayedTask {

    public MercenaryEventTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
    }

    @Override
    public EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.WORLD;
    }

    @Override
    protected void execute() {

        if (!checkStamina()) {
            logInfo("Stamina check failed or insufficient. Task has been rescheduled.");
            tapBackButton();
            return;
        }

        // After checking stamina, return to world screen
        tapBackButton();

        int attempt = 0;
        while (attempt < 2) {
            if (navigateToEventScreen()) {
                handleMercenaryEvent();
                return;
            }
            logDebug("Navigation to Mercenary event failed, attempt " + (attempt + 1));
            sleepTask(300);
            tapBackButton();
            attempt++;
        }

        logWarning("Could not find the Mercenary event tab. Assuming event is unavailable. Task will be removed.");
        this.setRecurring(false);
    }

    private boolean checkStamina() {
        logInfo("Navigating to Intel screen to check stamina.");
        ensureOnIntelScreen();
        sleepTask(2000);

        try {
            Integer staminaValue = null;
            for (int attempt = 0; attempt < 5 && staminaValue == null; attempt++) {
                try {
                    String ocr = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(582, 23), new DTOPoint(672, 55));
                    if (ocr != null && !ocr.trim().isEmpty()) {
                        Matcher m = Pattern.compile("\\d+").matcher(ocr);
                        if (m.find()) {
                            staminaValue = Integer.valueOf(m.group());
                        }
                    }
                } catch (IOException | TesseractException ex) {
                    logDebug("Stamina OCR attempt " + (attempt + 1) + " failed: " + ex.getMessage());
                }
                if (staminaValue == null) {
                    sleepTask(100);
                }
            }

            if (staminaValue == null) {
                logWarning("Could not read stamina value after multiple attempts. Rescheduling for 5 minutes.");
                this.reschedule(LocalDateTime.now().plusMinutes(5));
                return false;
            }

            int minStaminaRequired = 30;
            if (staminaValue < minStaminaRequired) {
                logWarning("Not enough stamina to attack mercenary. Current: " + staminaValue + ", Required: " + minStaminaRequired);
                long minutesToRegen = (minStaminaRequired - staminaValue) * 5L;
                LocalDateTime rescheduleTime = LocalDateTime.now().plusMinutes(minutesToRegen);
                this.reschedule(rescheduleTime);
                logInfo("Rescheduling for " + rescheduleTime + " to regenerate stamina.");
                return false;
            }

            logInfo("Stamina is sufficient (" + staminaValue + ").");
            return true;

        } catch (Exception e) {
            logError("Unexpected error reading stamina: " + e.getMessage(), e);
            this.reschedule(LocalDateTime.now().plusMinutes(15));
            return false;
        }
    }

    private void handleMercenaryEvent() {
        try {
            if (isEventCompleted()) {
                logInfo("Mercenary event seems to be completed. Removing task.");
                this.setRecurring(false);
                return;
            }
            scoutAndAttack();
        } catch (Exception e) {
            logError("An error occurred during the Mercenary Event task: " + e.getMessage(), e);
            this.reschedule(LocalDateTime.now().plusMinutes(30)); // Reschedule on error
        }
    }

    private boolean navigateToEventScreen() {

		logInfo("Starting the Mercenary Event task.");

		// Search for the events button
		DTOImageSearchResult eventsResult = emuManager.searchTemplate(EMULATOR_NUMBER,
				EnumTemplates.HOME_EVENTS_BUTTON, 90);
		if (!eventsResult.isFound()) {
			logWarning("The 'Events' button was not found.");
			return false;
		}

		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, eventsResult.getPoint(), eventsResult.getPoint());
		sleepTask(2000);
		// Close any windows that may be open
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(529, 27), new DTOPoint(635, 63), 5, 300);

		// Search for the mercenary within events
		DTOImageSearchResult result = emuManager.searchTemplate(EMULATOR_NUMBER,
				EnumTemplates.MERCENARY_EVENT_TAB, 90);

		if (result.isFound()) {
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, result.getPoint(), result.getPoint());
			sleepTask(1000);
			logInfo("Successfully navigated to the Mercenary event.");
			return true;
		}

		// Swipe completely to the left
		logInfo("Mercenary event not immediately visible. Swiping left to locate it.");
		for (int i = 0; i < 3; i++) {
			emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(80, 120), new DTOPoint(578, 130));
			sleepTask(200);
		}

		int attempts = 0;
		while (attempts < 5) {
			result = emuManager.searchTemplate(EMULATOR_NUMBER,
					EnumTemplates.MERCENARY_EVENT_TAB, 90);

			if (result.isFound()) {
				emuManager.tapAtRandomPoint(EMULATOR_NUMBER, result.getPoint(), result.getPoint());
				sleepTask(1000);
				logInfo("Successfully navigated to the Mercenary event.");
    				return true;
			}

			logInfo("Mercenary event not found. Swiping right and retrying...");
			emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(630, 143), new DTOPoint(500, 128));
			sleepTask(200);
			attempts++;
		}

		logWarning("Mercenary event not found after multiple attempts. Aborting the task.");
		this.setRecurring(false);
		return false;
	}

    private boolean isEventCompleted() {
        logInfo("Checking if event is completed by looking for the Scout button.");
        DTOImageSearchResult scoutButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.MERCENARY_SCOUT_BUTTON, 90);
        return !scoutButton.isFound();
    }

    private void scoutAndAttack() throws IOException, TesseractException {
        logInfo("Scouting for mercenary.");
        DTOImageSearchResult scoutButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.MERCENARY_SCOUT_BUTTON, 90);
        if (scoutButton.isFound()) {
            emuManager.tapAtPoint(EMULATOR_NUMBER, scoutButton.getPoint());
            sleepTask(4000); // Wait to travel to mercenary location on map

            DTOImageSearchResult attackButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.MERCENARY_ATTACK_BUTTON, 90);
            if (attackButton.isFound()) {
                logInfo("Attacking mercenary.");
                emuManager.tapAtPoint(EMULATOR_NUMBER, attackButton.getPoint());
                sleepTask(2000);

                // Check if the march screen is open before proceeding
                DTOImageSearchResult deployButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.DEPLOY_BUTTON, 90);
                if (!deployButton.isFound()) {
                    logError("March queue is full or another issue occurred. Cannot start a new march.");
                    this.reschedule(LocalDateTime.now().plusMinutes(10));
                    return;
                }

                // Send troops
                emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(198, 1188));
                sleepTask(500);

                try {
                    String timeStr = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(521, 1141), new DTOPoint(608, 1162));
                    long travelTimeSeconds = parseTimeToSeconds(timeStr);

                    if (travelTimeSeconds > 0) {
                        emuManager.tapAtPoint(EMULATOR_NUMBER, deployButton.getPoint());
                        sleepTask(1000); // Wait for march to start
                        long returnTimeSeconds = (travelTimeSeconds * 2) + 2;
                        LocalDateTime rescheduleTime = LocalDateTime.now().plusSeconds(returnTimeSeconds);
                        this.reschedule(rescheduleTime);
                        logInfo("Mercenary march sent. Task will run again at " + rescheduleTime + ".");
                    } else {
                        logError("Failed to parse march time. Aborting attack.");
                        emuManager.tapBackButton(EMULATOR_NUMBER); // Go back from march screen
                    }
                } catch (IOException | TesseractException e) {
                    logError("Failed to read march time using OCR. Aborting attack. Error: " + e.getMessage(), e);
                    emuManager.tapBackButton(EMULATOR_NUMBER); // Go back from march screen
                }
            } else {
                logWarning("Attack button not found after scouting.");
            }
        } else {
            logInfo("Scout button not found, assuming event is completed. Removing task.");
            this.setRecurring(false);
        }
    }

    private long parseTimeToSeconds(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return 0;
        }
        Pattern pattern = Pattern.compile("(\\d{1,2}):(\\d{2}):(\\d{2})");
        Matcher matcher = pattern.matcher(timeStr.trim());
        if (matcher.find()) {
            try {
                int hours = Integer.parseInt(matcher.group(1));
                int minutes = Integer.parseInt(matcher.group(2));
                int seconds = Integer.parseInt(matcher.group(3));
                return (long) hours * 3600 + (long) minutes * 60 + seconds;
            } catch (NumberFormatException e) {
                logError("Failed to parse march time from OCR string: '" + timeStr + "'", e);
            }
        }
        logWarning("Could not parse march time from OCR string: '" + timeStr + "'.");
        return 0;
    }

}
