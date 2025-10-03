package cl.camodev.wosbot.serv.ocr;

import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.utiles.ocr.TextRecognitionProvider;
import java.io.IOException;
import net.sourceforge.tess4j.TesseractException;

/**
 * Implementation of {@link TextRecognitionProvider} that delegates to an {@link EmulatorManager}.
 * It encapsulates the emulator number and forwards OCR requests with or without
 * Tesseract settings.
 */
public class BotTextRecognitionProvider implements TextRecognitionProvider {

    private final EmulatorManager emulatorManager;
    private final String emulatorNumber;

    /**
     * Creates a new provider wrapping the given {@link EmulatorManager}.
     *
     * @param emulatorManager  the emulator manager to delegate calls to
     * @param emulatorNumber   identifier of the emulator instance
     */
    public BotTextRecognitionProvider(EmulatorManager emulatorManager, String emulatorNumber) {
        this.emulatorManager = emulatorManager;
        this.emulatorNumber = emulatorNumber;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String ocrRegion(DTOPoint p1, DTOPoint p2, DTOTesseractSettings settings) throws IOException, TesseractException {
        if (settings != null) {
            return emulatorManager.ocrRegionText(emulatorNumber, p1, p2, settings);
        } else {
            return emulatorManager.ocrRegionText(emulatorNumber, p1, p2);
        }
    }

}