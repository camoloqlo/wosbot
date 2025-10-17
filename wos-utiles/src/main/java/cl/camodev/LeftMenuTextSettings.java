package cl.camodev;

import cl.camodev.wosbot.ot.DTOTesseractSettings;

import java.awt.*;

public interface LeftMenuTextSettings {

    // OCR Settings for different types of text detection
    DTOTesseractSettings WHITE_SETTINGS = DTOTesseractSettings.builder()
            .setRemoveBackground(true)
            .setTextColor(new Color(255,255,255))
            .setReuseLastImage(true)
            //.setDebug(true)
            .setAllowedChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
            .build();

    DTOTesseractSettings WHITE_DURATION = DTOTesseractSettings.builder()
            .setRemoveBackground(true)
            .setTextColor(new Color(255,255,255))
            .setReuseLastImage(true)
            //.setDebug(true)
            .setAllowedChars("0123456789:d")
            .build();

    // OCR Settings for different types of text detection
    DTOTesseractSettings GREEN_TEXT_SETTINGS = DTOTesseractSettings.builder()
            .setRemoveBackground(true)
            .setTextColor(new Color(0,193,0))
            .setReuseLastImage(true)
            //.setDebug(true)
            .setAllowedChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
            .build();

    DTOTesseractSettings WHITE_NUMBERS = DTOTesseractSettings.builder()
            .setRemoveBackground(true)
            .setTextColor(new Color(255,255,255))
            .setReuseLastImage(true)
            .setAllowedChars("0123456789d")
            //.setDebug(true)
            .build();

    DTOTesseractSettings WHITE_ONLY_NUMBERS = DTOTesseractSettings.builder()
            .setRemoveBackground(true)
            .setTextColor(new Color(255,255,255))
            .setReuseLastImage(true)
            .setAllowedChars("0123456789")
            //.setDebug(true)
            .build();

    DTOTesseractSettings RED_SETTINGS = DTOTesseractSettings.builder()
            .setRemoveBackground(true)
            .setTextColor(new Color(243,59,59))
            .setReuseLastImage(true)
            //.setDebug(true)
            .build();


    DTOTesseractSettings ORANGE_SETTINGS = DTOTesseractSettings.builder()
            .setRemoveBackground(true)
            .setTextColor(new Color(237, 138, 33))
            .setReuseLastImage(true)
            //.setDebug(true)
            .setAllowedChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
            .build();

}
