package anki_telegram_bot.export;

import anki_telegram_bot.cards.CardData;
import anki_telegram_bot.cards.CardFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.PrintWriter;

@Component
public class ToFileExporter implements CardExporter {

    @Value("${anki.export.path:anki_cards.txt}")
    private String exportPath;

    @Override
    public void save(CardData card, CardFormat format) throws Exception {
        try (PrintWriter writer = new PrintWriter(new FileWriter(exportPath, true))) {
            writer.println(format.formatFront(card) + "\t" + format.formatBackHtml(card));
        }
    }
}
