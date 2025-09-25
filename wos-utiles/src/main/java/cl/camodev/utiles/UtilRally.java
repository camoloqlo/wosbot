package cl.camodev.utiles;

import cl.camodev.wosbot.ot.DTOPoint;

public class UtilRally {

    public static DTOPoint getMarchFlagPoint(int flagNumber) {
        DTOPoint flagPoint = null;
        switch (flagNumber) {
            case 1: flagPoint = new DTOPoint(70, 120); break;
            case 2: flagPoint = new DTOPoint(140, 120); break;
            case 3: flagPoint = new DTOPoint(210, 120); break;
            case 4: flagPoint = new DTOPoint(280, 120); break;
            case 5: flagPoint = new DTOPoint(350, 120); break;
            case 6: flagPoint = new DTOPoint(420, 120); break;
            case 7: flagPoint = new DTOPoint(490, 120); break;
            case 8: flagPoint = new DTOPoint(560, 120); break;
            default:
                flagPoint = new DTOPoint(70, 120);
                break;
        }
       return flagPoint;
    }
}
