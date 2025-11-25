package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper;

import java.time.LocalDateTime;

public class ExpertsRomulusTroopsTask extends DelayedTask {

    public ExpertsRomulusTroopsTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override
    protected void execute() {
        logInfo("Attempting to claim troops from Romulus.");
        try {
            // Opens the left menu on city section
            openLeftMenuCitySection(true);

            String troopType = profile.getConfig(EnumConfigurationKey.EXPERT_ROMULUS_TROOPS_TYPE_STRING, String.class);
            EnumTemplates troopTemplate;
            switch (troopType) {
                case "Infantry":
                    troopTemplate = EnumTemplates.GAME_HOME_SHORTCUTS_INFANTRY;
                    break;
                case "Lancer":
                    troopTemplate = EnumTemplates.GAME_HOME_SHORTCUTS_LANCER;
                    break;
                case "Marksman":
                    troopTemplate = EnumTemplates.GAME_HOME_SHORTCUTS_MARKSMAN;
                    break;
                default:
                    logError("Invalid troop type selected: " + troopType
                            + ". Please check configuration. Task will retry in 1 hour.");
                    this.reschedule(LocalDateTime.now().plusHours(1));
                    return;
            }

            logInfo("Searching for '" + troopType + "' button.");
            DTOImageSearchResult troopChoice = templateSearchHelper.searchTemplate(troopTemplate,
                    SearchConfigConstants.DEFAULT_SINGLE);
            if (troopChoice.isFound()) {
                tapPoint(troopChoice.getPoint());
                sleepTask(1000);
                tapBackButton();
                sleepTask(500);

                boolean claimed = false;
                for (int i = 0; i < 10; i++) {
                    logDebug("Searching for claim button (Attempt " + (i + 1) + "/10).");
                    DTOImageSearchResult claimButton = templateSearchHelper.searchTemplate(
                            EnumTemplates.ROMULUS_CLAIM_TROOPS_BUTTON,
                            TemplateSearchHelper.SearchConfig.builder()
                                    .withMaxAttempts(1)
                                    .withThreshold(80)
                                    .withDelay(300L)
                                    .withCoordinates(new DTOPoint(180, 351), new DTOPoint(443, 600))
                                    .build());
                    if (claimButton.isFound()) {
                        logInfo("Claiming " + troopType + " from Romulus. Rescheduling for next reset.");
                        tapPoint(claimButton.getPoint());
                        sleepTask(1000);
                        LocalDateTime nextReset = UtilTime.getGameReset();
                        this.reschedule(nextReset);
                        claimed = true;
                        break;
                    }
                    sleepTask(300);
                }

                if (!claimed) {
                    logWarning(
                            "Could not find the final claim button after 10 attempts. Assuming already claimed. Rescheduling for next reset.");
                    LocalDateTime nextReset = UtilTime.getGameReset();
                    this.reschedule(nextReset);
                }
            } else {
                logWarning(
                        "Could not find the button for troop type: " + troopType + ". Task will retry in 5 minutes.");
                this.reschedule(LocalDateTime.now().plusMinutes(5));
            }
        } catch (Exception e) {
            logError("An error occurred while trying to claim troops from Romulus. Task will retry in 5 minutes.", e);
            this.reschedule(LocalDateTime.now().plusMinutes(5));
        }
        logInfo("Romulus Troops task finished.");
    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }
}
