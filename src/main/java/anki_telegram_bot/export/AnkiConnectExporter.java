package anki_telegram_bot.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import anki_telegram_bot.Language;
import anki_telegram_bot.cards.CardData;
import anki_telegram_bot.cards.CardFormat;
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
public class AnkiConnectExporter implements CardExporter {

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

    @Override
    public void save(CardData card, CardFormat format) throws Exception {
        String front = format.formatFront(card);
        String back = format.formatBackHtml(card);

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
            if (error.contains("model was not found")) {
                throw new RuntimeException("Тип карточки не найден. Проверь ANKI_MODEL_NAME");
            } else if (error.contains("deck was not found")) {
                throw new RuntimeException("Колода не найдена. Проверь DECK_NAME_JP / DECK_NAME_ENG");
            } else {
                throw new RuntimeException("Не удалось сохранить карточку в Anki");
            }
        }

        trySync();
    }

    private void trySync() {
        try {
            sync();
        } catch (Exception e) {
            log.warn("Anki sync failed (card was saved): {}", e.getMessage());
        }
    }

    private void sync() throws Exception {
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
