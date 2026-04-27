package com.github.atokito.anki_telegram_bot;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LanguageTest {

    @Test
    void fromCodeKnownValues() {
        assertEquals(Language.JAPANESE, Language.fromCode("JP"));
        assertEquals(Language.RUSSIAN, Language.fromCode("RU"));
        assertEquals(Language.ENGLISH, Language.fromCode("EN"));
    }

    @Test
    void fromCodeUnknownReturnsUnknown() {
        assertEquals(Language.UNKNOWN, Language.fromCode("ZZ"));
        assertEquals(Language.UNKNOWN, Language.fromCode(""));
        assertEquals(Language.UNKNOWN, Language.fromCode(null));
    }

    @Test
    void toRussianNeverReturnsNull() {
        for (Language lang : Language.values()) {
            assertNotNull(lang.toRussian());
        }
    }
}
