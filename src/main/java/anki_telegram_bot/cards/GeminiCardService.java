package anki_telegram_bot.cards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import anki_telegram_bot.Language;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

@Slf4j
@Service
public class GeminiCardService {

    @Value("${gemini.url}")
    private String geminiUrl;

    @Value("${gemini.model}")
    private String geminiModel;

    @Value("${gemini.api.key}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CardData generateCard(String word, Language sourceLang, Language targetLang) throws RuntimeException {
        try {
            String prompt = buildPrompt(word, sourceLang, targetLang);
            String responseText = callGemini(prompt);
            CardData card = parseResponse(responseText);
            card.setMainLanguage(returnMain(sourceLang, targetLang));
            card.setSecondLanguage(returnSecond(sourceLang, targetLang));
            return card;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate card for word '{}'", word, e);
            throw new RuntimeException("Ошибка генерации карточки");
        }
    }

    private String buildPrompt(String word, Language sourceLang, Language targetLang) {
        Language mainLang = returnMain(sourceLang, targetLang);
        Language secondLang = returnSecond(sourceLang, targetLang);

        boolean jpInvolved = secondLang == Language.JAPANESE;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Задача: Переведи слово \"%s\" с языка %s на %s и представь результат в формате JSON.\n\n",
                word, sourceLang.toRussian(), targetLang.toRussian()));

        sb.append("Структура JSON:\n");
        sb.append("{\n");

        if (jpInvolved) {
            sb.append("  \"word\": \"слово на японском (кандзи/хирагана)\",\n");
            sb.append("  \"reading\": \"чтение японского слова хираганой\",\n");
            sb.append(String.format("  \"translation\": \"слово на %s\",\n", mainLang.toRussian()));
            sb.append("  \"example\": \"одно предложение на японском\",\n");
            sb.append("  \"exampleReading\": \"чтение предложения хираганой\",\n");
        } else {
            sb.append(String.format("  \"word\": \"перевод слова на %s\",\n", secondLang.toRussian()));
            sb.append("  \"reading\": \"транскрипция слова в формате IPA\",\n");
            sb.append(String.format("  \"translation\": \"слово на %s\",\n", mainLang.toRussian()));
            sb.append(String.format("  \"example\": \"одно предложение на %s\",\n", secondLang.toRussian()));
            sb.append("  \"exampleReading\": \"\",\n");
        }

        sb.append(String.format("  \"exampleTranslation\": \"перевод предложения на %s\"\n", mainLang.toRussian()));
        sb.append("}\n\n");

        sb.append("Правила:\n");
        sb.append("- Верни ТОЛЬКО чистый JSON.\n");
        if (jpInvolved) {
            sb.append("- Для японского языка в полях 'word' и 'example' используй только японское письмо (кандзи/кана).\n");
        }

        return sb.toString();
    }
    private Language returnMain (Language sourceLang, Language targetLang) {
        if (sourceLang == Language.RUSSIAN) {
            return sourceLang;
        } else if (targetLang == Language.RUSSIAN) {
            return targetLang;
        } else {
            return Language.ENGLISH;
        }
    }

    private Language returnSecond (Language sourceLang, Language targetLang) {
        if (sourceLang == Language.RUSSIAN) {
            return targetLang;
        } else if (targetLang == Language.RUSSIAN) {
            return sourceLang;
        } else {
            return Language.JAPANESE;
        }
    }

    private String callGemini(String prompt) throws Exception {
        try {
            return doCallGemini(prompt);
        } catch (HttpTimeoutException e) {
            log.warn("Gemini timeout, retrying...");
            return doCallGemini(prompt);
        }
    }

    private String doCallGemini(String prompt) throws Exception {
        String url = geminiUrl + geminiModel + ":generateContent?key=" + apiKey;

        var requestBody = objectMapper.createObjectNode();
        var contents = requestBody.putArray("contents");
        contents.addObject().putArray("parts").addObject().put("text", prompt);

        var genConfig = requestBody.putObject("generationConfig");
        genConfig.put("responseMimeType", "application/json");
        genConfig.put("maxOutputTokens", 1024);
        genConfig.putObject("thinkingConfig").put("thinkingBudget", 512);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.debug("Gemini response: {}", response.body());

        JsonNode root = objectMapper.readTree(response.body());
        if (root.has("error")) {
            String errorCode = root.path("error").path("status").asText();
            if (errorCode.equals("RESOURCE_EXHAUSTED")) {
                throw new RuntimeException("Лимит запросов к Gemini исчерпан. Попробуй позже.");
            }
            throw new RuntimeException("Ошибка Gemini: " + root.path("error").path("message").asText());
        }

        JsonNode candidates = root.path("candidates");
        if (candidates.isMissingNode() || candidates.isEmpty()) {
            throw new RuntimeException("Не удалось получить ответ от Gemini");
        }

        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (parts.isMissingNode() || parts.isEmpty()) {
            throw new RuntimeException("Неожиданный формат ответа Gemini");
        }

        return parts.get(0).path("text").asText();
    }

    private CardData parseResponse(String responseText) throws Exception {
        CardData card = objectMapper.readValue(responseText, CardData.class);
        if (card.getWord() == null || card.getWord().isBlank()
                || card.getTranslation() == null || card.getTranslation().isBlank()) {
            throw new RuntimeException("Gemini вернул неполный ответ");
        }
        return card;
    }

}
