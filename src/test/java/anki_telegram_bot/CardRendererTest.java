package anki_telegram_bot;

import anki_telegram_bot.cards.CardData;
import anki_telegram_bot.cards.CardRenderer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardRendererTest {

    private final CardRenderer renderer = new CardRenderer();

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
    void rendersWordWithReading() {
        CardData c = card("勉強", "べんきょう", "учёба", "毎日勉強する", "まいにちべんきょうする", "Я учусь каждый день");
        String result = renderer.render(c);
        assertTrue(result.startsWith("勉強 (べんきょう)"));
    }

    @Test
    void rendersWordWithoutReading() {
        CardData c = card("study", null, "учёба", "I study every day", "", "Я учусь каждый день");
        String result = renderer.render(c);
        assertTrue(result.startsWith("study\n"));
    }

    @Test
    void rendersTranslationOnSecondLine() {
        CardData c = card("study", null, "учёба", "I study", null, null);
        String[] lines = renderer.render(c).split("\n");
        assertEquals("учёба", lines[1]);
    }

    @Test
    void rendersExampleWithReadingAndTranslation() {
        CardData c = card("勉強", "べんきょう", "учёба", "毎日勉強する", "まいにちべんきょうする", "Я учусь каждый день");
        String result = renderer.render(c);
        assertTrue(result.contains("毎日勉強する"));
        assertTrue(result.contains("まいにちべんきょうする"));
        assertTrue(result.contains("Я учусь каждый день"));
    }

    @Test
    void skipsBlankExampleReading() {
        CardData c = card("study", "", "учёба", "I study", "", "Я учусь");
        String result = renderer.render(c);
        assertFalse(result.contains("\n\n\n"));
        assertTrue(result.contains("Я учусь"));
    }
}
