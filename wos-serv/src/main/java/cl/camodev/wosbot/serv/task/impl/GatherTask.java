package cl.camodev.wosbot.serv.task.impl;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import cl.camodev.utiles.UtilTime;
import cl.camodev.utiles.ocr.TextRecognitionRetrier;
import cl.camodev.utiles.time.TimeConverters;
import cl.camodev.utiles.time.TimeValidators;
import cl.camodev.wosbot.almac.entity.DailyTask;
import cl.camodev.wosbot.almac.repo.DailyTaskRepository;
import cl.camodev.wosbot.almac.repo.IDailyTaskRepository;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOArea;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper.SearchConfig;

/**
 * Unified task for managing all gathering operations (Meat, Wood, Coal, Iron).
 * 
 * <p>
 * This task:
 * <ul>
 * <li>Processes all enabled gather types in a single execution</li>
 * <li>Checks for active gathering marches and reschedules accordingly</li>
 * <li>Searches for resource tiles at configured levels</li>
 * <li>Deploys gathering marches with optional hero removal</li>
 * <li>Coordinates with Intel and GatherSpeed tasks to avoid conflicts</li>
 * </ul>
 * 
 * <p>
 * <b>Execution Flow:</b>
 * <ol>
 * <li>Check if Intel task is about to run (wait if so)</li>
 * <li>Check if GatherSpeed task needs to run first (wait if so)</li>
 * <li>For each enabled gather type:
 * <ul>
 * <li>Check if march is already active → reschedule for return time</li>
 * <li>If not active → search and deploy new march</li>
 * </ul>
 * </li>
 * <li>Reschedule based on earliest march return time</li>
 * </ol>
 */
public class GatherTask extends DelayedTask {

    // ========== Configuration Keys ==========
    private static final int DEFAULT_ACTIVE_MARCH_QUEUES = 6;
    private static final boolean DEFAULT_REMOVE_HEROES = false;
    private static final int DEFAULT_RESOURCE_LEVEL = 5;
    private static final boolean DEFAULT_INTEL_SMART_PROCESSING = false;
    private static final boolean DEFAULT_GATHER_SPEED_ENABLED = false;

    // ========== March Queue Coordinates ==========
    /**
     * March queue regions for detecting active gathering marches.
     * Format: {topLeft, bottomRight, timeTextStart}
     * - topLeft/bottomRight define the search area for resource icons
     * - timeTextStart defines where the remaining time text begins
     */
    private static final MarchQueueRegion[] MARCH_QUEUES = {
            new MarchQueueRegion(new DTOPoint(10, 342), new DTOPoint(435, 407), new DTOPoint(152, 378)), // Queue 1
            new MarchQueueRegion(new DTOPoint(10, 415), new DTOPoint(435, 480), new DTOPoint(152, 451)), // Queue 2
            new MarchQueueRegion(new DTOPoint(10, 488), new DTOPoint(435, 553), new DTOPoint(152, 524)), // Queue 3
            new MarchQueueRegion(new DTOPoint(10, 561), new DTOPoint(435, 626), new DTOPoint(152, 597)), // Queue 4
            new MarchQueueRegion(new DTOPoint(10, 634), new DTOPoint(435, 699), new DTOPoint(152, 670)), // Queue 5
            new MarchQueueRegion(new DTOPoint(10, 707), new DTOPoint(435, 772), new DTOPoint(152, 743)), // Queue 6
    };
    private static final int TIME_TEXT_WIDTH = 140;
    private static final int TIME_TEXT_HEIGHT = 19;

    // ========== Resource Search Menu ==========
    private static final DTOPoint SEARCH_BUTTON_TOP_LEFT = new DTOPoint(25, 850);
    private static final DTOPoint SEARCH_BUTTON_BOTTOM_RIGHT = new DTOPoint(67, 898);
    private static final DTOPoint RESOURCE_TAB_SWIPE_START = new DTOPoint(678, 913);
    private static final DTOPoint RESOURCE_TAB_SWIPE_END = new DTOPoint(40, 913);

    // ========== Resource Level Selection ==========
    private static final DTOPoint LEVEL_DISPLAY_TOP_LEFT = new DTOPoint(78, 991);
    private static final DTOPoint LEVEL_DISPLAY_BOTTOM_RIGHT = new DTOPoint(474, 1028);
    private static final DTOPoint LEVEL_SLIDER_SWIPE_START = new DTOPoint(435, 1052);
    private static final DTOPoint LEVEL_SLIDER_SWIPE_END = new DTOPoint(40, 1052);
    private static final DTOPoint LEVEL_INCREMENT_BUTTON_TOP_LEFT = new DTOPoint(470, 1040);
    private static final DTOPoint LEVEL_INCREMENT_BUTTON_BOTTOM_RIGHT = new DTOPoint(500, 1066);
    private static final DTOPoint LEVEL_DECREMENT_BUTTON_TOP_LEFT = new DTOPoint(50, 1040);
    private static final DTOPoint LEVEL_DECREMENT_BUTTON_BOTTOM_RIGHT = new DTOPoint(85, 1066);
    private static final DTOPoint LEVEL_LOCK_BUTTON = new DTOPoint(183, 1140);

    // ========== Search and Deployment ==========
    private static final DTOPoint SEARCH_EXECUTE_BUTTON_TOP_LEFT = new DTOPoint(301, 1200);
    private static final DTOPoint SEARCH_EXECUTE_BUTTON_BOTTOM_RIGHT = new DTOPoint(412, 1229);

    // ========== Constants ==========
    private static final int MAX_RESOURCE_TAB_SWIPE_ATTEMPTS = 4;
    private static final int INTEL_CONFLICT_BUFFER_MINUTES = 5;
    private static final int GATHER_SPEED_WAIT_BUFFER_MINUTES = 5;
    private static final int LEVEL_BUTTON_TAP_DELAY = 150;
    private static final int HERO_REMOVAL_DELAY = 300;
    private final IDailyTaskRepository dailyTaskRepository = DailyTaskRepository.getRepository();

    // ========== Configuration (loaded in loadConfiguration()) ==========
    private int activeMarchQueues;
    private boolean removeHeroes;
    private boolean intelSmartProcessing;
    private boolean intelRecallGatherEnabled;
    private boolean intelEnabled;
    private boolean gatherSpeedEnabled;
    private TextRecognitionRetrier<LocalDateTime> textHelper;

    // Per-resource configurations
    private boolean meatEnabled;
    private int meatLevel;
    private boolean woodEnabled;
    private int woodLevel;
    private boolean coalEnabled;
    private int coalLevel;
    private boolean ironEnabled;
    private int ironLevel;

    // ========== Execution State ==========
    private LocalDateTime earliestRescheduleTime;
    private List<GatherType> enabledGatherTypes;

    public GatherTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    /**
     * Loads task configuration from profile.
     */
    private void loadConfiguration() {
        // Global settings
        Integer configuredQueues = profile.getConfig(
                EnumConfigurationKey.GATHER_ACTIVE_MARCH_QUEUE_INT, Integer.class);
        this.activeMarchQueues = (configuredQueues != null) ? configuredQueues : DEFAULT_ACTIVE_MARCH_QUEUES;

        Boolean configuredRemoveHeroes = profile.getConfig(
                EnumConfigurationKey.GATHER_REMOVE_HEROS_BOOL, Boolean.class);
        this.removeHeroes = (configuredRemoveHeroes != null) ? configuredRemoveHeroes : DEFAULT_REMOVE_HEROES;

        Boolean configuredIntelSmart = profile.getConfig(
                EnumConfigurationKey.INTEL_SMART_PROCESSING_BOOL, Boolean.class);
        this.intelSmartProcessing = (configuredIntelSmart != null) ? configuredIntelSmart
                : DEFAULT_INTEL_SMART_PROCESSING;

        Boolean configuredIntelRecallGatherEnabled = profile.getConfig(
                EnumConfigurationKey.INTEL_RECALL_GATHER_TROOPS_BOOL, Boolean.class);
        this.intelRecallGatherEnabled = (configuredIntelRecallGatherEnabled != null) ? configuredIntelRecallGatherEnabled : false;

        Boolean configuredIntelEnabled = profile.getConfig(
                EnumConfigurationKey.INTEL_BOOL, Boolean.class);
        this.intelEnabled = (configuredIntelEnabled != null) ? configuredIntelEnabled : false;

        Boolean configuredGatherSpeed = profile.getConfig(
                EnumConfigurationKey.GATHER_SPEED_BOOL, Boolean.class);
        this.gatherSpeedEnabled = (configuredGatherSpeed != null) ? configuredGatherSpeed
                : DEFAULT_GATHER_SPEED_ENABLED;

        // Meat configuration
        Boolean configuredMeatEnabled = profile.getConfig(
                EnumConfigurationKey.GATHER_MEAT_BOOL, Boolean.class);
        this.meatEnabled = (configuredMeatEnabled != null) ? configuredMeatEnabled : false;

        Integer configuredMeatLevel = profile.getConfig(
                EnumConfigurationKey.GATHER_MEAT_LEVEL_INT, Integer.class);
        this.meatLevel = (configuredMeatLevel != null) ? configuredMeatLevel : DEFAULT_RESOURCE_LEVEL;

        // Wood configuration
        Boolean configuredWoodEnabled = profile.getConfig(
                EnumConfigurationKey.GATHER_WOOD_BOOL, Boolean.class);
        this.woodEnabled = (configuredWoodEnabled != null) ? configuredWoodEnabled : false;

        Integer configuredWoodLevel = profile.getConfig(
                EnumConfigurationKey.GATHER_WOOD_LEVEL_INT, Integer.class);
        this.woodLevel = (configuredWoodLevel != null) ? configuredWoodLevel : DEFAULT_RESOURCE_LEVEL;

        // Coal configuration
        Boolean configuredCoalEnabled = profile.getConfig(
                EnumConfigurationKey.GATHER_COAL_BOOL, Boolean.class);
        this.coalEnabled = (configuredCoalEnabled != null) ? configuredCoalEnabled : false;

        Integer configuredCoalLevel = profile.getConfig(
                EnumConfigurationKey.GATHER_COAL_LEVEL_INT, Integer.class);
        this.coalLevel = (configuredCoalLevel != null) ? configuredCoalLevel : DEFAULT_RESOURCE_LEVEL;

        // Iron configuration
        Boolean configuredIronEnabled = profile.getConfig(
                EnumConfigurationKey.GATHER_IRON_BOOL, Boolean.class);
        this.ironEnabled = (configuredIronEnabled != null) ? configuredIronEnabled : false;

        Integer configuredIronLevel = profile.getConfig(
                EnumConfigurationKey.GATHER_IRON_LEVEL_INT, Integer.class);
        this.ironLevel = (configuredIronLevel != null) ? configuredIronLevel : DEFAULT_RESOURCE_LEVEL;

        // Text recognition helper
        this.textHelper = new TextRecognitionRetrier<>(provider);

        logDebug(String.format("Configuration loaded - Active queues: %d, Remove heroes: %s",
                activeMarchQueues, removeHeroes));
        logDebug(String.format("Resources - Meat: %s (Lv%d), Wood: %s (Lv%d), Coal: %s (Lv%d), Iron: %s (Lv%d)",
                meatEnabled, meatLevel, woodEnabled, woodLevel, coalEnabled, coalLevel, ironEnabled, ironLevel));
    }

    /**
     * Resets execution-specific state.
     */
    private void resetExecutionState() {
        this.earliestRescheduleTime = null;
        this.enabledGatherTypes = new ArrayList<>();

        // Build list of enabled gather types
        if (meatEnabled)
            enabledGatherTypes.add(GatherType.MEAT);
        if (woodEnabled)
            enabledGatherTypes.add(GatherType.WOOD);
        if (coalEnabled)
            enabledGatherTypes.add(GatherType.COAL);
        if (ironEnabled)
            enabledGatherTypes.add(GatherType.IRON);

        logDebug("Execution state reset. Enabled types: " + enabledGatherTypes.size());
    }

    @Override
    protected void execute() {
        loadConfiguration();
        resetExecutionState();

        if (enabledGatherTypes.isEmpty()) {
            logInfo("No gather types enabled. Disabling task.");
            setRecurring(false);
            return;
        }

        logInfo(String.format("Starting gather task for %d resource types.", enabledGatherTypes.size()));

        // Check Intel task conflict
        if ((intelSmartProcessing || intelRecallGatherEnabled) && intelEnabled && isIntelAboutToRun()) {
            logWarning("Intel task scheduled to run soon. Rescheduling gather task for 35 minutes.");
            reschedule(LocalDateTime.now().plusMinutes(35));
            return;
        }

        // Check GatherSpeed dependency
        if (gatherSpeedEnabled && !isGatherSpeedTaskReady()) {
            logInfo("Waiting for GatherSpeed task. Checking again in 2 minutes.");
            reschedule(LocalDateTime.now().plusMinutes(2));
            return;
        }

        // Process each enabled gather type
        for (GatherType gatherType : enabledGatherTypes) {
            processGatherType(gatherType);
        }

        // Reschedule based on earliest march return time
        finalizeReschedule();
    }

    /**
     * Checks if Intel task is scheduled to run within the conflict buffer.
     */
    private boolean isIntelAboutToRun() {
        try {
            DailyTask intel = dailyTaskRepository.findByProfileIdAndTaskName(
                    profile.getId(), TpDailyTaskEnum.INTEL);

            if (intel == null) {
                return false;
            }

            long minutesUntilIntel = ChronoUnit.MINUTES.between(
                    LocalDateTime.now(), intel.getNextSchedule());

            boolean aboutToRun = minutesUntilIntel < INTEL_CONFLICT_BUFFER_MINUTES;

            if (aboutToRun) {
                logDebug(String.format("Intel task scheduled in %d minutes (threshold: %d minutes)",
                        minutesUntilIntel, INTEL_CONFLICT_BUFFER_MINUTES));
            }

            return aboutToRun;

        } catch (Exception e) {
            logWarning("Failed to check Intel task schedule: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if GatherSpeed task has been processed and is ready.
     */
    private boolean isGatherSpeedTaskReady() {
        try {
            DailyTask gatherSpeedTask = dailyTaskRepository.findByProfileIdAndTaskName(
                    profile.getId(), TpDailyTaskEnum.GATHER_BOOST);

            if (gatherSpeedTask == null) {
                logDebug("GatherSpeed task never executed. Waiting required.");
                return false;
            }

            LocalDateTime nextSchedule = gatherSpeedTask.getNextSchedule();
            if (nextSchedule == null) {
                logDebug("GatherSpeed task has no next schedule. Waiting required.");
                return false;
            }

            long minutesUntilNext = ChronoUnit.MINUTES.between(LocalDateTime.now(), nextSchedule);

            // If next run is very soon (< 5 min), wait for it
            // Negative values mean it's overdue but hasn't updated yet - allow gathering
            if (minutesUntilNext > 0 && minutesUntilNext < GATHER_SPEED_WAIT_BUFFER_MINUTES) {
                logDebug(String.format("GatherSpeed task scheduled in %d minutes. Waiting.", minutesUntilNext));
                return false;
            }

            logDebug(String.format("GatherSpeed task ready (next in %d minutes).", minutesUntilNext));
            return true;

        } catch (Exception e) {
            logError("Error checking GatherSpeed task: " + e.getMessage());
            return false;
        }
    }

    /**
     * Processes a single gather type (check active march or deploy new).
     */
    private void processGatherType(GatherType gatherType) {
        logInfo(String.format("Processing %s gathering.", gatherType.name()));

        marchHelper.openLeftMenuCitySection(false);

        ActiveMarchResult result = checkActiveMarch(gatherType);

        marchHelper.closeLeftMenu();

        if (result.isActive()) {
            logInfo(String.format("%s march is active. Returns in: %s",
                    gatherType.name(), UtilTime.localDateTimeToDDHHMMSS(result.getReturnTime())));
            updateRescheduleTime(result.getReturnTime());
        } else {
            logInfo(String.format("No active %s march found. Deploying new march.", gatherType.name()));
            deployNewGatherMarch(gatherType);
        }
    }

    /**
     * Checks if a gather march is active for the specified resource type.
     */
    private ActiveMarchResult checkActiveMarch(GatherType gatherType) {
        logDebug(String.format("Checking for active %s march", gatherType.name()));

        // Calculate search region based on active march queues
        int maxQueueIndex = Math.min(activeMarchQueues - 1, MARCH_QUEUES.length - 1);
        DTOPoint searchBottomRight = new DTOPoint(415, MARCH_QUEUES[maxQueueIndex].bottomRight.getY());

        DTOImageSearchResult resource = templateSearchHelper.searchTemplate(
                gatherType.getTemplate(),
                SearchConfig.builder()
                        .withArea(new DTOArea(MARCH_QUEUES[0].topLeft, searchBottomRight))
                        .withMaxAttempts(3)
                        .withDelay(3)
                        .build());

        if (!resource.isFound()) {
            return ActiveMarchResult.notActive();
        }

        logDebug(String.format("Active %s march detected", gatherType.name()));

        // Determine which queue contains this march
        int queueIndex = findMarchQueueIndex(resource.getPoint());

        if (queueIndex == -1) {
            logWarning("Could not determine queue index for active march");
            return ActiveMarchResult.withError(LocalDateTime.now().plusMinutes(5));
        }

        // Read remaining time
        LocalDateTime returnTime = readMarchReturnTime(queueIndex);

        if (returnTime == null) {
            logWarning("Failed to read march return time");
            return ActiveMarchResult.withError(LocalDateTime.now().plusMinutes(5));
        }

        return ActiveMarchResult.active(returnTime.plusMinutes(2)); // Add 2min buffer
    }

    /**
     * Finds which queue index contains the given point.
     */
    private int findMarchQueueIndex(DTOPoint point) {
        int maxQueues = Math.min(activeMarchQueues, MARCH_QUEUES.length);

        for (int i = 0; i < maxQueues; i++) {
            MarchQueueRegion region = MARCH_QUEUES[i];

            if (point.getX() >= region.topLeft.getX() &&
                    point.getX() <= region.bottomRight.getX() &&
                    point.getY() >= region.topLeft.getY() &&
                    point.getY() <= region.bottomRight.getY()) {
                logDebug(String.format("March found in queue %d", i + 1));
                return i;
            }
        }

        return -1;
    }

    /**
     * Reads the march return time from the specified queue.
     */
    private LocalDateTime readMarchReturnTime(int queueIndex) {
        MarchQueueRegion region = MARCH_QUEUES[queueIndex];

        DTOPoint timeTopLeft = region.timeTextStart;
        DTOPoint timeBottomRight = new DTOPoint(
                timeTopLeft.getX() + TIME_TEXT_WIDTH,
                timeTopLeft.getY() + TIME_TEXT_HEIGHT);

        DTOTesseractSettings settings = DTOTesseractSettings.builder()
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setRemoveBackground(true)
                .setTextColor(new Color(255, 255, 255))
                .setAllowedChars("0123456789:")
                .build();

        LocalDateTime cooldown = textHelper.execute(
                timeTopLeft,
                timeBottomRight,
                3,
                200L,
                settings,
                TimeValidators::isValidTime,
                TimeConverters::toLocalDateTime);

        if (cooldown == null) {
            logWarning("OCR returned empty time text");
            return null;
        }

        logDebug("Time OCR result: '" + UtilTime.localDateTimeToDDHHMMSS(cooldown) + "'");
        return cooldown;
    }

    /**
     * Deploys a new gather march for the specified resource type.
     */
    private void deployNewGatherMarch(GatherType gatherType) {
        if (!openResourceSearchMenu()) {
            updateRescheduleTime(LocalDateTime.now().plusMinutes(5));
            return;
        }

        if (!selectResourceTile(gatherType)) {
            updateRescheduleTime(LocalDateTime.now().plusMinutes(5));
            return;
        }

        int desiredLevel = getResourceLevel(gatherType);
        if (!setResourceLevel(desiredLevel)) {
            tapBackButton();
            updateRescheduleTime(LocalDateTime.now().plusMinutes(5));
            return;
        }

        if (!executeSearch()) {
            tapBackButton();
            updateRescheduleTime(LocalDateTime.now().plusMinutes(5));
            return;
        }

        if (!deployMarch(gatherType)) {
            tapBackButton();
            updateRescheduleTime(LocalDateTime.now().plusMinutes(5));
            return;
        }

        // Successfully deployed
        updateRescheduleTime(LocalDateTime.now().plusMinutes(5));
    }

    /**
     * Opens the resource search menu.
     */
    private boolean openResourceSearchMenu() {
        logDebug("Opening resource search menu");

        tapRandomPoint(SEARCH_BUTTON_TOP_LEFT, SEARCH_BUTTON_BOTTOM_RIGHT);
        sleepTask(2000); // Wait for search menu to open

        // Swipe left to find resource tiles tab
        swipe(RESOURCE_TAB_SWIPE_START, RESOURCE_TAB_SWIPE_END);
        sleepTask(500); // Wait for swipe animation

        return true;
    }

    /**
     * Selects the resource tile for the specified type.
     */
    private boolean selectResourceTile(GatherType gatherType) {
        logDebug(String.format("Searching for %s tile", gatherType.name()));

        for (int attempt = 0; attempt < MAX_RESOURCE_TAB_SWIPE_ATTEMPTS; attempt++) {
            DTOImageSearchResult tile = templateSearchHelper.searchTemplate(
                    gatherType.getTile(),
                    SearchConfig.builder().build());

            if (tile.isFound()) {
                logInfo(String.format("%s tile found", gatherType.name()));
                tapPoint(tile.getPoint());
                sleepTask(500); // Wait for tile selection
                return true;
            }

            if (attempt < MAX_RESOURCE_TAB_SWIPE_ATTEMPTS - 1) {
                logDebug(String.format("Tile not found, swiping (attempt %d/%d)",
                        attempt + 1, MAX_RESOURCE_TAB_SWIPE_ATTEMPTS));
                swipe(RESOURCE_TAB_SWIPE_START, RESOURCE_TAB_SWIPE_END);
                sleepTask(500); // Wait for swipe animation
            }
        }

        logError(String.format("%s tile not found after %d attempts",
                gatherType.name(), MAX_RESOURCE_TAB_SWIPE_ATTEMPTS));
        return false;
    }

    /**
     * Gets the configured level for a resource type.
     */
    private int getResourceLevel(GatherType gatherType) {
        switch (gatherType) {
            case MEAT:
                return meatLevel;
            case WOOD:
                return woodLevel;
            case COAL:
                return coalLevel;
            case IRON:
                return ironLevel;
            default:
                return DEFAULT_RESOURCE_LEVEL;
        }
    }

    /**
     * Sets the resource level to the desired value.
     */
    private boolean setResourceLevel(int desiredLevel) {
        logInfo(String.format("Setting resource level to %d", desiredLevel));

        // Read current level
        Integer currentLevel = readCurrentResourceLevel();

        if (currentLevel != null && currentLevel == desiredLevel) {
            logInfo("Desired level already selected");
            return true;
        }

        if (currentLevel == null) {
            // OCR failed, use backup plan: reset to level 1 and increment
            logDebug("OCR failed, using backup level selection");
            resetLevelToOne();

            if (desiredLevel > 1) {
                tapRandomPoint(
                        LEVEL_INCREMENT_BUTTON_TOP_LEFT,
                        LEVEL_INCREMENT_BUTTON_BOTTOM_RIGHT,
                        desiredLevel - 1,
                        LEVEL_BUTTON_TAP_DELAY);
            }
        } else {
            // OCR succeeded, adjust from current level
            logDebug(String.format("Current level: %d, adjusting to %d", currentLevel, desiredLevel));

            if (currentLevel < desiredLevel) {
                int taps = desiredLevel - currentLevel;
                tapRandomPoint(
                        LEVEL_INCREMENT_BUTTON_TOP_LEFT,
                        LEVEL_INCREMENT_BUTTON_BOTTOM_RIGHT,
                        taps,
                        LEVEL_BUTTON_TAP_DELAY);
            } else {
                int taps = currentLevel - desiredLevel;
                tapRandomPoint(
                        LEVEL_DECREMENT_BUTTON_TOP_LEFT,
                        LEVEL_DECREMENT_BUTTON_BOTTOM_RIGHT,
                        taps,
                        LEVEL_BUTTON_TAP_DELAY);
            }
        }

        // Ensure level lock checkbox is checked
        ensureLevelLocked();

        return true;
    }

    /**
     * Reads the current resource level from the display.
     */
    private Integer readCurrentResourceLevel() {
        DTOTesseractSettings settings = DTOTesseractSettings.builder()
                .setAllowedChars("0123456789")
                .setRemoveBackground(true)
                .setTextColor(new Color(255, 255, 255))
                .build();

        Integer level = readNumberValue(LEVEL_DISPLAY_TOP_LEFT, LEVEL_DISPLAY_BOTTOM_RIGHT, settings);

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
        swipe(LEVEL_SLIDER_SWIPE_START, LEVEL_SLIDER_SWIPE_END);
        sleepTask(300); // Wait for slider animation
    }

    /**
     * Ensures the level lock checkbox is checked.
     */
    private void ensureLevelLocked() {
        DTOImageSearchResult tick = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_HOME_SHORTCUTS_FARM_TICK,
                SearchConfig.builder().build());

        if (!tick.isFound()) {
            logDebug("Level not locked, tapping lock button");
            tapPoint(LEVEL_LOCK_BUTTON);
            sleepTask(300); // Wait for checkbox animation
        }
    }

    /**
     * Executes the resource search.
     */
    private boolean executeSearch() {
        logInfo("Executing resource search");

        tapRandomPoint(SEARCH_EXECUTE_BUTTON_TOP_LEFT, SEARCH_EXECUTE_BUTTON_BOTTOM_RIGHT);
        sleepTask(3000); // Wait for search to complete and map to load

        return true;
    }

    /**
     * Deploys a gather march to the found tile.
     */
    private boolean deployMarch(GatherType gatherType) {
        // Find and tap the Gather button on the map
        DTOImageSearchResult gatherButton = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_HOME_SHORTCUTS_FARM_GATHER,
                SearchConfig.builder().build());

        if (!gatherButton.isFound()) {
            logWarning("Gather button not found. Tile may be occupied.");
            return false;
        }

        logDebug("Tapping gather button");
        tapPoint(gatherButton.getPoint());
        sleepTask(1000); // Wait for march configuration screen

        // Check if preferred gather hero is present
        DTOImageSearchResult gatherHero = templateSearchHelper.searchTemplate(
                gatherType.getPreferredHero(),
                SearchConfig.builder()
                        .withCoordinates(new DTOPoint(51, 231), new DTOPoint(295, 649))
                        .build());

        if (!gatherHero.isFound()) {
            logWarning(String.format("Preferred gather hero for %s not found", gatherType.name()));
            return false;
        }

        logInfo(gatherType.name() + " hero found");

        // Remove heroes if configured
        if (removeHeroes) {
            removeDefaultHeroes();
        }

        // Deploy the march
        DTOImageSearchResult deployButton = templateSearchHelper.searchTemplate(
                EnumTemplates.GATHER_DEPLOY_BUTTON,
                SearchConfig.builder().build());

        if (!deployButton.isFound()) {
            logError("Deploy button not found");
            return false;
        }

        logInfo("Deploying gather march");
        tapPoint(deployButton.getPoint());
        sleepTask(1000); // Wait for deployment confirmation

        // Check if tile is already being gathered
        DTOImageSearchResult alreadyMarching = templateSearchHelper.searchTemplate(
                EnumTemplates.TROOPS_ALREADY_MARCHING,
                SearchConfig.builder().build());

        if (alreadyMarching.isFound()) {
            logWarning("Tile already being gathered by another player");
            tapBackButton();
            tapBackButton();
            return false;
        }

        logInfo(String.format("%s march deployed successfully", gatherType.name()));
        return true;
    }

    /**
     * Removes the 2nd and 3rd heroes from the march.
     */
    private void removeDefaultHeroes() {
        logDebug("Removing default heroes from march");

        List<DTOImageSearchResult> removeButtons = templateSearchHelper.searchTemplates(
                EnumTemplates.RALLY_REMOVE_HERO_BUTTON,
                SearchConfig.builder()
                        .withThreshold(90)
                        .withMaxResults(3)
                        .withMaxAttempts(3)
                        .build());

        if (removeButtons.isEmpty()) {
            logWarning("No hero remove buttons found");
            return;
        }

        // Sort by X coordinate (left to right)
        removeButtons.sort(Comparator.comparingInt(r -> r.getPoint().getX()));

        // Remove 2nd and 3rd heroes (skip first)
        for (int i = 1; i < removeButtons.size(); i++) {
            tapPoint(removeButtons.get(i).getPoint());
            sleepTask(HERO_REMOVAL_DELAY); // Wait for hero removal animation
        }

        logDebug(String.format("Removed %d heroes", removeButtons.size() - 1));
    }

    /**
     * Updates the earliest reschedule time if the new time is earlier.
     */
    private void updateRescheduleTime(LocalDateTime newTime) {
        if (earliestRescheduleTime == null || newTime.isBefore(earliestRescheduleTime)) {
            earliestRescheduleTime = newTime;
            logDebug("Updated earliest reschedule time: " +
                    UtilTime.localDateTimeToDDHHMMSS(newTime));
        }
    }

    /**
     * Finalizes rescheduling based on the earliest march return time.
     */
    private void finalizeReschedule() {
        if (earliestRescheduleTime == null) {
            // No marches active, check again in 5 minutes
            logInfo("No active marches found. Rescheduling in 5 minutes.");
            reschedule(LocalDateTime.now().plusMinutes(5));
        } else {
            logInfo(String.format("Rescheduling gather task for: %s",
                    earliestRescheduleTime.plusMinutes(5).format(DATETIME_FORMATTER)));
            reschedule(earliestRescheduleTime.plusMinutes(5));
        }
    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.WORLD;
    }

    @Override
    public boolean provideDailyMissionProgress() {
        return true;
    }

    // ========== Helper Classes ==========

    /**
     * Enum representing the four gather resource types.
     */
    public enum GatherType {
        MEAT(
                EnumTemplates.GAME_HOME_SHORTCUTS_MEAT,
                EnumTemplates.GAME_HOME_SHORTCUTS_FARM_MEAT,
                EnumTemplates.GATHER_MEAT_HERO),
        WOOD(
                EnumTemplates.GAME_HOME_SHORTCUTS_WOOD,
                EnumTemplates.GAME_HOME_SHORTCUTS_FARM_WOOD,
                EnumTemplates.GATHER_WOOD_HERO),
        COAL(
                EnumTemplates.GAME_HOME_SHORTCUTS_COAL,
                EnumTemplates.GAME_HOME_SHORTCUTS_FARM_COAL,
                EnumTemplates.GATHER_COAL_HERO),
        IRON(
                EnumTemplates.GAME_HOME_SHORTCUTS_IRON,
                EnumTemplates.GAME_HOME_SHORTCUTS_FARM_IRON,
                EnumTemplates.GATHER_IRON_HERO);

        private final EnumTemplates template;
        private final EnumTemplates tile;
        private final EnumTemplates preferredHero;

        GatherType(EnumTemplates template, EnumTemplates tile, EnumTemplates preferredHero) {
            this.template = template;
            this.tile = tile;
            this.preferredHero = preferredHero;
        }

        public EnumTemplates getTemplate() {
            return template;
        }

        public EnumTemplates getTile() {
            return tile;
        }

        public EnumTemplates getPreferredHero() {
            return preferredHero;
        }
    }

    /**
     * Represents a march queue region with search area and time text location.
     */
    private static class MarchQueueRegion {
        private final DTOPoint topLeft;
        private final DTOPoint bottomRight;
        private final DTOPoint timeTextStart;

        public MarchQueueRegion(DTOPoint topLeft, DTOPoint bottomRight, DTOPoint timeTextStart) {
            this.topLeft = topLeft;
            this.bottomRight = bottomRight;
            this.timeTextStart = timeTextStart;
        }
    }

    /**
     * Result object for active march checks.
     */
    private static class ActiveMarchResult {
        private final boolean active;
        private final LocalDateTime returnTime;

        private ActiveMarchResult(boolean active, LocalDateTime returnTime) {
            this.active = active;
            this.returnTime = returnTime;
        }

        public static ActiveMarchResult active(LocalDateTime returnTime) {
            return new ActiveMarchResult(true, returnTime);
        }

        public static ActiveMarchResult notActive() {
            return new ActiveMarchResult(false, null);
        }

        public static ActiveMarchResult withError(LocalDateTime fallbackTime) {
            return new ActiveMarchResult(true, fallbackTime);
        }

        public boolean isActive() {
            return active;
        }

        public LocalDateTime getReturnTime() {
            return returnTime;
        }
    }
}