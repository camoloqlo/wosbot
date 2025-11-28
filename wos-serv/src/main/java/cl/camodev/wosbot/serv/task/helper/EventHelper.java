package cl.camodev.wosbot.serv.task.helper;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.logging.ProfileLogger;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;

/**
 * Helper class for checking game event states.
 * 
 * <p>
 * This helper provides methods to detect whether specific game events
 * are currently active, such as:
 * <ul>
 * <li>Bear Hunt event</li>
 * <li>Tundra Truck event</li>
 * <li>Hero Mission event</li>
 * <li>Other temporary events</li>
 * </ul>
 * 
 * <p>
 * Event state checking is performed via template matching on event-specific
 * UI indicators.
 * 
 * @author WoS Bot
 */
public class EventHelper {

    private final TemplateSearchHelper templateSearchHelper;
    private final ProfileLogger logger;

    /**
     * Constructs a new EventHelper.
     * 
     * @param emuManager     The emulator manager instance
     * @param emulatorNumber The identifier for the emulator
     * @param profile        The profile this helper operates on
     */
    public EventHelper(
            EmulatorManager emuManager,
            String emulatorNumber,
            DTOProfiles profile) {
        this.templateSearchHelper = new TemplateSearchHelper(emuManager, emulatorNumber, profile);
        this.logger = new ProfileLogger(EventHelper.class, profile);
    }

    /**
     * Checks if the Bear Hunt event is currently running.
     * 
     * <p>
     * The Bear Hunt is a time-limited event where players hunt special bears
     * on the world map. When active, a distinctive UI indicator appears on screen.
     * 
     * <p>
     * This method uses template matching with retry logic to reliably detect
     * the event indicator. Tasks that consume stamina should check this before
     * executing to avoid conflicts with bear hunting activities.
     * 
     * @return true if Bear Hunt event is active, false otherwise
     */
    public boolean isBearRunning() {
        logger.debug("Checking if Bear Hunt event is running");

        DTOImageSearchResult result = templateSearchHelper.searchTemplate(
                EnumTemplates.BEAR_HUNT_IS_RUNNING,
                SearchConfigConstants.SINGLE_WITH_RETRIES);

        boolean isRunning = result.isFound();

        if (isRunning) {
            logger.debug("Bear Hunt event is currently active");
        } else {
            logger.debug("Bear Hunt event is not active");
        }

        return isRunning;
    }

    // Future expansion methods:
    // public boolean isTundraTruckRunning() { ... }
    // public boolean isHeroMissionRunning() { ... }
    // public boolean isMercenaryEventRunning() { ... }
}