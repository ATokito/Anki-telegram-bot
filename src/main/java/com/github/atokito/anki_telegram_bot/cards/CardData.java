package com.github.atokito.anki_telegram_bot.cards;

import com.github.atokito.anki_telegram_bot.Language;
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

    public String formatFront() {
        return reading == null || reading.isBlank()
                ? word
                : word + " (" + reading + ")";
    }

    public String formatBackHtml() {
        String exRead = exampleReading != null && !exampleReading.isBlank()
                ? "<br>" + exampleReading : "";
        String exTrans = exampleTranslation != null && !exampleTranslation.isBlank()
                ? "<br>" + exampleTranslation : "";
        return translation + "<br><br>" + example + exRead + exTrans;
    }
}