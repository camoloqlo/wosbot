package cl.camodev.wosbot.serv.task.helper;

import cl.camodev.ButtonContants;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;

public class NavigationHelper {

    private final TemplateSearchHelper templateSearchHelper;
    private final EmulatorManager emuManager;
    private final String emulatorNumber;

    /* Constructor for NavigationHelper.
     * @param emuManager The emulator manager instance.
     * @param emulatorNumber The identifier for the specific emulator.
     */
    public NavigationHelper(EmulatorManager emuManager, String emulatorNumber) {
        this.emuManager = emuManager;
        this.emulatorNumber = emulatorNumber;
        this.templateSearchHelper = new TemplateSearchHelper(emuManager, emulatorNumber);
    }

    /* Navigates to a specific alliance menu within the game.
     * @param menu The alliance menu to navigate to.
     * @return true if navigation was successful, false otherwise.
     */
    public boolean navigateToAllianceMenu(AllianceMenu menu) {
        emuManager.tapAtRandomPoint(emulatorNumber, ButtonContants.BOTTOM_MENU_ALLIANCE_BUTTON.topLeft(), ButtonContants.BOTTOM_MENU_ALLIANCE_BUTTON.bottomRight());
        EnumTemplates menuTemplate = switch (menu) {
            case WAR -> EnumTemplates.ALLIANCE_WAR_BUTTON;
            case CHESTS -> EnumTemplates.ALLIANCE_CHEST_BUTTON;
            case TERRITORY -> EnumTemplates.ALLIANCE_TERRITORY_BUTTON;
            case SHOP -> EnumTemplates.ALLIANCE_SHOP_BUTTON;
            case TECH -> EnumTemplates.ALLIANCE_TECH_BUTTON;
            case HELP -> EnumTemplates.ALLIANCE_HELP_BUTTON;
            case TRIUMPH -> EnumTemplates.ALLIANCE_TRIUMPH_BUTTON;
        };
        DTOImageSearchResult searchResult = templateSearchHelper.searchTemplate(
                menuTemplate,
                TemplateSearchHelper.SearchConfig.builder()
                        .withThreshold(90)
                        .withMaxAttempts(5)
                        .withDelay(1000)
                        .build()
        );
        if (searchResult.isFound()) {
            emuManager.tapAtRandomPoint(emulatorNumber, searchResult.getPoint(), searchResult.getPoint(), 1, 1000);
            return true;
        }

        return false;
    }

    public enum AllianceMenu {
        WAR, CHESTS, TERRITORY, SHOP, TECH, HELP, TRIUMPH
    }

}

