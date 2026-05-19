package anki_telegram_bot.cards;

public interface CardFormat {
    String formatFront(CardData card);
    String formatBackHtml(CardData card);

    default String formatExamplesHtml(CardData card) {
        String exRead = card.getExampleReading() != null && !card.getExampleReading().isBlank()
                ? "<br>" + card.getExampleReading() : "";
        String exTrans = card.getExampleTranslation() != null && !card.getExampleTranslation().isBlank()
                ? "<br>" + card.getExampleTranslation() : "";
        return "<br><br>" + card.getExample() + exRead + exTrans;
    }
}
