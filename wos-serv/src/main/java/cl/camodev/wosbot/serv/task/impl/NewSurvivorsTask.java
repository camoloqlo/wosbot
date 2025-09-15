package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;

import java.time.LocalDateTime;

import cl.camodev.wosbot.serv.task.EnumStartLocation;

public class NewSurvivorsTask extends DelayedTask {


    public NewSurvivorsTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override
    protected void execute() {
        logInfo("Starting the New Survivors task.");

        //I need to search for New Survivors Template
        logInfo("Searching for the 'New Survivors' notification.");
        DTOImageSearchResult newSurvivors = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_NEW_SURVIVORS,  90);
        if (newSurvivors.isFound()) {
            emuManager.tapAtPoint(EMULATOR_NUMBER, newSurvivors.getPoint());
            sleepTask(1000);
            //I need to accept the survivors then check if there's empty spots in the buildings
            logInfo("New survivors found. Welcoming them in.");
            DTOImageSearchResult welcomeIn = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_NEW_SURVIVORS_WELCOME_IN,  90);
            if (welcomeIn.isFound()) {
                emuManager.tapAtPoint(EMULATOR_NUMBER, welcomeIn.getPoint());
                logInfo("Waiting briefly before reassigning survivors to buildings.");
                sleepTask(10000);

                emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(309,20));
                sleepTask(300);

                //reset scroll (just in case)
                logInfo("Assigning survivors to available building slots.");
                emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(340, 610), new DTOPoint(340, 900));
                sleepTask(200);

                DTOImageSearchResult plusButton=null;
                while((plusButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_NEW_SURVIVORS_PLUS_BUTTON,  90)).isFound()){
                    emuManager.tapAtPoint(EMULATOR_NUMBER,plusButton.getPoint());
                    sleepTask(50);
                }

                //scroll down a little bit and do the same
                emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(340, 900),new DTOPoint(340, 610));
                sleepTask(200);
                while((plusButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_NEW_SURVIVORS_PLUS_BUTTON,  90)).isFound()){
                    emuManager.tapAtPoint(EMULATOR_NUMBER,plusButton.getPoint());
                    sleepTask(50);
                }

                logInfo("Survivor assignment complete. Rescheduling task.");
                this.reschedule(LocalDateTime.now().plusMinutes(profile.getConfig(EnumConfigurationKey.CITY_ACCEPT_NEW_SURVIVORS_OFFSET_INT,Integer.class)));
            }


        } else {
            logInfo("No new survivors found. Rescheduling task.");
            this.reschedule(LocalDateTime.now().plusMinutes(profile.getConfig(EnumConfigurationKey.CITY_ACCEPT_NEW_SURVIVORS_OFFSET_INT,Integer.class)));

        }


    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }
}
