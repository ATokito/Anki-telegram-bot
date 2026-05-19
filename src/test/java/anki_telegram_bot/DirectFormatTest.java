package anki_telegram_bot;

import anki_telegram_bot.cards.CardData;
import anki_telegram_bot.cards.DirectFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DirectFormatTest {

    private final DirectFormat format = new DirectFormat();

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
    void frontIncludesReadingWhenPresent() {
        CardData c = card("勉強", "べんきょう", "учёба", "例文", "", "");
        assertEquals("勉強 (べんきょう)", format.formatFront(c));
    }

    @Test
    void frontIsWordOnlyWhenReadingBlank() {
        CardData c = card("study", "", "учёба", "例文", "", "");
        assertEquals("study", format.formatFront(c));
    }

    @Test
    void frontIsWordOnlyWhenReadingNull() {
        CardData c = card("study", null, "учёба", "例文", "", "");
        assertEquals("study", format.formatFront(c));
    }

    @Test
    void backStartsWithTranslation() {
        CardData c = card("勉強", "べんきょう", "учёба", "毎日勉強する", "", "");
        assertTrue(format.formatBackHtml(c).startsWith("учёба"));
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
