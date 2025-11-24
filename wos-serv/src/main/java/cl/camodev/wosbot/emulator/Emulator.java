package cl.camodev.wosbot.emulator;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;

import cl.camodev.utiles.UtilOCR;
import cl.camodev.wosbot.console.enumerable.GameVersion;
import cl.camodev.wosbot.ex.ADBConnectionException;
import cl.camodev.wosbot.ot.DTORawImage;
import com.android.ddmlib.*;

import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class for Android emulator management and automation.
 * <p>
 * Provides common operations for interacting with emulators using ddmlib,
 * such as launching, closing, checking status, executing shell commands,
 * taking screenshots, simulating touch events, and OCR.
 * <p>
 * Subclasses must implement device-specific logic for serial retrieval,
 * launching, closing, and running status.
 * <p>
 * Includes robust retry logic and logging for reliable automation.
 *
 * @author cacuna
 */
public abstract class Emulator {
	protected static final int MAX_RETRIES = 10;
	protected static final int RETRY_DELAY_MS = 3000;
	protected static final int INIT_LOOPS = 10;
	protected static final int INIT_DELAY_MS = 500;
	private static final Logger logger = LoggerFactory.getLogger(Emulator.class);
	protected String consolePath;
	protected AndroidDebugBridge bridge = null;

	// Cache for devices to avoid repeated lookups
	private final ConcurrentHashMap<String, IDevice> deviceCache = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> deviceCacheTimestamp = new ConcurrentHashMap<>();
	private static final long DEVICE_CACHE_TTL = 30000; // 30 seconds cache TTL

	// Cache for isRunning status to avoid repeated external process calls
	private final ConcurrentHashMap<String, Boolean> runningStatusCache = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> runningStatusTimestamp = new ConcurrentHashMap<>();
	private static final long RUNNING_STATUS_CACHE_TTL = 5000; // 5 seconds cache TTL (shorter than device cache)

	// Cache for last captured screenshot per emulator
	private final ConcurrentHashMap<String, DTORawImage> lastScreenshotCache = new ConcurrentHashMap<>();

	public Emulator(String consolePath) {
		this.consolePath = consolePath;
		initializeBridge();
	}

	/**
	 * Gets the ADB executable path from the project's execution directory.
	 * @return Path to the ADB executable
	 */
	private String getProjectAdbPath() {
		// Get the current working directory (where the application is running)
		String currentDir = System.getProperty("user.dir");

		// Check if we're in the wos-hmi directory or the root project directory
		File adbFile = new File(currentDir, "lib" + File.separator + "adb" + File.separator + "adb.exe");
		if (adbFile.exists()) {
			return adbFile.getAbsolutePath();
		}

		// Try the wos-hmi subdirectory if we're in the root
		adbFile = new File(currentDir, "wos-hmi" + File.separator + "adb" + File.separator + "adb.exe");
		if (adbFile.exists()) {
			return adbFile.getAbsolutePath();
		}

		// Fallback to the original console path if project ADB not found
		logger.warn("Project ADB not found, falling back to console path: {}", consolePath);
		return consolePath + File.separator + "adb.exe";
	}

	/**
	 * Initializes the ddmlib bridge for ADB communication using the project's ADB.
	 */
	protected void initializeBridge() {
		if (bridge == null) {
			AndroidDebugBridge.disconnectBridge(5000, TimeUnit.MILLISECONDS);
			AndroidDebugBridge.terminate();
			AndroidDebugBridge.init(false);

			String adbPath = getProjectAdbPath();
			logger.info("Initializing ADB bridge with path: {}", adbPath);
			bridge = AndroidDebugBridge.createBridge(adbPath, true, 5000, TimeUnit.MILLISECONDS);
		}
	}

	/**
	 * Gets the device serial for the given emulator number.
	 * Must be implemented by subclasses.
	 * @param emulatorNumber Emulator identifier
	 * @return Device serial string
	 */
	protected abstract String getDeviceSerial(String emulatorNumber);

	/**
	 * Launches the emulator with the given number.
	 * Must be implemented by subclasses.
	 * @param emulatorNumber Emulator identifier
	 */
	public abstract void launchEmulator(String emulatorNumber);

	/**
	 * Closes the emulator with the given number.
	 * Must be implemented by subclasses.
	 * @param emulatorNumber Emulator identifier
	 */
	public abstract void closeEmulator(String emulatorNumber);

	/**
	 * Checks if the emulator is running.
	 * Must be implemented by subclasses.
	 * @param emulatorNumber Emulator identifier
	 * @return true if running, false otherwise
	 */
	public abstract boolean isRunning(String emulatorNumber);

	/**
	 * Waits for the ddmlib bridge to be ready.
	 * @throws InterruptedException if interrupted while waiting
	 */
	protected void waitForBridge() throws InterruptedException {
		int loops = 0;
		while ((bridge == null || !bridge.hasInitialDeviceList()) && loops < INIT_LOOPS) {
			Thread.sleep(INIT_DELAY_MS);
			loops++;
		}
	}

	/**
	 * Finds the IDevice instance for the given emulator number.
	 * @param emulatorNumber Emulator identifier
	 * @return IDevice instance or null if not found
	 * @throws InterruptedException if interrupted while waiting
	 */
	protected IDevice findDevice(String emulatorNumber) throws InterruptedException {
		waitForBridge();
		String serial = getDeviceSerial(emulatorNumber);

		// 1. First search in already connected devices (quick search)
		for (IDevice device : bridge.getDevices()) {
			if (serial.equals(device.getSerialNumber())) {
				logger.debug("Device found in cache: {}", serial);
				return device;
			}
		}

		// 2. If not found, try direct connection
		logger.info("Device not found in cache, connecting directly: " + serial);
		if (connectToDeviceBySerial(serial)) {
			// 3. Force bridge update and search again
			//forceDeviceDiscovery();

			// 4. Search for the newly connected device
			for (int i = 0; i < 5; i++) {
				for (IDevice device : bridge.getDevices()) {
					if (serial.equals(device.getSerialNumber())) {
                        logger.info("Device connected and found: {}", serial);
						return device;
					}
				}
				Thread.sleep(1000); // Wait 1 second between attempts
			}
		}

		logger.warn("Could not connect to device: {}", serial);
		return null;
	}

	/**
	 * Connects to a device by its serial.
	 * @param serial Device serial string
	 * @return true if connection is successful, false otherwise
	 */
	private boolean connectToDeviceBySerial(String serial) {
		try {
			String address = extractAddressFromSerial(serial);
            logger.info("Attempting to connect to: {}", address);

			String adbPath = getProjectAdbPath();
			ProcessBuilder pb = new ProcessBuilder(adbPath, "connect", address);
			pb.directory(new File(adbPath).getParentFile());

			Process process = pb.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String result = reader.readLine();

			int exitCode = process.waitFor();

			if (exitCode == 0 && result != null) {
				if (result.contains("connected") || result.contains("already connected")) {
                    logger.info("Successful connection to: {}", address);
					return true;
				}
			}

            logger.warn("Could not connect to: {} - Response: {}", address, result);
			return false;
		} catch (Exception e) {
            logger.error("Error connecting to device: {}", serial, e);
			return false;
		}
	}

	/**
	 * Executes an action with retries for the given emulator.
	 * @param emulatorNumber Emulator identifier
	 * @param action Function to execute with IDevice
	 * @param actionName Name for logging
	 * @param <T> Return type
	 * @return Result of the action
	 */
	protected <T> T withRetries(String emulatorNumber, Function<IDevice, T> action, String actionName) {
		if (!isRunning(emulatorNumber)) {
			logger.error("Emulator {} is not running, cannot perform action {}", emulatorNumber, actionName);
			throw new ADBConnectionException(
					"Emulator " + emulatorNumber + " is not running, cannot perform action " + actionName);
		}

		// --- Phase 1: Initial attempts with ADB restarts ---
		for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
			try {
				IDevice device = findDevice(emulatorNumber);
				if (device == null) {
					logger.error("Device not found for {}: {}", actionName, emulatorNumber);
					if (attempt >= MAX_RETRIES / 2) {
						logger.info("Attempting ADB restart as last resort (attempt {})", attempt);
						restartAdb();
					}
					Thread.sleep(RETRY_DELAY_MS / 2);
					continue;
				}

				if (!device.isOnline()) {
					logger.warn("Device found but not online, waiting... (attempt {})", attempt);
					Thread.sleep(2000);
					continue;
				}
				return action.apply(device);
			} catch (Exception e) {
				logger.warn("Attempt {} of {} failed: {}", attempt, actionName, e.getMessage());
				if (attempt >= MAX_RETRIES - 2) {
					logger.warn("Multiple failures, attempting ADB restart (attempt {})", attempt);
					try {
						restartAdb();
						Thread.sleep(RETRY_DELAY_MS);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
					}
				} else {
					try {
						Thread.sleep(RETRY_DELAY_MS / 2);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
					}
				}
			}
		}

		// --- Phase 2: If Phase 1 failed, restart emulator and try again ---
		logger.warn(
				"All {} initial attempts failed for {} on {}. Attempting emulator restart and new set of retries...",
				MAX_RETRIES, actionName, emulatorNumber);
		try {
			logger.info("Attempting to restart the emulator for {} on {}", actionName, emulatorNumber);
			closeEmulator(emulatorNumber);
			Thread.sleep(5000); // Wait for emulator to close
			launchEmulator(emulatorNumber);
			Thread.sleep(15000); // Wait for emulator to launch and stabilize
		} catch (Exception e) {
			logger.error("Failed to restart emulator for {} on {}: {}", actionName, emulatorNumber, e.getMessage());
			throw new ADBConnectionException("Emulator restart failed for " + actionName + " on " + emulatorNumber, e);
		}

		// --- Phase 3: Second set of attempts after emulator restart ---
		for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
			try {
				IDevice device = findDevice(emulatorNumber);
				if (device == null) {
					logger.error("Device not found after emulator restart for {}: {}", actionName, emulatorNumber);
					if (attempt >= MAX_RETRIES / 2) {
						logger.info("Attempting ADB restart as last resort after emulator restart (attempt {})",
								attempt);
						restartAdb();
					}
					Thread.sleep(RETRY_DELAY_MS / 2);
					continue;
				}

				if (!device.isOnline()) {
					logger.warn("Device found but not online after emulator restart, waiting... (attempt {})", attempt);
					Thread.sleep(2000);
					continue;
				}
				return action.apply(device);
			} catch (Exception e) {
				logger.warn("Attempt {} of {} failed after emulator restart: {}", attempt, actionName, e.getMessage());
				if (attempt >= MAX_RETRIES - 2) {
					logger.warn("Multiple failures after emulator restart, attempting ADB restart (attempt {})",
							attempt);
					try {
						restartAdb();
						Thread.sleep(RETRY_DELAY_MS);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
					}
				} else {
					try {
						Thread.sleep(RETRY_DELAY_MS / 2);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
					}
				}
			}
		}

		// --- Phase 4: Final failure ---
		logger.error("All attempts including emulator restart and final ADB restart failed for {} on {}", actionName,
				emulatorNumber);
		throw new ADBConnectionException("All attempts including ADB restart and device restart failed for "
				+ actionName + " on " + emulatorNumber);
	}

	/**
	 * Gets a color component from a RawImage.
	 * @param rawImage RawImage from ddmlib
	 * @param baseOffset Base offset in image data
	 * @param bitOffset Bit offset for the color
	 * @return Color component value
	 */
	protected int getColorComponent(RawImage rawImage, int baseOffset, int bitOffset) {
		if (bitOffset == -1)
			return 0;
		int byteOffset = bitOffset / 8;
		return rawImage.data[baseOffset + byteOffset] & 0xFF;
	}

	/**
	 * Captures a screenshot from the emulator.
	 * @param emulatorNumber Emulator identifier
	 * @return DTORawImage with raw screenshot data
	 */
    public DTORawImage captureScreenshot(String emulatorNumber)  {
        long startTime = System.currentTimeMillis();
        logger.debug("=== Screenshot Capture Started === Emulator: {}", emulatorNumber);

        // Get serial directly
        long serialStart = System.currentTimeMillis();
        String serial = getDeviceSerial(emulatorNumber);
        logger.debug("Serial lookup: {} ms", (System.currentTimeMillis() - serialStart));

        if (serial == null) {
            logger.error("Cannot get serial for emulator {}", emulatorNumber);
            throw new ADBConnectionException("Cannot get serial for emulator " + emulatorNumber);
        }

        // Get device
        IDevice device = null;
        try {
            device = getCachedDevice(emulatorNumber);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (device == null || !device.isOnline()) {
            throw new ADBConnectionException("Device " + serial + " is not online");
        }

        try {
            long captureStartTime = System.currentTimeMillis();

            // Use native screencap command for maximum speed
            ByteArrayOutputStream imageData = new ByteArrayOutputStream();
            CollectingOutputReceiver receiver = new CollectingOutputReceiver() {
                @Override
                public void addOutput(byte[] data, int offset, int length) {
                    imageData.write(data, offset, length);
                }
            };

            // Execute screencap command (raw format is fastest)
            device.executeShellCommand("screencap", receiver, 2000, TimeUnit.MILLISECONDS);

            byte[] rawData = imageData.toByteArray();
            long captureEndTime = System.currentTimeMillis();
            logger.debug("Screencap command executed: {} ms", (captureEndTime - captureStartTime));

            if (rawData.length < 12) {
                throw new RuntimeException("Invalid screencap data: too small");
            }

            // Parse raw screencap header (first 12 bytes)
            // Format: width(4) height(4) format(4) [pixel data...]
            int width = (rawData[0] & 0xFF) | ((rawData[1] & 0xFF) << 8) |
                    ((rawData[2] & 0xFF) << 16) | ((rawData[3] & 0xFF) << 24);
            int height = (rawData[4] & 0xFF) | ((rawData[5] & 0xFF) << 8) |
                    ((rawData[6] & 0xFF) << 16) | ((rawData[7] & 0xFF) << 24);
            int format = (rawData[8] & 0xFF) | ((rawData[9] & 0xFF) << 8) |
                    ((rawData[10] & 0xFF) << 16) | ((rawData[11] & 0xFF) << 24);

            logger.debug("Screencap header: {}x{}, format: {}", width, height, format);

            // Extract pixel data (skip 12-byte header)
            byte[] pixelData = new byte[rawData.length - 12];
            System.arraycopy(rawData, 12, pixelData, 0, pixelData.length);

            // Determine bpp based on format (usually RGBA_8888 = 1)
            int bpp = (format == 1) ? 32 : 16; // RGBA_8888 or RGB_565

            DTORawImage result = new DTORawImage(
                    pixelData,
                    width,
                    height,
                    bpp
            );

            long totalTime = System.currentTimeMillis() - startTime;
            logger.debug("=== Screenshot Completed === Total: {} ms, {} bytes",
                    totalTime, pixelData.length);

            // Update cache
            lastScreenshotCache.put(emulatorNumber, result);

            return result;

        } catch (TimeoutException e) {
            logger.error("Screencap timeout for {}", emulatorNumber);
            throw new RuntimeException("Screencap timeout", e);
        } catch (Exception e) {
            logger.error("Failed to capture screenshot for {}: {}", emulatorNumber, e.getMessage());
            throw new RuntimeException("Error capturing screenshot", e);
        }
    }

	/**
	 * Simulates a tap event at a random point within the given area.
	 * @param emulatorNumber Emulator identifier
	 * @param point1 First corner
	 * @param point2 Second corner
	 * @param tapCount Number of taps
	 * @param delayMs Delay between taps in milliseconds
	 * @return true if successful
	 */
	protected boolean tapWithDdmlib(String emulatorNumber, DTOPoint point1, DTOPoint point2, int tapCount, int delayMs) {
		return withRetries(emulatorNumber, device -> {
			Random random = new Random();
			int minX = Math.min(point1.getX(), point2.getX());
			int maxX = Math.max(point1.getX(), point2.getX());
			int minY = Math.min(point1.getY(), point2.getY());
			int maxY = Math.max(point1.getY(), point2.getY());

			for (int i = 1; i <= tapCount; i++) {
				int x = minX + random.nextInt(maxX - minX + 1);
				int y = minY + random.nextInt(maxY - minY + 1);

				try {
					device.executeShellCommand("input tap " + x + " " + y, new NullOutputReceiver());
                    // Detailed log with coordinates and tap count
                    logger.debug("Tap {}/{} executed at ({},{}) on emulator {}", i, tapCount, x, y, emulatorNumber);
					Thread.sleep(delayMs);
				} catch (Exception ex) {
					throw new RuntimeException(ex);
				}
			}
			return Boolean.TRUE;
		}, "tapAtRandomPoint x" + tapCount);
	}

	/**
	 * Restarts the ADB bridge using the project's ADB executable.
	 */
	public void restartAdb() {
		AndroidDebugBridge.disconnectBridge(5000, TimeUnit.MILLISECONDS);
		AndroidDebugBridge.terminate();
		AndroidDebugBridge.init(false);

		String adbPath = getProjectAdbPath();
		logger.info("Restarting ADB bridge with path: {}", adbPath);
		bridge = AndroidDebugBridge.createBridge(adbPath, true, 5000, TimeUnit.MILLISECONDS);
		logger.info("ADB restarted successfully");
	}

	/**
	 * Executes a swipe gesture from the start point to the end point on the emulator.
	 * @param emulatorNumber Emulator identifier
	 * @param point Start point
	 * @param point2 End point
	 */
	public void swipe(String emulatorNumber, DTOPoint point, DTOPoint point2) {
		withRetries(emulatorNumber, device -> {
			try {
				String command = String.format("input swipe %d %d %d %d", point.getX(), point.getY(), point2.getX(), point2.getY());
				device.executeShellCommand(command, new NullOutputReceiver());
				logger.debug("Swipe executed from ({},{}) to ({},{}) on emulator {}",
						point.getX(), point.getY(), point2.getX(), point2.getY(), emulatorNumber);
				return null;
			} catch (Exception e) {
				throw new RuntimeException("Error executing swipe", e);
			}
		}, "swipe");
	}

	/**
	 * Simulates pressing the back button on the emulator.
	 * @param emulatorNumber Emulator identifier
	 */
	public void pressBackButton(String emulatorNumber) {
		withRetries(emulatorNumber, device -> {
			try {
				device.executeShellCommand("input keyevent KEYCODE_BACK", new NullOutputReceiver());
                logger.debug("Back button pressed on emulator {}", emulatorNumber);
				return null;
			} catch (Exception e) {
				throw new RuntimeException("Error pressing back button", e);
			}
		}, "pressBackButton");
	}

	/**
	 * Writes text on the emulator using ADB input.
	 * Automatically escapes special characters for shell compatibility.
	 *
	 * @param emulatorNumber Emulator identifier
	 * @param text Text to write
	 */
	public void writeText(String emulatorNumber, String text) {
		withRetries(emulatorNumber, device -> {
			try {
				// Escape special characters for shell input
				String escapedText = escapeTextForShell(text);

				// Use input text command
				String command = "input text \"" + escapedText + "\"";
				device.executeShellCommand(command, new NullOutputReceiver());
				logger.debug("Text written on emulator {}: {}", emulatorNumber, text);
				return null;
			} catch (Exception e) {
				throw new RuntimeException("Error writing text: " + text, e);
			}
		}, "writeText");
	}

	/**
	 * Clears text from the currently focused input field.
	 * Simulates pressing backspace multiple times.
	 *
	 * @param emulatorNumber Emulator identifier
	 * @param count Number of backspace key presses
	 */
	public void clearText(String emulatorNumber, int count) {
		withRetries(emulatorNumber, device -> {
			try {
				for (int i = 0; i < count; i++) {
					device.executeShellCommand("input keyevent KEYCODE_DEL", new NullOutputReceiver());
					Thread.sleep(50); // Small delay between key presses
				}
				logger.debug("Cleared {} characters on emulator {}", count, emulatorNumber);
				return null;
			} catch (Exception e) {
				throw new RuntimeException("Error clearing text", e);
			}
		}, "clearText");
	}

	/**
	 * Escapes special characters in text for safe shell input.
	 * Handles spaces, quotes, and other special shell characters.
	 *
	 * @param text Text to escape
	 * @return Escaped text safe for shell commands
	 */
	private String escapeTextForShell(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}

		// Replace spaces with %s (ADB input text uses %s for spaces)
		String escaped = text.replace(" ", "%s");

		// Escape special characters for shell
		escaped = escaped.replace("\"", "\\\"");
		escaped = escaped.replace("'", "\\'");
		escaped = escaped.replace("$", "\\$");
		escaped = escaped.replace("`", "\\`");
		escaped = escaped.replace("\\", "\\\\");
		escaped = escaped.replace("&", "\\&");
		escaped = escaped.replace("|", "\\|");
		escaped = escaped.replace(";", "\\;");
		escaped = escaped.replace("<", "\\<");
		escaped = escaped.replace(">", "\\>");
		escaped = escaped.replace("(", "\\(");
		escaped = escaped.replace(")", "\\)");

		return escaped;
	}

	/**
	 * Checks if an app is installed on the emulator.
	 * @param emulatorNumber Emulator identifier
	 * @param packageName Package name to check
	 * @return true if installed, false otherwise
	 */
	public boolean isAppInstalled(String emulatorNumber, String packageName) {
		return withRetries(emulatorNumber, device -> {
			try {
				StringBuilder output = new StringBuilder();
				IShellOutputReceiver receiver = new IShellOutputReceiver() {
					@Override
					public void addOutput(byte[] data, int offset, int length) {
						output.append(new String(data, offset, length));
					}

					@Override
					public void flush() {
						// Doesn't need implementation
					}

					@Override
					public boolean isCancelled() {
						return false;
					}
				};

				device.executeShellCommand("pm list packages | grep " + packageName, receiver);
				String result = output.toString().trim();
				return !result.isEmpty();
			} catch (Exception e) {
				throw new RuntimeException("Error checking if app is installed: " + packageName, e);
			}
		}, "isAppInstalled");
	}

	/**
	 * Checks if the package is running in the foreground using dumpsys window windows.
	 * @param emulatorNumber Emulator identifier
	 * @param packageName Package name to check
	 * @return true if in foreground, false otherwise
	 */
	public boolean isPackageRunning(String emulatorNumber, String packageName) {
		return withRetries(emulatorNumber, device -> {
			try {
				// List of commands to try, in order:
				List<String> cmds = Arrays.asList(
						"dumpsys window windows",             // Android ≤ 10
						"dumpsys window displays",            // Android 11+
						"dumpsys window",                     // global fallback
						"dumpsys activity activities"         // search for mResumedActivity
				);
				for (String cmd : cmds) {
					CollectingOutputReceiver recv = new CollectingOutputReceiver();
					device.executeShellCommand(cmd, recv, 5, TimeUnit.SECONDS);
					String out = recv.getOutput();

					for (String line : out.split("\\r?\\n")) {
						line = line.trim();
						// for the first 3 commands...
						if ((cmd.contains("window")) &&
								(line.contains("mCurrentFocus") || line.contains("mFocusedApp")) &&
								line.contains(packageName + "/")) {
							logger.trace("✅ Foreground detected with `{}`: {}", cmd, line);
							return true;
						}
						// for dumpsys activity activities...
						if (cmd.contains("activity") &&
								line.contains("mResumedActivity") &&
								line.contains(packageName + "/")) {
							logger.info("✅ Foreground detected with `{}`: {}", cmd, line);
							return true;
						}
					}
				}
				logger.info("App {} is not in foreground on emulator {}", packageName, emulatorNumber);
				return false;
			} catch (Exception e) {
				throw new RuntimeException("Error checking if package is running: " + packageName, e);
			}
		}, "isAppInForeground");
	}

	/**
	 * Launches an app on the emulator using monkey.
	 * @param emulatorNumber Emulator identifier
	 * @param packageName Package name to launch
	 */
	public void launchApp(String emulatorNumber, String packageName) {
		withRetries(emulatorNumber, device -> {
			try {
				device.executeShellCommand("monkey -p " + packageName + " -c android.intent.category.LAUNCHER 1", new NullOutputReceiver());
                logger.info("Application {} launched on emulator {}", packageName, emulatorNumber);
				return null;
			} catch (Exception e) {
				throw new RuntimeException("Error launching app: " + packageName, e);
			}
		}, "launchApp");
	}

	/**
	 * Sends the game to background by pressing the home button.
	 * This keeps the game running but returns to the home screen.
	 * @param emulatorNumber Emulator identifier
	 */
	public void sendGameToBackground(String emulatorNumber) {
		withRetries(emulatorNumber, device -> {
			try {
				device.executeShellCommand("input keyevent KEYCODE_HOME", new NullOutputReceiver());
                logger.info("Game sent to background on emulator {}", emulatorNumber);
				return null;
			} catch (Exception e) {
				throw new RuntimeException("Error sending game to background", e);
			}
		}, "sendGameToBackground");
	}

	/**
	 * Simulates a random tap at a point within the given area.
	 * @param emulatorNumber Emulator identifier
	 * @param point1 First corner
	 * @param point2 Second corner
	 * @return true if successful
	 */
	public boolean tapAtRandomPoint(String emulatorNumber, DTOPoint point1, DTOPoint point2) {
		return tapWithDdmlib(emulatorNumber, point1, point2, 1, 0);
	}

	/**
	 * Simulates multiple random taps at points within the given area.
	 * @param emulatorNumber Emulator identifier
	 * @param point1 First corner
	 * @param point2 Second corner
	 * @param tapCount Number of taps
	 * @param delayMs Delay between taps in milliseconds
	 * @return true if successful
	 */
	public boolean tapAtRandomPoint(String emulatorNumber, DTOPoint point1, DTOPoint point2, int tapCount, int delayMs) {
		return tapWithDdmlib(emulatorNumber, point1, point2, tapCount, delayMs);
	}

	/**
	 * Performs OCR on a region of the emulator screen.
	 * @param emulatorNumber Emulator identifier
	 * @param p1 First corner
	 * @param p2 Second corner
	 * @return Recognized text
	 * @throws IOException if image capture fails
	 * @throws TesseractException if OCR fails
	 */
	public String ocrRegionText(String emulatorNumber, DTOPoint p1, DTOPoint p2) throws IOException, TesseractException {
		DTORawImage rawImage = captureScreenshot(emulatorNumber);
		if (rawImage == null)
			throw new IOException("Could not capture image.");

        String language = (EmulatorManager.GAME == GameVersion.CHINA) ? "eng+chi_sim" : "eng";
		return UtilOCR.ocrFromRegion(rawImage, p1, p2, language);
	}

	/**
	 * Performs OCR on a region of the emulator screen with custom Tesseract settings.
	 * @param emulatorNumber Emulator identifier
	 * @param p1 First corner
	 * @param p2 Second corner
	 * @param settings Tesseract OCR configuration settings
	 * @return Recognized text
	 * @throws IOException if image capture fails
	 * @throws TesseractException if OCR fails
	 */
	public String ocrRegionText(String emulatorNumber, DTOPoint p1, DTOPoint p2, DTOTesseractSettings settings) throws IOException, TesseractException {
		DTORawImage rawImage;

		// Check if we should reuse the last image
		if (settings != null && settings.isReuseLastImage()) {
			rawImage = lastScreenshotCache.get(emulatorNumber);
			if (rawImage != null) {
				logger.debug("Reusing cached screenshot for OCR on emulator {}", emulatorNumber);
			} else {
				logger.debug("No cached screenshot available, capturing new one for emulator {}", emulatorNumber);
				rawImage = captureScreenshot(emulatorNumber);
			}
		} else {
			// Normal behavior: capture new screenshot
			rawImage = captureScreenshot(emulatorNumber);
		}

		if (rawImage == null)
			throw new IOException("Could not capture image.");

		return UtilOCR.ocrFromRegion(rawImage, p1, p2, settings);
	}

	/**
	 * Gets a cached device or finds it if not in cache or expired.
	 * This significantly improves performance by avoiding repeated device lookups.
	 * @param emulatorNumber Emulator identifier
	 * @return IDevice instance or null if not found
	 * @throws InterruptedException if interrupted while waiting
	 */
	protected IDevice getCachedDevice(String emulatorNumber) throws InterruptedException {
		long currentTime = System.currentTimeMillis();

		// Check if we have a cached device and it's still valid
		IDevice cachedDevice = deviceCache.get(emulatorNumber);
		Long cacheTime = deviceCacheTimestamp.get(emulatorNumber);

		if (cachedDevice != null && cacheTime != null) {
			// Check if cache is still valid (TTL not expired)
			if ((currentTime - cacheTime) < DEVICE_CACHE_TTL) {
				// Verify device is still online
				if (cachedDevice.isOnline()) {
					logger.trace("Using cached device for emulator {}", emulatorNumber);
					return cachedDevice;
				} else {
					logger.debug("Cached device is offline, refreshing cache for emulator {}", emulatorNumber);
				}
			} else {
				logger.trace("Device cache expired for emulator {}, refreshing", emulatorNumber);
			}
		}

		// Cache miss or expired, find device and update cache
		IDevice device = findDevice(emulatorNumber);
		if (device != null) {
			deviceCache.put(emulatorNumber, device);
			deviceCacheTimestamp.put(emulatorNumber, currentTime);
			logger.debug("Device cached for emulator {}", emulatorNumber);
		}

		return device;
	}

	/**
	 * Invalidates the device cache for a specific emulator.
	 * Useful when a device disconnects or needs to be refreshed.
	 * @param emulatorNumber Emulator identifier
	 */
	protected void invalidateDeviceCache(String emulatorNumber) {
		deviceCache.remove(emulatorNumber);
		deviceCacheTimestamp.remove(emulatorNumber);
		logger.debug("Device cache invalidated for emulator {}", emulatorNumber);
	}

	/**
	 * Checks if the emulator is running with caching to avoid repeated external process calls.
	 * This wrapper method provides significant performance improvement for MEmu emulator.
	 * @param emulatorNumber Emulator identifier
	 * @return true if running, false otherwise
	 */
	protected boolean isRunningCached(String emulatorNumber) {
		long currentTime = System.currentTimeMillis();

		// Check if we have a cached status and it's still valid
		Boolean cachedStatus = runningStatusCache.get(emulatorNumber);
		Long statusTime = runningStatusTimestamp.get(emulatorNumber);

		if (cachedStatus != null && statusTime != null) {
			// Check if cache is still valid (TTL not expired)
			if ((currentTime - statusTime) < RUNNING_STATUS_CACHE_TTL) {
				logger.trace("Using cached running status for emulator {}: {}", emulatorNumber, cachedStatus);
				return cachedStatus;
			}
		}

		// Cache miss or expired, call actual isRunning and update cache
		boolean status = isRunning(emulatorNumber);
		runningStatusCache.put(emulatorNumber, status);
		runningStatusTimestamp.put(emulatorNumber, currentTime);
		logger.trace("Running status cached for emulator {}: {}", emulatorNumber, status);

		return status;
	}

	/**
	 * Invalidates the running status cache for a specific emulator.
	 * Should be called when launching or closing an emulator.
	 * @param emulatorNumber Emulator identifier
	 */
	protected void invalidateRunningStatusCache(String emulatorNumber) {
		runningStatusCache.remove(emulatorNumber);
		runningStatusTimestamp.remove(emulatorNumber);
		logger.debug("Running status cache invalidated for emulator {}", emulatorNumber);
	}

	/**
	 * Extracts the IP:port address from a device serial string.
	 * @param serial Device serial string
	 * @return IP:port address
	 */
	private String extractAddressFromSerial(String serial) {
		if (serial.startsWith("emulator-")) {
			// For emulator serials like emulator-5554, convert to 127.0.0.1:5554
			String port = serial.substring("emulator-".length());
			return "127.0.0.1:" + port;
		} else if (serial.contains(":")) {
			// Already in IP:port format (used by MEmu)
			return serial;
		}
		// If it doesn't match any known format, return as is
		return serial;
	}

}
