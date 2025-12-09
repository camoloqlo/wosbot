package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Task responsible for completing daily labyrinth challenges.
 * This task navigates to the labyrinth menu and executes appropriate challenges
 * based on the current day of the week.
 */
public class DailyLabyrinthTask extends DelayedTask {

    // =========================== CONSTANTS ===========================

    // Navigation points
    private static final DTOPoint SCROLL_START_POINT = new DTOPoint(400, 800);
    private static final DTOPoint SCROLL_END_POINT = new DTOPoint(400, 100);
    private static final DTOPoint SKIP_BUTTON = new DTOPoint(71, 827);
    private static final DTOPoint RESULT_SKIP_BUTTON = new DTOPoint(640, 175);

    // Timing constants
    private static final int MENU_NAVIGATION_DELAY = 1000;
    private static final int TAB_SWITCH_DELAY = 500;
    private static final int SCROLL_DELAY = 1300;
    private static final int LABYRINTH_LOAD_DELAY = 2000;
    private static final int BATTLE_COMPLETION_DELAY = 3000;

    // =========================== CONSTRUCTOR ===========================

    public DailyLabyrinthTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    // =========================== TASK OVERRIDES ===========================

    @Override
    public boolean provideDailyMissionProgress() {
        return true;
    }

    @Override
    public EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }

    @Override
    protected void execute() {
        logInfo("Starting Daily Labyrinth task for profile: " + profile.getName());

        try {
            // Step 1: Navigate to labyrinth menu
            if (!navigateToLabyrinthMenu()) {
                rescheduleOneHourLater("Failed to navigate to the Labyrinth menu");
                return;
            }

            // Step 2: Execute challenges based on current day
            executeLabyrinthChallenges();

            logInfo("Daily Labyrinth task completed successfully for profile: " + profile.getName());
            reschedule(UtilTime.getGameReset());

        } catch (Exception e) {
            logError("An error occurred during the Labyrinth task: " + e.getMessage());
            rescheduleOneHourLater("Unexpected error during execution: " + e.getMessage());
        }
    }

    // =========================== NAVIGATION METHODS ===========================

    /**
     * Opens the side menu, switches to city tab, scrolls down and searches for
     * labyrinth
     * 
     * @return true if navigation was successful, false otherwise
     */
    private boolean navigateToLabyrinthMenu() {
        logInfo("Navigating to the Labyrinth menu...");

        // Open side menu
        marchHelper.openLeftMenuCitySection(true);

        // Scroll down to find labyrinth
        swipe(SCROLL_START_POINT, SCROLL_END_POINT);
        sleepTask(SCROLL_DELAY);

        // Search for labyrinth in menu
        DTOImageSearchResult labyrinthResult = templateSearchHelper.searchTemplate(
                EnumTemplates.LEFT_MENU_LABYRINTH_BUTTON,
                SearchConfigConstants.DEFAULT_SINGLE);
        if (labyrinthResult.isFound()) {
            tapPoint(labyrinthResult.getPoint());
            sleepTask(LABYRINTH_LOAD_DELAY);
            logInfo("Successfully navigated to the Labyrinth menu.");
            return true;
        } else {
            logWarning("Labyrinth menu item not found.");
            return false;
        }
    }

    // =========================== CHALLENGE EXECUTION ===========================

    /**
     * Executes labyrinth challenges based on the current day of the week
     */
    private void executeLabyrinthChallenges() {
        DayOfWeek currentDay = LocalDateTime.now(ZoneOffset.UTC).getDayOfWeek();
        List<Integer> availableDungeons = getAvailableDungeons(currentDay);

        logInfo("Executing challenges for " + currentDay + ". Available dungeons: " + availableDungeons);

        boolean anyCompleted = false;
        for (Integer dungeonNumber : availableDungeons) {
            if (executeDungeonChallenge(dungeonNumber)) {
                logInfo("Successfully completed challenge for dungeon " + dungeonNumber + ".");
                anyCompleted = true;

            }
        }

        if (!anyCompleted) {
            logWarning("No dungeons were successfully completed today.");
        }
    }

    /**
     * Executes a specific dungeon challenge
     * 
     * @param dungeonNumber the dungeon number to challenge
     * @return true if challenge was completed successfully
     */
    private boolean executeDungeonChallenge(int dungeonNumber) {
        logInfo("Attempting to execute challenge for dungeon " + dungeonNumber + ".");

        DTOImageSearchResult labyrinthResult = templateSearchHelper.searchTemplate(
                getDungeonTemplate(dungeonNumber),
                SearchConfigConstants.DEFAULT_SINGLE);
        if (!labyrinthResult.isFound()) {
            logWarning("Dungeon " + dungeonNumber + " is not available today.");
            return false;
        }

        tapPoint(labyrinthResult.getPoint());
        sleepTask(TAB_SWITCH_DELAY);

        // Try quick challenge first
        if (attemptQuickChallenge(dungeonNumber)) {
            return true;
        }

        // Try raid challenge
        if (attemptRaidChallenge(dungeonNumber)) {
            return true;
        }

        // Try normal challenge
        return attemptNormalChallenge(dungeonNumber);
    }

    /**
     * Attempts to execute a quick challenge
     */
    private boolean attemptQuickChallenge(int dungeonNumber) {
        tapPoint(new DTOPoint(700, 1200));
        sleepTask(100);
        DTOImageSearchResult quickChallengeResult = templateSearchHelper.searchTemplate(
                EnumTemplates.LABYRINTH_QUICK_CHALLENGE,
                SearchConfigConstants.DEFAULT_SINGLE);
        if (quickChallengeResult.isFound()) {
            logInfo("'Quick Challenge' is available for dungeon " + dungeonNumber + ".");
            tapPoint(quickChallengeResult.getPoint());
            sleepTask(MENU_NAVIGATION_DELAY);

            // Skip battle animation
            tapPoint(SKIP_BUTTON);
            sleepTask(300);
            tapRandomPoint(SKIP_BUTTON, SKIP_BUTTON, 10, 50);
            tapBackButton();
            return true;
        }
        return false;
    }

    /**
     * Attempts to execute a raid challenge
     */
    private boolean attemptRaidChallenge(int dungeonNumber) {
        DTOImageSearchResult raidResult = templateSearchHelper.searchTemplate(
                EnumTemplates.LABYRINTH_RAID_CHALLENGE,
                SearchConfigConstants.DEFAULT_SINGLE);
        if (raidResult.isFound()) {
            logInfo("'Raid Challenge' is available for dungeon " + dungeonNumber + ".");
            tapPoint(raidResult.getPoint());
            sleepTask(400);
            tapRandomPoint(SKIP_BUTTON, SKIP_BUTTON, 10, 50);
            tapBackButton();
            sleepTask(400);
            tapBackButton();
            return true;
        }
        return false;
    }

    /**
     * Attempts to execute a normal challenge
     */
    private boolean attemptNormalChallenge(int dungeonNumber) {
        DTOImageSearchResult normalChallengeResult = templateSearchHelper.searchTemplate(
                EnumTemplates.LABYRINTH_NORMAL_CHALLENGE,
                SearchConfigConstants.DEFAULT_SINGLE);
        if (!normalChallengeResult.isFound()) {
            logWarning("No 'Normal Challenge' button found for dungeon " + dungeonNumber + ".");
            return false;
        }

        tapPoint(normalChallengeResult.getPoint());
        sleepTask(300);

        // Try quick deploy first
        DTOImageSearchResult quickDeployResult = templateSearchHelper.searchTemplate(
                EnumTemplates.LABYRINTH_QUICK_DEPLOY,
                SearchConfigConstants.DEFAULT_SINGLE);
        if (quickDeployResult.isFound()) {
            logInfo("'Quick Deploy' button found. Deploying for dungeon " + dungeonNumber + ".");
            tapPoint(quickDeployResult.getPoint());
            sleepTask(100);
        }

        // Deploy troops
        DTOImageSearchResult deployResult = templateSearchHelper.searchTemplate(
                EnumTemplates.LABYRINTH_DEPLOY,
                SearchConfigConstants.DEFAULT_SINGLE);
        if (deployResult.isFound()) {
            logInfo("'Deploy' button found. Deploying troops for dungeon " + dungeonNumber + ".");
            tapPoint(deployResult.getPoint());
            sleepTask(BATTLE_COMPLETION_DELAY);

            // Skip battle results
            tapRandomPoint(RESULT_SKIP_BUTTON, RESULT_SKIP_BUTTON, 10, 50);
            tapBackButton();
            return true;
        }

        logWarning("Could not find 'Deploy' button for dungeon " + dungeonNumber + ".");
        return false;
    }

    // =========================== UTILITY METHODS ===========================

    /**
     * Returns the list of available dungeons based on the day of the week
     * 
     * @param dayOfWeek the current day of the week
     * @return list of available dungeon numbers
     */
    private List<Integer> getAvailableDungeons(DayOfWeek dayOfWeek) {
        List<Integer> dungeons = new ArrayList<>();

        switch (dayOfWeek) {
            case MONDAY, TUESDAY -> dungeons.add(1);
            case WEDNESDAY, THURSDAY -> {
                dungeons.add(2);
                dungeons.add(3);
            }
            case FRIDAY, SATURDAY -> {
                dungeons.add(4);
                dungeons.add(5);
            }
            case SUNDAY -> dungeons.add(6);
        }

        return dungeons;
    }

    /**
     * Returns the appropriate template for each dungeon number
     * 
     * @param dungeonNumber the dungeon number (1-6)
     * @return the corresponding template enum
     */
    private EnumTemplates getDungeonTemplate(int dungeonNumber) {
        return switch (dungeonNumber) {
            case 1 -> EnumTemplates.LABYRINTH_DUNGEON_1;
            case 2 -> EnumTemplates.LABYRINTH_DUNGEON_2;
            case 3 -> EnumTemplates.LABYRINTH_DUNGEON_3;
            case 4 -> EnumTemplates.LABYRINTH_DUNGEON_4;
            case 5 -> EnumTemplates.LABYRINTH_DUNGEON_5;
            case 6 -> EnumTemplates.LABYRINTH_DUNGEON_6;
            default -> {
                logWarning("Invalid dungeon number: " + dungeonNumber + ". Using dungeon 1 as a fallback.");
                yield EnumTemplates.LABYRINTH_DUNGEON_1;
            }
        };
    }

    /**
     * Reschedules the task for one hour later with a reason
     * 
     * @param reason the reason for rescheduling
     */
    private void rescheduleOneHourLater(String reason) {
        LocalDateTime nextExecution = LocalDateTime.now().plusHours(1);
        logWarning(reason + ". Rescheduling task for one hour later.");
        this.reschedule(nextExecution);
    }

}
