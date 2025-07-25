package cl.camodev.wosbot.emulator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import cl.camodev.utiles.ImageSearchUtil;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.emulator.impl.LDPlayerEmulator;
import cl.camodev.wosbot.emulator.impl.MEmuEmulator;
import cl.camodev.wosbot.emulator.impl.MuMuEmulator;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServConfig;
import cl.camodev.wosbot.serv.task.TaskQueue;
import cl.camodev.wosbot.serv.task.WaitingThread;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmulatorManager {

	private static final Logger logger = LoggerFactory.getLogger(EmulatorManager.class);

	public static String WHITEOUT_PACKAGE = "com.gof.global";
	private static EmulatorManager instance;
	private final ReentrantLock lock = new ReentrantLock();
	private final Condition permitsAvailable = lock.newCondition();
	private final PriorityQueue<WaitingThread> waitingQueue = new PriorityQueue<>();
	private Emulator emulator;
	private int MAX_RUNNING_EMULATORS = 3;

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

		// Obtener el emulador activo guardado
		String savedActiveEmulator = globalConfig.get(EnumConfigurationKey.CURRENT_EMULATOR_STRING.name());
		if (savedActiveEmulator == null) {
			throw new IllegalStateException("No active emulator set. Ensure an emulator is selected.");
		}
		MAX_RUNNING_EMULATORS = Optional.ofNullable(globalConfig.get(EnumConfigurationKey.MAX_RUNNING_EMULATORS_INT.name())).map(Integer::parseInt).orElse(Integer.parseInt(EnumConfigurationKey.MAX_RUNNING_EMULATORS_INT.getDefaultValue()));
		try {
			EmulatorType emulatorType = EmulatorType.valueOf(savedActiveEmulator);
			String consolePath = globalConfig.get(emulatorType.getConfigKey());

			if (consolePath == null || consolePath.isEmpty()) {
				throw new IllegalStateException("No path found for the selected emulator: " + emulatorType.getDisplayName());
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
			//restartAdbServer();

		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("Invalid emulator type found in configuration: " + savedActiveEmulator, e);
		}
	}

        /**
         * Ensures the emulator has been configured before executing any action.
         */
	private void checkEmulatorInitialized() {
		if (emulator == null) {
			throw new IllegalStateException();
		}
	}

        /**
         * Captures a screenshot from the emulator.
         */
	public byte[] captureScreenshotViaADB(String emulatorNumber) {
		checkEmulatorInitialized();
		return emulator.captureScreenshot(emulatorNumber);
	}

        /**
         * Performs a tap at a specific coordinate.
         */
	public void tapAtPoint(String emulatorNumber, DTOPoint point) {
		checkEmulatorInitialized();
		emulator.tapAtRandomPoint(emulatorNumber, point, point);

	}

        /**
         * Performs a tap at a random coordinate within an area.
         */
	public boolean tapAtRandomPoint(String emulatorNumber, DTOPoint point1, DTOPoint point2) {
		checkEmulatorInitialized();
		return emulator.tapAtRandomPoint(emulatorNumber, point1, point2);
	}

        /**
         * Performs multiple random taps within an area with a delay between them.
         */
	public boolean tapAtRandomPoint(String emulatorNumber, DTOPoint point1, DTOPoint point2, int tapCount, int delayMs) {
		checkEmulatorInitialized();
		return emulator.tapAtRandomPoint(emulatorNumber, point1, point2, tapCount, delayMs);
	}

        /**
         * Performs a swipe between two points.
         */
	public void executeSwipe(String emulatorNumber, DTOPoint start, DTOPoint end) {
		checkEmulatorInitialized();
		emulator.swipe(emulatorNumber, start, end);
	}

        /**
         * Checks if an application is installed on the emulator.
         */
	public boolean isWhiteoutSurvivalInstalled(String emulatorNumber) {
		checkEmulatorInitialized();
		return emulator.isAppInstalled(emulatorNumber, WHITEOUT_PACKAGE);
	}

        /**
         * Presses the back button on the emulator.
         */
	public void tapBackButton(String emulatorNumber) {
		checkEmulatorInitialized();
		emulator.pressBackButton(emulatorNumber);
	}

        /**
         * Runs OCR on a region of the screen and extracts text.
         */
	public String ocrRegionText(String emulatorNumber, DTOPoint p1, DTOPoint p2) throws IOException, TesseractException {
		checkEmulatorInitialized();
		return emulator.ocrRegionText(emulatorNumber, p1, p2);
	}

        /**
         * Searches for an image within the emulator's captured screen.
         */
	public DTOImageSearchResult searchTemplate(String emulatorNumber, String templatePath, DTOPoint topLeftCorner, DTOPoint bottomRightCorner , double threshold) {
		checkEmulatorInitialized();
		byte[] screenshot = captureScreenshotViaADB(emulatorNumber);
                return ImageSearchUtil.findTemplate(screenshot, templatePath, topLeftCorner, bottomRightCorner, threshold);
	}

        /**
         * Searches for an image across the entire emulator screen.
         */
	public DTOImageSearchResult searchTemplate(String emulatorNumber, String templatePath, double threshold) {
		checkEmulatorInitialized();
		byte[] screenshot = captureScreenshotViaADB(emulatorNumber);
                return ImageSearchUtil.findTemplate(screenshot, templatePath, new DTOPoint(0,0), new DTOPoint(720,1280), threshold);
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

        /**
         * Acquires a slot in the emulator queue for the given profile.
         * The callback receives periodic position updates while waiting.
         */
        public void acquireEmulatorSlot(DTOProfiles profile, PositionCallback callback) throws InterruptedException {
		lock.lock();
		try {
                        // If a slot is available and nobody is waiting, acquire it immediately.
			logger.info("Profile " + profile.getName() + " is getting queue slot.");
			if (MAX_RUNNING_EMULATORS > 0 && waitingQueue.isEmpty()) {
				logger.info("Profile " + profile.getName() + " acquired slot immediately.");
				MAX_RUNNING_EMULATORS--;
				return;
			}

                        // Create the object representing the current thread and its priority
			WaitingThread currentWaiting = new WaitingThread(Thread.currentThread(), profile.getId());
			waitingQueue.add(currentWaiting);

                        // Wait with timeout in order to notify the position periodically.

			while (waitingQueue.peek() != currentWaiting || MAX_RUNNING_EMULATORS <= 0) {
                                // Wait up to 1 second.
				permitsAvailable.await(1, TimeUnit.SECONDS);

                                // Query and notify the current thread position in the queue.
				int position = getPosition(currentWaiting);
				callback.onPositionUpdate(Thread.currentThread(), position);
			}
            logger.info("Profile {} acquired slot", profile.getName());
                        // It's this thread's turn and a slot is available.
                        waitingQueue.poll(); // Remove the thread from the queue.
                        MAX_RUNNING_EMULATORS--; // Acquire the slot.

                        // Notify all waiting threads to re-evaluate the condition.
			permitsAvailable.signalAll();
		} finally {
			lock.unlock();
		}
	}

	public void releaseEmulatorSlot(DTOProfiles profile) {
		lock.lock();
		try {
            logger.info("Profile {} is releasing queue slot.", profile.getName());
			MAX_RUNNING_EMULATORS++;
			permitsAvailable.signalAll();
		} finally {
			lock.unlock();
		}
	}

	private int getPosition(WaitingThread target) {
		int pos = 1;
		for (WaitingThread wt : waitingQueue) {
			if (wt.equals(target)) {
				break;
			}
			pos++;
		}
		return pos;
	}

	public void resetQueueState() {
		lock.lock();
		try {
			waitingQueue.clear();
			permitsAvailable.signalAll();
		} finally {
			lock.unlock();
		}
	}

}
