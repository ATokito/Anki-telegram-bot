package anki_telegram_bot.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import anki_telegram_bot.Language;
import anki_telegram_bot.cards.CardData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Component
public class AnkiConnectExporter {

    @Value("${anki.connect.url:http://localhost:8765}")
    private String ankiConnectUrl;

    @Value("${anki.connect.jp:Japanese}")
    private String japaneseDeck;

    @Value("${anki.connect.eng:English}")
    private String englishDeck;

    @Value("${anki.connect.model:Простая}")
    private String modelName;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void export(CardData card) throws Exception {
        String front = card.formatFront();
        String back = card.formatBackHtml();

        String deckName = card.getSecondLanguage() == Language.JAPANESE ? japaneseDeck : englishDeck;

        var requestBody = objectMapper.createObjectNode();
        requestBody.put("action", "addNote");
        requestBody.put("version", 6);

        var params = requestBody.putObject("params");
        var note = params.putObject("note");
        note.put("deckName", deckName);
        note.put("modelName", modelName);

        var fields = note.putObject("fields");
        fields.put("Front", front);
        fields.put("Back", back);

        var options = note.putObject("options");
        options.put("allowDuplicate", false);

        note.putArray("tags");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ankiConnectUrl))
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.debug("AnkiConnect response: {}", response.body());

        JsonNode root = objectMapper.readTree(response.body());
        String error = root.path("error").asText();
        if (!error.equals("null") && !error.isBlank()) {
            throw new RuntimeException(error);
        }
    }

    public void sync() throws Exception {
        var requestBody = objectMapper.createObjectNode();
        requestBody.put("action", "sync");
        requestBody.put("version", 6);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ankiConnectUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.debug("Anki sync response: {}", response.body());
    }
}
