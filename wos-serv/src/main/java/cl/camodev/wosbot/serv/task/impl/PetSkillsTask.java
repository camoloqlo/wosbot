package cl.camodev.wosbot.serv.task.impl;

import java.awt.Color;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import cl.camodev.utiles.UtilTime;
import cl.camodev.utiles.number.NumberConverters;
import cl.camodev.utiles.number.NumberValidators;
import cl.camodev.utiles.time.TimeConverters;
import cl.camodev.utiles.time.TimeValidators;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOArea;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.impl.StaminaService;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;

/**
 * Unified Pet Skills task that processes all enabled pet skills in a single
 * execution.
 * 
 * <p>
 * This task:
 * <ul>
 * <li>Opens the Pets menu</li>
 * <li>Processes each enabled pet skill (Stamina, Food, Treasure,
 * Gathering)</li>
 * <li>Uses skills if available (off cooldown)</li>
 * <li>Reads cooldown timers for each skill</li>
 * <li>Reschedules to the earliest cooldown time among all skills</li>
 * </ul>
 * 
 * <p>
 * <b>Skill Types:</b>
 * <ul>
 * <li>Stamina: Adds stamina based on skill level (35 + (level-1)*5)</li>
 * <li>Food: Increases food production</li>
 * <li>Treasure: Provides resource rewards</li>
 * <li>Gathering: Increases gathering speed</li>
 * </ul>
 * 
 * <p>
 * <b>Execution Flow:</b>
 * <ol>
 * <li>Load configuration to determine which skills are enabled</li>
 * <li>Validate at least one skill is enabled</li>
 * <li>Open Pets menu (with retry logic)</li>
 * <li>For each enabled skill: tap icon, check state, use if available, read
 * cooldown</li>
 * <li>Reschedule to earliest cooldown or fallback time</li>
 * </ol>
 * 
 * <p>
 * <b>Rescheduling Logic:</b>
 * <ul>
 * <li>If any cooldown is successfully read: reschedule to earliest
 * cooldown</li>
 * <li>If all OCR fails: reschedule in 5 minutes as fallback</li>
 * <li>If no skills enabled: reschedule to game reset</li>
 * </ul>
 */
public class PetSkillsTask extends DelayedTask {

    // ========== Pet Skills Menu Coordinates ==========
    // Skill icon regions (for tapping on the pets menu screen)
    private static final DTOPoint STAMINA_SKILL_TOP_LEFT = new DTOPoint(240, 260);
    private static final DTOPoint STAMINA_SKILL_BOTTOM_RIGHT = new DTOPoint(320, 350);
    private static final DTOPoint GATHERING_SKILL_TOP_LEFT = new DTOPoint(380, 260);
    private static final DTOPoint GATHERING_SKILL_BOTTOM_RIGHT = new DTOPoint(460, 350);
    private static final DTOPoint FOOD_SKILL_TOP_LEFT = new DTOPoint(540, 260);
    private static final DTOPoint FOOD_SKILL_BOTTOM_RIGHT = new DTOPoint(620, 350);
    private static final DTOPoint TREASURE_SKILL_TOP_LEFT = new DTOPoint(240, 410);
    private static final DTOPoint TREASURE_SKILL_BOTTOM_RIGHT = new DTOPoint(320, 490);

    // ========== Skill Details UI (overlay on pets menu) ==========
    private static final DTOArea TREASURE_COOLDOWN_OCR_AREA = new DTOArea(
            new DTOPoint(231, 428),
            new DTOPoint(330, 470));
    private static final DTOArea GATHERING_COOLDOWN_OCR_AREA = new DTOArea(
            new DTOPoint(379, 292),
            new DTOPoint(474, 314));
    private static final DTOArea FOOD_COOLDOWN_OCR_AREA = new DTOArea(
            new DTOPoint(522, 288),
            new DTOPoint(626, 318));
    private static final DTOArea STAMINA_COOLDOWN_OCR_AREA = new DTOArea(
            new DTOPoint(229, 285),
            new DTOPoint(334, 320));
    private static final DTOPoint SKILL_LEVEL_OCR_TOP_LEFT = new DTOPoint(276, 779);
    private static final DTOPoint SKILL_LEVEL_OCR_BOTTOM_RIGHT = new DTOPoint(363, 811);

    // ========== Retry Constants ==========
    private static final int FALLBACK_RESCHEDULE_MINUTES = 5;
    private static final int SKILL_LEVEL_OCR_MAX_RETRIES = 3;
    private static final int OCR_RETRY_DELAY_MS = 200;

    // ========== Stamina Calculation Constants ==========
    private static final int STAMINA_BASE_VALUE = 35;
    private static final int STAMINA_PER_LEVEL = 5;
    private static final int STAMINA_FALLBACK_VALUE = 35; // Level 1 equivalent

    // ========== OCR Settings ==========
    private static final DTOTesseractSettings COOLDOWN_OCR_SETTINGS = DTOTesseractSettings.builder()
            .setAllowedChars("0123456789d:")
            .setRemoveBackground(true)
            .setTextColor(new Color(244, 59, 59))
            .build();

    private static final DTOTesseractSettings SKILL_LEVEL_OCR_SETTINGS = DTOTesseractSettings.builder()
            .setAllowedChars("0123456789")
            .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
            .setRemoveBackground(true)
            .setTextColor(new Color(69, 88, 110))
            .build();

    private static final DTOTesseractSettings GATHERING_SKILL_OCR_SETTINGS = DTOTesseractSettings.builder()
            .setAllowedChars("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")
            .setRemoveBackground(true)
            .setTextColor(new Color(0, 187, 0))
            .build();

    // ========== Configuration (loaded in loadConfiguration()) ==========
    private boolean staminaEnabled;
    private boolean foodEnabled;
    private boolean treasureEnabled;
    private boolean gatheringEnabled;

    // ========== Execution State (reset each execution) ==========
    private int navigationAttempts;
    private LocalDateTime earliestCooldown;

    /**
     * Constructs a new PetSkillsTask.
     *
     * @param profile the profile this task belongs to
     * @param tpTask  the task type enum
     */
    public PetSkillsTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    /**
     * Loads task configuration from the profile.
     * This must be called from execute() to ensure configuration is current.
     * 
     * <p>
     * Loads individual skill enable flags:
     * <ul>
     * <li>Stamina skill enabled/disabled</li>
     * <li>Food skill enabled/disabled</li>
     * <li>Treasure skill enabled/disabled</li>
     * <li>Gathering skill enabled/disabled</li>
     * </ul>
     * 
     * <p>
     * All flags default to false if not configured.
     */
    private void loadConfiguration() {
        this.staminaEnabled = getConfigBoolean(EnumConfigurationKey.PET_SKILL_STAMINA_BOOL, false);
        this.foodEnabled = getConfigBoolean(EnumConfigurationKey.PET_SKILL_FOOD_BOOL, false);
        this.treasureEnabled = getConfigBoolean(EnumConfigurationKey.PET_SKILL_TREASURE_BOOL, false);
        this.gatheringEnabled = getConfigBoolean(EnumConfigurationKey.PET_SKILL_GATHERING_BOOL, false);

        logDebug(String.format("Configuration loaded - Stamina: %s, Food: %s, Treasure: %s, Gathering: %s",
                staminaEnabled, foodEnabled, treasureEnabled, gatheringEnabled));
    }

    /**
     * Helper method to safely retrieve boolean configuration values.
     * 
     * @param key          the configuration key to retrieve
     * @param defaultValue the default value if configuration is not set
     * @return the configured boolean value or default if not set
     */
    private boolean getConfigBoolean(EnumConfigurationKey key, boolean defaultValue) {
        Boolean value = profile.getConfig(key, Boolean.class);
        return (value != null) ? value : defaultValue;
    }

    /**
     * Resets execution-specific state before each run.
     * 
     * <p>
     * Resets:
     * <ul>
     * <li>Navigation attempt counter</li>
     * <li>Earliest cooldown tracker</li>
     * </ul>
     */
    private void resetExecutionState() {
        this.navigationAttempts = 0;
        this.earliestCooldown = null;
        logDebug("Execution state reset");
    }

    /**
     * Main execution method for the Pet Skills task.
     * 
     * <p>
     * Flow:
     * <ol>
     * <li>Load current configuration</li>
     * <li>Reset execution state</li>
     * <li>Build list of enabled skills</li>
     * <li>Validate at least one skill is enabled</li>
     * <li>Open Pets menu</li>
     * <li>Process all enabled skills</li>
     * <li>Close Pets menu</li>
     * <li>Reschedule based on cooldowns</li>
     * </ol>
     * 
     * <p>
     * Rescheduling:
     * <ul>
     * <li>If no skills enabled: reschedule to game reset</li>
     * <li>If menu open fails: reschedule in 5 minutes</li>
     * <li>If cooldowns read: reschedule to earliest</li>
     * <li>If no cooldowns read: reschedule in 5 minutes</li>
     * </ul>
     */
    @Override
    protected void execute() {
        loadConfiguration();
        resetExecutionState();

        List<PetSkill> enabledSkills = buildEnabledSkillsList();

        if (enabledSkills.isEmpty()) {
            handleNoSkillsEnabled();
            return;
        }

        logInfo(String.format("Starting Pet Skills task for %d skill(s).", enabledSkills.size()));

        if (!openPetsMenu()) {
            handleMenuOpenFailure();
            return;
        }

        processAllSkills(enabledSkills);
        closePetsMenu();
        finalizeRescheduling();
    }

    /**
     * Handles the case where no pet skills are enabled.
     * Reschedules the task to retry at game reset.
     */
    private void handleNoSkillsEnabled() {
        logInfo("No pet skills enabled. Rescheduling to retry at reset.");
        reschedule(UtilTime.getGameReset());
    }

    /**
     * Handles failure to open the Pets menu.
     * Reschedules the task to retry in a few minutes.
     */
    private void handleMenuOpenFailure() {
        logWarning("Failed to open Pets menu. Rescheduling for retry.");
        reschedule(LocalDateTime.now().plusMinutes(FALLBACK_RESCHEDULE_MINUTES));
    }

    /**
     * Builds a list of enabled pet skills based on current configuration.
     * 
     * @return list of PetSkill enums that are enabled, may be empty
     */
    private List<PetSkill> buildEnabledSkillsList() {
        List<PetSkill> skills = new ArrayList<>();

        if (staminaEnabled) {
            skills.add(PetSkill.STAMINA);
        }
        if (foodEnabled) {
            skills.add(PetSkill.FOOD);
        }
        if (treasureEnabled) {
            skills.add(PetSkill.TREASURE);
        }
        if (gatheringEnabled) {
            skills.add(PetSkill.GATHERING);
        }

        logDebug("Enabled skills: " + skills);
        return skills;
    }

    /**
     * Opens the Pets menu by searching for and tapping the Pets button.
     * 
     * <p>
     * Includes retry logic up to MAX_NAVIGATION_ATTEMPTS.
     * 
     * @return true if menu opened successfully, false after max retries
     */
    private boolean openPetsMenu() {
        logDebug("Opening Pets menu");

        DTOImageSearchResult petsButton = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_HOME_PETS,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (!petsButton.isFound()) {
            navigationAttempts++;

            if (navigationAttempts >= 3) { // Max navigation attempts
                logError("Could not find Pets menu after 3 attempts.");
                return false;
            }

            logWarning("Pets button not found (attempt " + navigationAttempts + "/3).");
            return false;
        }

        logInfo("Pets button found. Opening menu.");
        tapPoint(petsButton.getPoint());
        sleepTask(1000); // Wait for Pets menu to load

        return true;
    }

    /**
     * Processes all enabled pet skills sequentially.
     * 
     * <p>
     * For each skill:
     * <ul>
     * <li>Taps skill icon to show details overlay</li>
     * <li>Checks if skill is learned and unlocked</li>
     * <li>Uses skill if available</li>
     * <li>Reads and tracks cooldown timer</li>
     * </ul>
     * 
     * @param enabledSkills list of skills to process
     */
    private void processAllSkills(List<PetSkill> enabledSkills) {
        for (PetSkill skill : enabledSkills) {
            logInfo("Processing " + skill.name() + " skill.");
            processSkill(skill);
        }
    }

    /**
     * Processes a single pet skill.
     * 
     * <p>
     * Flow:
     * <ol>
     * <li>Taps skill icon to show details overlay</li>
     * <li>Checks if skill is learned (returns early if not)</li>
     * <li>Checks if skill is locked (returns early if locked)</li>
     * <li>For gathering skill: checks if already Active and proceeds with
     * deployment</li>
     * <li>Attempts to use skill if Use button is visible</li>
     * <li>Reads cooldown timer and tracks earliest cooldown</li>
     * </ol>
     * 
     * <p>
     * Note: All skill details appear as overlays on the same pets menu screen.
     * No explicit navigation back is needed between skills.
     * 
     * @param skill the skill to process
     */
    private void processSkill(PetSkill skill) {
        tapSkillIcon(skill);

        if (!isSkillLearned(skill)) {
            return;
        }

        if (isSkillLocked(skill)) {
            return;
        }

        // Special handling for gathering skill: check if already Active
        if (skill == PetSkill.GATHERING && isGatheringSkillActive()) {
            logInfo("Gathering skill is already Active. Proceeding with deployment flow.");
            deployGatheringSkillMarch();
            readAndTrackCooldown(skill);
            return;
        }

        boolean skillUsed = tryUseSkill(skill);
        if (skillUsed) {
            logInfo(skill.name() + " skill used successfully.");
        } else {
            logDebug(skill.name() + " skill is on cooldown.");
        }

        readAndTrackCooldown(skill);
    }

    /**
     * Taps the skill icon to display its details overlay.
     * 
     * @param skill the skill whose icon to tap
     */
    private void tapSkillIcon(PetSkill skill) {
        tapRandomPoint(skill.getTopLeft(), skill.getBottomRight());
        sleepTask(300); // Wait for details overlay to appear
    }

    /**
     * Checks if a skill is learned by looking for the info/skills indicator.
     * 
     * @param skill the skill to check
     * @return true if skill is learned, false otherwise
     */
    private boolean isSkillLearned(PetSkill skill) {
        DTOImageSearchResult infoSkill = templateSearchHelper.searchTemplate(
                EnumTemplates.PETS_INFO_SKILLS,
                SearchConfigConstants.QUICK_SEARCH);

        if (!infoSkill.isFound()) {
            logInfo(skill.name() + " skill not learned yet. Skipping.");
            return false;
        }

        return true;
    }

    /**
     * Checks if a skill is locked (requires unlocking).
     * 
     * @param skill the skill to check
     * @return true if skill is locked, false if unlocked
     */
    private boolean isSkillLocked(PetSkill skill) {
        DTOImageSearchResult unlockText = templateSearchHelper.searchTemplate(
                EnumTemplates.PETS_UNLOCK_TEXT,
                SearchConfigConstants.QUICK_SEARCH);

        if (unlockText.isFound()) {
            logInfo(skill.name() + " skill is locked. Skipping.");
            return true;
        }

        return false;
    }

    /**
     * Attempts to use a skill if the Use button is visible.
     * 
     * <p>
     * If the Use button is not found, the skill is assumed to be on cooldown.
     * 
     * <p>
     * Special handling for Stamina skill: adds stamina to profile after use.
     * 
     * @param skill the skill to use
     * @return true if skill was used, false if on cooldown
     */
    private boolean tryUseSkill(PetSkill skill) {
        DTOImageSearchResult useButton = templateSearchHelper.searchTemplate(
                EnumTemplates.PETS_SKILL_USE,
                SearchConfigConstants.QUICK_SEARCH);

        if (!useButton.isFound()) {
            return false;
        }

        logDebug("Use button found. Using skill.");
        tapRandomPoint(
                useButton.getPoint(),
                useButton.getPoint(),
                3, // Number of taps
                100); // Delay between taps in ms

        sleepTask(1500); // Wait for skill use animation

        if (skill == PetSkill.STAMINA) {
            addStaminaBySkillLevel();
        } else if (skill == PetSkill.GATHERING) {
            // Deploy gathering march as part of gathering skill activation
            deployGatheringSkillMarch();
        }

        return true;
    }

    /**
     * Reads the cooldown timer for a skill and tracks the earliest cooldown.
     * 
     * <p>
     * Uses OCR to read the cooldown display and convert to Duration.
     * Updates the earliestCooldown field if this cooldown is sooner.
     * 
     * <p>
     * If OCR fails, logs a warning and continues without updating cooldown.
     * 
     * @param skill the skill whose cooldown to read
     */
    private void readAndTrackCooldown(PetSkill skill) {
        Duration cooldownDuration;

        switch (skill) {
            case STAMINA:
                cooldownDuration = readSkillCooldown(STAMINA_COOLDOWN_OCR_AREA);
                break;

            case FOOD:
                cooldownDuration = readSkillCooldown(FOOD_COOLDOWN_OCR_AREA);
                break;

            case TREASURE:
                cooldownDuration = readSkillCooldown(TREASURE_COOLDOWN_OCR_AREA);
                break;

            case GATHERING:
                cooldownDuration = readSkillCooldown(GATHERING_COOLDOWN_OCR_AREA);
                break;

            default:
                cooldownDuration = null;
        }

        if (cooldownDuration == null) {
            logWarning("Failed to read cooldown for " + skill.name() + ". Using 5 minute fallback cooldown.");
            cooldownDuration = Duration.ofMinutes(5);
        }

        LocalDateTime cooldownEnd = LocalDateTime.now().plus(cooldownDuration);

        logInfo(String.format("%s skill cooldown until: %s (in %s)",
                skill.name(),
                cooldownEnd.format(DATETIME_FORMATTER),
                UtilTime.localDateTimeToDDHHMMSS(cooldownEnd)));

        updateEarliestCooldown(cooldownEnd);
    }

    /**
     * Reads the cooldown duration from the UI using OCR for a specific skill area.
     * 
     * @param area The area containing the cooldown text
     * @return Duration representing the cooldown time, or null if OCR fails
     */
    private Duration readSkillCooldown(DTOArea area) {
        return durationHelper.execute(
                area.topLeft(),
                area.bottomRight(),
                5, // Max retries
                200L, // Retry delay in ms
                COOLDOWN_OCR_SETTINGS,
                TimeValidators::isValidTime,
                TimeConverters::toDuration);
    }

    /**
     * Checks if the gathering skill is currently in "Active" state.
     * 
     * <p>
     * The gathering skill displays green "Active" text when it's currently deployed
     * in the gathering process. This method uses OCR to detect this state.
     * 
     * @return true if gathering skill shows "Active", false otherwise
     */
    private boolean isGatheringSkillActive() {
        String text = stringHelper.execute(
                GATHERING_COOLDOWN_OCR_AREA.topLeft(),
                GATHERING_COOLDOWN_OCR_AREA.bottomRight(),
                3, // Max retries
                200L, // Retry delay in ms
                GATHERING_SKILL_OCR_SETTINGS,
                s -> !s.isEmpty(), // Accept any text
                s -> s.trim().toUpperCase()); // Return raw text

        if (text == null || text.isEmpty()) {
            logDebug("Could not read gathering skill status - OCR returned null or empty");
            return false;
        }

        boolean isActive = text.contains("ACTIVE");

        if (isActive) {
            logDebug("Gathering skill is Active: " + text);
        }

        return isActive;
    }

    /**
     * Updates the earliest cooldown tracker if the provided cooldown is sooner.
     * 
     * @param cooldownEnd the cooldown end time to compare
     */
    private void updateEarliestCooldown(LocalDateTime cooldownEnd) {
        if (earliestCooldown == null || cooldownEnd.isBefore(earliestCooldown)) {
            earliestCooldown = cooldownEnd;
            logDebug("Updated earliest cooldown: " + earliestCooldown.format(DATETIME_FORMATTER));
        }
    }

    /**
     * Adds stamina based on the skill level displayed in the UI.
     * 
     * <p>
     * Formula: 35 + (level - 1) * 5
     * 
     * <p>
     * If OCR fails to read the skill level, uses a fallback value of 35 (level 1
     * equivalent).
     */
    private void addStaminaBySkillLevel() {
        Integer level = readSkillLevel();

        int staminaToAdd;
        if (level == null) {
            staminaToAdd = STAMINA_FALLBACK_VALUE;
            logWarning("Failed to read Stamina skill level. Using fallback value: " + staminaToAdd);
        } else {
            staminaToAdd = calculateStaminaForLevel(level);
            logInfo(String.format("Stamina skill level: %d. Added %d stamina.", level, staminaToAdd));
        }

        StaminaService.getServices().addStamina(profile.getId(), staminaToAdd);
    }

    /**
     * Reads the skill level from the UI using OCR.
     * 
     * @return the skill level as an Integer, or null if OCR fails
     */
    private Integer readSkillLevel() {
        return integerHelper.execute(
                SKILL_LEVEL_OCR_TOP_LEFT,
                SKILL_LEVEL_OCR_BOTTOM_RIGHT,
                SKILL_LEVEL_OCR_MAX_RETRIES,
                OCR_RETRY_DELAY_MS,
                SKILL_LEVEL_OCR_SETTINGS,
                text -> NumberValidators.matchesPattern(text, Pattern.compile(".*?(\\d+).*")),
                text -> NumberConverters.regexToInt(text, Pattern.compile(".*?(\\d+).*")));
    }

    /**
     * Calculates stamina amount for a given skill level.
     * 
     * <p>
     * Formula: 35 + (level - 1) * 5
     * 
     * @param level the skill level (must be >= 1)
     * @return the stamina amount for that level
     */
    private int calculateStaminaForLevel(int level) {
        return STAMINA_BASE_VALUE + (level - 1) * STAMINA_PER_LEVEL;
    }

    /**
     * Closes the Pets menu by tapping the back button.
     */
    private void closePetsMenu() {
        logDebug("Closing Pets menu");
        tapBackButton();
        sleepTask(500); // Wait for menu to close
    }

    /**
     * Finalizes rescheduling based on earliest cooldown among all skills.
     * 
     * <p>
     * Rescheduling logic:
     * <ul>
     * <li>If any cooldown was successfully read: reschedule to earliest
     * cooldown</li>
     * <li>If all OCR failed: reschedule in FALLBACK_RESCHEDULE_MINUTES as
     * fallback</li>
     * </ul>
     */
    private void finalizeRescheduling() {
        if (earliestCooldown != null) {
            logInfo("Rescheduling Pet Skills task for: " +
                    earliestCooldown.format(DATETIME_FORMATTER));
            reschedule(earliestCooldown);
        } else {
            logWarning("No cooldown parsed for any enabled skill. Rescheduling in " +
                    FALLBACK_RESCHEDULE_MINUTES + " minutes.");
            reschedule(LocalDateTime.now().plusMinutes(FALLBACK_RESCHEDULE_MINUTES));
        }
    }

    /**
     * Specifies that this task can start from any screen location.
     * The task will handle navigation to the pets menu internally.
     * 
     * @return EnumStartLocation.ANY
     */
    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.ANY;
    }

    /**
     * Executes the gathering skill deployment if enabled.
     * 
     * <p>
     * Flow:
     * <ol>
     * <li>Leave pets menu</li>
     * <li>Check for idle marches using MarchHelper</li>
     * <li>If no idle marches, set fallback cooldown and return</li>
     * <li>Open resource search menu</li>
     * <li>Select resource tile based on user configuration</li>
     * <li>Select highest level available</li>
     * <li>Search for tile and deploy march</li>
     * <li>Reopen pets menu to read gathering skill cooldown</li>
     * </ol>
     */
    private void deployGatheringSkillMarch() {
        logInfo("Deploying gathering skill march...");

        try {
            // Step 1: Leave pets menu
            tapBackButton();
            sleepTask(500);

            navigationHelper.ensureCorrectScreenLocation(EnumStartLocation.WORLD);
            sleepTask(500);

            // Step 2: Check for idle marches using MarchHelper
            if (!marchHelper.checkMarchesAvailable()) {
                logWarning("No idle marches available for gathering skill march. Setting 5 minute fallback cooldown.");
                LocalDateTime fallbackCooldown = LocalDateTime.now().plusMinutes(5);
                updateEarliestCooldown(fallbackCooldown);
                return;
            }

            logInfo("Idle march found, proceeding with deployment");
            sleepTask(500);

            // Step 3: Open resource search menu
            if (!openResourceSearchMenu()) {
                logWarning("Failed to open resource search menu");
                LocalDateTime fallbackCooldown = LocalDateTime.now().plusMinutes(5);
                updateEarliestCooldown(fallbackCooldown);
                return;
            }

            // Step 4-7: Select resource tile, set level, execute search, and deploy
            GatheringResourceType resourceType = getConfiguredGatheringResource();
            if (!deployGatheringMarch(resourceType)) {
                logWarning("Failed to deploy gathering march");
                tapBackButton();
                LocalDateTime fallbackCooldown = LocalDateTime.now().plusMinutes(5);
                updateEarliestCooldown(fallbackCooldown);
                return;
            }

            // Step 8: Reopen pets menu to read gathering skill cooldown
            logDebug("Reopening pets menu to read gathering skill cooldown");
            if (openPetsMenu()) {
                // Navigate to gathering skill and read its cooldown
                tapSkillIcon(PetSkill.GATHERING);
                sleepTask(300);
                readAndTrackCooldown(PetSkill.GATHERING);
            } else {
                logWarning("Could not reopen pets menu to read gathering skill cooldown. Using fallback.");
                LocalDateTime fallbackCooldown = LocalDateTime.now().plusMinutes(5);
                updateEarliestCooldown(fallbackCooldown);
            }
        } catch (Exception e) {
            logWarning("Error deploying gathering skill march: " + e.getMessage());
            LocalDateTime fallbackCooldown = LocalDateTime.now().plusMinutes(5);
            updateEarliestCooldown(fallbackCooldown);
        }
    }

    /**
     * Gets the configured resource type for gathering skill.
     * 
     * @return the resource type to gather, defaults to MEAT
     */
    private GatheringResourceType getConfiguredGatheringResource() {
        String resourceConfig = profile.getConfig(
                EnumConfigurationKey.PET_SKILL_GATHERING_RESOURCE_STRING,
                String.class);

        if (resourceConfig == null) {
            return GatheringResourceType.MEAT;
        }

        try {
            return GatheringResourceType.valueOf(resourceConfig.toUpperCase());
        } catch (IllegalArgumentException e) {
            logWarning("Invalid gathering resource configuration: " + resourceConfig + ", using MEAT");
            return GatheringResourceType.MEAT;
        }
    }

    /**
     * Opens the resource search menu by tapping the search button area.
     * 
     * @return true if menu opened successfully, false otherwise
     */
    private boolean openResourceSearchMenu() {
        logDebug("Opening resource search menu");

        tapRandomPoint(new DTOPoint(25, 850), new DTOPoint(67, 898));
        sleepTask(2000); // Wait for search menu to open

        // Swipe left to find resource tiles tab
        swipe(new DTOPoint(678, 913), new DTOPoint(40, 913));
        sleepTask(500); // Wait for swipe animation

        return true;
    }

    /**
     * Deploys a gathering march for the specified resource type.
     * 
     * <p>
     * Flow:
     * <ul>
     * <li>Select resource tile by searching for and tapping the tile template</li>
     * <li>Set level to highest available (with OCR-based adjustment)</li>
     * <li>Execute search</li>
     * <li>Find and tap gather button on map</li>
     * <li>Deploy march</li>
     * </ul>
     * 
     * @param resourceType the resource to gather
     * @return true if march was deployed successfully, false otherwise
     */
    private boolean deployGatheringMarch(GatheringResourceType resourceType) {
        logInfo("Deploying gathering march for: " + resourceType.name());

        try {
            // Step 1: Select resource tile by searching for tile template
            if (!selectResourceTile(resourceType)) {
                logWarning("Failed to select resource tile");
                return false;
            }
            sleepTask(500);

            // Step 2: Set level to highest available
            if (!selectHighestLevel()) {
                logWarning("Failed to set resource level");
                return false;
            }
            sleepTask(500);

            // Step 3: Execute search
            if (!executeResourceSearch()) {
                logWarning("Failed to execute resource search");
                return false;
            }
            sleepTask(500);

            // Step 4: Find and tap gather button on map
            DTOImageSearchResult gatherButton = templateSearchHelper.searchTemplate(
                    EnumTemplates.GAME_HOME_SHORTCUTS_FARM_GATHER,
                    SearchConfigConstants.SINGLE_WITH_RETRIES);

            if (!gatherButton.isFound()) {
                logWarning("Gather button not found. Tile may be occupied.");
                return false;
            }

            logDebug("Tapping gather button");
            tapPoint(gatherButton.getPoint());
            sleepTask(1000); // Wait for march configuration screen

            // Step 5: Deploy the march
            DTOImageSearchResult deployButton = templateSearchHelper.searchTemplate(
                    EnumTemplates.GATHER_DEPLOY_BUTTON,
                    SearchConfigConstants.SINGLE_WITH_RETRIES);

            if (!deployButton.isFound()) {
                logError("Deploy button not found");
                return false;
            }

            logInfo("Deploying gather march");
            tapPoint(deployButton.getPoint());
            sleepTask(1000); // Wait for deployment confirmation

            logInfo(String.format("%s march deployed successfully!", resourceType.name()));
            return true;

        } catch (Exception e) {
            logWarning("Error deploying gathering march: " + e.getMessage());
            return false;
        }
    }

    /**
     * Selects the resource tile by searching for and tapping the tile template.
     * Swipes through resource tabs until the tile is found.
     * 
     * @param resourceType the resource to select
     * @return true if tile was found and selected, false otherwise
     */
    private boolean selectResourceTile(GatheringResourceType resourceType) {
        logDebug(String.format("Searching for %s tile", resourceType.name()));

        final int MAX_SWIPE_ATTEMPTS = 4;

        for (int attempt = 0; attempt < MAX_SWIPE_ATTEMPTS; attempt++) {
            // Get the appropriate tile template based on resource type
            EnumTemplates tileTemplate = getResourceTileTemplate(resourceType);

            DTOImageSearchResult tile = templateSearchHelper.searchTemplate(
                    tileTemplate,
                    SearchConfigConstants.SINGLE_WITH_RETRIES);

            if (tile.isFound()) {
                logInfo(String.format("%s tile found", resourceType.name()));
                tapPoint(tile.getPoint());
                sleepTask(500); // Wait for tile selection
                return true;
            }

            if (attempt < MAX_SWIPE_ATTEMPTS - 1) {
                logDebug(String.format("Tile not found, swiping (attempt %d/%d)",
                        attempt + 1, MAX_SWIPE_ATTEMPTS));
                swipe(new DTOPoint(678, 913), new DTOPoint(40, 913));
                sleepTask(500); // Wait for swipe animation
            }
        }

        logError(String.format("%s tile not found after %d attempts",
                resourceType.name(), MAX_SWIPE_ATTEMPTS));
        return false;
    }

    /**
     * Gets the tile template for a resource type.
     * 
     * @param resourceType the resource type
     * @return the appropriate EnumTemplates tile template
     */
    private EnumTemplates getResourceTileTemplate(GatheringResourceType resourceType) {
        switch (resourceType) {
            case MEAT:
                return EnumTemplates.GAME_HOME_SHORTCUTS_FARM_MEAT;
            case WOOD:
                return EnumTemplates.GAME_HOME_SHORTCUTS_FARM_WOOD;
            case COAL:
                return EnumTemplates.GAME_HOME_SHORTCUTS_FARM_COAL;
            case IRON:
                return EnumTemplates.GAME_HOME_SHORTCUTS_FARM_IRON;
            default:
                return EnumTemplates.GAME_HOME_SHORTCUTS_FARM_MEAT;
        }
    }

    /**
     * Sets the resource level to the highest available.
     * 
     * <p>
     * Reads current level via OCR, then adjusts to maximum by incrementing.
     * If OCR fails, uses backup plan of resetting to level 1 and incrementing to max.
     * 
     * @return true if level was set successfully, false otherwise
     */
    private boolean selectHighestLevel() {
        logInfo("Setting resource level to highest available");

        final int DESIRED_LEVEL = 20; // Highest gather level
        final DTOPoint LEVEL_INCREMENT_BUTTON_TOP_LEFT = new DTOPoint(470, 1040);
        final DTOPoint LEVEL_INCREMENT_BUTTON_BOTTOM_RIGHT = new DTOPoint(500, 1066);
        final DTOPoint LEVEL_DECREMENT_BUTTON_TOP_LEFT = new DTOPoint(50, 1040);
        final DTOPoint LEVEL_DECREMENT_BUTTON_BOTTOM_RIGHT = new DTOPoint(85, 1066);
        final DTOPoint LEVEL_LOCK_BUTTON = new DTOPoint(183, 1140);
        final int LEVEL_BUTTON_TAP_DELAY = 150;

        // Read current level
        Integer currentLevel = readCurrentGatheringLevel();

        if (currentLevel != null && currentLevel == DESIRED_LEVEL) {
            logInfo("Desired level already selected");
            return true;
        }

        if (currentLevel == null) {
            // OCR failed, use backup plan: reset to level 1 and increment
            logDebug("OCR failed, using backup level selection");
            resetLevelToOne();

            if (DESIRED_LEVEL > 1) {
                tapRandomPoint(
                        LEVEL_INCREMENT_BUTTON_TOP_LEFT,
                        LEVEL_INCREMENT_BUTTON_BOTTOM_RIGHT,
                        DESIRED_LEVEL - 1,
                        LEVEL_BUTTON_TAP_DELAY);
            }
        } else {
            // OCR succeeded, adjust from current level
            logDebug(String.format("Current level: %d, adjusting to %d", currentLevel, DESIRED_LEVEL));

            if (currentLevel < DESIRED_LEVEL) {
                int taps = DESIRED_LEVEL - currentLevel;
                tapRandomPoint(
                        LEVEL_INCREMENT_BUTTON_TOP_LEFT,
                        LEVEL_INCREMENT_BUTTON_BOTTOM_RIGHT,
                        taps,
                        LEVEL_BUTTON_TAP_DELAY);
            } else {
                int taps = currentLevel - DESIRED_LEVEL;
                tapRandomPoint(
                        LEVEL_DECREMENT_BUTTON_TOP_LEFT,
                        LEVEL_DECREMENT_BUTTON_BOTTOM_RIGHT,
                        taps,
                        LEVEL_BUTTON_TAP_DELAY);
            }
        }

        // Ensure level lock checkbox is checked
        ensureLevelLocked(LEVEL_LOCK_BUTTON);

        return true;
    }

    /**
     * Reads the current gathering level from the display via OCR.
     * 
     * @return the current level as an Integer, or null if OCR fails
     */
    private Integer readCurrentGatheringLevel() {
        DTOArea levelArea = new DTOArea(
                new DTOPoint(78, 991),
                new DTOPoint(474, 1028));

        DTOTesseractSettings settings = DTOTesseractSettings.builder()
                .setAllowedChars("0123456789")
                .setRemoveBackground(true)
                .setTextColor(new Color(255, 255, 255))
                .build();

        Integer level = integerHelper.execute(
                levelArea.topLeft(),
                levelArea.bottomRight(),
                3, // Max retries
                200L, // Retry delay
                settings,
                text -> NumberValidators.matchesPattern(text, Pattern.compile(".*?(\\d+).*")),
                text -> NumberConverters.regexToInt(text, Pattern.compile(".*?(\\d+).*")));

        if (level != null) {
            logDebug("Current level detected: " + level);
        } else {
            logWarning("Failed to read current level via OCR");
        }

        return level;
    }

    /**
     * Resets the level slider to level 1.
     */
    private void resetLevelToOne() {
        logDebug("Resetting level slider to 1");
        swipe(new DTOPoint(40, 1052), new DTOPoint(435, 1052));
        sleepTask(300); // Wait for slider animation
    }

    /**
     * Ensures the level lock checkbox is checked.
     * 
     * @param levelLockButton the point of the lock button
     */
    private void ensureLevelLocked(DTOPoint levelLockButton) {
        DTOImageSearchResult tick = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_HOME_SHORTCUTS_FARM_TICK,
                SearchConfigConstants.SINGLE_WITH_RETRIES);

        if (!tick.isFound()) {
            logDebug("Level not locked, tapping lock button");
            tapPoint(levelLockButton);
            sleepTask(300); // Wait for checkbox animation
        }
    }

    /**
     * Executes the resource search.
     * 
     * @return true if search executed successfully, false otherwise
     */
    private boolean executeResourceSearch() {
        logInfo("Executing resource search");

        tapRandomPoint(new DTOPoint(301, 1200), new DTOPoint(412, 1229));
        sleepTask(3000); // Wait for search to complete and map to load

        return true;
    }

    /**
     * Enum representing gathering resource types for the pet skill.
     */
    public enum GatheringResourceType {
        MEAT(0),
        WOOD(1),
        COAL(2),
        IRON(3);

        private final int tabIndex;

        GatheringResourceType(int tabIndex) {
            this.tabIndex = tabIndex;
        }

        public int getTabIndex() {
            return tabIndex;
        }
    }

    /**
     * Enum representing the four pet skills with their screen coordinates.
     * 
     * <p>
     * Each skill has a defined region on the pets menu screen that can be tapped
     * to display the skill's details overlay.
     */
    public enum PetSkill {
        /** Stamina skill - adds stamina to the profile */
        STAMINA(STAMINA_SKILL_TOP_LEFT, STAMINA_SKILL_BOTTOM_RIGHT),

        /** Gathering skill - increases gathering speed */
        GATHERING(GATHERING_SKILL_TOP_LEFT, GATHERING_SKILL_BOTTOM_RIGHT),

        /** Food skill - increases food production */
        FOOD(FOOD_SKILL_TOP_LEFT, FOOD_SKILL_BOTTOM_RIGHT),

        /** Treasure skill - provides resource rewards */
        TREASURE(TREASURE_SKILL_TOP_LEFT, TREASURE_SKILL_BOTTOM_RIGHT);

        private final DTOArea area;

        /**
         * Constructs a PetSkill with screen coordinates.
         * 
         * @param topLeft     top-left corner of the skill icon region
         * @param bottomRight bottom-right corner of the skill icon region
         */
        PetSkill(DTOPoint topLeft, DTOPoint bottomRight) {
            this.area = new DTOArea(topLeft, bottomRight);
        }

        /**
         * Gets the top-left corner of the skill icon region.
         * 
         * @return DTOPoint representing top-left coordinate
         */
        public DTOPoint getTopLeft() {
            return area.topLeft();
        }

        /**
         * Gets the bottom-right corner of the skill icon region.
         * 
         * @return DTOPoint representing bottom-right coordinate
         */
        public DTOPoint getBottomRight() {
            return area.bottomRight();
        }
    }
}