package cl.camodev.wosbot.serv.task.constants;

import cl.camodev.wosbot.ot.DTOArea;
import cl.camodev.wosbot.ot.DTOPoint;

/**
 * Common game screen areas and coordinates used across multiple tasks.
 * 
 * <p>
 * This interface provides standardized coordinate definitions for:
 * <ul>
 * <li>Profile and stamina management</li>
 * <li>Left menu navigation</li>
 * <li>March slot checking</li>
 * <li>Alliance operations</li>
 * <li>Rally and flag selection</li>
 * <li>OCR regions for common UI elements</li>
 * </ul>
 * 
 * <p>
 * All coordinates are based on a standard game resolution and should be
 * consistent across all emulator instances.
 * 
 * @author WoS Bot
 */
public interface CommonGameAreas {

        // ========================================================================
        // PROFILE & STAMINA AREAS
        // ========================================================================

        /** Profile avatar button in top-left corner */
        DTOArea PROFILE_AVATAR = new DTOArea(
                        new DTOPoint(24, 24),
                        new DTOPoint(61, 61));

        /** Stamina icon button in profile screen */
        DTOArea STAMINA_BUTTON = new DTOArea(
                        new DTOPoint(223, 1101),
                        new DTOPoint(244, 1123));

        /** OCR region for reading current stamina fraction (e.g., "45/120") */
        DTOArea STAMINA_OCR_AREA = new DTOArea(
                        new DTOPoint(324, 255),
                        new DTOPoint(477, 289));

        /** OCR region for reading spent stamina on deployment screen */
        DTOArea SPENT_STAMINA_OCR_AREA = new DTOArea(
                        new DTOPoint(540, 1215),
                        new DTOPoint(590, 1245));

        // ========================================================================
        // LEFT MENU AREAS
        // ========================================================================

        /** Trigger area for opening the left menu */
        DTOArea LEFT_MENU_TRIGGER = new DTOArea(
                        new DTOPoint(8, 538),
                        new DTOPoint(8, 560));

        /** City tab button in left menu */
        DTOArea LEFT_MENU_CITY_TAB = new DTOArea(
                        new DTOPoint(100, 270),
                        new DTOPoint(120, 270));

        /** Wilderness tab button in left menu */
        DTOArea LEFT_MENU_WILDERNESS_TAB = new DTOArea(
                        new DTOPoint(320, 270),
                        new DTOPoint(340, 270));

        /** Point for closing left menu (tap on city tab area) */
        DTOPoint LEFT_MENU_CLOSE_CITY = new DTOPoint(110, 270);

        /** Point for closing left menu (tap outside menu area) */
        DTOPoint LEFT_MENU_CLOSE_OUTSIDE = new DTOPoint(463, 548);

        // ========================================================================
        // MARCH SLOT COORDINATES
        // ========================================================================

        /**
         * Top-left corners of march status slots in left menu (wilderness tab).
         * Ordered from slot 6 (top) to slot 1 (bottom).
         */
        DTOPoint[] MARCH_SLOTS_TOP_LEFT = {
                        new DTOPoint(189, 740), // March 6
                        new DTOPoint(189, 667), // March 5
                        new DTOPoint(189, 594), // March 4
                        new DTOPoint(189, 521), // March 3
                        new DTOPoint(189, 448), // March 2
                        new DTOPoint(189, 375) // March 1
        };

        /**
         * Bottom-right corners of march status slots in left menu (wilderness tab).
         * Ordered from slot 6 (top) to slot 1 (bottom).
         */
        DTOPoint[] MARCH_SLOTS_BOTTOM_RIGHT = {
                        new DTOPoint(258, 768), // March 6
                        new DTOPoint(258, 695), // March 5
                        new DTOPoint(258, 622), // March 4
                        new DTOPoint(258, 549), // March 3
                        new DTOPoint(258, 476), // March 2
                        new DTOPoint(258, 403) // March 1
        };

        // ========================================================================
        // ALLIANCE AREAS
        // ========================================================================

        /** Alliance button in bottom navigation bar */
        DTOArea BOTTOM_MENU_ALLIANCE_BUTTON = new DTOArea(
                        new DTOPoint(493, 1187),
                        new DTOPoint(561, 1240));

        /** Rally section tab in Alliance War menu */
        DTOArea ALLIANCE_WAR_RALLY_TAB = new DTOArea(
                        new DTOPoint(81, 114),
                        new DTOPoint(195, 152));

        /** Auto-join settings button in Alliance War rally section */
        DTOArea ALLIANCE_AUTOJOIN_MENU_BUTTON = new DTOArea(
                        new DTOPoint(260, 1200),
                        new DTOPoint(450, 1240));

        /** Disable button in auto-join settings popup */
        DTOArea ALLIANCE_AUTOJOIN_DISABLE_BUTTON = new DTOArea(
                        new DTOPoint(120, 1069),
                        new DTOPoint(249, 1122));

        // ========================================================================
        // RALLY & FLAG SELECTION
        // ========================================================================

        /** OCR region for reading "Unlock" text when selecting locked flag */
        DTOArea FLAG_UNLOCK_TEXT_OCR = new DTOArea(
                        new DTOPoint(297, 126),
                        new DTOPoint(424, 168));

        // ========================================================================
        // TRAVEL TIME & DEPLOYMENT
        // ========================================================================

        /** OCR region for reading march travel time on deployment screen */
        DTOArea TRAVEL_TIME_OCR_AREA = new DTOArea(
                        new DTOPoint(521, 1141),
                        new DTOPoint(608, 1162));
}
