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

        DTOImageSearchResult petsButton = searchTemplateWithRetries(
                EnumTemplates.GAME_HOME_PETS);

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
        DTOImageSearchResult infoSkill = searchTemplateWithRetries(
                EnumTemplates.PETS_INFO_SKILLS,
                90,
                1);

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
        DTOImageSearchResult unlockText = searchTemplateWithRetries(
                EnumTemplates.PETS_UNLOCK_TEXT,
                90,
                1);

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
        DTOImageSearchResult useButton = searchTemplateWithRetries(
                EnumTemplates.PETS_SKILL_USE,
                90,
                1);

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
     * Indicates that this task does not provide daily mission progress.
     * 
     * @return false
     */
    @Override
    public boolean provideDailyMissionProgress() {
        return false;
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