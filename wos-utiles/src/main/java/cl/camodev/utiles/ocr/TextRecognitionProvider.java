package cl.camodev.utiles.ocr;

import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import java.io.IOException;
import net.sourceforge.tess4j.TesseractException;

/**
 * An abstraction over OCR sources. Implementations can call an emulator, a screenshot,
 * or any other mechanism to perform optical character recognition on a given screen region.
 * The provider hides the underlying transport)
 * and exposes a simple method returning the recognized text.
 */
public interface TextRecognitionProvider {

    /**
     * Performs OCR on the region defined by the given points using the provided settings.
     *
     * @param p1      the first corner (top‑left) of the region to capture
     * @param p2      the second corner (bottom‑right) of the region to capture
     * @param settings optional Tesseract configuration (may be {@code null})
     * @return the recognized text, or {@code null} if no text could be recognized
     * @throws IOException         if an image capture or file I/O error occurs
     * @throws TesseractException  if the underlying OCR engine fails
     */
    String ocrRegion(DTOPoint p1, DTOPoint p2, DTOTesseractSettings settings) throws IOException, TesseractException;
}