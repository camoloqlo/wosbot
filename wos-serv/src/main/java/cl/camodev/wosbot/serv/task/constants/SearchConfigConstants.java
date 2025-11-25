package cl.camodev.wosbot.serv.task.constants;

import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper;

/**
 * Interface providing predefined SearchConfig constants for common template
 * search scenarios.
 * These constants encapsulate standard search configurations to promote code
 * reuse
 * and maintain consistency across template search operations.
 *
 * <p>
 * Each constant defines specific behavior for retry logic, matching
 * sensitivity,
 * and timing to suit different search scenarios.
 */
public interface SearchConfigConstants {

        /**
         * Default configuration for single template search.
         * <ul>
         * <li>Attempts: 1</li>
         * <li>Threshold: 90%</li>
         * <li>Delay: 300ms</li>
         * </ul>
         */
        TemplateSearchHelper.SearchConfig DEFAULT_SINGLE = TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(1)
                        .withThreshold(90)
                        .withDelay(300L)
                        .build();

        /**
         * Configuration for single template search with retries.
         * <ul>
         * <li>Attempts: 3</li>
         * <li>Threshold: 90%</li>
         * <li>Delay: 200ms</li>
         * </ul>
         */
        TemplateSearchHelper.SearchConfig SINGLE_WITH_RETRIES = TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(3)
                        .withThreshold(90)
                        .withDelay(200L)
                        .build();

        /**
         * Configuration for single template search with 2 retries.
         * <ul>
         * <li>Attempts: 2</li>
         * <li>Threshold: 90%</li>
         * <li>Delay: 200ms</li>
         * </ul>
         */
        TemplateSearchHelper.SearchConfig SINGLE_WITH_2_RETRIES = TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(2)
                        .withThreshold(90)
                        .withDelay(200L)
                        .build();

        /**
         * Configuration for template search with high sensitivity.
         * Uses lower threshold to match more variations.
         * <ul>
         * <li>Attempts: 3</li>
         * <li>Threshold: 80%</li>
         * <li>Delay: 200ms</li>
         * </ul>
         */
        TemplateSearchHelper.SearchConfig HIGH_SENSITIVITY = TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(3)
                        .withThreshold(80)
                        .withDelay(200L)
                        .build();

        /**
         * Configuration for template search with strict matching.
         * Uses higher threshold for precise matching.
         * <ul>
         * <li>Attempts: 3</li>
         * <li>Threshold: 95%</li>
         * <li>Delay: 200ms</li>
         * </ul>
         */
        TemplateSearchHelper.SearchConfig STRICT_MATCHING = TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(3)
                        .withThreshold(95)
                        .withDelay(200L)
                        .build();

        /**
         * Configuration for multiple template search returning multiple results.
         * <ul>
         * <li>Attempts: 3</li>
         * <li>Threshold: 90%</li>
         * <li>Delay: 200ms</li>
         * <li>Max Results: 3</li>
         * </ul>
         */
        TemplateSearchHelper.SearchConfig MULTIPLE_RESULTS = TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(3)
                        .withThreshold(90)
                        .withDelay(200L)
                        .withMaxResults(3)
                        .build();

        /**
         * Configuration for quick template search with minimal delay.
         * <ul>
         * <li>Attempts: 1</li>
         * <li>Threshold: 90%</li>
         * <li>Delay: 100ms</li>
         * </ul>
         */
        TemplateSearchHelper.SearchConfig QUICK_SEARCH = TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(1)
                        .withThreshold(90)
                        .withDelay(100L)
                        .build();

        /**
         * Configuration for resilient template search with many retries.
         * Suitable for unreliable or dynamic UI elements.
         * <ul>
         * <li>Attempts: 5</li>
         * <li>Threshold: 90%</li>
         * <li>Delay: 300ms</li>
         * </ul>
         */
        TemplateSearchHelper.SearchConfig RESILIENT = TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(5)
                        .withThreshold(90)
                        .withDelay(300L)
                        .build();
}
