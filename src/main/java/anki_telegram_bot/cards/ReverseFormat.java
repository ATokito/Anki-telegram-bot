package anki_telegram_bot.cards;

import org.springframework.stereotype.Component;

@Component
public class ReverseFormat implements CardFormat {

    @Override
    public String formatFront(CardData card) {
        return card.getTranslation();
    }

    @Override
    public String formatBackHtml(CardData card) {
        return card.getWordWithReading() + formatExamplesHtml(card);
    }
}
