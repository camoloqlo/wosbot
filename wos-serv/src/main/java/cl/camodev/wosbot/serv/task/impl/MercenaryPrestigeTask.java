

package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.DelayedTask;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MercenaryPrestigeTask extends DelayedTask {

    public MercenaryPrestigeTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
    }

    @Override
    protected void execute() {
        if (!profile.getConfig(EnumConfigurationKey.MERCENARY_EVENT_BOOL, Boolean.class)) {
            logInfo("Mercenary Prestige is disabled in config.");
            return;
        }
        // Step 1: Click Merc.png, Merc1.png oder Merc2.png (ODER-Verknüpfung) with retry mechanism
        boolean found = false;
        EnumTemplates[] mercTemplates = new EnumTemplates[] {
            EnumTemplates.MERCENARY_MERC, // Merc.png
            EnumTemplates.MERCENARY_MERC1, // Merc1.png
            EnumTemplates.MERCENARY_MERC2  // Merc2.png
        };

        for (int attempt = 0; attempt < 5 && !found; attempt++) {
            for (EnumTemplates t : mercTemplates) {
                logDebug("Searching for Mercenary template '" + t + "', attempt " + (attempt + 1) + ".");
                DTOImageSearchResult result = emuManager.searchTemplate(EMULATOR_NUMBER, t, 0.8);
                if (result != null && result.isFound()) {
                    // Klicke exakt auf die erkannte Position
                    emuManager.tapAtPoint(EMULATOR_NUMBER, result.getPoint());
                    found = true;
                    break;
                }
            }
            if (!found && attempt < 4) {
                sleepTask(300);
            }
        }
        if (!found) {
            logWarning("Keines der Mercenary-Start-Templates (Merc.png, Merc1.png, Merc2.png) gefunden nach 5 Versuchen!");
            return; // Exit early if Step 1 failed
        }
        sleepTask(2500);
        // Step 2: Click 2.png
        clickTemplate(EnumTemplates.MERCENARY_2);
        sleepTask(2500);
        // Step 3: Click 3.png
        clickTemplate(EnumTemplates.MERCENARY_3);
        sleepTask(2500);
    // Step 4: Drag 4.png to 4drop.png (auskommentiert zum Testen)
    // dragTemplate(EnumTemplates.MERCENARY_4, EnumTemplates.MERCENARY_4DROP);
    // sleepTask(1000);
        // OCR: Read cooldown time after dragDrop, with retries and normalization
        String cooldownText = "";
        int seconds = -1;
        int maxOcrAttempts = 5;
        for (int attempt = 1; attempt <= maxOcrAttempts; attempt++) {
            cooldownText = ocrBetweenTemplates(EnumTemplates.MERCENARY_4, EnumTemplates.MERCENARY_4DROP);
            if (cooldownText != null) cooldownText = cooldownText.trim().replace("O", "0").replace("l", "1").replace("I", "1").replace("|", "1").replace("\n", "").replace("\r", "");
            seconds = parseCooldown(cooldownText);
            if (seconds > 0) break;
            sleepTask(1000);
        }
        if (seconds > 0) {
            int adjustedSeconds = seconds * 2 + 2;
            logInfo("Rescheduling MercenaryPrestigeTask in " + adjustedSeconds + " seconds (original: " + seconds + ")");
            // Step 5: Click 5.png only if cooldown is detected
            clickTemplate(EnumTemplates.MERCENARY_5);
            sleepTask(500);
            reschedule(LocalDateTime.now().plusSeconds(adjustedSeconds));
        } else {
            logWarning("Could not parse cooldown after " + maxOcrAttempts + " attempts, defaulting to 10 minutes. Last text: '" + cooldownText + "'");
            reschedule(LocalDateTime.now().plusMinutes(10));
        }
    }

    private void clickTemplate(EnumTemplates template) {
        boolean found = false;
        for (int attempt = 0; attempt < 5; attempt++) {
            logDebug("Searching for template '" + template + "', attempt " + (attempt + 1) + ".");
            DTOImageSearchResult result = emuManager.searchTemplate(EMULATOR_NUMBER, template, 0.8);
            if (result != null && result.isFound()) {
                emuManager.tapAtPoint(EMULATOR_NUMBER, result.getPoint());
                found = true;
                break;
            }
            sleepTask(300);
        }
        if (!found) {
            logWarning("Template not found after 5 attempts: " + template.name());
        }
    }

    private void dragTemplate(EnumTemplates from, EnumTemplates to) {
        DTOImageSearchResult fromResult = emuManager.searchTemplate(EMULATOR_NUMBER, from, 0.8);
        DTOImageSearchResult toResult = emuManager.searchTemplate(EMULATOR_NUMBER, to, 0.8);
        if (fromResult != null && fromResult.isFound() && toResult != null && toResult.isFound()) {
            emuManager.executeSwipe(EMULATOR_NUMBER, fromResult.getPoint(), toResult.getPoint());
        } else {
            logWarning("Drag templates not found: " + from.name() + " or " + to.name());
        }
    }

    private String ocrBetweenTemplates(EnumTemplates from, EnumTemplates to) {
        try {
            // Use fixed region determined by adb getevent drag
            int x1 = 488;
            int y1 = 1128;
            int x2 = 604;
            int y2 = 1179;
            return emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(x1, y1), new DTOPoint(x2, y2));
        } catch (Exception e) {
            logError("OCR failed: " + e.getMessage(), e);
        }
        return "";
    }

    private int parseCooldown(String text) {
        // Accepts formats like 00:12:34 or 12:34
        Pattern p = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");
        Matcher m = p.matcher(text);
        if (m.find()) {
            int h = 0, mnt = 0, s = 0;
            if (m.group(3) != null) {
                h = Integer.parseInt(m.group(1));
                mnt = Integer.parseInt(m.group(2));
                s = Integer.parseInt(m.group(3));
            } else {
                mnt = Integer.parseInt(m.group(1));
                s = Integer.parseInt(m.group(2));
            }
            return h * 3600 + mnt * 60 + s;
        }
        return -1;
    }
}
