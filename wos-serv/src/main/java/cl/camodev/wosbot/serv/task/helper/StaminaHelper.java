package cl.camodev.wosbot.serv.task.helper;

import cl.camodev.utiles.UtilTime;
import cl.camodev.utiles.number.NumberConverters;
import cl.camodev.utiles.number.NumberValidators;
import cl.camodev.utiles.ocr.TextRecognitionRetrier;
import cl.camodev.utiles.time.TimeConverters;
import cl.camodev.utiles.time.TimeValidators;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.logging.ProfileLogger;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.StaminaService;
import cl.camodev.wosbot.serv.task.constants.CommonGameAreas;
import cl.camodev.wosbot.serv.task.constants.CommonOCRSettings;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Helper class for managing stamina-related operations in the game.
 * 
 * <p>
 * This helper encapsulates all stamina management functionality including:
 * <ul>
 * <li>Reading current stamina from profile screen</li>
 * <li>Reading spent stamina from deployment screen</li>
 * <li>Updating stamina service with current values</li>
 * <li>Calculating stamina regeneration time</li>
 * <li>Checking stamina availability for tasks</li>
 * <li>Parsing travel time for march calculations</li>
 * </ul>
 * 
 * <p>
 * All stamina values are tracked via {@link StaminaService} for consistency
 * across tasks and profiles.
 * 
 * @author WoS Bot
 * @see StaminaService
 * @see CommonGameAreas
 * @see CommonOCRSettings
 */
public class StaminaHelper {

    private static final int STAMINA_REGENERATION_MINUTES_PER_POINT = 5;
    private static final int DEFAULT_SOLO_STAMINA_COST = 10;
    private static final int DEFAULT_RALLY_STAMINA_COST = 25;

    private final EmulatorManager emuManager;
    private final String emulatorNumber;
    private final TextRecognitionRetrier<Integer> integerHelper;
    private final TextRecognitionRetrier<Duration> durationHelper;
    private final StaminaService staminaService;
    private final Long profileId;
    private final ProfileLogger logger;
    private final MarchHelper marchHelper;
    private final String profileName;
    private final ServLogs servLogs;
    private static final String HELPER_NAME = "StaminaHelper";

    /**
     * Constructs a new StaminaHelper.
     * 
     * @param emuManager     The emulator manager instance
     * @param emulatorNumber The identifier for the emulator
     * @param integerHelper  OCR helper for reading integer values
     * @param durationHelper OCR helper for reading duration values
     * @param profile        The profile this helper operates on
     * @param marchHelper    Helper for checking march availability
     */
    public StaminaHelper(
            EmulatorManager emuManager,
            String emulatorNumber,
            TextRecognitionRetrier<Integer> integerHelper,
            TextRecognitionRetrier<Duration> durationHelper,
            DTOProfiles profile,
            MarchHelper marchHelper) {
        this.emuManager = emuManager;
        this.emulatorNumber = emulatorNumber;
        this.integerHelper = integerHelper;
        this.durationHelper = durationHelper;
        this.staminaService = StaminaService.getServices();
        this.profileId = profile.getId();
        this.logger = new ProfileLogger(StaminaHelper.class, profile);
        this.marchHelper = marchHelper;
        this.profileName = profile.getName();
        this.servLogs = ServLogs.getServices();
    }

    /**
     * Updates the stamina value by reading it from the profile screen.
     * 
     * <p>
     * <b>Navigation Sequence:</b>
     * <ol>
     * <li>Tap profile avatar to open profile screen</li>
     * <li>Tap stamina icon to view stamina details</li>
     * <li>Perform OCR to read current stamina fraction (e.g., "45/120")</li>
     * <li>Update StaminaService with parsed value</li>
     * <li>Navigate back to previous screen</li>
     * </ol>
     * 
     * <p>
     * If OCR fails to read stamina, the service value remains unchanged.
     */
    public void updateStaminaFromProfile() {
        logDebug("Opening profile to update stamina");

        // Navigate to profile screen
        emuManager.tapAtRandomPoint(
                emulatorNumber,
                CommonGameAreas.PROFILE_AVATAR.topLeft(),
                CommonGameAreas.PROFILE_AVATAR.bottomRight(),
                1,
                500 // Wait for profile screen to open
        );

        // Open stamina details
        emuManager.tapAtRandomPoint(
                emulatorNumber,
                CommonGameAreas.STAMINA_BUTTON.topLeft(),
                CommonGameAreas.STAMINA_BUTTON.bottomRight(),
                1,
                500 // Wait for stamina popup
        );

        // Read stamina via OCR
        Integer stamina = integerHelper.execute(
                CommonGameAreas.STAMINA_OCR_AREA.topLeft(),
                CommonGameAreas.STAMINA_OCR_AREA.bottomRight(),
                5, // Max retry attempts
                200L, // Delay between retries
                CommonOCRSettings.STAMINA_FRACTION_SETTINGS,
                NumberValidators::isFractionFormat,
                NumberConverters::fractionToFirstInt);

        if (stamina != null) {
            logInfo("Stamina: " + stamina);
            staminaService.setStamina(profileId, stamina);
        } else {
            logWarning("Failed to read stamina from profile screen");
        }

        // Navigate back
        emuManager.tapBackButton(emulatorNumber);
        emuManager.tapBackButton(emulatorNumber);
    }

    /**
     * Reads the spent stamina amount from the deployment screen.
     * 
     * <p>
     * This should be called while on the deployment confirmation screen
     * to determine how much stamina will be consumed by the action.
     * 
     * @return The stamina cost, or null if OCR failed
     */
    public Integer getSpentStamina() {
        Integer spentStamina = integerHelper.execute(
                CommonGameAreas.SPENT_STAMINA_OCR_AREA.topLeft(),
                CommonGameAreas.SPENT_STAMINA_OCR_AREA.bottomRight(),
                5, // Max retry attempts
                200L, // Delay between retries
                CommonOCRSettings.SPENT_STAMINA_SETTINGS,
                text -> NumberValidators.matchesPattern(text, CommonOCRSettings.NUMBER_PATTERN),
                text -> NumberConverters.regexToInt(text, CommonOCRSettings.NUMBER_PATTERN));

        if (spentStamina != null) {
            logDebug("Spent stamina: " + spentStamina);
        } else {
            logDebug("Failed to read spent stamina from deployment screen");
        }

        return spentStamina;
    }

    /**
     * Subtracts stamina from the profile's current total.
     * 
     * <p>
     * If the exact stamina cost cannot be determined, uses default values:
     * <ul>
     * <li>Solo actions: {@value DEFAULT_SOLO_STAMINA_COST} stamina</li>
     * <li>Rally actions: {@value DEFAULT_RALLY_STAMINA_COST} stamina</li>
     * </ul>
     * 
     * @param spentStamina The actual stamina spent (from OCR), or null to use
     *                     default
     * @param rally        Whether this is a rally action (true) or solo action
     *                     (false)
     */
    public void subtractStamina(Integer spentStamina, boolean rally) {
        if (spentStamina != null) {
            logDebug("Stamina decreased by " + spentStamina +
                    ". Current: " + (getCurrentStamina() - spentStamina));
            staminaService.subtractStamina(profileId, spentStamina);
            return;
        }

        // Use default values if we couldn't parse stamina
        int defaultStamina = rally ? DEFAULT_RALLY_STAMINA_COST : DEFAULT_SOLO_STAMINA_COST;
        logDebug("No stamina value found. Using default: " + defaultStamina);
        staminaService.subtractStamina(profileId, defaultStamina);
    }

    /**
     * Adds stamina to the profile's current total.
     * 
     * <p>
     * This is typically used when claiming stamina rewards or items.
     * 
     * @param stamina The amount of stamina to add, or null to do nothing
     */
    public void addStamina(Integer stamina) {
        if (stamina == null) {
            return;
        }

        logDebug("Adding " + stamina + " stamina. New total: " +
                (getCurrentStamina() + stamina));
        staminaService.addStamina(profileId, stamina);
    }

    /**
     * Calculates the time (in minutes) required to regenerate from current to
     * target stamina.
     * 
     * <p>
     * Stamina regenerates at a rate of 1 point per
     * {@value STAMINA_REGENERATION_MINUTES_PER_POINT} minutes.
     * 
     * @param currentStamina The current stamina amount
     * @param targetStamina  The desired stamina amount
     * @return Minutes required to reach target, or 0 if already at or above target
     */
    public int staminaRegenerationTime(int currentStamina, int targetStamina) {
        if (currentStamina >= targetStamina) {
            return 0;
        }

        int staminaNeeded = targetStamina - currentStamina;
        int minutes = staminaNeeded * STAMINA_REGENERATION_MINUTES_PER_POINT;

        logDebug("Stamina regeneration: " + staminaNeeded +
                " points = " + minutes + " minutes");

        return minutes;
    }

    /**
     * Gets the current stamina value for this profile from the service.
     * 
     * @return The current stamina amount
     */
    public int getCurrentStamina() {
        return staminaService.getCurrentStamina(profileId);
    }

    /**
     * Checks if sufficient stamina and marches are available, or reschedules if
     * not.
     * 
     * <p>
     * This method performs two checks:
     * <ol>
     * <li>Verifies current stamina meets the minimum requirement</li>
     * <li>Verifies at least one march slot is available</li>
     * </ol>
     * 
     * <p>
     * If either check fails, the method returns false and the caller should
     * exit the task (it will be rescheduled automatically).
     * 
     * @param minStaminaLevel     Minimum stamina required to proceed
     * @param refreshStaminaLevel Target stamina for rescheduling
     * @param rescheduleCallback  Callback to reschedule the task
     * @return true if requirements are met, false if task was rescheduled
     */
    public boolean checkStaminaAndMarchesOrReschedule(
            int minStaminaLevel,
            int refreshStaminaLevel,
            RescheduleCallback rescheduleCallback) {

        int currentStamina = getCurrentStamina();
        logInfo("Current stamina: " + currentStamina);

        if (currentStamina < minStaminaLevel) {
            int regenMinutes = staminaRegenerationTime(currentStamina, refreshStaminaLevel);
            LocalDateTime rescheduleTime = LocalDateTime.now().plusMinutes(regenMinutes);

            rescheduleCallback.reschedule(rescheduleTime);

            logWarning("Not enough stamina (" + currentStamina +
                    "/" + minStaminaLevel + "). Rescheduling to " +
                    UtilTime.localDateTimeToDDHHMMSS(rescheduleTime));
            return false;
        }

        if (!marchHelper.checkMarchesAvailable()) {
            LocalDateTime rescheduleTime = LocalDateTime.now().plusMinutes(1);
            rescheduleCallback.reschedule(rescheduleTime);

            logWarning("No marches available, rescheduling for 1 minute");
            return false;
        }

        return true;
    }

    /**
     * Checks if the current stamina meets the minimum requirement, otherwise reschedules.
     *
     * @param minStaminaLevel Minimum stamina required to proceed
     * @param refreshStaminaLevel Target stamina for rescheduling
     * @param rescheduleCallback Callback to reschedule the task
     * @return true if stamina is sufficient, false if rescheduled
     */
    public boolean checkStaminaOrReschedule(
            int minStaminaLevel,
            int refreshStaminaLevel,
            RescheduleCallback rescheduleCallback) {

        int currentStamina = getCurrentStamina();
        logger.info("Current stamina: " + currentStamina);

        if (currentStamina < minStaminaLevel) {
            int regenMinutes = staminaRegenerationTime(currentStamina, refreshStaminaLevel);
            LocalDateTime rescheduleTime = LocalDateTime.now().plusMinutes(regenMinutes);

            rescheduleCallback.reschedule(rescheduleTime);

            logger.warn("Not enough stamina. (" + currentStamina + "/" + minStaminaLevel +
                    "). Rescheduling to " + UtilTime.localDateTimeToDDHHMMSS(rescheduleTime));

            return false;
        }

        return true;
    }

    /**
     * Parses the travel time displayed on the deployment screen.
     * 
     * <p>
     * Travel time is read via OCR from the march preview and converted
     * to total seconds for scheduling calculations.
     * 
     * @return Travel time in seconds, or 0 if OCR failed
     */
    public long parseTravelTime() {
        Duration marchingTime = durationHelper.execute(
                CommonGameAreas.TRAVEL_TIME_OCR_AREA.topLeft(),
                CommonGameAreas.TRAVEL_TIME_OCR_AREA.bottomRight(),
                3, // Max retry attempts
                200L, // Delay between retries
                CommonOCRSettings.TRAVEL_TIME_SETTINGS,
                TimeValidators::isValidTime,
                TimeConverters::toDuration);

        if (marchingTime != null) {
            long seconds = marchingTime.getSeconds();
            logDebug("Travel time: " + seconds + " seconds");
            return seconds;
        }

        logWarning("Failed to parse travel time");
        return 0;
    }

    /**
     * Callback interface for rescheduling tasks.
     * Used to decouple StaminaHelper from DelayedTask dependencies.
     */
    @FunctionalInterface
    public interface RescheduleCallback {
        void reschedule(LocalDateTime time);
    }

    // ========================================================================
    // LOGGING METHODS
    // ========================================================================

    private void logInfo(String message) {
        String prefixedMessage = profileName + " - " + message;
        logger.info(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.INFO, HELPER_NAME, profileName, message);
    }

    private void logWarning(String message) {
        String prefixedMessage = profileName + " - " + message;
        logger.warn(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.WARNING, HELPER_NAME, profileName, message);
    }

    @SuppressWarnings("unused")
    private void logError(String message) {
        String prefixedMessage = profileName + " - " + message;
        logger.error(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.ERROR, HELPER_NAME, profileName, message);
    }

    private void logDebug(String message) {
        String prefixedMessage = profileName + " - " + message;
        logger.debug(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.DEBUG, HELPER_NAME, profileName, message);
    }
}
