package anki_telegram_bot;

import anki_telegram_bot.cards.CardData;
import anki_telegram_bot.cards.ReverseFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReverseFormatTest {

    private final ReverseFormat format = new ReverseFormat();

    private CardData card(String word, String reading, String translation,
                          String example, String exampleReading, String exampleTranslation) {
        CardData card = new CardData();
        card.setWord(word);
        card.setReading(reading);
        card.setTranslation(translation);
        card.setExample(example);
        card.setExampleReading(exampleReading);
        card.setExampleTranslation(exampleTranslation);
        return card;
    }

    @Test
    void frontIsTranslation() {
        CardData c = card("勉強", "べんきょう", "учёба", "例文", "", "");
        assertEquals("учёба", format.formatFront(c));
    }

    @Test
    void backStartsWithWordAndReading() {
        CardData c = card("勉強", "べんきょう", "учёба", "毎日勉強する", "", "");
        assertTrue(format.formatBackHtml(c).startsWith("勉強 (べんきょう)"));
    }

    @Test
    void backStartsWithWordOnlyWhenReadingBlank() {
        CardData c = card("study", "", "учёба", "I study", "", "");
        assertTrue(format.formatBackHtml(c).startsWith("study"));
    }

    @Test
    void backStartsWithWordOnlyWhenReadingNull() {
        CardData c = card("study", null, "учёба", "I study", "", "");
        assertTrue(format.formatBackHtml(c).startsWith("study"));
    }

    @Test
    void backIncludesExampleReadingAndTranslation() {
        CardData c = card("勉強", "べんきょう", "учёба", "毎日勉強する", "まいにちべんきょうする", "Я учусь каждый день");
        String back = format.formatBackHtml(c);
        assertTrue(back.contains("まいにちべんきょうする"));
        assertTrue(back.contains("Я учусь каждый день"));
    }

    @Test
    void backSkipsBlankExampleReading() {
        CardData c = card("study", "", "учёба", "I study", "", "Я учусь");
        String back = format.formatBackHtml(c);
        assertFalse(back.contains("<br><br><br>"));
        assertTrue(back.contains("Я учусь"));
    }
}
