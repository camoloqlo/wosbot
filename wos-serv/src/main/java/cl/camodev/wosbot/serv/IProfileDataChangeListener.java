package cl.camodev.wosbot.serv;

import cl.camodev.wosbot.ot.DTOProfiles;

public interface IProfileDataChangeListener {
    void onProfileDataChanged(DTOProfiles profile);
}
