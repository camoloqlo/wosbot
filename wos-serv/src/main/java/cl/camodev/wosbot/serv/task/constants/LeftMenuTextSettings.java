package cl.camodev.wosbot.serv.task.constants;

import cl.camodev.wosbot.ot.DTOTesseractSettings;

import java.awt.*;

public interface LeftMenuTextSettings {

        // OCR Settings for different types of text detection
        DTOTesseractSettings WHITE_SETTINGS = DTOTesseractSettings.builder()
                        .setRemoveBackground(true)
                        .setTextColor(new Color(255, 255, 255))
                        .setReuseLastImage(true)
                        .setAllowedChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
                        .build();

        DTOTesseractSettings WHITE_DURATION = DTOTesseractSettings.builder()
                        .setRemoveBackground(true)
                        .setTextColor(new Color(255, 255, 255))
                        .setReuseLastImage(true)
                        .setAllowedChars("0123456789:d")
                        .build();

        // OCR Settings for different types of text detection
        DTOTesseractSettings GREEN_TEXT_SETTINGS = DTOTesseractSettings.builder()
                        .setRemoveBackground(true)
                        .setTextColor(new Color(0, 193, 0))
                        .setReuseLastImage(true)
                        .setAllowedChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
                        .build();

        DTOTesseractSettings WHITE_NUMBERS = DTOTesseractSettings.builder()
                        .setRemoveBackground(true)
                        .setTextColor(new Color(255, 255, 255))
                        .setReuseLastImage(true)
                        .setAllowedChars("0123456789d")
                        .build();

        DTOTesseractSettings WHITE_ONLY_NUMBERS = DTOTesseractSettings.builder()
                        .setRemoveBackground(true)
                        .setTextColor(new Color(255, 255, 255))
                        .setReuseLastImage(true)
                        .setAllowedChars("0123456789")
                        .build();

        DTOTesseractSettings RED_SETTINGS = DTOTesseractSettings.builder()
                        .setRemoveBackground(true)
                        .setTextColor(new Color(243, 59, 59))
                        .setReuseLastImage(true)
                        .build();

        DTOTesseractSettings ORANGE_SETTINGS = DTOTesseractSettings.builder()
                        .setRemoveBackground(true)
                        .setTextColor(new Color(237, 138, 33))
                        .setReuseLastImage(true)
                        .setAllowedChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
                        .build();

}
