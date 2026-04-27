package com.github.atokito.anki_telegram_bot.cards;

import org.springframework.stereotype.Component;

@Component
public class CardRenderer {

    public String render(CardData data) {
        String wordLine = data.formatFront();

        String exampleLine = data.getExample() +
                (data.getExampleReading() != null && !data.getExampleReading().isBlank()
                        ? "\n" + data.getExampleReading() : "") +
                (data.getExampleTranslation() != null && !data.getExampleTranslation().isBlank()
                        ? "\n" + data.getExampleTranslation() : "");

        return wordLine + "\n" + data.getTranslation() + "\n\n" + exampleLine;
    }
}