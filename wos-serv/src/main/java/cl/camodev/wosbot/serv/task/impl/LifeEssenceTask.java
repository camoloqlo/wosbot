package cl.camodev.wosbot.serv.task.impl;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;

import cl.camodev.wosbot.serv.task.EnumStartLocation;

public class LifeEssenceTask extends DelayedTask {

	private static final int MAX_ATTEMPTS = 5;
	private static final int NAVIGATION_DELAY = 2000;
	private static final int MENU_WAIT_DELAY = 3000;
	
	private int attempts = 0;
    private LocalDateTime nextScrollTime = null;

	public LifeEssenceTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
        nextScrollTime = LocalDateTime.now();

	}

	@Override
	protected void execute() {
		if (attempts > MAX_ATTEMPTS) {
			this.setRecurring(false);
			logWarning("Too many failed attempts (" + attempts + "). Removing Life Essence task from the scheduler.");
			return;
		}

		logInfo("Starting Life Essence collection task.");
		
		// Navigate to the Life Essence menu
		if (!navigateToLifeEssenceMenu()) {
			attempts++;
			logWarning("Failed to navigate to Life Essence menu. Attempt " + attempts + " of " + MAX_ATTEMPTS);
			rescheduleWithBackoff();
			return;
		}
		
		// Claim available Life Essence
		int claimedCount = claimLifeEssence();

		// Buy the weekly free scroll if available
        if (nextScrollTime == null || LocalDateTime.now().isAfter(nextScrollTime)) {
            buyWeeklyFreeScroll();
        } else {
            logInfo("Skipping weekly free scroll; next allowed at " + nextScrollTime.toLocalTime() + ".");
        }
		
		// Exit menu and reschedule
		exitAndReschedule(claimedCount);
	}
	
	private void buyWeeklyFreeScroll() {
		logInfo("Attempting to buy weekly free scroll.");

		// Navigate to the shop tab
		logDebug("Navigating to the shop tab.");
		tapPoint(new DTOPoint(670, 195));
		sleepTask(300);

		// Search for the weekly free scroll
		DTOImageSearchResult scroll = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.ISLAND_WEEKLY_FREE_SCROLL, 90);
		if (scroll.isFound()) {
			logInfo("Weekly free scroll found. Attempting to purchase.");
			tapPoint(scroll.getPoint());
			sleepTask(300);

			// Tap the buy button
			DTOImageSearchResult buyButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.ISLAND_WEEKLY_FREE_SCROLL_BUY_BUTTON, 90);
			if (buyButton.isFound()) {
				tapPoint(buyButton.getPoint());
				sleepTask(500);
				logInfo("Weekly free scroll purchased successfully.");
                // Set next scroll time to next monday at reset (00:00 UTC)
                nextScrollTime = ZonedDateTime.now(ZoneOffset.UTC)
                        .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                        .truncatedTo(ChronoUnit.DAYS)
                        .toLocalDateTime();
			} else {
				logWarning("Buy button for weekly free scroll not found.");
			}
		} else {
			logInfo("No weekly free scroll available to purchase.");
		}

        tapRandomPoint(new DTOPoint(40, 30),new DTOPoint(40, 30),1, 1000); // Exit shop
	}

	/**
	 * Navigate to the Life Essence menu
	 * @return true if navigation was successful, false otherwise
	 */
	private boolean navigateToLifeEssenceMenu() {
		// Step 1: Tap on the menu shortcut
		logInfo("Navigating to the Life Essence menu.");
		tapPoint(new DTOPoint(12, 550)); // More precise tap on the shortcut
		sleepTask(NAVIGATION_DELAY);
		
		// Step 2: Ensure we're in the city view by tapping the city button
		logDebug("Ensuring we are in the city view.");
		tapPoint(new DTOPoint(110, 270));
		sleepTask(NAVIGATION_DELAY);
		
		// Step 3: Swipe down to reveal the Life Essence menu option
		logDebug("Swiping down to reveal Life Essence menu.");
		swipe(new DTOPoint(220, 845), new DTOPoint(220, 94));
		sleepTask(1000);
		
		// Try a second swipe if needed
		DTOImageSearchResult lifeEssenceMenu = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.LIFE_ESSENCE_MENU, 90);
		if (!lifeEssenceMenu.isFound()) {
			logDebug("Life Essence menu not found on first swipe, trying again.");
			swipe(new DTOPoint(220, 845), new DTOPoint(220, 94));
			sleepTask(1000);
			lifeEssenceMenu = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.LIFE_ESSENCE_MENU, 90);
		}
		
		// Step 4: Tap on the Life Essence menu option if found
		if (lifeEssenceMenu.isFound()) {
			logInfo("Life Essence menu found. Tapping to open.");
			tapPoint(lifeEssenceMenu.getPoint());
			sleepTask(MENU_WAIT_DELAY); // Allow menu to fully load
			tapBackButton();
			tapBackButton();
			return true;
		} else {
			logWarning("Life Essence menu not found after multiple attempts.");
			return false;
		}
	}
	
	/**
	 * Claim available Life Essence items
	 * @return number of items claimed
	 */
	private int claimLifeEssence() {
		logInfo("Searching for Life Essence to claim.");
		int claimCount = 0;
		
		// First, tap back twice to stop any interfering animations
		tapBackButton();
		sleepTask(500);
		tapBackButton();
		sleepTask(1000);
		
		// Look for claimable Life Essence
		for (int i = 1; i <= 5; i++) {
			logDebug("Searching for Life Essence, attempt " + i + ".");
			
			List<DTOImageSearchResult> lifeEssence = emuManager.searchTemplates(
				EMULATOR_NUMBER,
				EnumTemplates.LIFE_ESSENCE_CLAIM,
				new DTOPoint(0,65),
				new DTOPoint(720,1280),
				90,
				5
			);

			// Process each found Life Essence item
			for(DTOImageSearchResult essence : lifeEssence) {
				logDebug("Found claimable Life Essence.");
				tapPoint(essence.getPoint());
				sleepTask(500); // Increased delay for animation to complete
				claimCount++;
			}
		}
			
		logInfo("Claimed " + claimCount + " Life Essence items.");
		return claimCount;
	}
	
	/**
	 * Exit the Life Essence interface and reschedule the task
	 * @param claimCount number of items claimed
	 */
	private void exitAndReschedule(int claimCount) {
		// Exit the Life Essence interface
		tapPoint(new DTOPoint(40, 30)); // Tap back/exit button
		sleepTask(1000);
		
		// Get the configured time offset for the next run
		int offsetMinutes = profile.getConfig(EnumConfigurationKey.LIFE_ESSENCE_OFFSET_INT, Integer.class);
		
		// Adjust the offset based on claim success (shorter time if nothing was claimed)
		if (claimCount == 0 && attempts < MAX_ATTEMPTS) {
			offsetMinutes = Math.max(offsetMinutes / 2, 15); // At least 15 minutes, but shorter than normal
		}
		
		// Reschedule the task
		LocalDateTime nextSchedule = LocalDateTime.now().plusMinutes(offsetMinutes);
		this.reschedule(nextSchedule);
		
		// Reset attempts if we had a successful run
		if (claimCount > 0) {
			attempts = 0;
		}
		
		logInfo("Life Essence task completed. Claimed: " + claimCount + 
			". Rescheduled for " + nextSchedule.toLocalTime() + " (" + offsetMinutes + " minutes from now).");
	}
	
	/**
	 * Reschedule with exponential backoff when encountering errors
	 */
	private void rescheduleWithBackoff() {
		// Calculate backoff time: 5-30 minutes based on attempt count
		int backoffMinutes = Math.min(5 * attempts, 30);
		LocalDateTime nextSchedule = LocalDateTime.now().plusMinutes(backoffMinutes);
		
		this.reschedule(nextSchedule);
		
		logInfo("Rescheduling after failure with " + backoffMinutes + 
			" minute backoff. Next attempt at " + nextSchedule.toLocalTime());
	}

	@Override
	protected EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.HOME;
	}

}
