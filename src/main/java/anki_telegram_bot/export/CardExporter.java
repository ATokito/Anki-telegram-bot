package anki_telegram_bot.export;

import anki_telegram_bot.cards.CardData;
import anki_telegram_bot.cards.CardFormat;

public interface CardExporter {
    void save(CardData card, CardFormat format) throws Exception;
}
