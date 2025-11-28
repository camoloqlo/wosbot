package cl.camodev.wosbot.serv.task.constants;

import cl.camodev.wosbot.ot.DTOTesseractSettings;
import java.awt.Color;
import java.util.regex.Pattern;

/**
 * Common OCR settings and patterns used across multiple tasks.
 * 
 * <p>
 * This interface provides standardized Tesseract configurations for:
 * <ul>
 * <li>Stamina reading (fraction format)</li>
 * <li>Spent stamina reading (single numbers)</li>
 * <li>Travel time reading (time format)</li>
 * <li>Common regex patterns for number extraction</li>
 * </ul>
 * 
 * <p>
 * All settings are optimized for the game's specific text rendering
 * and should provide reliable OCR results across different screen states.
 * 
 * @author WoS Bot
 * @see DTOTesseractSettings
 */
public interface CommonOCRSettings {

        // ========================================================================
        // STAMINA OCR SETTINGS
        // ========================================================================

        /**
         * OCR settings for reading stamina in fraction format (e.g., "45/120").
         * 
         * <p>
         * Configuration:
         * <ul>
         * <li>White text on various backgrounds</li>
         * <li>Allows digits and forward slash</li>
         * <li>Background removal enabled</li>
         * <li>LSTM engine for better accuracy</li>
         * </ul>
         */
        DTOTesseractSettings STAMINA_FRACTION_SETTINGS = DTOTesseractSettings.builder()
                        .setAllowedChars("0123456789/")
                        .setRemoveBackground(true)
                        .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                        .setTextColor(new Color(255, 255, 255))
                        .build();

        /**
         * OCR settings for reading spent stamina on deployment screen.
         * 
         * <p>
         * Configuration:
         * <ul>
         * <li>White text (254, 254, 254)</li>
         * <li>Single line mode</li>
         * <li>Numbers only</li>
         * <li>Background removal enabled</li>
         * </ul>
         */
        DTOTesseractSettings SPENT_STAMINA_SETTINGS = DTOTesseractSettings.builder()
                        .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                        .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                        .setRemoveBackground(true)
                        .setTextColor(new Color(254, 254, 254))
                        .setAllowedChars("0123456789")
                        .build();

        // ========================================================================
        // TRAVEL TIME OCR SETTINGS
        // ========================================================================

        /**
         * OCR settings for reading march travel time (e.g., "12:34:56").
         * 
         * <p>
         * Configuration:
         * <ul>
         * <li>Single line mode</li>
         * <li>Numbers and colons only</li>
         * <li>LSTM engine</li>
         * </ul>
         */
        DTOTesseractSettings TRAVEL_TIME_SETTINGS = DTOTesseractSettings.builder()
                        .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                        .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                        .setAllowedChars("0123456789:")
                        .build();

        // ========================================================================
        // COMMON REGEX PATTERNS
        // ========================================================================

        /**
         * Pattern for extracting any number from text.
         * Matches one or more digits anywhere in the string.
         * 
         * <p>
         * Example matches: "45", "Score: 1234", "Level 99"
         */
        Pattern NUMBER_PATTERN = Pattern.compile(".*?(\\d+).*");
}
