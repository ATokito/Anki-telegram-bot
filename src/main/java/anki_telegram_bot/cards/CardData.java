package anki_telegram_bot.cards;

import anki_telegram_bot.Language;
import lombok.Data;

@Data
public class CardData {
    private Language mainLanguage;
    private Language secondLanguage;
    private String word;
    private String reading;
    private String translation;
    private String example;
    private String exampleReading;
    private String exampleTranslation;

    public String getWordWithReading() {
        return reading == null || reading.isBlank()
                ? word
                : word + " (" + reading + ")";
    }
}