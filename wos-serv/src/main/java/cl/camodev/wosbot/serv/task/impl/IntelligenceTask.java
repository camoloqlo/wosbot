package cl.camodev.wosbot.serv.task.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.almac.repo.DailyTaskRepository;
import cl.camodev.wosbot.almac.repo.IDailyTaskRepository;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTaskState;
import cl.camodev.wosbot.serv.impl.ServTaskManager;
import cl.camodev.wosbot.serv.impl.StaminaService;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.TaskQueue;
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
	private DTOTaskState autoJoinTask;

	public IntelligenceTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
		super(profile, tpTask);
	}

	@Override
	protected void execute() {
		logInfo("Starting Intel task.");

		// Load configuration fresh after profile refresh
		loadConfiguration();

		// Reset runtime state
		beastMarchSent = false;
		boolean anyIntelProcessed = false;
		boolean nonBeastIntelProcessed = false;

		// Check march availability once
		MarchesAvailable marchesAvailable = checkMarchAvailability();
		marchQueueLimitReached = !marchesAvailable.available();

		autoJoinTask = ServTaskManager.getInstance().getTaskState(profile.getId(), TpDailyTaskEnum.ALLIANCE_AUTOJOIN.getId());
		isAutoJoinTaskEnabled = (autoJoinTask != null) ? true : false;

		if (!autoJoinDisabledForIntel && isAutoJoinTaskEnabled && autoJoinTask.isScheduled()) {
			logInfo("Auto-join is enabled and scheduled, proceeding to disable it.");
			autoJoinDisabledForIntel = disableAutoJoin();
			if (!autoJoinDisabledForIntel)
				logDebug("Failed to disable auto-join, proceeding anyway.");
		}

		// Claim completed missions
		claimCompletedMissions();

		// Check stamina
		if (!hasEnoughStamina()) {
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
			ensureOnIntelScreen();
			logInfo("Searching for survivor camps using grayscale matching.");
			EnumTemplates survivorTemplate = fcEra ? EnumTemplates.INTEL_SURVIVOR_GRAYSCALE_FC
					: EnumTemplates.INTEL_SURVIVOR_GRAYSCALE;
			if (searchAndProcessGrayscale(survivorTemplate, 5, 90, this::processSurvivor)) {
				anyIntelProcessed = true;
				nonBeastIntelProcessed = true;
			}
		}

		// Process explorations
		if (explorationsEnabled) {
			ensureOnIntelScreen();
			logInfo("Searching for explorations using grayscale matching.");
			EnumTemplates journeyTemplate = fcEra ? EnumTemplates.INTEL_JOURNEY_GRAYSCALE_FC
					: EnumTemplates.INTEL_JOURNEY_GRAYSCALE;
			if (searchAndProcessGrayscale(journeyTemplate, 5, 90, this::processJourney)) {
				anyIntelProcessed = true;
				nonBeastIntelProcessed = true;
			}
		}

		// Handle rescheduling
		handleRescheduling(anyIntelProcessed, nonBeastIntelProcessed, marchesAvailable);

		logInfo("Intel Task finished.");
	}

	/**
	 * Load configuration from profile after refresh.
	 * Called at the start of each execution to ensure config is current.
	 */
	private void loadConfiguration() {
		this.fcEra = profile.getConfig(EnumConfigurationKey.INTEL_FC_ERA_BOOL, Boolean.class);
		this.useSmartProcessing = profile.getConfig(EnumConfigurationKey.INTEL_SMART_PROCESSING_BOOL, Boolean.class);
		this.useFlag = profile.getConfig(EnumConfigurationKey.INTEL_USE_FLAG_BOOL, Boolean.class);
		this.flagNumber = useFlag ? profile.getConfig(EnumConfigurationKey.INTEL_BEASTS_FLAG_INT, Integer.class) : null;
		this.beastsEnabled = profile.getConfig(EnumConfigurationKey.INTEL_BEASTS_BOOL, Boolean.class);
		this.fireBeastsEnabled = profile.getConfig(EnumConfigurationKey.INTEL_FIRE_BEAST_BOOL, Boolean.class);
		this.survivorCampsEnabled = profile.getConfig(EnumConfigurationKey.INTEL_CAMP_BOOL, Boolean.class);
		this.explorationsEnabled = profile.getConfig(EnumConfigurationKey.INTEL_EXPLORATION_BOOL, Boolean.class);

		logDebug("Configuration loaded: fcEra=" + fcEra + ", useSmartProcessing=" + useSmartProcessing +
				", useFlag=" + useFlag + ", beastsEnabled=" + beastsEnabled);
	}

	/**
	 * Check march availability using appropriate method based on configuration
	 */
	private MarchesAvailable checkMarchAvailability() {
        boolean available = checkMarchesAvailable();
        if (available) {return new MarchesAvailable(true, LocalDateTime.now());}
		if (useSmartProcessing) {
            return getMarchesAvailable();
        }
        return new MarchesAvailable(false, LocalDateTime.now().plusMinutes(5));
	}

	/**
	 * Determine if beasts should be processed based on current state
	 */
	private boolean shouldProcessBeasts() {
		if (marchQueueLimitReached) {
			logInfo("No marches available, skipping beast search.");
			return false;
		}

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
		ensureOnIntelScreen();
		logInfo("Searching for completed missions to claim.");

		for (int i = 0; i < 2; i++) {
			logDebug("Searching for completed missions. Attempt " + (i + 1) + ".");
			List<DTOImageSearchResult> completed = emuManager.searchTemplates(EMULATOR_NUMBER,
					EnumTemplates.INTEL_COMPLETED, 90, 10);

			if (completed.isEmpty()) {
				logInfo("No completed missions found on attempt " + (i + 1) + ".");
				continue;
			}

			logInfo("Found " + completed.size() + " completed missions. Claiming them now.");

			for (DTOImageSearchResult completedMission : completed) {
				tapPoint(completedMission.getPoint());
				sleepTask(500);
				tapRandomPoint(new DTOPoint(700, 1270), new DTOPoint(710, 1280), 5, 250);
				sleepTask(500);
			}
		}
	}

	/**
	 * Process all beast intel (fire beasts and regular beasts)
	 */
	private boolean processBeastIntel() {
		ensureOnIntelScreen();
		boolean beastFound = false;

		// Search for fire beasts if enabled
		if (fireBeastsEnabled && !marchQueueLimitReached && !(useFlag && beastMarchSent)) {
			logInfo("Searching for fire beasts.");
			if (searchAndProcess(EnumTemplates.INTEL_FIRE_BEAST, 5, 90, this::processBeast)) {
				beastFound = true;
				if (useFlag) {
					return true; // Only one beast march in flag mode
				}
			}
		}

		// Search for regular beasts
		if (!marchQueueLimitReached && !(useFlag && beastMarchSent)) {
			logInfo("Searching for beasts using grayscale matching.");
			EnumTemplates beastTemplate = fcEra ? EnumTemplates.INTEL_BEAST_GRAYSCALE_FC
					: EnumTemplates.INTEL_BEAST_GRAYSCALE;
			if (searchAndProcessGrayscale(beastTemplate, 5, 90, this::processBeast)) {
				beastFound = true;
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
			return;
		}

		if (marchQueueLimitReached && !nonBeastIntelProcessed && !beastMarchSent) {
			// Only beasts found but no marches available
			if (useSmartProcessing && marchesAvailable.rescheduleTo() != null) {
				reschedule(marchesAvailable.rescheduleTo());
				logInfo("March queue is full, and only beasts remain. Rescheduling for when marches will be available at "
						+ marchesAvailable.rescheduleTo());
			} else {
				LocalDateTime rescheduleTime = LocalDateTime.now().plusMinutes(5);
				reschedule(rescheduleTime);
				logInfo("March queue is full, and only beasts remain. Rescheduling for 5 minutes at " + rescheduleTime);
			}
			return;
		}

		if (!beastMarchSent || marchQueueLimitReached) {
			// Some intel was processed but might have skipped beasts
			reschedule(LocalDateTime.now().plusMinutes(2));
			logInfo("Rescheduling in 2 minutes to check if any intel got skipped. " +
					"Beast march sent: " + beastMarchSent + ", March queue full: " + marchQueueLimitReached);
			return;
		}

		// Beast march was sent successfully - already rescheduled in processBeast()
		logInfo("Beast march sent successfully. Task already rescheduled for march return.");
	}

	/**
	 * Try to read intel cooldown timer and reschedule accordingly
	 */
	private void tryRescheduleFromCooldown() {
		logInfo("No intel items found. Attempting to read the cooldown timer.");
		try {
			String rescheduleTimeStr = OCRWithRetries(new DTOPoint(120, 110), new DTOPoint(600, 146));
			LocalDateTime rescheduleTime = UtilTime.parseTime(rescheduleTimeStr);
			reschedule(rescheduleTime);
			tapBackButton();
			autoJoinDisabledForIntel = false;
			TaskQueue queue = servScheduler.getQueueManager().getQueue(profile.getId());
			if (isAutoJoinTaskEnabled && autoJoinTask.isScheduled()) {
				queue.executeTaskNow(TpDailyTaskEnum.ALLIANCE_AUTOJOIN, true);
			}
			logInfo("No new intel found. Rescheduling task to run at: " + rescheduleTime);
		} catch (Exception e) {
			reschedule(LocalDateTime.now().plusMinutes(5));
			logError("Error reading intel cooldown timer: " + e.getMessage(), e);
		}
	}

	/**
	 * Search for a template using grayscale matching and process it
	 */
	private boolean searchAndProcessGrayscale(EnumTemplates template, int maxAttempts, int confidence,
			Consumer<DTOImageSearchResult> processMethod) {
		for (int attempt = 0; attempt < maxAttempts; attempt++) {
			logDebug("Searching for grayscale template '" + template + "', attempt " + (attempt + 1) + ".");
			DTOImageSearchResult result = emuManager.searchTemplateGrayscale(EMULATOR_NUMBER, template, confidence);

			if (result.isFound()) {
				logInfo("Grayscale template found: " + template);
				processMethod.accept(result);
				return true;
			}
		}
		return false;
	}

	/**
	 * Search for a template and process it
	 */
	private boolean searchAndProcess(EnumTemplates template, int maxAttempts, int confidence,
			Consumer<DTOImageSearchResult> processMethod) {
		for (int attempt = 0; attempt < maxAttempts; attempt++) {
			logDebug("Searching for template '" + template + "', attempt " + (attempt + 1) + ".");
			DTOImageSearchResult result = emuManager.searchTemplate(EMULATOR_NUMBER, template, confidence);

			if (result.isFound()) {
				logInfo("Template found: " + template);
				processMethod.accept(result);
				return true;
			}
		}
		return false;
	}

	private void processJourney(DTOImageSearchResult result) {
		tapPoint(result.getPoint());
		sleepTask(2000);

		DTOImageSearchResult view = searchTemplateWithRetries(EnumTemplates.INTEL_VIEW);
		if (!view.isFound()) {
			logWarning("Could not find the 'View' button for the journey. Going back.");
			tapBackButton();
			return;
		}

		tapPoint(view.getPoint());
		sleepTask(500);

		DTOImageSearchResult explore = searchTemplateWithRetries(EnumTemplates.INTEL_EXPLORE);
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

		DTOImageSearchResult view = searchTemplateWithRetries(EnumTemplates.INTEL_VIEW);
		if (!view.isFound()) {
			logWarning("Could not find the 'View' button for the survivor. Going back.");
			tapBackButton();
			return;
		}

		tapPoint(view.getPoint());
		sleepTask(500);

		DTOImageSearchResult rescue = searchTemplateWithRetries(EnumTemplates.INTEL_RESCUE);
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
			logInfo("March queue is full. Skipping beast hunt.");
			return;
		}

		if (useFlag && beastMarchSent) {
			logInfo("Beast march already sent with flag. Skipping beast hunt.");
			return;
		}

		tapPoint(beast.getPoint());
		sleepTask(2000);

		DTOImageSearchResult view = searchTemplateWithRetries(EnumTemplates.INTEL_VIEW);
		if (!view.isFound()) {
			logWarning("Could not find the 'View' button for the beast. Going back.");
			tapBackButton();
			return;
		}
		tapPoint(view.getPoint());
		sleepTask(500);

		DTOImageSearchResult attack = searchTemplateWithRetries(EnumTemplates.INTEL_ATTACK);
		if (!attack.isFound()) {
			logWarning("Could not find the 'Attack' button for the beast. Going back.");
			tapBackButton();
			tapBackButton(); // Back from view screen
			return;
		}
		tapPoint(attack.getPoint());
		sleepTask(500);

		// Check if the march screen is open
		DTOImageSearchResult deployButton = searchTemplateWithRetries(EnumTemplates.DEPLOY_BUTTON);
		if (!deployButton.isFound()) {
			logError("March queue is full. Cannot start a new march.");
			marchQueueLimitReached = true;
			return;
		}

		// Select flag if needed
		if (useFlag) {
			selectFlag(flagNumber);
		}

		// Equalize troops
		DTOImageSearchResult equalizeButton = searchTemplateWithRetries(EnumTemplates.RALLY_EQUALIZE_BUTTON);
		if (equalizeButton.isFound()) {
			tapPoint(equalizeButton.getPoint());
			sleepTask(300);
		}

		// Parse travel time
		long travelTimeSeconds = 0;
		try {
			travelTimeSeconds = parseTravelTime();
			logInfo("Successfully parsed travel time: " + travelTimeSeconds + "s");
		} catch (Exception e) {
			logError("Error parsing travel time: " + e.getMessage());
		}

		// Parse stamina cost
		Integer spentStamina = getSpentStamina();
		logDebug("Spent stamina read: " + spentStamina);

		// Deploy march
		DTOImageSearchResult deploy = searchTemplateWithRetries(EnumTemplates.DEPLOY_BUTTON, 90, 3);
		if (!deploy.isFound()) {
			logError("Deploy button not found. Rescheduling to try again in 5 minutes.");
			reschedule(LocalDateTime.now().plusMinutes(5));
			return;
		}

		tapPoint(deploy.getPoint());
		sleepTask(2000);

		// Verify deployment
		deploy = searchTemplateWithRetries(EnumTemplates.DEPLOY_BUTTON, 90, 3);
		if (deploy.isFound()) {
			logWarning(
					"Deploy button still present after deployment attempt. March may have failed. Rescheduling in 5 minutes.");
			reschedule(LocalDateTime.now().plusMinutes(5));
			return;
		}

		logInfo("Beast march deployed successfully.");
		beastMarchSent = true;

		// Update stamina
		subtractStamina(spentStamina, false); // false = not rally, use 10 stamina default

		// Reschedule for march return
		if (travelTimeSeconds <= 0) {
			logError("Failed to parse travel time via OCR. Using 5 minute fallback reschedule.");
			LocalDateTime rescheduleTime = LocalDateTime.now().plusMinutes(5);
			reschedule(rescheduleTime);
			return;
		}

		LocalDateTime rescheduleTime = LocalDateTime.now().plusSeconds(travelTimeSeconds);
		reschedule(rescheduleTime);
		logInfo("Beast march scheduled to return at " + UtilTime.localDateTimeToDDHHMMSS(rescheduleTime));
	}

	private MarchesAvailable getMarchesAvailable() {
		// Open active marches panel
		tapPoint(new DTOPoint(2, 550));
		sleepTask(500);
		tapPoint(new DTOPoint(340, 265));
		sleepTask(500);

		// Try OCR to find idle marches
		try {
			for (int i = 0; i < 5; i++) {
				String ocrSearchResult = emuManager.ocrRegionText(EMULATOR_NUMBER,
						new DTOPoint(10, 342),
						new DTOPoint(435, 772));
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
			DTOImageSearchResult resource = searchTemplateWithRetries(gatherType.getTemplate());
			if (!resource.isFound()) {
				logDebug("March queue for " + gatherType.getName() + " is not active. (Used: " +
						activeMarchQueues + "/" + totalMarchesAvailable + ")");
				continue;
			}

			// March detected for this resource type
			activeMarchQueues++;
			logInfo("March queue for " + gatherType.getName() + " detected. (Used: " +
					activeMarchQueues + "/" + totalMarchesAvailable + ")");

			// Find when this gather task is scheduled to complete
			LocalDateTime task = iDailyTaskRepository
					.findByProfileIdAndTaskName(profile.getId(), gatherType.getTask())
					.getNextSchedule();

			if (task.isBefore(earliestAvailableMarch)) {
				earliestAvailableMarch = task;
				logInfo("Updated earliest available march: " + earliestAvailableMarch);
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

	@Override
	protected EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.WORLD;
	}

	@Override
	protected boolean consumesStamina() {
		return true;
	}

	private enum GatherType {
		MEAT("meat", EnumTemplates.GAME_HOME_SHORTCUTS_MEAT, TpDailyTaskEnum.GATHER_MEAT),
		WOOD("wood", EnumTemplates.GAME_HOME_SHORTCUTS_WOOD, TpDailyTaskEnum.GATHER_WOOD),
		COAL("coal", EnumTemplates.GAME_HOME_SHORTCUTS_COAL, TpDailyTaskEnum.GATHER_COAL),
		IRON("iron", EnumTemplates.GAME_HOME_SHORTCUTS_IRON, TpDailyTaskEnum.GATHER_IRON);

		final String name;
		final EnumTemplates template;
		final TpDailyTaskEnum task;

		GatherType(String name, EnumTemplates enumTemplate, TpDailyTaskEnum task) {
			this.name = name;
			this.template = enumTemplate;
			this.task = task;
		}

		public String getName() {
			return name;
		}

		public EnumTemplates getTemplate() {
			return template;
		}

		public TpDailyTaskEnum getTask() {
			return task;
		}
	}

	public record MarchesAvailable(boolean available, LocalDateTime rescheduleTo) {
	}
}