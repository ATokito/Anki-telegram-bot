package anki_telegram_bot.export;

import anki_telegram_bot.cards.CardData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

@Component
public class ToFileExporter {

    @Value("${anki.export.path:anki_cards.txt}")
    private String exportPath;

    public void export(CardData card) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(exportPath, true))) {
            writer.println(card.formatFront() + "\t" + card.formatBackHtml());
        }
    }
}
