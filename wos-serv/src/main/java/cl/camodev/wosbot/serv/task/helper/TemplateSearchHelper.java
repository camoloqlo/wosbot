package cl.camodev.wosbot.serv.task.helper;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.logging.ProfileLogger;
import cl.camodev.wosbot.ot.DTOArea;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServLogs;
import java.util.List;

/**
 * Helper class for template searching operations.
 * 
 * <p>
 * Provides template search functionality including:
 * <ul>
 * <li>Single and multiple template matching</li>
 * <li>Grayscale and color template matching</li>
 * <li>Configurable retry logic with delays</li>
 * <li>Search area specification</li>
 * </ul>
 * 
 * @author WoS Bot
 */
public class TemplateSearchHelper {

    private final EmulatorManager emuManager;
    private final String emulatorNumber;
    private final ProfileLogger logger;
    private final String profileName;
    private final ServLogs servLogs;
    private static final String HELPER_NAME = "TemplateSearchHelper";

    /**
     * Constructs a new TemplateSearchHelper.
     * 
     * @param emuManager     The emulator manager instance
     * @param emulatorNumber The identifier for the specific emulator
     * @param profile        The profile this helper operates on
     */
    public TemplateSearchHelper(EmulatorManager emuManager, String emulatorNumber, DTOProfiles profile) {
        this.emuManager = emuManager;
        this.emulatorNumber = emulatorNumber;
        this.logger = new ProfileLogger(TemplateSearchHelper.class, profile);
        this.profileName = profile.getName();
        this.servLogs = ServLogs.getServices();
    }

    /**
     * Searches for a single instance of a template on the emulator screen.
     * Retries the search based on the configuration settings.
     * 
     * @param template The template to search for
     * @param config   The search configuration (maxAttempts, delay, threshold,
     *                 area, coordinates)
     * @return A DTOImageSearchResult object if found, null otherwise
     */
    public DTOImageSearchResult searchTemplate(EnumTemplates template, SearchConfig config) {
        DTOImageSearchResult result = null;
        int attempts = 0;

        while (attempts < config.getMaxAttempts()) {
            attempts++;

            result = executeSearch(emulatorNumber, template, config);

            // If template found, return immediately
            if (result != null && result.isFound()) {
                logDebug("Template " + template.name() + " found at attempt " + attempts);
                return result;
            }

            // If not the last attempt, wait for the delay
            if (attempts < config.getMaxAttempts()) {
                sleep(config.getDelayBetweenAttempts());
            }
        }

        logDebug("Template " + template.name() + " not found after " + attempts + " attempts");
        return result;
    }

    /**
     * Searches for a single instance of a template using grayscale matching.
     * Retries the search based on the configuration settings.
     * Both the template and the screen image are converted to grayscale before
     * matching.
     * 
     * @param template The template to search for
     * @param config   The search configuration (maxAttempts, delay, threshold,
     *                 area, coordinates)
     * @return A DTOImageSearchResult object if found, null otherwise
     */
    public DTOImageSearchResult searchTemplateGrayscale(EnumTemplates template, SearchConfig config) {
        DTOImageSearchResult result = null;
        int attempts = 0;

        while (attempts < config.getMaxAttempts()) {
            attempts++;

            result = executeSearchGrayscale(emulatorNumber, template, config);

            // If template found, return immediately
            if (result != null && result.isFound()) {
                logDebug("Grayscale template " + template.name() + " found at attempt " + attempts);
                return result;
            }

            // If not the last attempt, wait for the delay
            if (attempts < config.getMaxAttempts()) {
                logDebug("Grayscale template " + template.name() + " not found on attempt " + attempts
                        + ", retrying in " + config.getDelayBetweenAttempts() + "ms...");
                sleep(config.getDelayBetweenAttempts());
            }
        }

        logDebug("Grayscale template " + template.name() + " not found after " + attempts + " attempts");
        return result;
    }

    /**
     * Searches for multiple instances of a template on the emulator screen.
     * Retries the search based on the configuration settings.
     * Returns immediately upon finding at least one match.
     * 
     * @param template The template to search for
     * @param config   The search configuration (maxAttempts, delay, threshold,
     *                 maxResults, area, coordinates)
     * @return A list of DTOImageSearchResult objects for all matches found, or null
     *         if none found after all attempts
     */
    public List<DTOImageSearchResult> searchTemplates(EnumTemplates template, SearchConfig config) {
        List<DTOImageSearchResult> results = null;
        int attempts = 0;

        while (attempts < config.getMaxAttempts()) {
            attempts++;

            results = executeMultipleSearch(emulatorNumber, template, config);

            // If at least one result found, return immediately
            if (results != null && !results.isEmpty()) {
                logDebug("Multiple template " + template.name() + " found: " + results.size() + " matches");
                return results;
            }

            // If not the last attempt, wait for the delay
            if (attempts < config.getMaxAttempts()) {
                logDebug("Multiple template " + template.name() + " not found on attempt " + attempts + ", retrying in "
                        + config.getDelayBetweenAttempts() + "ms...");
                sleep(config.getDelayBetweenAttempts());
            }
        }

        logDebug("Multiple template " + template.name() + " not found after " + attempts + " attempts");
        return results;
    }

    /**
     * Searches for multiple instances of a template using grayscale matching.
     * Retries the search based on the configuration settings.
     * Both the template and the screen image are converted to grayscale before
     * matching.
     * Returns immediately upon finding at least one match.
     * 
     * @param template The template to search for
     * @param config   The search configuration (maxAttempts, delay, threshold,
     *                 maxResults, area, coordinates)
     * @return A list of DTOImageSearchResult objects for all matches found, or null
     *         if none found after all attempts
     */
    public List<DTOImageSearchResult> searchTemplatesGrayscale(EnumTemplates template, SearchConfig config) {
        List<DTOImageSearchResult> results = null;
        int attempts = 0;

        while (attempts < config.getMaxAttempts()) {
            attempts++;

            results = executeMultipleSearchGrayscale(emulatorNumber, template, config);

            // If at least one result found, return immediately
            if (results != null && !results.isEmpty()) {
                logDebug("Grayscale multiple template " + template.name() + " found: " + results.size() + " matches");
                return results;
            }

            // If not the last attempt, wait for the delay
            if (attempts < config.getMaxAttempts()) {
                logDebug("Grayscale multiple template " + template.name() + " not found on attempt " + attempts
                        + ", retrying in " + config.getDelayBetweenAttempts() + "ms...");
                sleep(config.getDelayBetweenAttempts());
            }
        }

        logDebug("Grayscale multiple template " + template.name() + " not found after " + attempts + " attempts");
        return results;
    }

    /**
     * Executes a single template search based on the provided configuration.
     * Supports searching within a specified area, between custom coordinates, or on
     * the entire screen.
     * 
     * @param emulatorNumber The emulator identifier
     * @param template       The template to search for
     * @param config         The search configuration
     * @return A DTOImageSearchResult with the search result
     */
    private DTOImageSearchResult executeSearch(String emulatorNumber, EnumTemplates template, SearchConfig config) {

        DTOImageSearchResult result;
        if (config.hasArea()) {
            result = emuManager.searchTemplate(emulatorNumber, template,
                    config.getArea().topLeft(), config.getArea().bottomRight(), config.getThreshold());
        } else if (config.hasCoordinates()) {
            result = emuManager.searchTemplate(emulatorNumber, template,
                    config.getStartPoint(), config.getEndPoint(), config.getThreshold());
        } else {
            result = emuManager.searchTemplate(emulatorNumber, template, config.getThreshold());
        }

        return result;
    }

    /**
     * Executes a single grayscale template search based on the provided
     * configuration.
     * Supports searching within a specified area, between custom coordinates, or on
     * the entire screen.
     * Both the template and screen are converted to grayscale before matching.
     * 
     * @param emulatorNumber The emulator identifier
     * @param template       The template to search for
     * @param config         The search configuration
     * @return A DTOImageSearchResult with the search result
     */
    private DTOImageSearchResult executeSearchGrayscale(String emulatorNumber, EnumTemplates template,
            SearchConfig config) {

        DTOImageSearchResult result;
        if (config.hasArea()) {
            result = emuManager.searchTemplateGrayscale(emulatorNumber, template,
                    config.getArea().topLeft(), config.getArea().bottomRight(), config.getThreshold());
        } else if (config.hasCoordinates()) {
            result = emuManager.searchTemplateGrayscale(emulatorNumber, template,
                    config.getStartPoint(), config.getEndPoint(), config.getThreshold());
        } else {
            result = emuManager.searchTemplateGrayscale(emulatorNumber, template, config.getThreshold());
        }

        return result;
    }

    /**
     * Executes a multiple template search based on the provided configuration.
     * Supports searching within a specified area, between custom coordinates, or on
     * the entire screen.
     * 
     * @param emulatorNumber The emulator identifier
     * @param template       The template to search for
     * @param config         The search configuration (includes maxResults)
     * @return A list of DTOImageSearchResult objects with all matches found
     */
    private List<DTOImageSearchResult> executeMultipleSearch(String emulatorNumber, EnumTemplates template,
            SearchConfig config) {

        List<DTOImageSearchResult> results;
        if (config.hasArea()) {
            results = emuManager.searchTemplates(emulatorNumber, template,
                    config.getArea().topLeft(), config.getArea().bottomRight(), config.getThreshold(),
                    config.getMaxResults());
        } else if (config.hasCoordinates()) {
            results = emuManager.searchTemplates(emulatorNumber, template,
                    config.getStartPoint(), config.getEndPoint(), config.getThreshold(), config.getMaxResults());
        } else {
            results = emuManager.searchTemplates(emulatorNumber, template, config.getThreshold(),
                    config.getMaxResults());
        }

        if (results != null && !results.isEmpty()) {
            logDebug("Multiple search result: FOUND " + results.size() + " matches");
        } else {
            logDebug("Multiple search result: NOT FOUND");
        }
        return results;
    }

    /**
     * Executes a multiple grayscale template search based on the provided
     * configuration.
     * Supports searching within a specified area, between custom coordinates, or on
     * the entire screen.
     * Both the template and screen are converted to grayscale before matching.
     * 
     * @param emulatorNumber The emulator identifier
     * @param template       The template to search for
     * @param config         The search configuration (includes maxResults)
     * @return A list of DTOImageSearchResult objects with all matches found
     */
    private List<DTOImageSearchResult> executeMultipleSearchGrayscale(String emulatorNumber, EnumTemplates template,
            SearchConfig config) {

        List<DTOImageSearchResult> results;
        if (config.hasArea()) {
            results = emuManager.searchTemplatesGrayscale(emulatorNumber, template,
                    config.getArea().topLeft(), config.getArea().bottomRight(), config.getThreshold(),
                    config.getMaxResults());
        } else if (config.hasCoordinates()) {
            results = emuManager.searchTemplatesGrayscale(emulatorNumber, template,
                    config.getStartPoint(), config.getEndPoint(), config.getThreshold(), config.getMaxResults());
        } else {
            results = emuManager.searchTemplatesGrayscale(emulatorNumber, template, config.getThreshold(),
                    config.getMaxResults());
        }

        return results;
    }

    /**
     * Sleeps for the specified duration.
     * If interrupted, restores the interrupt status.
     * 
     * @param milliseconds The duration to sleep in milliseconds
     */
    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            logWarning("Sleep interrupted, restoring interrupt status");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Configuration class for template search operations.
     * Provides builder pattern for flexible configuration of search parameters.
     * 
     * Supports:
     * - Retry logic with configurable maximum attempts and delay between attempts
     * - Threshold adjustment for template matching sensitivity
     * - Multiple result limits for batch searches
     * - Search area specification via area object or custom coordinates
     */
    public static class SearchConfig {
        private final int maxAttempts;
        private final long delayBetweenAttempts;
        private final int threshold;
        private final int maxResults;
        private final DTOPoint startPoint;
        private final DTOPoint endPoint;
        private final DTOArea area;

        private SearchConfig(Builder builder) {
            this.maxAttempts = builder.maxAttempts;
            this.delayBetweenAttempts = builder.delayBetweenAttempts;
            this.threshold = builder.threshold;
            this.maxResults = builder.maxResults;
            this.startPoint = builder.startPoint;
            this.endPoint = builder.endPoint;
            this.area = builder.area;
        }

        /**
         * Creates a new Builder for constructing SearchConfig instances.
         * 
         * @return A new Builder with default values
         */
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private int maxAttempts = 1;
            private long delayBetweenAttempts = 300;
            private int threshold = 90;
            private int maxResults = 1;
            private DTOPoint startPoint = null;
            private DTOPoint endPoint = null;
            private DTOArea area = null;

            /**
             * Sets the maximum number of search attempts.
             * 
             * @param attempts The maximum number of attempts (default: 1)
             * @return This builder for method chaining
             */
            public Builder withMaxAttempts(int attempts) {
                this.maxAttempts = attempts;
                return this;
            }

            /**
             * Sets the delay between consecutive search attempts.
             * 
             * @param milliseconds The delay in milliseconds (default: 300)
             * @return This builder for method chaining
             */
            public Builder withDelay(long milliseconds) {
                this.delayBetweenAttempts = milliseconds;
                return this;
            }

            /**
             * Sets the threshold for template matching.
             * Higher values require closer matches (0-100, default: 90).
             * 
             * @param threshold The matching threshold percentage (default: 90)
             * @return This builder for method chaining
             */
            public Builder withThreshold(int threshold) {
                this.threshold = threshold;
                return this;
            }

            /**
             * Sets the maximum number of results to return for multiple searches.
             * 
             * @param maxResults The maximum number of results (default: 1)
             * @return This builder for method chaining
             */
            public Builder withMaxResults(int maxResults) {
                this.maxResults = maxResults;
                return this;
            }

            /**
             * Sets a specific area for the search.
             * Clears any previously set coordinates.
             * 
             * @param area The area to search within
             * @return This builder for method chaining
             */
            public Builder withArea(DTOArea area) {
                this.area = area;
                this.startPoint = null;
                this.endPoint = null;
                return this;
            }

            /**
             * Sets custom coordinates for the search area.
             * Clears any previously set area.
             * 
             * @param start The top-left corner coordinate
             * @param end   The bottom-right corner coordinate
             * @return This builder for method chaining
             */
            public Builder withCoordinates(DTOPoint start, DTOPoint end) {
                this.startPoint = start;
                this.endPoint = end;
                this.area = null;
                return this;
            }

            /**
             * Builds and returns the SearchConfig instance with the configured parameters.
             * 
             * @return A new SearchConfig instance
             */
            public SearchConfig build() {
                return new SearchConfig(this);
            }
        }

        // Getters
        /**
         * Gets the maximum number of search attempts.
         * 
         * @return The maximum attempts
         */
        public int getMaxAttempts() {
            return maxAttempts;
        }

        /**
         * Gets the delay between search attempts.
         * 
         * @return The delay in milliseconds
         */
        public long getDelayBetweenAttempts() {
            return delayBetweenAttempts;
        }

        /**
         * Gets the matching threshold.
         * 
         * @return The threshold percentage (0-100)
         */
        public int getThreshold() {
            return threshold;
        }

        /**
         * Gets the maximum number of results.
         * 
         * @return The maximum results for multiple searches
         */
        public int getMaxResults() {
            return maxResults;
        }

        /**
         * Gets the start point of the search area.
         * 
         * @return The start point coordinate, or null if not set
         */
        public DTOPoint getStartPoint() {
            return startPoint;
        }

        /**
         * Gets the end point of the search area.
         * 
         * @return The end point coordinate, or null if not set
         */
        public DTOPoint getEndPoint() {
            return endPoint;
        }

        /**
         * Gets the area to search within.
         * 
         * @return The search area, or null if not set
         */
        public DTOArea getArea() {
            return area;
        }

        /**
         * Checks if custom coordinates have been set for the search area.
         * 
         * @return true if both start and end points are set, false otherwise
         */
        public boolean hasCoordinates() {
            return startPoint != null && endPoint != null;
        }

        /**
         * Checks if an area has been set for the search.
         * 
         * @return true if an area is set, false otherwise
         */
        public boolean hasArea() {
            return area != null;
        }
    }

    // ========================================================================
    // LOGGING METHODS
    // ========================================================================

    @SuppressWarnings("unused")
    private void logInfo(String message) {
        String prefixedMessage = profileName + " - " + message;
        logger.info(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.INFO, HELPER_NAME, profileName, message);
    }

    private void logWarning(String message) {
        String prefixedMessage = profileName + " - " + message;
        logger.warn(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.WARNING, HELPER_NAME, profileName, message);
    }

    @SuppressWarnings("unused")
    private void logError(String message) {
        String prefixedMessage = profileName + " - " + message;
        logger.error(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.ERROR, HELPER_NAME, profileName, message);
    }

    private void logDebug(String message) {
        String prefixedMessage = profileName + " - " + message;
        logger.debug(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.DEBUG, HELPER_NAME, profileName, message);
    }
}
