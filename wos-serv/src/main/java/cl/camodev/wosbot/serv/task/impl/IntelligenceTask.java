package cl.camodev.wosbot.serv.task.impl;

import java.awt.Color;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTaskState;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.impl.ServTaskManager;
import cl.camodev.wosbot.serv.impl.StaminaService;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.TaskQueue;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper.SearchConfig;
import net.sourceforge.tess4j.TesseractException;

public class IntelligenceTask extends DelayedTask {

	// Constants
	private static final int MIN_STAMINA_REQUIRED = 30;
	private static final int SURVIVOR_STAMINA_COST = 12;
	private static final int JOURNEY_STAMINA_COST = 10;

	private final IDailyTaskRepository iDailyTaskRepository = DailyTaskRepository.getRepository();

	// Runtime state (reset each execution)
	private boolean marchQueueLimitReached;
	private boolean beastMarchSent;

	// Specific configurations
	private boolean autoJoinDisabledForIntel;
	private boolean recallGatherTroops;

	// Configuration (loaded fresh each execution after profile refresh)
	private boolean fcEra;
	private boolean useSmartProcessing;
	private boolean useFlag;
	private Integer flagNumber;
	private boolean beastsEnabled;
	private boolean fireBeastsEnabled;
	private boolean survivorCampsEnabled;
	private boolean explorationsEnabled;
	private boolean isAutoJoinTaskEnabled;
	private boolean processingTask;
	private DTOTaskState autoJoinTask;
	private TextRecognitionRetrier<LocalDateTime> textHelper;

	private SearchConfig searchConfigMultiple = SearchConfig.builder()
			.withMaxAttempts(3)
			.withDelay(300L)
			.withThreshold(90)
			.withMaxResults(10)
			.build();

	public IntelligenceTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
		super(profile, tpTask);
	}

	@Override
	protected void execute() {
		logInfo("Starting Intel task.");

		// Load configuration fresh after profile refresh
		loadConfiguration();

		// Reset runtime state
		processingTask = true;
		beastMarchSent = false;

		autoJoinTask = ServTaskManager.getInstance().getTaskState(profile.getId(),
				TpDailyTaskEnum.ALLIANCE_AUTOJOIN.getId());
		isAutoJoinTaskEnabled = (autoJoinTask != null) ? true : false;

		if (!autoJoinDisabledForIntel && isAutoJoinTaskEnabled && autoJoinTask.isScheduled()) {
			logInfo("Auto-join is enabled and scheduled, proceeding to disable it.");
			autoJoinDisabledForIntel = allianceHelper.disableAutoJoin();
			if (!autoJoinDisabledForIntel)
				logDebug("Failed to disable auto-join, proceeding anyway.");
		}

		// Recall gather troops if recall is enabled
		if (recallGatherTroops) {
			logInfo("Recall gather troops enabled. Recalling all gather troops...");
			recallGatherTroops();
			logInfo("All gather troops recalled. Proceeding with intel processing.");
		}

		while (processingTask) {
			boolean anyIntelProcessed = false;
			boolean nonBeastIntelProcessed = false;
			beastMarchSent = false;

			// Return to world screen for march checks
			navigationHelper.ensureCorrectScreenLocation(EnumStartLocation.WORLD);

			// Check march availability once
			MarchesAvailable marchesAvailable = checkMarchAvailability();
			marchQueueLimitReached = !marchesAvailable.available();

			// Claim completed missions
			claimCompletedMissions();

			// Check stamina
			if (!hasEnoughStamina()) {
				processingTask = false;
				return; // Already rescheduled in hasEnoughStamina()
			}

			// Process beasts
			if (beastsEnabled && shouldProcessBeasts()) {
				if (processBeastIntel()) {
					anyIntelProcessed = true;
				}
			}

			// Process survivor camps
			if (survivorCampsEnabled) {
				intelScreenHelper.ensureOnIntelScreen();
				logInfo("Searching for survivor camps using grayscale matching.");
				EnumTemplates survivorTemplate = fcEra ? EnumTemplates.INTEL_SURVIVOR_GRAYSCALE_FC
						: EnumTemplates.INTEL_SURVIVOR_GRAYSCALE;
				if (searchAndProcessGrayscale(survivorTemplate, this::processSurvivor)) {
					anyIntelProcessed = true;
					nonBeastIntelProcessed = true;
				}
			}

			// Process explorations
			if (explorationsEnabled) {
				intelScreenHelper.ensureOnIntelScreen();
				logInfo("Searching for explorations using grayscale matching.");
				EnumTemplates journeyTemplate = fcEra ? EnumTemplates.INTEL_JOURNEY_GRAYSCALE_FC
						: EnumTemplates.INTEL_JOURNEY_GRAYSCALE;
				if (searchAndProcessGrayscale(journeyTemplate, this::processJourney)) {
					anyIntelProcessed = true;
					nonBeastIntelProcessed = true;
				}
			}

			// Handle rescheduling
			handleRescheduling(anyIntelProcessed, nonBeastIntelProcessed, marchesAvailable);
		}

		logInfo("Intel Task finished");
	}

	/**
	 * Load configuration from profile after refresh.
	 * Called at the start of each execution to ensure config is current.
	 */
	private void loadConfiguration() {
		this.fcEra = profile.getConfig(EnumConfigurationKey.INTEL_FC_ERA_BOOL, Boolean.class);
		this.useSmartProcessing = profile.getConfig(EnumConfigurationKey.INTEL_SMART_PROCESSING_BOOL, Boolean.class);
		this.recallGatherTroops = profile.getConfig(EnumConfigurationKey.INTEL_RECALL_GATHER_TROOPS_BOOL,
				Boolean.class);
		this.useFlag = profile.getConfig(EnumConfigurationKey.INTEL_USE_FLAG_BOOL, Boolean.class);
		this.flagNumber = useFlag ? profile.getConfig(EnumConfigurationKey.INTEL_BEASTS_FLAG_INT, Integer.class) : null;
		this.beastsEnabled = profile.getConfig(EnumConfigurationKey.INTEL_BEASTS_BOOL, Boolean.class);
		this.fireBeastsEnabled = profile.getConfig(EnumConfigurationKey.INTEL_FIRE_BEAST_BOOL, Boolean.class);
		this.survivorCampsEnabled = profile.getConfig(EnumConfigurationKey.INTEL_CAMP_BOOL, Boolean.class);
		this.explorationsEnabled = profile.getConfig(EnumConfigurationKey.INTEL_EXPLORATION_BOOL, Boolean.class);
		this.textHelper = new TextRecognitionRetrier<>(provider);

		logDebug("Configuration loaded: fcEra=" + fcEra + ", useSmartProcessing=" + useSmartProcessing +
				", recallGatherTroops=" + recallGatherTroops + ", useFlag=" + useFlag + ", beastsEnabled="
				+ beastsEnabled);
	}

	/**
	 * Check march availability using appropriate method based on configuration
	 */
	private MarchesAvailable checkMarchAvailability() {
		if (useSmartProcessing) {
			return getMarchesAvailable();
		} else {
			boolean available = marchHelper.checkMarchesAvailable();
			return new MarchesAvailable(available, LocalDateTime.now());
		}
	}

	/**
	 * Determine if beasts should be processed based on current state
	 */
	private boolean shouldProcessBeasts() {
		// Always search for beasts - we can find them even if marches are unavailable
		// The processBeast() method will handle the case where marches are full

		if (useFlag && beastMarchSent) {
			logInfo("Beast march already sent (flag mode), skipping beast search.");
			return false;
		}

		return true;
	}

	/**
	 * Check if there's enough stamina to continue
	 */
	private boolean hasEnoughStamina() {
		int staminaValue = StaminaService.getServices().getCurrentStamina(profile.getId());

		if (staminaValue < MIN_STAMINA_REQUIRED) {
			logWarning("Not enough stamina to process intel. Current stamina: " + staminaValue +
					". Required: " + MIN_STAMINA_REQUIRED + ".");
			long minutesToRegen = (long) (MIN_STAMINA_REQUIRED - staminaValue) * 5L;
			LocalDateTime rescheduleTime = LocalDateTime.now().plusMinutes(minutesToRegen);
			reschedule(rescheduleTime);
			return false;
		}
		return true;
	}

	/**
	 * Claim all completed missions
	 */
	private void claimCompletedMissions() {
		intelScreenHelper.ensureOnIntelScreen();
		logInfo("Searching for completed missions to claim.");

		for (int i = 0; i < 2; i++) {
			logDebug("Searching for completed missions. Attempt " + (i + 1) + ".");
			List<DTOImageSearchResult> completed = templateSearchHelper.searchTemplates(
					EnumTemplates.INTEL_COMPLETED,
					searchConfigMultiple);

			if (completed.isEmpty()) {
				logInfo("No completed missions found on attempt " + (i + 1) + ".");
				continue;
			}

			logInfo("Found " + completed.size() + " completed missions. Claiming them now.");

			for (DTOImageSearchResult completedMission : completed) {
				tapPoint(completedMission.getPoint());
				sleepTask(500);
				tapRandomPoint(new DTOPoint(700, 1270), new DTOPoint(710, 1280), 3, 100);
				sleepTask(500);
			}
		}
	}

	/**
	 * Process all beast intel (fire beasts and regular beasts)
	 */
	private boolean processBeastIntel() {
		intelScreenHelper.ensureOnIntelScreen();
		boolean beastFound = false;

		// Search for fire beasts if enabled
		if (fireBeastsEnabled && !(useFlag && beastMarchSent)) {
			logInfo("Searching for fire beasts.");
			if (searchAndProcessGrayscale(EnumTemplates.INTEL_FIRE_BEAST, this::processBeast)) {
				beastFound = true;
				if (useFlag) {
					return true; // Only one beast march in flag mode
				}
			}
		}

		// Search for regular beasts
		if (!(useFlag && beastMarchSent)) {
			logInfo("Searching for beasts using grayscale matching.");
			EnumTemplates[] beast_screenings;
			if (fcEra) {
				// In FC era prefer the old FC template, then fallback to new FC 
				beast_screenings = new EnumTemplates[] {
						EnumTemplates.INTEL_BEAST_GRAYSCALE_FC,
						EnumTemplates.INTEL_BEAST_GRAYSCALE_FC1,
				};
			} else {
				// Non-FC default
				beast_screenings = new EnumTemplates[] {
						EnumTemplates.INTEL_BEAST_GRAYSCALE
				};
			}

			for (EnumTemplates beast_screening : beast_screenings) {
				if (searchAndProcessGrayscale(beast_screening, this::processBeast)) {
					beastFound = true;
					break;
				}
			}
		}

		return beastFound;
	}

	/**
	 * Handle rescheduling logic based on what was processed
	 */
	private void handleRescheduling(boolean anyIntelProcessed, boolean nonBeastIntelProcessed,
			MarchesAvailable marchesAvailable) {
		sleepTask(500);

		if (!anyIntelProcessed) {
			// No intel found at all - try to read cooldown timer
			tryRescheduleFromCooldown();

			// Re-queue gather tasks if we recalled troops earlier
			if (recallGatherTroops) {
				logInfo("Intel processing complete. Re-queueing gather tasks...");
				requeueGatherTasks();
			}

			processingTask = false;
			return;
		}

		// If intel WAS found but marches are full, we need different logic
		if (marchQueueLimitReached && nonBeastIntelProcessed) {
			// Non-beast intel (survivors/journeys) were processed
			// Even if marches are full, we processed what we could
			reschedule(LocalDateTime.now().plusMinutes(2));
			logInfo("Non-beast intel processed but march queue full. " +
					"Rescheduling in 2 minutes to check for more.");

			processingTask = false;
			return;
		}

		if (marchQueueLimitReached && !nonBeastIntelProcessed && !beastMarchSent) {
			// Only beasts found but no marches available and no beast deployed
			if (useSmartProcessing && marchesAvailable.rescheduleTo() != null) {
				reschedule(marchesAvailable.rescheduleTo());
				logInfo("March queue is full, and only beasts remain. Rescheduling for when marches will be available at "
						+ marchesAvailable.rescheduleTo());
			} else {
				reschedule(LocalDateTime.now().plusMinutes(2));
				logInfo("March queue is full, and only beasts remain. Rescheduling in 2 minutes");
			}

			processingTask = false;
			return;
		}

		if (!beastMarchSent || marchQueueLimitReached) {
			// Some intel was processed but might have skipped beasts
			reschedule(LocalDateTime.now().plusMinutes(2));
			logInfo("Rescheduling in 2 minutes to check if any intel got skipped. " +
					"Beast march sent: " + beastMarchSent + ", March queue full: " + marchQueueLimitReached);

			processingTask = false;
			return;
		}

		// Beast march was sent successfully
		logInfo("Beast march sent successfully. Continuing processing.");
	}

	/**
	 * Try to read intel cooldown timer and reschedule accordingly
	 */
	private void tryRescheduleFromCooldown() {
		logInfo("No intel items found. Attempting to read the cooldown timer.");

		LocalDateTime cooldown = textHelper.execute(
				new DTOPoint(378, 103),
				new DTOPoint(508, 146),
				3,
				200L,
				DTOTesseractSettings.builder()
						.setAllowedChars("0123456789")
						.setRemoveBackground(true)
						.setTextColor(new Color(255, 255, 255))
						.build(),
				TimeValidators::isValidTime,
				TimeConverters::toLocalDateTime);

		if (cooldown == null) {
			logWarning("Failed to read cooldown timer via OCR. Rescheduling in 10 minutes.");
			reschedule(LocalDateTime.now().plusMinutes(10));
			tapBackButton();
			autoJoinDisabledForIntel = false;
			return;
		}

		reschedule(cooldown);
		tapBackButton();
		autoJoinDisabledForIntel = false;

		TaskQueue queue = servScheduler.getQueueManager().getQueue(profile.getId());
		if (isAutoJoinTaskEnabled && autoJoinTask.isScheduled()) {
			queue.executeTaskNow(TpDailyTaskEnum.ALLIANCE_AUTOJOIN, true);
		}

		logInfo("No new intel found. Rescheduling task to run at: " + cooldown.format(DATETIME_FORMATTER));
	}

	/**
	 * Search for a template using grayscale matching and process it
	 */
	private boolean searchAndProcessGrayscale(EnumTemplates template, Consumer<DTOImageSearchResult> processMethod) {
		logInfo("Searching for grayscale template '" + template + "'");
		DTOImageSearchResult result = templateSearchHelper.searchTemplateGrayscale(template, SearchConfigConstants.SINGLE_WITH_RETRIES);

		if (result.isFound()) {
			logInfo("Grayscale template found: " + template);
			processMethod.accept(result);
			return true;
		}
		logWarning("Grayscale template not found: " + template);
		return false;
	}

	private void processJourney(DTOImageSearchResult result) {
		tapPoint(result.getPoint());
		sleepTask(2000);

		DTOImageSearchResult view = templateSearchHelper.searchTemplate(EnumTemplates.INTEL_VIEW, SearchConfigConstants.SINGLE_WITH_RETRIES);
		if (!view.isFound()) {
			logWarning("Could not find the 'View' button for the journey. Going back.");
			tapBackButton();
			return;
		}

		tapPoint(view.getPoint());
		sleepTask(500);

		DTOImageSearchResult explore = templateSearchHelper.searchTemplate(EnumTemplates.INTEL_EXPLORE, SearchConfigConstants.SINGLE_WITH_RETRIES);
		if (!explore.isFound()) {
			logWarning("Could not find the 'Explore' button for the journey. Going back.");
			tapBackButton();
			tapBackButton(); // Back from view screen
			return;
		}

		tapPoint(explore.getPoint());
		sleepTask(500);
		tapPoint(new DTOPoint(520, 1200));
		sleepTask(1000);
		tapBackButton();
		StaminaService.getServices().subtractStamina(profile.getId(), JOURNEY_STAMINA_COST);
	}

	private void processSurvivor(DTOImageSearchResult result) {
		tapPoint(result.getPoint());
		sleepTask(2000);

		DTOImageSearchResult view = templateSearchHelper.searchTemplate(EnumTemplates.INTEL_VIEW, SearchConfigConstants.SINGLE_WITH_RETRIES);
		if (!view.isFound()) {
			logWarning("Could not find the 'View' button for the survivor. Going back.");
			tapBackButton();
			return;
		}

		tapPoint(view.getPoint());
		sleepTask(500);

		DTOImageSearchResult rescue = templateSearchHelper.searchTemplate(EnumTemplates.INTEL_RESCUE, SearchConfigConstants.SINGLE_WITH_RETRIES);
		if (!rescue.isFound()) {
			logWarning("Could not find the 'Rescue' button for the survivor. Going back.");
			tapBackButton();
			tapBackButton(); // Back from view screen
			return;
		}

		tapPoint(rescue.getPoint());
		sleepTask(500);
		StaminaService.getServices().subtractStamina(profile.getId(), SURVIVOR_STAMINA_COST);
	}

	private void processBeast(DTOImageSearchResult beast) {
		if (marchQueueLimitReached) {
			logInfo("Beast found but march queue is full. Skipping deployment but marking as found.");
			return;
		}

		if (useFlag && beastMarchSent) {
			logInfo("Beast march already sent with flag. Skipping beast hunt.");
			return;
		}

		tapPoint(beast.getPoint());
		sleepTask(2000);

		DTOImageSearchResult view = templateSearchHelper.searchTemplate(EnumTemplates.INTEL_VIEW, SearchConfigConstants.SINGLE_WITH_RETRIES);
		if (!view.isFound()) {
			logWarning("Could not find the 'View' button for the beast. Going back.");
			tapBackButton();
			return;
		}
		tapPoint(view.getPoint());
		sleepTask(500);

		DTOImageSearchResult attack = templateSearchHelper.searchTemplate(EnumTemplates.INTEL_ATTACK, SearchConfigConstants.SINGLE_WITH_RETRIES);
		if (!attack.isFound()) {
			logWarning("Could not find the 'Attack' button for the beast. Going back.");
			tapBackButton();
			tapBackButton(); // Back from view screen
			return;
		}
		tapPoint(attack.getPoint());
		sleepTask(500);

		// Check if the march screen is open
		DTOImageSearchResult deployButton = templateSearchHelper.searchTemplate(EnumTemplates.DEPLOY_BUTTON,
				SearchConfigConstants.SINGLE_WITH_RETRIES);
		if (!deployButton.isFound()) {
			logError("March queue is full. Cannot start a new march.");
			marchQueueLimitReached = true;
			return;
		}

		// Select flag if needed
		if (useFlag) {
			marchHelper.selectFlag(flagNumber);
		}

		// Equalize troops
		DTOImageSearchResult equalizeButton = templateSearchHelper.searchTemplate(EnumTemplates.RALLY_EQUALIZE_BUTTON,
				SearchConfigConstants.SINGLE_WITH_RETRIES);
		if (equalizeButton.isFound()) {
			tapPoint(equalizeButton.getPoint());
			sleepTask(300);
		}

		// Parse travel time
		long travelTimeSeconds = staminaHelper.parseTravelTime();;

		// Parse stamina cost
		Integer spentStamina = staminaHelper.getSpentStamina();

		// Deploy march
		DTOImageSearchResult deploy = templateSearchHelper.searchTemplate(EnumTemplates.DEPLOY_BUTTON, SearchConfigConstants.SINGLE_WITH_RETRIES);
		if (!deploy.isFound()) {
			logError("Deploy button not found. Rescheduling to try again in 5 minutes.");
			reschedule(LocalDateTime.now().plusMinutes(5));
			processingTask = false; // Stop processing task
			return;
		}

		tapPoint(deploy.getPoint());
		sleepTask(1000);

		// Check for deployment confirmation dialog (troop imbalance)
		DTOImageSearchResult confirmDialog = templateSearchHelper.searchTemplate(EnumTemplates.DEPLOY_CONFIRMATION_DIALOG, SearchConfigConstants.SINGLE_WITH_RETRIES);
		if (confirmDialog.isFound()) {
			logInfo("Deployment confirmation dialog detected (troop imbalance). Confirming deployment.");
			tapPoint(new DTOPoint(211, 713));
			sleepTask(300);
			tapPoint(new DTOPoint(509, 789));
			sleepTask(300);
		}

		// Verify deployment
		deploy = templateSearchHelper.searchTemplate(EnumTemplates.DEPLOY_BUTTON, SearchConfigConstants.SINGLE_WITH_RETRIES);
		if (deploy.isFound()) {
			logWarning(
					"Deploy button still present after deployment attempt. March may have failed. Rescheduling in 5 minutes.");
			reschedule(LocalDateTime.now().plusMinutes(5));
			processingTask = false; // Stop processing task
			return;
		}

		logInfo("Beast march deployed successfully.");
		beastMarchSent = true;

		// Update stamina
		staminaHelper.subtractStamina(spentStamina, false); // false = not rally, use 10 stamina default

		// Reschedule for march return
		if (travelTimeSeconds <= 0) {
			logError("Failed to parse travel time via OCR. Using 5 minute fallback reschedule.");
			LocalDateTime rescheduleTime = LocalDateTime.now().plusMinutes(5);
			reschedule(rescheduleTime);
			processingTask = false; // Stop processing task
			return;
		}

		if (useSmartProcessing) {
			LocalDateTime rescheduleTime = LocalDateTime.now().plusSeconds(travelTimeSeconds);
			reschedule(rescheduleTime);
			logInfo("Beast march scheduled to return at " + UtilTime.localDateTimeToDDHHMMSS(rescheduleTime));
			processingTask = false; // Stop processing task
		}
	}

	private MarchesAvailable getMarchesAvailable() {
		// Open active marches panel
		marchHelper.openLeftMenuCitySection(false);

		DTOTesseractSettings settings = DTOTesseractSettings.builder()
				.setAllowedChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
				.setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
				.build();

		// Try OCR to find idle marches
		try {
			for (int i = 0; i < 5; i++) {
				String ocrSearchResult = emuManager.ocrRegionText(EMULATOR_NUMBER,
						new DTOPoint(10, 342),
						new DTOPoint(435, 772),
						settings);
				Pattern idleMarchesPattern = Pattern.compile("idle");
				Matcher m = idleMarchesPattern.matcher(ocrSearchResult.toLowerCase());
				if (m.find()) {
					logInfo("Idle marches detected via OCR");
					return new MarchesAvailable(true, null);
				} else {
					logDebug("No idle marches detected via OCR (Attempt " + (i + 1) + "/5).");
				}
			}
		} catch (IOException | TesseractException e) {
			logDebug("OCR attempt failed: " + e.getMessage());
		}

		logInfo("No idle marches detected via OCR. Analyzing gather march queues...");

		// Collect active march queue counts
		int totalMarchesAvailable = profile.getConfig(EnumConfigurationKey.GATHER_ACTIVE_MARCH_QUEUE_INT,
				Integer.class);
		int activeMarchQueues = 0;
		LocalDateTime earliestAvailableMarch = LocalDateTime.now().plusHours(14); // Very long default time

		for (GatherType gatherType : GatherType.values()) {
			DTOImageSearchResult resource = templateSearchHelper.searchTemplate(gatherType.getTemplate(), SearchConfigConstants.SINGLE_WITH_RETRIES);
			if (!resource.isFound()) {
				logDebug("March queue for " + gatherType.getName() + " is not active. (Used: " +
						activeMarchQueues + "/" + totalMarchesAvailable + ")");
				continue;
			}

			// March detected for this resource type
			activeMarchQueues++;
			logInfo("March queue for " + gatherType.getName() + " detected. (Used: " +
					activeMarchQueues + "/" + totalMarchesAvailable + ")");

			// Find when the unified GATHER_RESOURCES task is scheduled to complete
			DailyTask gatherTask = iDailyTaskRepository
					.findByProfileIdAndTaskName(profile.getId(), TpDailyTaskEnum.GATHER_RESOURCES);

			if (gatherTask != null && gatherTask.getNextSchedule() != null) {
				LocalDateTime nextSchedule = gatherTask.getNextSchedule();

				if (nextSchedule.isBefore(earliestAvailableMarch)) {
					earliestAvailableMarch = nextSchedule;
					logInfo("Updated earliest available march: " + earliestAvailableMarch);
				}
			}
		}

		if (activeMarchQueues >= totalMarchesAvailable) {
			logInfo("All march queues used. Earliest available march: " + earliestAvailableMarch);
			return new MarchesAvailable(false, earliestAvailableMarch);
		}

		// Not all march queues are used, but no idle marches detected
		// Could be auto-rally marches returning soon
		logInfo("Not all march queues used (" + activeMarchQueues + "/" + totalMarchesAvailable +
				"), but no idle marches. Suspected auto-rally marches. Rescheduling in 5 minutes.");
		return new MarchesAvailable(false, LocalDateTime.now().plusMinutes(5));
	}

	/**
	 * Recalls all gathering troops back to the city
	 * Checks for returning arrows, march view, and speedup buttons
	 * Continues until all troops are recalled or max retries reached
	 */
	private void recallGatherTroops() {
		int maxRetries = 120; // Safety limit to avoid long-running loop
		int attempt = 0;

		logInfo("Recalling all gather troops to the city...");

		while (attempt < maxRetries) {
			attempt++;

			DTOImageSearchResult returningArrow = templateSearchHelper.searchTemplate(EnumTemplates.MARCHES_AREA_RECALL_BUTTON,
					SearchConfigConstants.SINGLE_WITH_RETRIES);
			DTOImageSearchResult marchView = templateSearchHelper.searchTemplate(EnumTemplates.MARCHES_AREA_VIEW_BUTTON,
					SearchConfigConstants.SINGLE_WITH_RETRIES);
			DTOImageSearchResult marchSpeedup = templateSearchHelper.searchTemplate(EnumTemplates.MARCHES_AREA_SPEEDUP_BUTTON,
					SearchConfigConstants.SINGLE_WITH_RETRIES);

			boolean foundReturning = returningArrow != null && returningArrow.isFound();
			boolean foundView = marchView != null && marchView.isFound();
			boolean foundSpeedup = marchSpeedup != null && marchSpeedup.isFound();

			logDebug(String.format("recallGatherTroops status => returning:%b view:%b speedup:%b (attempt %d)",
					foundReturning, foundView, foundSpeedup, attempt));

			// If nothing is present, we're done
			if (!foundReturning && !foundView && !foundSpeedup) {
				logInfo("No march indicators found. All gather troops are recalled or none present.");
				return; // Finished successfully
			}

			// If there's a returning arrow (recall button), tap it to recall troops
			if (foundReturning) {
				logInfo("Returning arrow found - attempting to tap recall button");
				tapRandomPoint(returningArrow.getPoint(), returningArrow.getPoint(), 1, 300);
				tapRandomPoint(new DTOPoint(446, 780), new DTOPoint(578, 800), 1, 200); // Confirm recall
			}

			// If 'view' or 'speedup' buttons are present, wait for UI to clear
			if (foundView || foundSpeedup) {
				logInfo("Troops are still marching - waiting for them to return");
				sleepTask(1000);
			}

			// Short sleep between attempts to avoid tight loop
			sleepTask(200);
		}

		// If we reach here, max retries were hit
		logError("recallGatherTroops exceeded max attempts (" + maxRetries + "), exiting to avoid deadlock");
	}

	/**
	 * Re-queues gather tasks after intel completes
	 * This ensures gather troops can be sent out again
	 */
	private void requeueGatherTasks() {
		logInfo("Re-queueing gather tasks after Intel completion...");

		// Get the task queue for this profile
		TaskQueue queue = servScheduler.getQueueManager().getQueue(profile.getId());
		if (queue == null) {
			logError("Could not access task queue for profile " + profile.getName());
			return;
		}

		// Check and re-queue gather task if enabled
		if (profile.getConfig(EnumConfigurationKey.GATHER_TASK_BOOL, Boolean.class)) {
			queue.executeTaskNow(TpDailyTaskEnum.GATHER_RESOURCES, true);
			logInfo("Re-queued Gather Resources task");
		}

		sleepTask(500);
	}

	@Override
	protected EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.WORLD;
	}

	@Override
	protected boolean consumesStamina() {
		return true;
	}

	private enum GatherType {
		MEAT("meat", EnumTemplates.GAME_HOME_SHORTCUTS_MEAT),
		WOOD("wood", EnumTemplates.GAME_HOME_SHORTCUTS_WOOD),
		COAL("coal", EnumTemplates.GAME_HOME_SHORTCUTS_COAL),
		IRON("iron", EnumTemplates.GAME_HOME_SHORTCUTS_IRON);

		final String name;
		final EnumTemplates template;

		GatherType(String name, EnumTemplates enumTemplate) {
			this.name = name;
			this.template = enumTemplate;
		}

		public String getName() {
			return name;
		}

		public EnumTemplates getTemplate() {
			return template;
		}
	}

	public record MarchesAvailable(boolean available, LocalDateTime rescheduleTo) {
	}
}