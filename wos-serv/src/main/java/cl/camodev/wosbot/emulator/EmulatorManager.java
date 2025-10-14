package cl.camodev.wosbot.emulator;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import cl.camodev.utiles.ImageSearchUtil;
import cl.camodev.utiles.UtilOCR;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.GameVersion;
import cl.camodev.wosbot.emulator.impl.LDPlayerEmulator;
import cl.camodev.wosbot.emulator.impl.MEmuEmulator;
import cl.camodev.wosbot.emulator.impl.MuMuEmulator;
import cl.camodev.wosbot.ot.*;
import cl.camodev.wosbot.serv.impl.ServConfig;
import cl.camodev.wosbot.serv.impl.ServProfiles;
import cl.camodev.wosbot.serv.task.WaitingThread;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmulatorManager {

    private static final Logger logger = LoggerFactory.getLogger(EmulatorManager.class);

    public static GameVersion GAME = GameVersion.GLOBAL;
    private static EmulatorManager instance;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition permitsAvailable = lock.newCondition();
    private final PriorityQueue<WaitingThread> waitingQueue = new PriorityQueue<>();
    private Emulator emulator;
    private int MAX_RUNNING_EMULATORS = 3;
    private final Set<Thread> activeSlots = new HashSet<>();

    private EmulatorManager() {

    }

    public static EmulatorManager getInstance() {
        if (instance == null) {
            instance = new EmulatorManager();
        }
        return instance;
    }

    public void initialize() {
        resetQueueState();
        HashMap<String, String> globalConfig = ServConfig.getServices().getGlobalConfig();

        if (globalConfig == null || globalConfig.isEmpty()) {
            throw new IllegalStateException("No emulator configuration found. Ensure initialization is completed.");
        }

        String gameVersionName = globalConfig.getOrDefault(EnumConfigurationKey.GAME_VERSION_STRING.name(),
                GameVersion.GLOBAL.name());
        try {
            GAME = GameVersion.valueOf(gameVersionName);
            logger.info("Game version set to {}", GAME.name());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid game version '{}' found in configuration, using default GLOBAL", gameVersionName);
            GAME = GameVersion.GLOBAL;
        }

        String savedActiveEmulator = globalConfig.get(EnumConfigurationKey.CURRENT_EMULATOR_STRING.name());
        if (savedActiveEmulator == null) {
            throw new IllegalStateException("No active emulator set. Ensure an emulator is selected.");
        }
        MAX_RUNNING_EMULATORS = Optional
                .ofNullable(globalConfig.get(EnumConfigurationKey.MAX_RUNNING_EMULATORS_INT.name()))
                .map(Integer::parseInt)
                .orElse(Integer.parseInt(EnumConfigurationKey.MAX_RUNNING_EMULATORS_INT.getDefaultValue()));
        try {
            EmulatorType emulatorType = EmulatorType.valueOf(savedActiveEmulator);
            String consolePath = globalConfig.get(emulatorType.getConfigKey());

            if (consolePath == null || consolePath.isEmpty()) {
                throw new IllegalStateException(
                        "No path found for the selected emulator: " + emulatorType.getDisplayName());
            }

            switch (emulatorType) {
                case MUMU:
                    this.emulator = new MuMuEmulator(consolePath);
                    break;
                case MEMU:
                    this.emulator = new MEmuEmulator(consolePath);
                    break;
                case LDPLAYER:
                    this.emulator = new LDPlayerEmulator(consolePath);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported emulator type: " + emulatorType);
            }

            logger.info("Emulator initialized: {}", emulatorType.getDisplayName());
            // restartAdbServer();

        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid emulator type found in configuration: " + savedActiveEmulator, e);
        }
    }

    /**
     * Checks if the emulator has been configured before executing any action.
     */
    private void checkEmulatorInitialized() {
        if (emulator == null) {
            throw new IllegalStateException();
        }
    }

    /**
     * Captures a screenshot of the emulator as DTORawImage.
     * The conversion to BufferedImage is done only when needed by specific
     * operations.
     */
    public DTORawImage captureScreenshotViaADB(String emulatorNumber) {
        checkEmulatorInitialized();
        return emulator.captureScreenshot(emulatorNumber);
    }

    /**
     * Taps at a specific coordinate.
     */
    public void tapAtPoint(String emulatorNumber, DTOPoint point) {
        checkEmulatorInitialized();

        // Get profile name and log the tap
        String profileName = getProfileNameForEmulator(emulatorNumber);
        logger.info("{} - Tapping at ({},{}) for emulator {}",
                profileName, point.getX(), point.getY(), emulatorNumber);

        emulator.tapAtRandomPoint(emulatorNumber, point, point);
    }

    /**
     * Taps at a random coordinate within an area.
     */
    public boolean tapAtRandomPoint(String emulatorNumber, DTOPoint point1, DTOPoint point2) {
        checkEmulatorInitialized();

        // Get profile name and log the tap
        String profileName = getProfileNameForEmulator(emulatorNumber);
        logger.info("{} - Random tapping in area ({},{}) to ({},{}) for emulator {}",
                profileName, point1.getX(), point1.getY(), point2.getX(), point2.getY(), emulatorNumber);

        return emulator.tapAtRandomPoint(emulatorNumber, point1, point2);
    }

    /**
     * Performs multiple random taps within an area with a delay between them.
     */
    public boolean tapAtRandomPoint(String emulatorNumber, DTOPoint point1, DTOPoint point2, int tapCount,
            int delayMs) {
        checkEmulatorInitialized();

        // Get profile name and log the tap
        String profileName = getProfileNameForEmulator(emulatorNumber);
        logger.info("{} - Multiple random tapping ({} times) in area ({},{}) to ({},{}) for emulator {}",
                profileName, tapCount, point1.getX(), point1.getY(), point2.getX(), point2.getY(), emulatorNumber);

        return emulator.tapAtRandomPoint(emulatorNumber, point1, point2, tapCount, delayMs);
    }

    /**
     * Swipes between two points.
     */
    public void executeSwipe(String emulatorNumber, DTOPoint start, DTOPoint end) {
        checkEmulatorInitialized();

        // Get profile name and log the swipe
        String profileName = getProfileNameForEmulator(emulatorNumber);
        logger.info("{} - Swiping from ({},{}) to ({},{}) for emulator {}",
                profileName, start.getX(), start.getY(), end.getX(), end.getY(), emulatorNumber);

        emulator.swipe(emulatorNumber, start, end);
    }

    /**
     * Checks if an application is installed on the emulator.
     */
    public boolean isWhiteoutSurvivalInstalled(String emulatorNumber) {
        checkEmulatorInitialized();
        return emulator.isAppInstalled(emulatorNumber, GAME.getPackageName());
    }

    /**
     * Presses the back button on the emulator.
     */
    public void tapBackButton(String emulatorNumber) {
        checkEmulatorInitialized();

        // Get profile name and log the back button press
        String profileName = getProfileNameForEmulator(emulatorNumber);
        logger.info("{} - Pressing back button for emulator {}",
                profileName, emulatorNumber);

        emulator.pressBackButton(emulatorNumber);
    }

    /**
     * Executes OCR on a screen region and extracts text.
     */
    public String ocrRegionText(String emulatorNumber, DTOPoint p1, DTOPoint p2)
            throws IOException, TesseractException {
        checkEmulatorInitialized();
        return emulator.ocrRegionText(emulatorNumber, p1, p2);
    }

    /**
     * Executes OCR on a screen region and extracts text with custom Tesseract
     * settings.
     * 
     * @param emulatorNumber Emulator identifier
     * @param p1             First corner of the region
     * @param p2             Second corner of the region
     * @param settings       Tesseract OCR configuration settings
     * @return Recognized text
     * @throws IOException        if image capture fails
     * @throws TesseractException if OCR fails
     */
    public String ocrRegionText(String emulatorNumber, DTOPoint p1, DTOPoint p2, DTOTesseractSettings settings)
            throws IOException, TesseractException {
        checkEmulatorInitialized();
        return emulator.ocrRegionText(emulatorNumber, p1, p2, settings);
    }

    /**
     * Helper method to get profile name from emulator number
     */
    private String getProfileNameForEmulator(String emulatorNumber) {
        try {
            // Use ServProfiles to find profile with this emulator number
            List<DTOProfiles> profiles = ServProfiles.getServices().getProfiles();

            if (profiles != null) {
                for (DTOProfiles profile : profiles) {
                    if (emulatorNumber.equals(profile.getEmulatorNumber())) {
                        return profile.getName();
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not get profile name for emulator {}: {}",
                    emulatorNumber, e.getMessage());
        }

        return "Unknown";
    }

    /**
     * Generates the region-specific template path based on the configured game
     * version
     */
    private String getRegionSpecificTemplatePath(String originalPath) {
        try {
            String regionSuffix = "";

            if (GAME == GameVersion.CHINA) {
                regionSuffix = "_CH";
            }
            if (regionSuffix.isEmpty()) {
                return originalPath;
            }

            // Insert the suffix before the extension
            int lastDotIndex = originalPath.lastIndexOf('.');
            if (lastDotIndex != -1) {
                String pathWithoutExtension = originalPath.substring(0, lastDotIndex);
                String extension = originalPath.substring(lastDotIndex);
                return pathWithoutExtension + regionSuffix + extension;
            } else {
                return originalPath + regionSuffix;
            }
        } catch (Exception e) {
            logger.warn("Error generating region-specific template path for {}: {}", originalPath, e.getMessage());
            return originalPath;
        }
    }

    /**
     * Checks if a template resource exists
     */
    private boolean templateResourceExists(String templatePath) {
        try (var is = ImageSearchUtil.class.getResourceAsStream(templatePath)) {
            return is != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the most appropriate template path according to the configured region
     */
    private String getBestTemplatePath(String originalPath) {
        // Generate region-specific path
        String regionSpecificPath = getRegionSpecificTemplatePath(originalPath);

        // If it's different from the original, check if it exists
        if (!regionSpecificPath.equals(originalPath) && templateResourceExists(regionSpecificPath)) {
            logger.debug("Using region-specific template: {}", regionSpecificPath);
            return regionSpecificPath;
        }

        // If the specific version doesn't exist or it's the global version, use the
        // original
        logger.debug("Using base template: {}", originalPath);
        return originalPath;
    }

    /**
     * Searches for an image on the captured screen of the emulator.
     */
    public DTOImageSearchResult searchTemplate(String emulatorNumber, EnumTemplates templatePath,
            DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double threshold) {
        checkEmulatorInitialized();
        DTORawImage rawImage = captureScreenshotViaADB(emulatorNumber);
        String bestTemplatePath = getBestTemplatePath(templatePath.getTemplate());

        try {
            // Set profile name in ImageSearchUtil for logging
            String profileName = getProfileNameForEmulator(emulatorNumber);
            ImageSearchUtil.setProfileName(profileName);

            // Pass the complete DTORawImage object
            return ImageSearchUtil.searchTemplate(rawImage, bestTemplatePath, topLeftCorner, bottomRightCorner,
                    threshold);
        } finally {
            // Clear profile name after the search is done
            ImageSearchUtil.clearProfileName();
        }
    }

    /**
     * Searches for an image on the entire emulator screen.
     */
    public DTOImageSearchResult searchTemplate(String emulatorNumber, EnumTemplates templatePath, double threshold) {
        checkEmulatorInitialized();
        DTORawImage rawImage = captureScreenshotViaADB(emulatorNumber);
        String bestTemplatePath = getBestTemplatePath(templatePath.getTemplate());

        try {
            // Set profile name in ImageSearchUtil for logging
            String profileName = getProfileNameForEmulator(emulatorNumber);
            ImageSearchUtil.setProfileName(profileName);

            // Pass the complete DTORawImage object
            return ImageSearchUtil.searchTemplate(rawImage, bestTemplatePath, new DTOPoint(0, 0),
                    new DTOPoint(720, 1280), threshold);
        } finally {
            // Clear profile name after the search is done
            ImageSearchUtil.clearProfileName();
        }
    }

    /**
     * Searches for an image on the specified region of the emulator screen using
     * grayscale matching.
     */
    public DTOImageSearchResult searchTemplateGrayscale(String emulatorNumber, EnumTemplates templatePath,
            DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double threshold) {
        checkEmulatorInitialized();
        DTORawImage rawImage = captureScreenshotViaADB(emulatorNumber);
        String bestTemplatePath = getBestTemplatePath(templatePath.getTemplate());

        try {
            // Set profile name in ImageSearchUtil for logging
            String profileName = getProfileNameForEmulator(emulatorNumber);
            ImageSearchUtil.setProfileName(profileName);

            // Pass the complete DTORawImage object
            return ImageSearchUtil.searchTemplateGrayscale(rawImage, bestTemplatePath, topLeftCorner, bottomRightCorner,
                    threshold);
        } finally {
            // Clear profile name after the search is done
            ImageSearchUtil.clearProfileName();
        }
    }

    /**
     * Searches for an image on the entire emulator screen using grayscale matching.
     */
    public DTOImageSearchResult searchTemplateGrayscale(String emulatorNumber, EnumTemplates templatePath,
            double threshold) {
        checkEmulatorInitialized();
        DTORawImage rawImage = captureScreenshotViaADB(emulatorNumber);
        String bestTemplatePath = getBestTemplatePath(templatePath.getTemplate());

        try {
            // Set profile name in ImageSearchUtil for logging
            String profileName = getProfileNameForEmulator(emulatorNumber);
            ImageSearchUtil.setProfileName(profileName);

            return ImageSearchUtil.searchTemplateGrayscale(rawImage, bestTemplatePath, new DTOPoint(0, 0),
                    new DTOPoint(720, 1280), threshold);
        } finally {
            // Clear profile name after the search is done
            ImageSearchUtil.clearProfileName();
        }
    }

    /**
     * Searches for multiple instances of an image on the specified region of the
     * emulator screen using grayscale matching.
     */
    public List<DTOImageSearchResult> searchTemplatesGrayscale(String emulatorNumber, EnumTemplates templatePath,
            DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double threshold, int maxResults) {
        checkEmulatorInitialized();
        DTORawImage rawImage = captureScreenshotViaADB(emulatorNumber);
        String bestTemplatePath = getBestTemplatePath(templatePath.getTemplate());

        try {
            // Set profile name in ImageSearchUtil for logging
            String profileName = getProfileNameForEmulator(emulatorNumber);
            ImageSearchUtil.setProfileName(profileName);

            return ImageSearchUtil.searchTemplateGrayscaleMultiple(rawImage, bestTemplatePath, topLeftCorner,
                    bottomRightCorner, threshold, maxResults);
        } finally {
            // Clear profile name after the search is done
            ImageSearchUtil.clearProfileName();
        }
    }

    /**
     * Searches for multiple instances of an image on the entire emulator screen
     * using grayscale matching.
     */
    public List<DTOImageSearchResult> searchTemplatesGrayscale(String emulatorNumber, EnumTemplates templatePath,
            double threshold, int maxResults) {
        checkEmulatorInitialized();
        DTORawImage rawImage = captureScreenshotViaADB(emulatorNumber);
        String bestTemplatePath = getBestTemplatePath(templatePath.getTemplate());

        try {
            // Set profile name in ImageSearchUtil for logging
            String profileName = getProfileNameForEmulator(emulatorNumber);
            ImageSearchUtil.setProfileName(profileName);

            return ImageSearchUtil.searchTemplateGrayscaleMultiple(rawImage, bestTemplatePath, new DTOPoint(0, 0),
                    new DTOPoint(720, 1280), threshold, maxResults);
        } finally {
            // Clear profile name after the search is done
            ImageSearchUtil.clearProfileName();
        }
    }

    public List<DTOImageSearchResult> searchTemplates(String emulatorNumber, EnumTemplates templatePath,
            DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double threshold, int maxResults) {
        checkEmulatorInitialized();
        DTORawImage rawImage = captureScreenshotViaADB(emulatorNumber);
        String bestTemplatePath = getBestTemplatePath(templatePath.getTemplate());

        try {
            // Set profile name in ImageSearchUtil for logging
            String profileName = getProfileNameForEmulator(emulatorNumber);
            ImageSearchUtil.setProfileName(profileName);

            return ImageSearchUtil.searchTemplateMultiple(rawImage, bestTemplatePath, topLeftCorner, bottomRightCorner,
                    threshold, maxResults);
        } finally {
            // Clear profile name after the search is done
            ImageSearchUtil.clearProfileName();
        }
    }

    public List<DTOImageSearchResult> searchTemplates(String emulatorNumber, EnumTemplates templatePath,
            double threshold, int maxResults) {
        checkEmulatorInitialized();
        DTORawImage rawImage = captureScreenshotViaADB(emulatorNumber);
        String bestTemplatePath = getBestTemplatePath(templatePath.getTemplate());

        try {
            // Set profile name in ImageSearchUtil for logging
            String profileName = getProfileNameForEmulator(emulatorNumber);
            ImageSearchUtil.setProfileName(profileName);

            return ImageSearchUtil.searchTemplateMultiple(rawImage, bestTemplatePath, new DTOPoint(0, 0),
                    new DTOPoint(720, 1280), threshold, maxResults);
        } finally {
            // Clear profile name after the search is done
            ImageSearchUtil.clearProfileName();
        }
    }

    /**
     * Analyzes the colors in a region of the screen, counting pixels that match
     * certain criteria
     * 
     * @param emulatorNumber Emulator identifier
     * @param topLeft        Top-left point of the region to analyze
     * @param bottomRight    Bottom-right point of the region to analyze
     * @param stepSize       Step size for scanning (e.g., 2 to check every other
     *                       pixel)
     * @return Array with counts for [background, green, red] pixels
     */
    public int[] analyzeRegionColors(String emulatorNumber, DTOPoint topLeft, DTOPoint bottomRight, int stepSize) {
        try {
            // Take a single screenshot as DTORawImage, then convert only when needed
            DTORawImage rawImage = emulator.captureScreenshot(emulatorNumber);
            BufferedImage image = UtilOCR.convertRawImageToBufferedImage(rawImage);

            int[] counts = new int[3]; // [background, green, red]

            // Scan the region
            for (int y = topLeft.getY(); y <= bottomRight.getY(); y += stepSize) {
                for (int x = topLeft.getX(); x <= bottomRight.getX(); x += stepSize) {
                    int rgb = image.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;

                    // Check if it's background color (127, 173, 205)
                    if (Math.abs(r - 127) < 20 && Math.abs(g - 173) < 20 && Math.abs(b - 205) < 20) {
                        counts[0]++; // background
                    }
                    // Check if it's green text
                    else if (g > Math.max(r, b) * 1.2 && g > 100) {
                        counts[1]++; // green
                    }
                    // Check if it's red text
                    else if (r > Math.max(g, b) * 1.2 && r > 100) {
                        counts[2]++; // red
                    }
                }
            }

            return counts;
        } catch (Exception e) {
            logger.error("Error analyzing region colors", e);
            return new int[] { 0, 0, 0 };
        }
    }

    public void launchEmulator(String emulatorNumber) {
        checkEmulatorInitialized();
        emulator.launchEmulator(emulatorNumber);
    }

    /**
     * Closes the emulator.
     */
    public void closeEmulator(String emulatorNumber) {
        checkEmulatorInitialized();
        emulator.closeEmulator(emulatorNumber);
    }

    public void launchApp(String emulatorNumber, String packageName) {
        checkEmulatorInitialized();
        emulator.launchApp(emulatorNumber, packageName);
    }

    public void sendGameToBackground(String emulatorNumber) {
        checkEmulatorInitialized();
        emulator.sendGameToBackground(emulatorNumber);
    }

    /**
     * Writes text on the emulator using ADB input.
     * Automatically escapes special characters for shell compatibility.
     *
     * @param emulatorNumber Emulator identifier
     * @param text           Text to write
     */
    public void writeText(String emulatorNumber, String text) {
        checkEmulatorInitialized();

        // Get profile name and log the text input
        String profileName = getProfileNameForEmulator(emulatorNumber);
        logger.info("{} - Writing text on emulator {}: {}",
                profileName, emulatorNumber, text);

        emulator.writeText(emulatorNumber, text);
    }

    /**
     * Clears text from the currently focused input field.
     * Simulates pressing backspace multiple times.
     *
     * @param emulatorNumber Emulator identifier
     * @param count          Number of backspace key presses
     */
    public void clearText(String emulatorNumber, int count) {
        checkEmulatorInitialized();

        // Get profile name and log the action
        String profileName = getProfileNameForEmulator(emulatorNumber);
        logger.info("{} - Clearing {} characters on emulator {}",
                profileName, count, emulatorNumber);

        emulator.clearText(emulatorNumber, count);
    }

    public boolean isRunning(String emulatorNumber) {
        checkEmulatorInitialized();
        return emulator.isRunning(emulatorNumber);
    }

    public boolean isPackageRunning(String emulatorNumber, String packageName) {
        checkEmulatorInitialized();
        return emulator.isPackageRunning(emulatorNumber, packageName);
    }

    public void restartAdbServer() {
        checkEmulatorInitialized();
        emulator.restartAdb();
    }

    public void adquireEmulatorSlot(DTOProfiles profile, PositionCallback callback) throws InterruptedException {
        Thread currentThread = Thread.currentThread();
        lock.lock();
        try {
            // Check if this thread already has an active slot
            if (activeSlots.contains(currentThread)) {
                if (emulator.isRunning(profile.getEmulatorNumber())) {
                    logger.info("Profile {} already has an active slot, continuing without acquiring a new one.",
                            profile.getName());
                    logSlotHolders();
                    profile.setQueuePosition(0);
                    return;
                } else {
                    activeSlots.remove(currentThread);
                    // MAX_RUNNING_EMULATORS++;
                    logger.info(
                            "Profile {} had a slot, but emulator was not running, removing from slot holders and placing in queue. ",
                            profile.getName());
                    logSlotHolders();
                }
            }

            // If a slot is available and no one is waiting, it is acquired immediately.
            logger.info("Profile " + profile.getName() + " is requesting queue slot.");
            if (activeSlots.size() < MAX_RUNNING_EMULATORS && waitingQueue.isEmpty()) {
                logger.info("Profile " + profile.getName() + " acquired slot immediately.");
                logger.debug("Current slot holders: " + activeSlots);
                profile.setQueuePosition(0);
                // MAX_RUNNING_EMULATORS--;
                activeSlots.add(currentThread); // Track this thread as having a slot
                logSlotHolders();
                return;
            }

            // Create the object representing the current thread with its priority
            WaitingThread currentWaiting = new WaitingThread(currentThread, profile);
            waitingQueue.add(currentWaiting);

            // Wait with a timeout to be able to notify the position periodically.
            while (waitingQueue.peek() != currentWaiting || activeSlots.size() >= MAX_RUNNING_EMULATORS) {
                // Wait for up to 1 second.
                permitsAvailable.await(1, TimeUnit.SECONDS);

                // Query and notify the current position of the thread in the queue.
                int position = getPosition(currentWaiting);
                profile.setQueuePosition(position);
                callback.onPositionUpdate(currentThread, position);
            }
            logger.info("Profile {} acquired slot", profile.getName());
            logger.debug("Current slot holders: " + activeSlots);
            // It's the turn and a slot is available.
            waitingQueue.poll(); // Remove the thread from the queue.
            profile.setQueuePosition(0);
            // MAX_RUNNING_EMULATORS--; // Acquire the slot.
            activeSlots.add(currentThread); // Track this thread as having a slot
            logSlotHolders();
            // Notify other threads to re-evaluate the condition.
            permitsAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void logSlotHolders() {
        String listOfProfiles = activeSlots.stream()
                .map(Thread::getName)
                .map(name -> name.contains("-") ? name.substring(name.indexOf('-') + 1) : name)
                .toList().toString();
        logger.info("Current slot holders: {}/{}. {}", activeSlots.size(), MAX_RUNNING_EMULATORS, listOfProfiles);

    }

    public void releaseEmulatorSlot(DTOProfiles profile) {
        Thread currentThread = Thread.currentThread();
        lock.lock();
        try {
            logger.info("Profile {} is releasing queue slot.", profile.getName());
            profile.setQueuePosition(Integer.MAX_VALUE);

            // Only increment MAX_RUNNING_EMULATORS if this thread actually had a slot
            if (activeSlots.remove(currentThread)) {
                // MAX_RUNNING_EMULATORS++;
                logger.info("Thread {} released its slot, slots available: {}", currentThread.getName(),
                        MAX_RUNNING_EMULATORS);
            } else {
                logger.warn("Thread {} tried to release a slot it didn't have", currentThread.getName());
            }
            logSlotHolders();
            permitsAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private int getPosition(WaitingThread waitingThread) {
        int position = 1;
        for (WaitingThread wt : waitingQueue) {
            if (wt.equals(waitingThread)) {
                return position;
            }
            position++;
        }
        return 0;
    }

    public void resetQueueState() {
        lock.lock();
        try {
            waitingQueue.clear();
            activeSlots.clear(); // Clear the set of active slots
            permitsAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

}
