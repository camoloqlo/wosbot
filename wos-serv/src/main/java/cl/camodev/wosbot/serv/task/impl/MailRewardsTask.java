package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOArea;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper.SearchConfig;

/**
 * Mail Rewards task that automatically claims all rewards from the mail inbox.
 * 
 * <p>
 * This task:
 * <ul>
 * <li>Opens the mail menu from the bottom-right corner icon</li>
 * <li>Processes all three mail tabs (Alliance, System, Reports)</li>
 * <li>Claims all visible rewards in each tab</li>
 * <li>Scrolls through mail list to find additional unclaimed rewards</li>
 * <li>Reschedules based on configured offset or daily game reset</li>
 * </ul>
 * 
 * <p>
 * <b>Mail Tabs:</b>
 * <ol>
 * <li>Alliance - Alliance-related messages and rewards</li>
 * <li>System - System notifications and rewards</li>
 * <li>Reports - Beast and events reports</li>
 * </ol>
 * 
 * <p>
 * <b>Claim Strategy:</b>
 * The task uses a two-phase claiming approach:
 * <ol>
 * <li>Initial claim of all visible rewards</li>
 * <li>Scroll down and check for "overflow" rewards (mail that appears after
 * initial claim)</li>
 * </ol>
 * 
 * <p>
 * This ensures all rewards are claimed even when the mail list extends beyond
 * the initial visible area.
 */
public class MailRewardsTask extends DelayedTask {

    // ========== Search and Retry Limits ==========
    private static final int MAX_MAIL_SEARCH_ATTEMPTS = 100;
    private static final int MAIL_MENU_SEARCH_RETRIES = 5;
    private static final int MAIL_MENU_OPEN_VERIFICATION_RETRIES = 5;
    private static final int UNCLAIMED_REWARDS_SEARCH_RETRIES = 3;

    // ========== Navigation Constants ==========
    private static final int SWIPES_PER_PAGE = 10;
    private static final int MAIL_TAB_COUNT = 3;

    // ========== Reschedule Constants ==========
    private static final int ERROR_RETRY_MINUTES = 10;
    private static final int DEFAULT_OFFSET_MINUTES = 60;

    // ========== Screen Regions ==========
    // Mail menu icon search region (bottom-right corner of screen)
    private static final DTOPoint MAIL_MENU_SEARCH_TOP_LEFT = new DTOPoint(600, 1000);
    private static final DTOPoint MAIL_MENU_SEARCH_BOTTOM_RIGHT = new DTOPoint(715, 1100);

    // Mail menu opened verification region (top-left of mail screen)
    private static final DTOPoint MAIL_MENU_OPEN_TOP_LEFT = new DTOPoint(75, 10);
    private static final DTOPoint MAIL_MENU_OPEN_BOTTOM_RIGHT = new DTOPoint(175, 60);

    // Claim button region (small button at bottom of screen)
    private static final DTOPoint CLAIM_BUTTON_TOP_LEFT = new DTOPoint(420, 1227);
    private static final DTOPoint CLAIM_BUTTON_BOTTOM_RIGHT = new DTOPoint(450, 1250);
    private static final int CLAIM_BUTTON_TAP_COUNT = 4;
    private static final int CLAIM_BUTTON_TAP_DELAY = 500;

    // Mail list scroll region (vertical swipe to reveal more mail)
    private static final DTOPoint SCROLL_START_POINT = new DTOPoint(40, 913);
    private static final DTOPoint SCROLL_END_POINT = new DTOPoint(40, 400);

    // ========== Mail Tab Buttons ==========
    // Tab button coordinates: Alliance (left), System (center), Reports (right)
    private static final DTOPoint TAB_ALLIANCE = new DTOPoint(230, 120);
    private static final DTOPoint TAB_SYSTEM = new DTOPoint(360, 120);
    private static final DTOPoint TAB_REPORTS = new DTOPoint(500, 120);
    private static final DTOPoint[] MAIL_TAB_BUTTONS = { TAB_ALLIANCE, TAB_SYSTEM, TAB_REPORTS };
    private static final String[] MAIL_TAB_NAMES = { "Alliance", "System", "Reports" };

    // ========== Configuration (loaded in loadConfiguration()) ==========
    private int scheduleOffsetMinutes;

    /**
     * Constructs a new MailRewardsTask.
     *
     * @param profile the profile this task belongs to
     * @param tpTask  the task type enum
     */
    public MailRewardsTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    /**
     * Loads task configuration from the profile.
     * This must be called from execute() to ensure configuration is current.
     * 
     * <p>
     * Loads:
     * <ul>
     * <li>Schedule offset in minutes (how long after completion to run again)</li>
     * </ul>
     * 
     * <p>
     * Default offset is 60 minutes if not configured.
     */
    private void loadConfiguration() {
        Integer configuredOffset = profile.getConfig(
                EnumConfigurationKey.MAIL_REWARDS_OFFSET_INT, Integer.class);
        this.scheduleOffsetMinutes = (configuredOffset != null)
                ? configuredOffset
                : DEFAULT_OFFSET_MINUTES;

        logDebug("Configuration loaded - Schedule offset: " + scheduleOffsetMinutes + " minutes");
    }

    /**
     * Main execution method for the Mail Rewards task.
     * 
     * <p>
     * Flow:
     * <ol>
     * <li>Load configuration</li>
     * <li>Open mail menu</li>
     * <li>Process each mail tab (Alliance, System, Reports)</li>
     * <li>Close mail menu</li>
     * <li>Schedule next run</li>
     * </ol>
     * 
     * <p>
     * If mail menu cannot be opened, reschedules for retry in 10 minutes.
     */
    @Override
    protected void execute() {
        loadConfiguration();

        if (!openMailMenu()) {
            handleMailMenuOpenFailure();
            return;
        }

        processAllMailTabs();
        closeMailMenu();
        scheduleNextRun();
    }

    /**
     * Opens the mail menu by searching for and tapping the mail icon.
     * 
     * <p>
     * The mail icon is located in the bottom-right corner of the screen.
     * After tapping, verifies that the mail menu actually opened.
     * 
     * @return true if mail menu opened successfully, false otherwise
     */
    private boolean openMailMenu() {
        logInfo("Opening mail menu.");

        DTOImageSearchResult mailMenu = templateSearchHelper.searchTemplate(
                EnumTemplates.MAIL_MENU,
                SearchConfig.builder()
                        .withArea(new DTOArea(MAIL_MENU_SEARCH_TOP_LEFT, MAIL_MENU_SEARCH_BOTTOM_RIGHT))
                        .withMaxAttempts(MAIL_MENU_SEARCH_RETRIES)
                        .withDelay(200L)
                        .build());

        if (!mailMenu.isFound()) {
            logError("Unable to find mail menu icon.");
            return false;
        }

        tapPoint(mailMenu.getPoint());
        sleepTask(500); // Wait for mail menu to open

        return verifyMailMenuOpened();
    }

    /**
     * Verifies that the mail menu successfully opened.
     * 
     * <p>
     * Checks for the mail menu header in the top-left corner of the screen.
     * 
     * @return true if mail menu is open, false otherwise
     */
    private boolean verifyMailMenuOpened() {
        DTOImageSearchResult inMailMenu = templateSearchHelper.searchTemplate(
                EnumTemplates.MAIL_MENU_OPEN,
                SearchConfig.builder()
                        .withArea(new DTOArea(MAIL_MENU_OPEN_TOP_LEFT, MAIL_MENU_OPEN_BOTTOM_RIGHT))
                        .withMaxAttempts(MAIL_MENU_OPEN_VERIFICATION_RETRIES)
                        .withDelay(200L)
                        .build());

        if (!inMailMenu.isFound()) {
            logError("Mail menu did not open successfully.");
            return false;
        }

        logDebug("Mail menu opened successfully.");
        return true;
    }

    /**
     * Handles failure to open the mail menu.
     * Reschedules the task to retry in a few minutes.
     */
    private void handleMailMenuOpenFailure() {
        logError("Failed to open mail menu. Retrying in " + ERROR_RETRY_MINUTES + " minutes.");
        reschedule(LocalDateTime.now().plusMinutes(ERROR_RETRY_MINUTES));
    }

    /**
     * Processes all three mail tabs (Alliance, System, Reports).
     * 
     * <p>
     * For each tab:
     * <ol>
     * <li>Switches to the tab</li>
     * <li>Claims all visible rewards</li>
     * <li>Scrolls to find and claim additional "overflow" rewards</li>
     * </ol>
     */
    private void processAllMailTabs() {
        for (int i = 0; i < MAIL_TAB_COUNT; i++) {
            DTOPoint tabButton = MAIL_TAB_BUTTONS[i];
            String tabName = MAIL_TAB_NAMES[i];

            logInfo("Processing " + tabName + " tab.");
            processMailTab(tabButton);
        }
    }

    /**
     * Processes a single mail tab.
     * 
     * <p>
     * Strategy:
     * <ol>
     * <li>Switch to the tab</li>
     * <li>Claim all visible rewards (initial claim)</li>
     * <li>Check for unclaimed rewards indicator</li>
     * <li>If found: scroll down to reveal more mail and claim again</li>
     * <li>Repeat until no more unclaimed rewards found</li>
     * </ol>
     * 
     * <p>
     * The two-phase claiming approach handles "overflow" mail that only
     * becomes visible after the initial claim action.
     * 
     * @param tabButton the coordinates of the tab button to tap
     */
    private void processMailTab(DTOPoint tabButton) {
        switchToTab(tabButton);

        // Initial claim of visible rewards
        claimAllVisibleRewards();

        // Check for and process overflow rewards
        processOverflowRewards();
    }

    /**
     * Switches to a mail tab by tapping its button.
     * 
     * @param tabButton the coordinates of the tab button
     */
    private void switchToTab(DTOPoint tabButton) {
        tapPoint(tabButton);
        sleepTask(200); // Wait for tab content to load
    }

    /**
     * Claims all visible rewards in the current tab.
     * 
     * <p>
     * Taps the claim button multiple times to ensure all rewards are claimed.
     */
    private void claimAllVisibleRewards() {
        logInfo("Claiming rewards in current tab.");
        tapRandomPoint(
                CLAIM_BUTTON_TOP_LEFT,
                CLAIM_BUTTON_BOTTOM_RIGHT,
                CLAIM_BUTTON_TAP_COUNT,
                CLAIM_BUTTON_TAP_DELAY);
        sleepTask(500); // Wait for claim animation
    }

    /**
     * Processes overflow rewards that appear after scrolling.
     * 
     * <p>
     * Strategy:
     * <ol>
     * <li>Check for unclaimed rewards indicator</li>
     * <li>If found: scroll down to reveal more mail</li>
     * <li>Claim revealed rewards</li>
     * <li>Repeat until no more unclaimed rewards</li>
     * </ol>
     * 
     * <p>
     * Includes a safety limit to prevent infinite loops if something goes wrong.
     */
    private void processOverflowRewards() {
        int searchAttempts = 0;

        while (hasUnclaimedRewards()) {
            if (searchAttempts > 0) {
                logInfo("Overflow rewards detected. Scrolling to reveal more mail.");
                scrollDownMailList();
            }

            claimAllVisibleRewards();

            searchAttempts++;
            if (searchAttempts >= MAX_MAIL_SEARCH_ATTEMPTS) {
                logError("There is absolutely no way this condition should ever be hit in a normal scenario. " +
                        "Something is broken. Either you have not checked your mail in DAYS, " +
                        "or we are stuck somewhere you shouldn't be. " +
                        "Please report to the devs which menu this was stuck on if you see this message.");
                break;
            }
        }

        if (searchAttempts > 0) {
            logDebug("Processed " + searchAttempts + " overflow reward cycle(s).");
        }
    }

    /**
     * Checks if there are unclaimed rewards in the current tab.
     * 
     * <p>
     * Searches for the unclaimed rewards indicator icon.
     * 
     * @return true if unclaimed rewards found, false otherwise
     */
    private boolean hasUnclaimedRewards() {
        DTOImageSearchResult unclaimedRewards = templateSearchHelper.searchTemplate(
                EnumTemplates.MAIL_UNCLAIMED_REWARDS,
                SearchConfig.builder()
                        .withMaxAttempts(UNCLAIMED_REWARDS_SEARCH_RETRIES)
                        .withDelay(100L)
                        .build());

        return unclaimedRewards.isFound();
    }

    /**
     * Scrolls down the mail list to reveal additional mail items.
     * 
     * <p>
     * Performs multiple upward swipes (which scrolls the list downward)
     * to reveal mail that was below the initial visible area.
     */
    private void scrollDownMailList() {
        for (int i = 0; i < SWIPES_PER_PAGE; i++) {
            // Swipe up to scroll down the mail list
            swipe(SCROLL_START_POINT, SCROLL_END_POINT);
            sleepTask(250); // Wait for scroll animation
        }
    }

    /**
     * Closes the mail menu by tapping the back button.
     */
    private void closeMailMenu() {
        logDebug("Closing mail menu.");
        tapBackButton();
        sleepTask(500); // Wait for menu to close
    }

    /**
     * Schedules the next execution of this task.
     * 
     * <p>
     * Scheduling logic:
     * <ul>
     * <li>Calculates next run time as: now + configured offset</li>
     * <li>If calculated time is after game reset: caps at game reset time</li>
     * <li>This ensures the task runs at least once per day</li>
     * </ul>
     * 
     * <p>
     * Example: If offset is 60 minutes but only 30 minutes remain until reset,
     * the task will run at reset time instead.
     */
    private void scheduleNextRun() {
        LocalDateTime nextExecutionTime = calculateNextExecutionTime();
        reschedule(nextExecutionTime);
        logInfo("Mail rewards task completed. Next run at: " + nextExecutionTime.format(DATETIME_FORMATTER));
    }

    /**
     * Calculates the next execution time based on configured offset.
     * 
     * <p>
     * The next run time is capped at the game reset time to ensure
     * the task runs at least once per day.
     * 
     * @return the LocalDateTime for next execution
     */
    private LocalDateTime calculateNextExecutionTime() {
        LocalDateTime proposedTime = LocalDateTime.now().plusMinutes(scheduleOffsetMinutes);
        LocalDateTime gameResetTime = UtilTime.getGameReset();

        // Cap at game reset to ensure daily execution
        return proposedTime.isAfter(gameResetTime) ? gameResetTime : proposedTime;
    }

    /**
     * Specifies that this task can start from any screen location.
     * The task will handle navigation to the mail menu internally.
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
}