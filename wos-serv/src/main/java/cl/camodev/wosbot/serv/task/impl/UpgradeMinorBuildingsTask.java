package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;

public class UpgradeMinorBuildingsTask extends DelayedTask {

    public UpgradeMinorBuildingsTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTaskEnum){
        super(profile,tpDailyTaskEnum);
    }

    @Override
    protected void execute() {

        // verify if the queue is busy




    }
}
