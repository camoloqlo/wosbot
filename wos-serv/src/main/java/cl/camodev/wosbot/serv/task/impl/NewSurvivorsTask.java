package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;

import java.time.LocalDateTime;

public class NewSurvivorsTask extends DelayedTask {

    public NewSurvivorsTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override
    protected void execute() {
        logInfo("Starting the New Survivors task.");

        // I need to search for New Survivors Template
        logInfo("Searching for the 'New Survivors' notification.");
        DTOImageSearchResult newSurvivors = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_HOME_NEW_SURVIVORS, SearchConfigConstants.DEFAULT_SINGLE);
        if (newSurvivors.isFound()) {
            tapPoint(newSurvivors.getPoint());
            sleepTask(1000);
            // I need to accept the survivors then check if there's empty spots in the
            // buildings
            logInfo("New survivors found. Welcoming them in.");
            DTOImageSearchResult welcomeIn = templateSearchHelper.searchTemplate(
                    EnumTemplates.GAME_HOME_NEW_SURVIVORS_WELCOME_IN, SearchConfigConstants.DEFAULT_SINGLE);
            if (welcomeIn.isFound()) {
                tapPoint(welcomeIn.getPoint());
                logInfo("Waiting briefly before reassigning survivors to buildings.");
                sleepTask(10000);

                tapPoint(new DTOPoint(309, 20));
                sleepTask(300);

                // reset scroll (just in case)
                logInfo("Assigning survivors to available building slots.");
                emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(340, 610), new DTOPoint(340, 900));
                sleepTask(200);

                DTOImageSearchResult plusButton = null;
                while ((plusButton = templateSearchHelper.searchTemplate(
                        EnumTemplates.GAME_HOME_NEW_SURVIVORS_PLUS_BUTTON, SearchConfigConstants.DEFAULT_SINGLE))
                        .isFound()) {
                    emuManager.tapAtPoint(EMULATOR_NUMBER, plusButton.getPoint());
                    sleepTask(50);
                }

                // scroll down a little bit and do the same
                emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(340, 900), new DTOPoint(340, 610));
                sleepTask(200);
                while ((plusButton = templateSearchHelper.searchTemplate(
                        EnumTemplates.GAME_HOME_NEW_SURVIVORS_PLUS_BUTTON, SearchConfigConstants.DEFAULT_SINGLE))
                        .isFound()) {
                    emuManager.tapAtPoint(EMULATOR_NUMBER, plusButton.getPoint());
                    sleepTask(50);
                }

                logInfo("Survivor assignment complete. Rescheduling task.");
                this.reschedule(LocalDateTime.now().plusMinutes(
                        profile.getConfig(EnumConfigurationKey.CITY_ACCEPT_NEW_SURVIVORS_OFFSET_INT, Integer.class)));
            }

        } else {
            logInfo("No new survivors found. Rescheduling task.");
            this.reschedule(LocalDateTime.now().plusMinutes(
                    profile.getConfig(EnumConfigurationKey.CITY_ACCEPT_NEW_SURVIVORS_OFFSET_INT, Integer.class)));

        }

    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }
}
