package anki_telegram_bot.cards;

import org.springframework.stereotype.Component;

@Component
public class DirectFormat implements CardFormat {

    @Override
    public String formatFront(CardData card) {
        return card.getWordWithReading();
    }

    @Override
    public String formatBackHtml(CardData card) {
        return card.getTranslation() + formatExamplesHtml(card);
    }
}
