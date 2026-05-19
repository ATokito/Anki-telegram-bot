package anki_telegram_bot.cards;

import org.springframework.stereotype.Component;

@Component
public class CardRenderer {

    public String render(CardData data, CardFormat format) {
        String front = format.formatFront(data);
        String back = format.formatBackHtml(data)
                .replace("<br><br>", "\n\n")
                .replace("<br>", "\n");
        return front + "\n" + back;
    }
}