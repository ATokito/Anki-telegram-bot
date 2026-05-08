package anki_telegram_bot;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LanguageDetectorTest {

    private final LanguageDetector detector = new LanguageDetector();

    @Test
    void detectsJapanese() {
        assertEquals(Language.JAPANESE, detector.detect("勉強"));
    }

    @Test
    void detectsRussian() {
        assertEquals(Language.RUSSIAN, detector.detect("учёба"));
    }

    @Test
    void detectsEnglish() {
        assertEquals(Language.ENGLISH, detector.detect("study"));
    }

    @Test
    void handlesNull() {
        assertEquals(Language.UNKNOWN, detector.detect(null));
    }
}