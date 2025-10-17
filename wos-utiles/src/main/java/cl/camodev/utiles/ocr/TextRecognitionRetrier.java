package cl.camodev.utiles.ocr;

import cl.camodev.wosbot.ot.DTOArea;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOTesseractSettings;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic helper class that performs OCR on a region with retry logic. It uses a supplied
 * {@link TextRecognitionProvider} to fetch text, tests the text with a user‑supplied predicate to
 * determine success, and converts the text to an arbitrary return type on success.
 *
 * @param <T> the type the recognized text should be converted to upon success
 */
public class TextRecognitionRetrier<T> {

    private final TextRecognitionProvider textRecognitionProvider;
    private final Logger logger;

    /**
     * Constructs a new retry helper.
     *
     * @param textRecognitionProvider the provider used to perform OCR; must not be {@code null}
     */
    public TextRecognitionRetrier(TextRecognitionProvider textRecognitionProvider) {
        this.textRecognitionProvider = Objects.requireNonNull(textRecognitionProvider, "ocrProvider");
        this.logger = LoggerFactory.getLogger(TextRecognitionRetrier.class);
    }

    /**
     * Attempts to read text from the region defined by {@code p1} and {@code p2} using the
     * provided {@code settings}. The attempt is repeated up to {@code maxRetries} times
     * with a delay between attempts. Once the {@code successPredicate} returns {@code true}
     * for the recognized text, the text is passed through {@code converter} and returned.
     * If no attempt succeeds, {@code null} is returned.
     *
     * @param p1              top‑left corner of the region to capture
     * @param p2              bottom‑right corner of the region to capture
     * @param maxRetries      maximum number of OCR attempts
     * @param delayMs         delay in milliseconds between attempts
     * @param settings        optional Tesseract settings for the OCR engine
     * @param successPredicate predicate to determine whether the recognized text
     *                        constitutes a successful read
     * @param converter       function to convert the recognized text into the return type {@code T}
     * @return the converted value on success, or {@code null} if all attempts fail
     */
    public T execute(DTOPoint p1,
                     DTOPoint p2,
                     int maxRetries,
                     long delayMs,
                     DTOTesseractSettings settings,
                     Predicate<String> successPredicate,
                     Function<String, T> converter) {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            logger.debug("Performing OCR (attempt {} of {})", attempt + 1, maxRetries);
            try {
                String raw = textRecognitionProvider.ocrRegion(p1, p2, settings);
                if (raw != null && successPredicate.test(raw)) {
                    return converter.apply(raw);
                }
            } catch (IOException | TesseractException | RuntimeException e) {
                logger.warn("OCR attempt {} threw an exception: {}", attempt + 1, e.getMessage());
            }

            if (attempt < maxRetries - 1) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    public T execute(DTOArea area,
                     int maxRetries,
                     long delayMs,
                     DTOTesseractSettings settings,
                     Predicate<String> successPredicate,
                     Function<String, T> converter) {
        return execute(area.topLeft(), area.bottomRight(), maxRetries, delayMs, settings, successPredicate, converter);
    }
}