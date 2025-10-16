package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Task responsible for managing troop training operations in the game.
 * Handles both normal training and promotion-priority training modes.
 * Supports Infantry, Lancer, and Marksman troop types.
 */
public class TrainingTroopsTask extends DelayedTask {

    // ===============================
    // CONSTANTS AND FIELDS
    // ===============================

    private final TroopType troopType;

    // ===============================
    // CONSTRUCTOR
    // ===============================

    public TrainingTroopsTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask, TroopType troopType) {
        super(profile, tpDailyTask);
        this.troopType = troopType;
    }

    // ===============================
    // MAIN EXECUTION METHODS
    // ===============================

    /**
     * Parse time string and add it to the current LocalDateTime
     * @param baseTime Base time to add to (unused but kept for method signature compatibility)
     * @param timeString Time string in format "[n]d HH:mm:ss"
     * @return LocalDateTime with the parsed time added
     */
    public static LocalDateTime addTimeToLocalDateTime(LocalDateTime baseTime, String timeString) {
        Pattern pattern = Pattern.compile("(?i).*?(?:(\\d+)\\s*d\\s*)?(\\d{1,2}:\\d{2}:\\d{2}).*", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(timeString.trim());

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Time string does not match expected format [n]d HH:mm:ss: " + timeString);
        }

        String daysStr = matcher.group(1);   // Optional days component
        String timeStr = matcher.group(2);   // Required time component

        int daysToAdd = (daysStr != null) ? Integer.parseInt(daysStr) : 0;

        // Parse time component
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("H:mm:ss");
        LocalTime timePart = LocalTime.parse(timeStr, timeFormatter);

        return LocalDateTime.now()
                .plusDays(daysToAdd)
                .plusHours(timePart.getHour())
                .plusMinutes(timePart.getMinute())
                .plusSeconds(timePart.getSecond());
    }

    @Override
    protected void execute() {
        logInfo("Starting training task for troop type: " + troopType);

        // Navigate to troops interface
        logInfo("Navigating to the training interface for " + troopType);

        // Open left menu on city section
        openLeftMenuCitySection(true);

        // Search for the specific troop type
        DTOImageSearchResult troopsResult = emuManager.searchTemplate(EMULATOR_NUMBER, troopType.getTemplate(), 90);

        if (troopsResult.isFound()) {
            handleTroopStatusCheck(troopsResult);
        } else {
            logInfo("Troop type " + troopType + " not found. Ending task.");
        }
    }



    /**
     * Handle checking the status of troops and determine next action
     * @param troopsResult The search result for the troop type
     */
    private void handleTroopStatusCheck(DTOImageSearchResult troopsResult) {
        // Attempt multiple times to read troop status (OCR can be unreliable)
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                logDebug("Reading troop status (Attempt " + attempt + "/3)...");

                DTOPoint[] points = getTroopsPoints(troopType);
                String rawText = emuManager.ocrRegionText(EMULATOR_NUMBER, points[0], points[1]);

                if (rawText != null) {
                    rawText = rawText.trim().replaceAll("^[\\p{Punct}\\s]+", "");
                } else {
                    rawText = "";
                }

                if (handleTroopStatus(rawText, troopsResult)) {
                    return; // Status handled successfully, exit
                }

                // If none of the status conditions were met, try manual attempt
                logInfo("Status read failed. Tapping on troop and reopening interface to refresh.");

                // Tap on the troop first
                emuManager.tapAtRandomPoint(EMULATOR_NUMBER,
                    troopsResult.getPoint(), troopsResult.getPoint());
                sleepTask(2000);

                // Navigate back to home/training camp area
                tapRandomPoint(new DTOPoint(310, 650), new DTOPoint(450, 730), 10, 100);
                sleepTask(500);

                // Reopen the troops interface completely
                reopenTroopsInterface();

            } catch (Exception e) {
                logError("Error during training check attempt " + attempt, e);
                if (attempt == 2) {
                    logInfo("All attempts failed. Rescheduling check in 1 hour.");
                    reschedule(LocalDateTime.now().plusHours(1));
                }
            }
        }
    }

    /**
     * Reopen the troops interface after a manual attempt
     * This ensures we're in the correct state to read troop status
     */
    private void reopenTroopsInterface() {
        logInfo("Reopening troops interface for a status refresh.");

        // Open left menu on city section
        openLeftMenuCitySection(true);

        logInfo("Troops interface reopened successfully.");
    }

    /**
     * Process the troop status and take appropriate action
     * @param statusText The OCR text containing troop status
     * @param troopsResult The search result for the troop type
     * @return true if status was handled, false if should retry
     */
    private boolean handleTroopStatus(String statusText, DTOImageSearchResult troopsResult) {
        logInfo("Processing troop status text: '" + statusText + "'");

        // Check if troops are currently upgrading
        if (statusText.contains("Upgrading")) {
            logInfo("Troops are currently upgrading. Rescheduling check in 1 hour.");
            reschedule(LocalDateTime.now().plusHours(1));
            return true;
        }

        // Check if training is completed or troops are idle
        if (statusText.contains("Completed") || statusText.contains("Idle")) {
            logInfo("Training is complete or troops are idle. Starting new training process.");

            // Tap on troop to enter training interface
           emuManager.tapAtRandomPoint(EMULATOR_NUMBER,
                troopsResult.getPoint(), troopsResult.getPoint());
            sleepTask(2000);

            // Execute the training process
            processTraining();
            sleepTask(1000);

            // Extract and schedule next training time
            scheduleNextTraining();
            return true;
        }

        // Parse remaining training time and reschedule
        try {
            LocalDateTime nextTrainingTime = parseTime(statusText);
            logInfo("Training in progress. Rescheduling for: " + nextTrainingTime);
            reschedule(nextTrainingTime);
            closeLeftMenu();
            return true;
        } catch (Exception e) {
            logWarning("Could not parse training time from status text: '" + statusText + "'");
            closeLeftMenu();
            return false; // Retry reading status
        }
    }

    // ===============================
    // TRAINING PROCESS METHODS
    // ===============================

    /**
     * Extract next training completion time and schedule the task accordingly
     */
    private void scheduleNextTraining() {
        logInfo("Extracting next training completion time.");

        Optional<LocalDateTime> optionalNextTime = extractNextTime();

        if (optionalNextTime.isPresent()) {
            LocalDateTime nextSchedule = optionalNextTime.get();
            logInfo("Next training check scheduled for: " + nextSchedule);
            reschedule(nextSchedule);
        } else {
            // Fallback: reschedule in 1 hour if next time cannot be extracted
            LocalDateTime fallback = LocalDateTime.now().plusHours(1);
            logInfo("Failed to extract next training time. Rescheduling in 1 hour as a fallback.");
            reschedule(fallback);
        }
    }

    /**
     * Main training process orchestrator
     * Decides between promotion-priority training or normal training
     */
    public void processTraining() {
        logInfo("Starting training process");

        // Navigate to training camp area
        if (!navigateToTrainingCamp()) {
            logInfo("Failed to navigate to the training camp. Aborting training process.");
            return;
        }

        // Enter training interface
        enterTrainingInterface();

        // Check training mode configuration and execute accordingly
        boolean prioritizePromotion = profile.getConfig(
            EnumConfigurationKey.TRAIN_PRIORITIZE_PROMOTION_BOOL, Boolean.class);
        logInfo("Promotion priority mode is " + (prioritizePromotion ? "enabled." : "disabled."));

        if (prioritizePromotion) {
            handlePromotionPriorityTraining();
        } else {
            handleNormalTraining();
        }
    }

    /**
     * Navigate to the training camp area
     * @return true if navigation successful, false otherwise
     */
    private boolean navigateToTrainingCamp() {
        logInfo("Navigating to the training camp.");

        // Tap on training camp area
        tapRandomPoint(new DTOPoint(310, 650), new DTOPoint(450, 730), 10, 100);

        // Verify we're in the correct location by searching for training template
        DTOImageSearchResult trainingResult = emuManager
            .searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_CAMP_TRAIN, 90);

        boolean found = trainingResult.isFound();
        logInfo("Successfully navigated to training camp: " + found);

        if (found) {
            tapPoint(trainingResult.getPoint());
            sleepTask(500);
        }

        return found;
    }

    // ===============================
    // PROMOTION PRIORITY TRAINING
    // ===============================

    /**
     * Enter the training interface from the training camp
     */
    private void enterTrainingInterface() {
        logInfo("Entering the training interface.");

        // Tap on training interface area
        tapRandomPoint(new DTOPoint(222, 157), new DTOPoint(504, 231), 10, 100);
        sleepTask(500);
    }

    /**
     * Handles training logic when promotion priority is enabled
     * Attempts to find and promote lower-tier troops before training new ones
     */
    private void handlePromotionPriorityTraining() {
        logInfo("Handling promotion-priority training.");

        // Reset troop list position to beginning
        resetTroopListPosition();

        // Find the maximum available troop level
        int maxLevel = findMaxAvailableTroopLevel();
        logInfo("Found maximum available troop level: " + maxLevel);

        if (maxLevel == -1) {
            logInfo("No troop levels were detected. Falling back to normal training.");
            executeNormalTraining();
            return;
        }

        // Reset position again and attempt promotions
        swipe( new DTOPoint(73, 785),new DTOPoint(690, 785));
        sleepTask(100);
        swipe( new DTOPoint(73, 785),new DTOPoint(690, 785));
        sleepTask(400);
        boolean promotionExecuted = attemptTroopPromotions(maxLevel);

        if (!promotionExecuted) {
            logInfo("No promotable troops found. Executing normal training instead.");
            executeNormalTraining();
        }
    }

    /**
     * Reset the troop list to the beginning position by swiping
     */
    private void resetTroopListPosition() {
        logInfo("Resetting troop list position to the beginning.");

        // Swipe from right to left to go to beginning of troop list
        swipe(new DTOPoint(690, 785), new DTOPoint(73, 785));
        sleepTask(100);
        swipe(new DTOPoint(690, 785), new DTOPoint(73, 785));
        sleepTask(100);
    }

    /**
     * Find the maximum available troop level by iterating through all templates
     * @return The highest level found, or -1 if none found
     */
    private int findMaxAvailableTroopLevel() {
        List<EnumTemplates> templates = getTroopsTemplates(troopType);
        logInfo("Searching for the maximum troop level among " + templates.size() + " templates...");

        int maxLevel = -1;

        // Iterate through templates from highest to lowest tier
        for (EnumTemplates template : templates) {
            logInfo("Checking for template: " + template.name());

            DTOImageSearchResult troop = emuManager.searchTemplate(EMULATOR_NUMBER, template, 98);

            if (troop.isFound()) {
                int level = extractLevelFromTemplateName(template.name());
                if (level > 0) {
                    logInfo("Found troop template: " + template.name() + " (Level " + level + ")");
                    maxLevel = level;
                    break; // Found highest available level
                }
            } else {
                // Swipe to search for next troop type
                swipe(new DTOPoint(490, 773), new DTOPoint(530, 773));
                sleepTask(400);
            }
        }

        return maxLevel;
    }

    /**
     * Extract numeric level from template name
     * @param templateName The template name (e.g., "TRAINING_INFANTRY_T6")
     * @return The extracted level number, or -1 if not found
     */
    private int extractLevelFromTemplateName(String templateName) {
        try {
            String levelStr = templateName.replaceAll("[^0-9]", "");

            if (!levelStr.isEmpty()) {
                int level = Integer.parseInt(levelStr);
                logInfo("Extracted level " + level + " from template name: " + templateName);
                return level;
            } else {
                logWarning("No numeric level found in template name: " + templateName);
                return -1;
            }
        } catch (Exception e) {
            logError("Error extracting level from template " + templateName, e);
            return -1;
        }
    }

    /**
     * Attempt to find and execute troop promotions for levels below maxLevel
     * @param maxLevel The maximum level available for promotion target
     * @return true if a promotion was executed, false otherwise
     */
    private boolean attemptTroopPromotions(int maxLevel) {
        List<EnumTemplates> templates = getTroopsTemplates(troopType);
        logInfo("Searching for promotable troops (levels below " + maxLevel + ")...");

        // Search from highest to lowest level to prioritize higher-level promotions
        for (int i = templates.size() - 1; i >= 0; i--) {
            EnumTemplates template = templates.get(i);
            int templateLevel = extractLevelFromTemplateName(template.name());

            // Only consider troops with levels lower than maximum available
            if (templateLevel > 0 && templateLevel <= maxLevel) {
                if (attemptSingleTroopPromotion(template)) {
                    return true; // Promotion successful
                }
            }
        }

        logInfo("No promotable troops found.");
        return false;
    }

    /**
     * Attempt to promote a single troop type
     * @param template The troop template to attempt promotion for
     * @return true if promotion was successful, false otherwise
     */
    private boolean attemptSingleTroopPromotion(EnumTemplates template) {
        logInfo("Attempting to promote: " + template.name());
        int attempts = 0;
    
        while(attempts < 3) {
            DTOImageSearchResult troop = emuManager.searchTemplate(EMULATOR_NUMBER, template, 98);
            
            if (troop.isFound()) {
                // Tap on troop to check promotion availability
                tapPoint(troop.getPoint());
                sleepTask(500);
    
                DTOImageSearchResult promoteButton = emuManager.searchTemplate(EMULATOR_NUMBER,
                    EnumTemplates.TRAINING_TROOP_PROMOTE, 90);
    
                if (promoteButton.isFound()) {
                    return executePromotion(template, promoteButton);
                } else {
                    logInfo("Promotion button not found for: " + template.name());
                }
            } else {
                logDebug("Template " + template.name() + " not found (attempt " + (attempts + 1) + "/3).");
            }

            attempts++;
            sleepTask(300);
        }
        logWarning("Failed to find template " + template.name() + " after 3 attempts. Moving to the next troop level.");
        navigateToNextTroop();
        return false;
    }

    /**
     * Execute the promotion process for a troop
     * @param template The troop template being promoted
     * @param promoteButton The promote button search result
     * @return true if promotion executed successfully
     */
    private boolean executePromotion(EnumTemplates template, DTOImageSearchResult promoteButton) {
        logInfo("Executing promotion for " + template.name());

        // Tap promote button
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, promoteButton.getPoint(), promoteButton.getPoint());
        sleepTask(500);

        // Confirm promotion
        tapPoint(new DTOPoint(523, 900));
        sleepTask(500);

        logInfo("Promotion confirmed for: " + template.name());
        return true;
    }

    // ===============================
    // NORMAL TRAINING METHODS
    // ===============================

    /**
     * Navigate to the next troop in the list
     */
    private void navigateToNextTroop() {
        swipe(new DTOPoint(530, 773), new DTOPoint(490, 773));
        sleepTask(300);
    }

    /**
     * Handle normal training when promotion priority is disabled
     */
    private void handleNormalTraining() {
        logInfo("Handling normal training mode.");
        executeNormalTraining();
    }

    // ===============================
    // TIME PARSING AND EXTRACTION
    // ===============================

    /**
     * Execute normal training by finding and tapping the training button
     */
    private void executeNormalTraining() {
        DTOImageSearchResult trainingButtonResult = emuManager
            .searchTemplate(EMULATOR_NUMBER, EnumTemplates.TRAINING_TRAIN_BUTTON, 90);

        if (trainingButtonResult.isFound()) {
            logInfo("Normal training button found. Executing training.");

            emuManager.tapAtRandomPoint(EMULATOR_NUMBER,
                trainingButtonResult.getPoint(), trainingButtonResult.getPoint());
            sleepTask(500);
        } else {
            logInfo("Training button not found. No training is available at the moment.");
        }
    }

    /**
     * Extract the next training completion time from the UI
     * @return Optional containing the next training time, or empty if extraction failed
     */
    private Optional<LocalDateTime> extractNextTime() {
        try {
            // OCR region containing training completion time
            String text = emuManager.ocrRegionText(EMULATOR_NUMBER,
                new DTOPoint(410, 997), new DTOPoint(586, 1048));

            LocalDateTime nextTime = addTimeToLocalDateTime(LocalDateTime.now(), text);
            logInfo("Successfully extracted next training time: " + nextTime);
            return Optional.of(nextTime);

        } catch (IOException | TesseractException e) {
            logError("Error during OCR while extracting training time.", e);
            return Optional.empty();
        } catch (Exception e) {
            logError("An unexpected error occurred while extracting training time.", e);
            return Optional.empty();
        }
    }

    /**
     * Parse training time from OCR text
     * @param input The OCR text containing time information
     * @return LocalDateTime when training will complete
     */
    public LocalDateTime parseTime(String input) {
        return addTimeToLocalDateTime(LocalDateTime.now(), input);
    }

    // ===============================
    // CONFIGURATION AND UTILITY METHODS
    // ===============================

    /**
     * Get the list of troop templates for a specific troop type
     * @param type The troop type to get templates for
     * @return List of templates ordered from highest to lowest tier
     */
    public List<EnumTemplates> getTroopsTemplates(TroopType type) {
        List<EnumTemplates> templates = new ArrayList<>();
        return switch (type) {
            case INFANTRY -> {
                templates.add(EnumTemplates.TRAINING_INFANTRY_T11);
                templates.add(EnumTemplates.TRAINING_INFANTRY_T10);
                templates.add(EnumTemplates.TRAINING_INFANTRY_T9);
                templates.add(EnumTemplates.TRAINING_INFANTRY_T8);
                templates.add(EnumTemplates.TRAINING_INFANTRY_T7);
                templates.add(EnumTemplates.TRAINING_INFANTRY_T6);
                templates.add(EnumTemplates.TRAINING_INFANTRY_T5);
                templates.add(EnumTemplates.TRAINING_INFANTRY_T4);
                templates.add(EnumTemplates.TRAINING_INFANTRY_T3);
                templates.add(EnumTemplates.TRAINING_INFANTRY_T2);
                templates.add(EnumTemplates.TRAINING_INFANTRY_T1);
                yield templates;
            }
            case LANCER -> {
                templates.add(EnumTemplates.TRAINING_LANCER_T11);
                templates.add(EnumTemplates.TRAINING_LANCER_T10);
                templates.add(EnumTemplates.TRAINING_LANCER_T9);
                templates.add(EnumTemplates.TRAINING_LANCER_T8);
                templates.add(EnumTemplates.TRAINING_LANCER_T7);
                templates.add(EnumTemplates.TRAINING_LANCER_T6);
                templates.add(EnumTemplates.TRAINING_LANCER_T5);
                templates.add(EnumTemplates.TRAINING_LANCER_T4);
                templates.add(EnumTemplates.TRAINING_LANCER_T3);
                templates.add(EnumTemplates.TRAINING_LANCER_T2);
                templates.add(EnumTemplates.TRAINING_LANCER_T1);
                yield templates;
            }
            case MARKSMAN -> {
                templates.add(EnumTemplates.TRAINING_MARKSMAN_T11);
                templates.add(EnumTemplates.TRAINING_MARKSMAN_T10);
                templates.add(EnumTemplates.TRAINING_MARKSMAN_T9);
                templates.add(EnumTemplates.TRAINING_MARKSMAN_T8);
                templates.add(EnumTemplates.TRAINING_MARKSMAN_T7);
                templates.add(EnumTemplates.TRAINING_MARKSMAN_T6);
                templates.add(EnumTemplates.TRAINING_MARKSMAN_T5);
                templates.add(EnumTemplates.TRAINING_MARKSMAN_T4);
                templates.add(EnumTemplates.TRAINING_MARKSMAN_T3);
                templates.add(EnumTemplates.TRAINING_MARKSMAN_T2);
                templates.add(EnumTemplates.TRAINING_MARKSMAN_T1);
                yield templates;
            }
        };
    }

    /**
     * Get the screen coordinates for OCR reading of troop status
     * @param type The troop type to get coordinates for
     * @return Array of DTOPoint containing top-left and bottom-right coordinates
     */
    public DTOPoint[] getTroopsPoints(TroopType type) {
        return switch (type) {
            case INFANTRY -> new DTOPoint[]{new DTOPoint(142, 557), new DTOPoint(340, 592)};
            case LANCER -> new DTOPoint[]{new DTOPoint(142, 628), new DTOPoint(340, 663)};
            case MARKSMAN -> new DTOPoint[]{new DTOPoint(142, 705), new DTOPoint(340, 737)};
        };
    }

    // ===============================
    // TASK FRAMEWORK OVERRIDES
    // ===============================

    @Override
    protected Object getDistinctKey() {
        return troopType;
    }

    @Override
    public boolean provideDailyMissionProgress() {
        return true;
    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }

    // ===============================
    // INNER CLASSES
    // ===============================

    /**
     * Enumeration of available troop types for training
     */
    public enum TroopType {
        INFANTRY(EnumTemplates.GAME_HOME_SHORTCUTS_INFANTRY),
        LANCER(EnumTemplates.GAME_HOME_SHORTCUTS_LANCER),
        MARKSMAN(EnumTemplates.GAME_HOME_SHORTCUTS_MARKSMAN);

        private final EnumTemplates template;

        TroopType(EnumTemplates template) {
            this.template = template;
        }

        public EnumTemplates getTemplate() {
            return template;
        }
    }

}
