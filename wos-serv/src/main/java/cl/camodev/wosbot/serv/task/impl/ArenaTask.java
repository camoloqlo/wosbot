package cl.camodev.wosbot.serv.task.impl;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import java.awt.Color;

/**
 * Task responsible for managing arena challenges.
 * It navigates to the arena, checks available attempts, and challenges opponents with lower power.
 * The task can be configured to buy extra attempts and refresh the opponent list using gems.
 * Activation hour can be set to control when the task runs daily.
 */
public class ArenaTask extends DelayedTask {
	// Points used for OCR and template matching
	private static final DTOPoint CHALLENGES_LEFT_TOP_LEFT = new DTOPoint(405, 951);
	private static final DTOPoint CHALLENGES_LEFT_BOTTOM_RIGHT = new DTOPoint(439, 986);
	// Activation time in "HH:mm" format (24-hour clock)
	private String activationHour = profile.getConfig(EnumConfigurationKey.ARENA_TASK_ACTIVATION_HOUR_STRING, String.class);
	private int extraAttempts = profile.getConfig(EnumConfigurationKey.ARENA_TASK_EXTRA_ATTEMPTS_INT, Integer.class);
	private boolean refreshWithGems = profile.getConfig(EnumConfigurationKey.ARENA_TASK_REFRESH_WITH_GEMS_BOOL, Boolean.class);
	private int attempts = 0;
    private boolean firstRun = false;
    private static final int MAX_GEM_REFRESHES = 5;
    private int gemRefreshCount = 0;
    private static final int[] ATTEMPT_PRICES = {100, 200, 400, 600, 800};

    public ArenaTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);

		// Only schedule if the task is enabled and activation hour is valid
		boolean isTaskEnabled = profile.getConfig(EnumConfigurationKey.ARENA_TASK_BOOL, Boolean.class);
		if (!isTaskEnabled) {
			logInfo("Arena task is disabled in configuration.");
			this.setRecurring(false);  // Prevent task from recurring
			return;
		}
		
		if (!isValidTimeFormat(activationHour)) {
			logWarning("Invalid activation hour format: " + activationHour + ". Scheduling to 10min before reset.");
			reschedule(UtilTime.getGameReset().minusMinutes(10));
			return;
		}
		
		// Try to schedule with activation time, fallback to 10min before game reset if it fails
		if (!scheduleActivationTime()) {
			logWarning("Failed to schedule with activation time. Scheduling to 10min before reset.");
			reschedule(UtilTime.getGameReset().minusMinutes(10));
		}
    }

    /**
     * Validates if the given time string is in valid HH:mm format (24-hour clock)
     */
    private boolean isValidTimeFormat(String time) {
        if (time == null || time.trim().isEmpty()) {
            return false;
        }
        try {
            String[] parts = time.split(":");
            if (parts.length != 2) {
                return false;
            }
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            return hours >= 0 && hours <= 23 && minutes >= 0 && minutes <= 59;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    protected void execute() {
        // If activation hour is set, verify it's actually time to run
        if (isValidTimeFormat(activationHour)) {
            ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
            String[] timeParts = activationHour.split(":");
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);
            
            // Calculate today's scheduled time
            ZonedDateTime scheduledTimeUtc = nowUtc.toLocalDate().atTime(hour, minute).atZone(ZoneId.of("UTC"));
            
            // Define the cutoff time (23:55 UTC today)
            ZonedDateTime cutoffTimeUtc = nowUtc.toLocalDate().atTime(23, 55).atZone(ZoneId.of("UTC"));
            
            // Check if we're before the scheduled time (too early)
            if (nowUtc.isBefore(scheduledTimeUtc)) {
                logDebug("Task triggered too early (current: " + nowUtc.format(DateTimeFormatter.ofPattern("HH:mm")) +
                    " UTC, scheduled: " + activationHour + " UTC). Rescheduling for scheduled time.");
                rescheduleForToday();
                return;
            }
            
            // Check if we're after the cutoff time (too late for today)
            if (nowUtc.isAfter(cutoffTimeUtc)) {
                logDebug("Task triggered too late (current: " + nowUtc.format(DateTimeFormatter.ofPattern("HH:mm")) +
                    " UTC, cutoff: 23:55 UTC). Scheduling for tomorrow.");
                rescheduleWithActivationHour();
                return;
            }
            
            logDebug("Task is running (current: " + nowUtc.format(DateTimeFormatter.ofPattern("HH:mm")) + " UTC, window: " + activationHour + " - 23:55 UTC)");
        }
    
        logInfo("Starting arena task.");
        
        // Navigate to marksman camp
        if (!navigateToArena()) {
			logInfo("Failed to navigate to arena.");
			rescheduleWithActivationHour();
            return;
        }

        // Check for first run condition
        firstRun = checkFirstRun();

        // Click on Challenge button
        if (!openChallengeList()) {
			logInfo("Failed to open challenge list.");
            rescheduleWithActivationHour();
            return;
        }

		// Set initial attempts
		if (!getAttempts()) {
			logInfo("Failed to read initial attempts");
			rescheduleWithActivationHour();
			return;
		}

		// Buy extra attempts if configured
		if (extraAttempts > 0) {
			int attemptsBought = buyExtraAttempts();
			attempts += attemptsBought;
		}

        // Process challenges
        if (!processChallenges()) {
			logInfo("Failed to process challenges.");
            rescheduleWithActivationHour();
            return;
        }

        rescheduleWithActivationHour();
    }
	
	private boolean navigateToArena() {
		// Navigate to marksman camp first
        // This sequence of taps is intended to open the event list.
        tapRandomPoint(new DTOPoint(3, 513), new DTOPoint(26, 588));
        sleepTask(500);
        tapRandomPoint(new DTOPoint(20, 250), new DTOPoint(200, 280));
        sleepTask(500);
		
        // Click on marksman camp shortcut
        DTOImageSearchResult marksmanResult = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_SHORTCUTS_MARKSMAN, 90);
        if (!marksmanResult.isFound()) {
			logWarning("Marksman camp shortcut not found.");
            return false;
        }
		tapPoint(marksmanResult.getPoint());
        sleepTask(1000);
		
        // Open arena
		tapPoint(new DTOPoint(702, 727));
        sleepTask(1000);
        return true;
    }

    private boolean checkFirstRun() {
        try {
			// Get arena score
            DTOTesseractSettings settings = new DTOTesseractSettings.Builder()
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setRemoveBackground(true)
                .setTextColor(new Color(255, 255, 255)) // White text
                .setDebug(true)
                .setAllowedChars("0123456789") // Only allow digits
                .build();

            Integer arenaScore = readNumberValue(new DTOPoint(567, 1065), new DTOPoint(649, 1099), settings);
            logInfo("Arena score: " + arenaScore);
            if(arenaScore != null && arenaScore == 1000) {
                logInfo("First run detected based on arena score of 1000.");
                return true;
            } else {
                return false;
            }
		} catch (Exception e) {
			logError("Failed to read arena score value: " + e.getMessage());
			return false;
		}
    }

    private boolean openChallengeList() {
		DTOImageSearchResult challengeResult = emuManager.searchTemplate(EMULATOR_NUMBER,EnumTemplates.ARENA_CHALLENGE_BUTTON, 90);
        if (!challengeResult.isFound()) {
			logWarning("Challenge button not found.");
            return false;
        }
		
        tapPoint(challengeResult.getPoint());
        sleepTask(1000);
        return true;
    }
	
    private boolean processChallenges() {
		// We don't need to read myPower anymore since we're using color detection
        // We'll use color detection instead of OCR for power comparison

		while (attempts > 0) {
            try {
                boolean foundOpponent = false;
                // Process each opponent from top to bottom
                for (int i = 0; i < 5; i++) {
                    if (attempts <= 0) {
                        break; // No more attempts left
                    }
                    // Calculate Y position for each opponent (starting from top)
                    int y;
                    if(firstRun) {
                        y = 380 + (i * 128);
                    } else {
                        y = 354 + (i * 128);
                    }
                    
                    // Check the color distribution in the power text area
                    logInfo("Analyzing power text color for opponent " + (i + 1) + " (position y=" + y + ")");
                    
                    // Define the area to scan (power text region)
                    DTOPoint topLeft = new DTOPoint(185, y);   // Left and top of power text
                    DTOPoint bottomRight = new DTOPoint(215, y + 14); // Right and bottom of power text

                    // Analyze the region colors (returns [background, green, red] counts)
                    int[] colorCounts = emuManager.analyzeRegionColors(EMULATOR_NUMBER, topLeft, bottomRight, 2);

                    int backgroundPixels = colorCounts[0];
                    int greenPixels = colorCounts[1];
                    int redPixels = colorCounts[2];
                    int totalColoredPixels = greenPixels + redPixels;

                    // Log detailed color distribution
                    int totalPixels = ((bottomRight.getX() - topLeft.getX()) * (bottomRight.getY() - topLeft.getY())) / 4; // Accounting for step size of 2
                    logDebug(String.format("Color analysis - Background: %d, Green: %d, Red: %d (Total sampled: %d)", 
                            backgroundPixels, greenPixels, redPixels, totalPixels));
                    
                    // If we have a significant number of colored pixels and green is dominant
                    if (totalColoredPixels > 10 && greenPixels > redPixels * 1.5) {
                        logInfo("Found predominantly green text for opponent " + (i + 1) + " - power is lower than ours");
                        sleepTask(1000);

                        // Since the text is mostly green, the opponent's power is lower than ours
						// Click the challenge button for this opponent
                        tapPoint(new DTOPoint(624, y));
                        sleepTask(2000);
                        
                        // Handle the battle and continue with remaining attempts
                        handleBattle();
                        attempts--;
                        if(!checkResult()) {
                            sleepTask(500);
                            continue; // If we lost, continue to next opponent
                        }
                        foundOpponent = true;
                        firstRun = false;
                        sleepTask(1000);
                        break;
                    } else {
                        logInfo("Found predominantly red text for opponent" + (i + 1) + " - power is higher than ours, skipping opponent.");
                    }
                }
				
                if (!foundOpponent) {
                    // Try to refresh the opponent list
                    if (!refreshOpponentList()) {
                        // If no refresh available and we still have attempts, challenge first opponent
                        logInfo("No more refreshes available and no suitable opponents found. Challenging first opponent to use remaining attempts.");
                        int y = firstRun ? 380 : 354;  // Y-coordinate for first opponent
                        
                        tapPoint(new DTOPoint(624, y));
                        sleepTask(2000);
                        
                        handleBattle();
                        attempts--;
                        checkResult();  // We don't care about the result here
                        firstRun = false;
                        sleepTask(1000);
                        continue;  // Continue to use remaining attempts
                    }
                    sleepTask(1000);
                }
				
            } catch (Exception e) {
				logError("Failed to read power values: " + e.getMessage());
                return false;
            }
        }
		
        logInfo("All attempts used. Arena task completed.");
        return true;
    }
	
    private void handleBattle() {
		tapPoint(new DTOPoint(530, 1200)); // Tap to start battle
        sleepTask(3000);
        tapPoint(new DTOPoint(60, 962)); // Tap pause button
        sleepTask(500);
        tapPoint(new DTOPoint(252, 635)); // Tap retreat button to skip arena animation
        sleepTask(1000);
    }
	
    private boolean refreshOpponentList() {
		// Try free refresh first
        DTOImageSearchResult freeRefreshResult = emuManager.searchTemplate(
			EMULATOR_NUMBER, EnumTemplates.ARENA_FREE_REFRESH_BUTTON, 90);
			
        if (freeRefreshResult.isFound()) {
			logInfo("Using free refresh");
            tapPoint(freeRefreshResult.getPoint());
            return true;
        }

        // Check if refresh with gems is available and enabled, and within limits
        if (refreshWithGems && gemRefreshCount < MAX_GEM_REFRESHES) {
			DTOImageSearchResult gemsRefreshResult = emuManager.searchTemplate(
                EMULATOR_NUMBER, EnumTemplates.ARENA_GEMS_REFRESH_BUTTON, 90);
				
            if (gemsRefreshResult.isFound()) {
                gemRefreshCount++;
				logInfo(String.format("Using gems refresh (%d/%d)", gemRefreshCount, MAX_GEM_REFRESHES));
                tapPoint(gemsRefreshResult.getPoint());
                sleepTask(500);
				
                // Check for confirmation popup
                DTOImageSearchResult confirmResult = emuManager.searchTemplate(
					EMULATOR_NUMBER, EnumTemplates.ARENA_GEMS_REFRESH_CONFIRM_BUTTON, 90);
                
					if (confirmResult.isFound()) {
						tapPoint(new DTOPoint(210,712));
						sleepTask(300);
                    tapPoint(confirmResult.getPoint());
                }
                return true;
            }
        }

        return false;
    }
	
    private boolean checkResult() {
        try {
            // Wait a bit longer for the result screen to stabilize
            sleepTask(1000);
            
            // Coordinates to find victory text
            DTOPoint victoryTopLeft = new DTOPoint(186, 392);
            DTOPoint victoryBottomRight = new DTOPoint(536, 494);
            // Coordinates to find defeat text
            DTOPoint defeatTopLeft = new DTOPoint(195, 290);
            DTOPoint defeatBottomRight = new DTOPoint(516, 384);

            // Check victory region first
            String victoryText = emuManager.ocrRegionText(EMULATOR_NUMBER,
                victoryTopLeft, victoryBottomRight);
            
            // Clean up the victory text
            String cleanVictory = victoryText != null ? victoryText.toLowerCase()
                .replace("—", "")
                .replace("gs", "")
                .replace("fs", "")
                .replace("es", "")
                .replace("aa", "")
                .trim() : "";
                
            if (cleanVictory.contains("victory")) {
                logInfo("Battle result: Victory");
                sleepTask(1000);
                tapBackButton();
                return true;
            }

            // If no victory found, check defeat region
            String defeatText = emuManager.ocrRegionText(EMULATOR_NUMBER,
                defeatTopLeft, defeatBottomRight);
            
            // Clean up the defeat text
            String cleanDefeat = defeatText != null ? defeatText.toLowerCase()
                .replace("—", "")
                .replace("gs", "")
                .replace("fs", "")
                .replace("es", "")
                .replace("aa", "")
                .trim() : "";

            if (cleanDefeat.contains("defeat")) {
                logInfo("Battle result: Defeat");
            } else {
                // If we can't read either result, log both attempts
                logWarning("Unrecognized battle result. Victory region: '" + victoryText + 
                          "', Defeat region: '" + defeatText + "'");
            }
            
            sleepTask(1000); // Wait before tapping back
            tapBackButton();
        } catch (Exception e) {
            logError("OCR error while checking battle result: " + e.getMessage());
        }
        return false;
    }
	
	private boolean getAttempts() {
		try {
            DTOTesseractSettings settings = new DTOTesseractSettings.Builder()
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setRemoveBackground(true)
                .setTextColor(new Color(91, 112, 147)) // Exact text color
                .setDebug(true)
                .setAllowedChars("0123456789") // Only allow digits
                .build();

			Integer attemptsText = readNumberValue(CHALLENGES_LEFT_TOP_LEFT, CHALLENGES_LEFT_BOTTOM_RIGHT, settings);
			if (attemptsText != null) {
				attempts = attemptsText;
				logInfo("Initial attempts available: " + attempts);
				return true;
			}
			logWarning("Failed to parse attempts text: " + attemptsText);
			return false;
		} catch (Exception e) {
			logError("OCR error while reading attempts: " + e.getMessage());
			return false;
		}
	}

	private int buyExtraAttempts() {
		// Tap the "+" attempts button
		tapPoint(new DTOPoint(467, 965));
		sleepTask(1000);

        // Check if the purchase confirmation popup appears
        DTOImageSearchResult confirmResult = emuManager.searchTemplate(
                EMULATOR_NUMBER, EnumTemplates.ARENA_GEMS_EXTRA_ATTEMPTS_BUTTON, 90);
        if (!confirmResult.isFound()) {
            logInfo("No more extra attempts available");
            return 0;
        }

		// Reset the queue counter to zero first
		logDebug("Resetting queue counter");
		swipe(new DTOPoint(420, 733), new DTOPoint(40, 733));
		sleepTask(300);
		
		logInfo("Attempting to buy " + extraAttempts + " extra attempts");

        // Coordinates for price location
        DTOPoint topLeft = new DTOPoint(328, 840);
        DTOPoint bottomRight = new DTOPoint(433, 883);

        // Find where we are in the price sequence
        // for(int i = 0; i < 10; i++) {
        //     Integer price = readNumberValue(topLeft, bottomRight);
        // }

        DTOTesseractSettings settings = new DTOTesseractSettings.Builder()
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setRemoveBackground(true)
                .setTextColor(new Color(255, 255, 255)) // White text
                .setDebug(true)
                .setAllowedChars("0123456789") // Only allow digits
                .build();

        Integer singleAttemptPrice = readNumberValue(topLeft, bottomRight, settings);
        if (singleAttemptPrice == null) {
            logWarning("Failed to read single attempt price");
            tapBackButton();
            return 0;
        }

        // Find which attempt number we're at based on current price
        int previousAttempts = -1;
        for (int i = 0; i < ATTEMPT_PRICES.length; i++) {
            if (ATTEMPT_PRICES[i] == singleAttemptPrice) {
                previousAttempts = i;
                break;
            }
        }

        if (previousAttempts == -1) {
            logWarning(String.format("Unexpected single attempt price: %d gems", singleAttemptPrice));
            tapBackButton();
            return 0;
        }

        // If we already have more attempts than requested, no need to buy more
        if (previousAttempts >= extraAttempts) {
            logInfo(String.format("Already have %d attempts (wanted %d), no need to buy more", 
                    previousAttempts, extraAttempts));
            tapBackButton();
            return 0;
        }

        // Calculate how many more attempts we can buy (limited by max attempts and how many we want)
        int remainingAttempts = Math.min(
            ATTEMPT_PRICES.length - previousAttempts,  // How many more we can buy
            extraAttempts - previousAttempts           // How many more we want to buy
        );
        
        if (remainingAttempts <= 0) {
            logWarning("No more attempts can be purchased");
            tapBackButton();
            return 0;
        }

        // Calculate total price for the remaining attempts
        int expectedPrice = 0;
        for (int i = previousAttempts; i < previousAttempts + remainingAttempts; i++) {
            expectedPrice += ATTEMPT_PRICES[i];
        }

        logDebug(String.format("Previous attempts: %d, Can buy %d more, Price will be: %d gems", 
                  previousAttempts, remainingAttempts, expectedPrice));

        if (remainingAttempts > 1) {
            tapRandomPoint(new DTOPoint(457, 713), new DTOPoint(499, 752),
                    remainingAttempts - 1, 400);
            sleepTask(300);
        }

        // Verify final price matches our expectation
        // Integer finalPrice = readNumberValue(topLeft, bottomRight);
        // if (finalPrice == null || finalPrice != expectedPrice) {
        //     logWarning(String.format("Final price mismatch! Expected: %d, Got: %s", 
        //               expectedPrice, finalPrice != null ? finalPrice.toString() : "null"));
        //     tapBackButton();
        //     return 0;
        // }

        // Price matches, proceed with purchase
        logInfo(String.format("Buying %d attempts for %d gems", remainingAttempts, expectedPrice));
		tapPoint(new DTOPoint(360, 860)); // Tap buy button
        return remainingAttempts;
	}

	/**
	 * Schedules the task based on the configured activation time in UTC
	 */
	private boolean scheduleActivationTime() {
		try {
			// Parse the activation time
			String[] timeParts = activationHour.split(":");
			int hour = Integer.parseInt(timeParts[0]);
			int minute = Integer.parseInt(timeParts[1]);
			
			// Get the current UTC time
			ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
			
			// Create a UTC time for today at the activation time
			ZonedDateTime activationTimeUtc = nowUtc.toLocalDate().atTime(hour, minute).atZone(ZoneId.of("UTC"));
			
			// If the activation time has already passed today, schedule for tomorrow
			if (nowUtc.isAfter(activationTimeUtc)) {
				activationTimeUtc = activationTimeUtc.plusDays(1);
			}
			
			// Convert UTC time to system default time zone
			ZonedDateTime localActivationTime = activationTimeUtc.withZoneSameInstant(ZoneId.systemDefault());
			
			// Schedule the task
			logInfo("Scheduling Arena task for activation at " + activationHour + " UTC (" + 
					localActivationTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " local time)");
			reschedule(localActivationTime.toLocalDateTime());
			return true;
		} catch (Exception e) {
			logError("Failed to schedule activation time: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Special reschedule method that respects the configured activation time
	 * If activation time is configured in valid HH:mm format, it uses that time for the next day
	 * Otherwise, it uses the standard game reset time
	 */
	private void rescheduleWithActivationHour() {
		// If activation hour is configured and valid
		if (isValidTimeFormat(activationHour)) {
			try {
				// Parse the activation time
				String[] timeParts = activationHour.split(":");
				int hour = Integer.parseInt(timeParts[0]);
				int minute = Integer.parseInt(timeParts[1]);
				
				// Schedule based on the configured activation time for the next day
				ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
				ZonedDateTime tomorrowActivationUtc = nowUtc.toLocalDate().plusDays(1)
					.atTime(hour, minute).atZone(ZoneId.of("UTC"));
				ZonedDateTime localActivationTime = tomorrowActivationUtc.withZoneSameInstant(ZoneId.systemDefault());
				
				logInfo("Rescheduling Arena task for next activation at " + activationHour + 
					" UTC tomorrow (" + localActivationTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + 
					" local time)");
				
				reschedule(localActivationTime.toLocalDateTime());
				return;
			} catch (Exception e) {
				logError("Failed to reschedule with activation time: " + e.getMessage());
			}
		}
		
		// Use standard game reset time if activation time is invalid or an error occurred
		logInfo("Rescheduling Arena task for game reset time");
		reschedule(UtilTime.getGameReset());
	}

    /**
	 * Reschedules the task for today's activation time (used when task is triggered too early)
	 */
	private void rescheduleForToday() {
		// If activation hour is configured and valid
		if (isValidTimeFormat(activationHour)) {
			try {
				// Parse the activation time
				String[] timeParts = activationHour.split(":");
				int hour = Integer.parseInt(timeParts[0]);
				int minute = Integer.parseInt(timeParts[1]);
				
				// Schedule based on the configured activation time for today
				ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
				ZonedDateTime todayActivationUtc = nowUtc.toLocalDate()
					.atTime(hour, minute).atZone(ZoneId.of("UTC"));
				ZonedDateTime localActivationTime = todayActivationUtc.withZoneSameInstant(ZoneId.systemDefault());
				
				logInfo("Rescheduling Arena task for activation at " + activationHour + 
					" UTC today (" + localActivationTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + 
					" local time)");
				
				reschedule(localActivationTime.toLocalDateTime());
				return;
			} catch (Exception e) {
				logError("Failed to reschedule for today's activation time: " + e.getMessage());
			}
		}
		
		// Use standard game reset time if activation time is invalid or an error occurred
		logInfo("Rescheduling Arena task for game reset time");
		reschedule(UtilTime.getGameReset());
	}

	@Override
	public EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.HOME;
	}

    @Override
    public boolean provideDailyMissionProgress() {
        return true;
    }
}