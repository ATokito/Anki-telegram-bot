package anki_telegram_bot;

import anki_telegram_bot.cards.CardData;
import anki_telegram_bot.cards.CardFormat;
import anki_telegram_bot.cards.CardRenderer;
import anki_telegram_bot.cards.DirectFormat;
import anki_telegram_bot.cards.GeminiCardService;
import anki_telegram_bot.cards.ReverseFormat;
import anki_telegram_bot.export.CardExporter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WordsToAnkiBot extends TelegramLongPollingBot {

    @Autowired
    private LanguageDetector detector;
    @Autowired
    private GeminiCardService cardService;
    @Autowired
    private CardRenderer cardRenderer;
    @Autowired
    private List<CardExporter> exporters;
    @Autowired
    private DirectFormat directFormat;
    @Autowired
    private ReverseFormat reverseFormat;
    @Autowired
    private BotSettingsService botSettingsService;

    private final Map<Long, CardData> pendingCards = new ConcurrentHashMap<>();
    private final Map<Long, String> pendingWords = new ConcurrentHashMap<>();
    private final Map<Long, Language> pendingLangs = new ConcurrentHashMap<>();

    @Value("${bot.username}")
    private String botUsername;

    @Value("${bot.token}")
    private String botToken;

    @Value("${bot.allowed.chat.id}")
    private long allowedChatId;

    private CardFormat cardFormat() {
        return "direct".equals(botSettingsService.get().getCardFormat()) ? directFormat : reverseFormat;
    }

    private LanguageMode languageMode() {
        try {
            return LanguageMode.valueOf(botSettingsService.get().getLanguageMode());
        } catch (IllegalArgumentException e) {
            return LanguageMode.MULTILINGUAL;
        }
    }

    @PostConstruct
    public void init() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this);
            log.info("Бот зарегистрирован: @{}", botUsername);
        } catch (TelegramApiException e) {
            log.error("Ошибка регистрации бота", e);
        }
    }

    @Override
    public String getBotUsername() { return botUsername; }

    @Override
    public String getBotToken() { return botToken; }

    @Override
    public void onUpdateReceived(Update update) {
        long chatId = update.hasMessage()
                ? update.getMessage().getChatId()
                : update.hasCallbackQuery() ? update.getCallbackQuery().getMessage().getChatId() : -1;

        if (chatId != allowedChatId) return;

        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();

            if (text.startsWith("/start")) {
                try {
                    sendText(chatId, "Привет! Отправь мне слово, и я создам карточку для Anki.");
                } catch (TelegramApiException e) {
                    log.error("Failed to send start message", e);
                }
                return;
            }

            if (text.startsWith("/card")) {
                try {
                    sendCardFormatKeyboard(chatId);
                } catch (TelegramApiException e) {
                    log.error("Failed to send card format keyboard", e);
                }
                return;
            }

            if (text.startsWith("/mode")) {
                try {
                    sendLanguageModeKeyboard(chatId);
                } catch (TelegramApiException e) {
                    log.error("Failed to send language mode keyboard", e);
                }
                return;
            }

            if (text.startsWith("/help")) {
                try {
                    sendText(chatId, "Отправь слово — получи карточку для Anki с переводом и примером.\n\n/card — формат карточки\n/mode — пара языков");
                } catch (TelegramApiException e) {
                    log.error("Failed to send help message", e);
                }
                return;
            }

            Language sourceLang = detector.detect(text);
            Language targetLang = resolveTargetLang(sourceLang, languageMode());

            if (targetLang != null) {
                try {
                    generateAndShowCard(chatId, text, sourceLang, targetLang);
                } catch (TelegramApiException e) {
                    log.error("Failed to generate card", e);
                }
            } else {
                pendingWords.put(chatId, text);
                pendingLangs.put(chatId, sourceLang);
                try {
                    sendTranslationKeyboard(chatId, sourceLang);
                } catch (TelegramApiException e) {
                    log.error("Failed to send translation keyboard", e);
                }
            }
        }

        if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        }
    }

    private Language resolveTargetLang(Language sourceLang, LanguageMode languageMode) {
        return switch (languageMode) {
            case RU_JP -> {
                if (sourceLang == Language.RUSSIAN) yield Language.JAPANESE;
                if (sourceLang == Language.JAPANESE) yield Language.RUSSIAN;
                yield null;
            }
            case RU_EN -> {
                if (sourceLang == Language.RUSSIAN) yield Language.ENGLISH;
                if (sourceLang == Language.ENGLISH) yield Language.RUSSIAN;
                yield null;
            }
            case MULTILINGUAL -> null;
        };
    }

    private void generateAndShowCard(long chatId, String word, Language sourceLang, Language targetLang) throws TelegramApiException {
        int loadingMessageId = sendText(chatId, "⏳ Перевод выполняется...").getMessageId();
        try {
            CardData card = cardService.generateCard(word, sourceLang, targetLang);
            pendingCards.put(chatId, card);
            deleteMessage(chatId, loadingMessageId);
            String reply = cardRenderer.render(card, cardFormat());
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(reply);
            message.setReplyMarkup(buildKeyboard());
            execute(message);
        } catch (RuntimeException e) {
            deleteMessage(chatId, loadingMessageId);
            sendText(chatId, "❌ " + e.getMessage());
        }
    }

    private InlineKeyboardMarkup buildKeyboard() {
        InlineKeyboardButton addButton = new InlineKeyboardButton("✓ Добавить");
        addButton.setCallbackData("add");

        InlineKeyboardButton skipButton = new InlineKeyboardButton("✕ Пропустить");
        skipButton.setCallbackData("skip");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(addButton, skipButton)));
        return markup;
    }

    private void sendCardFormatKeyboard(long chatId) throws TelegramApiException {
        String current = cardFormat() == reverseFormat ? "Основной → Изучаемый" : "Изучаемый → Основной";

        CardData example = new CardData();
        if (languageMode() == LanguageMode.RU_JP) {
            example.setWord("勉強");
            example.setReading("べんきょう");
            example.setTranslation("учёба");
            example.setExample("毎日勉強する");
            example.setExampleReading("まいにちべんきょうする");
            example.setExampleTranslation("Я учусь каждый день");
        } else {
            example.setWord("study");
            example.setReading("ˈstʌdi");
            example.setTranslation("учёба");
            example.setExample("I study every day.");
            example.setExampleReading("");
            example.setExampleTranslation("Я учусь каждый день.");
        }

        String text = "Текущий формат: " + current + "\n\n" +
                "Изучаемый → Основной:\n" +
                "Front: " + directFormat.formatFront(example) + "\n" +
                "Back: " + directFormat.formatBackHtml(example).replace("<br>", "\n") + "\n\n" +
                "Основной → Изучаемый:\n" +
                "Front: " + reverseFormat.formatFront(example) + "\n" +
                "Back: " + reverseFormat.formatBackHtml(example).replace("<br>", "\n");

        InlineKeyboardButton directBtn = new InlineKeyboardButton("Изучаемый → Основной");
        directBtn.setCallbackData("card_direct");

        InlineKeyboardButton reverseBtn = new InlineKeyboardButton("Основной → Изучаемый");
        reverseBtn.setCallbackData("card_reverse");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(directBtn, reverseBtn)));

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setReplyMarkup(markup);
        execute(message);
    }

    private void sendLanguageModeKeyboard(long chatId) throws TelegramApiException {
        String current = switch (languageMode()) {
            case MULTILINGUAL -> "Многоязычный";
            case RU_JP -> "RU ↔ JP";
            case RU_EN -> "RU ↔ EN";
        };

        InlineKeyboardButton multiBtn = new InlineKeyboardButton("🌍 Многоязычный");
        multiBtn.setCallbackData("lang_multilingual");

        InlineKeyboardButton ruJpBtn = new InlineKeyboardButton("RU ↔ JP");
        ruJpBtn.setCallbackData("lang_ru_jp");

        InlineKeyboardButton ruEnBtn = new InlineKeyboardButton("RU ↔ EN");
        ruEnBtn.setCallbackData("lang_ru_en");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(multiBtn, ruJpBtn, ruEnBtn)));

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Текущий режим: " + current + "\n\nВыбери пару языков:");
        message.setReplyMarkup(markup);
        execute(message);
    }

    private void sendTranslationKeyboard(long chatId, Language sourceLang) throws TelegramApiException {
        List<InlineKeyboardButton> buttons = new ArrayList<>();

        if (sourceLang != Language.JAPANESE) {
            InlineKeyboardButton jp = new InlineKeyboardButton("🇯🇵 JP");
            jp.setCallbackData("translate_" + Language.JAPANESE.getCode());
            buttons.add(jp);
        }
        if (sourceLang != Language.RUSSIAN) {
            InlineKeyboardButton ru = new InlineKeyboardButton("🇷🇺 RU");
            ru.setCallbackData("translate_" + Language.RUSSIAN.getCode());
            buttons.add(ru);
        }
        if (sourceLang != Language.ENGLISH) {
            InlineKeyboardButton en = new InlineKeyboardButton("🇬🇧 EN");
            en.setCallbackData("translate_" + Language.ENGLISH.getCode());
            buttons.add(en);
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(buttons));

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Выбери язык перевода:");
        message.setReplyMarkup(markup);
        execute(message);
    }

    private void handleCallback(CallbackQuery callback) {
        String data = callback.getData();
        long chatId = callback.getMessage().getChatId();
        int messageId = callback.getMessage().getMessageId();

        try {
            switch (data) {
                case "add" -> {
                    CardData card = pendingCards.get(chatId);
                    if (card != null) {
                        try {
                            for (CardExporter exporter : exporters) {
                                exporter.save(card, cardFormat());
                            }
                            pendingCards.remove(chatId);
                            removeKeyboard(chatId, messageId);
                            sendText(chatId, "✓ Карточка сохранена");
                            answerCallback(callback.getId(), "");
                        } catch (Exception e) {
                            answerCallback(callback.getId(), "");
                            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                            sendText(chatId, "❌ " + msg);
                        }
                    }
                }
                case "card_direct" -> {
                    botSettingsService.get().setCardFormat("direct");
                    botSettingsService.save();
                    deleteMessage(chatId, messageId);
                    sendText(chatId, "Формат: Изучаемый → Основной");
                    answerCallback(callback.getId(), "");
                }
                case "card_reverse" -> {
                    botSettingsService.get().setCardFormat("reverse");
                    botSettingsService.save();
                    deleteMessage(chatId, messageId);
                    sendText(chatId, "Формат: Основной → Изучаемый");
                    answerCallback(callback.getId(), "");
                }
                case "lang_multilingual" -> {
                    botSettingsService.get().setLanguageMode("MULTILINGUAL");
                    botSettingsService.save();
                    deleteMessage(chatId, messageId);
                    sendText(chatId, "Режим: Многоязычный");
                    answerCallback(callback.getId(), "");
                }
                case "lang_ru_jp" -> {
                    botSettingsService.get().setLanguageMode("RU_JP");
                    botSettingsService.save();
                    deleteMessage(chatId, messageId);
                    sendText(chatId, "Режим: RU ↔ JP");
                    answerCallback(callback.getId(), "");
                }
                case "lang_ru_en" -> {
                    botSettingsService.get().setLanguageMode("RU_EN");
                    botSettingsService.save();
                    deleteMessage(chatId, messageId);
                    sendText(chatId, "Режим: RU ↔ EN");
                    answerCallback(callback.getId(), "");
                }
                case "skip" -> {
                    pendingCards.remove(chatId);
                    removeKeyboard(chatId, messageId);
                    sendText(chatId, "Пропущено");
                    answerCallback(callback.getId(), "");
                }
                default -> {
                    if (data.startsWith("translate_")) {
                        Language targetLang = Language.fromCode(data.replace("translate_", ""));
                        String word = pendingWords.get(chatId);
                        Language sourceLang = pendingLangs.get(chatId);
                        if (word != null) {
                            answerCallback(callback.getId(), "");
                            deleteMessage(chatId, messageId);
                            pendingWords.remove(chatId);
                            pendingLangs.remove(chatId);
                            generateAndShowCard(chatId, word, sourceLang, targetLang);
                        }
                    }
                }
            }
        } catch (TelegramApiException e) {
            log.error("Telegram API error in callback handler", e);
        }
    }

    private void answerCallback(String callbackId, String text) throws TelegramApiException {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackId);
        answer.setText(text);
        execute(answer);
    }

    private org.telegram.telegrambots.meta.api.objects.Message sendText(long chatId, String text) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        return execute(message);
    }

    private void removeKeyboard(long chatId, int msgId) throws TelegramApiException {
        EditMessageReplyMarkup edit = new EditMessageReplyMarkup();
        edit.setChatId(chatId);
        edit.setMessageId(msgId);
        edit.setReplyMarkup(new InlineKeyboardMarkup(List.of()));
        execute(edit);
    }

    private void deleteMessage(long chatId, int msgId) {
        try {
            DeleteMessage delete = new DeleteMessage();
            delete.setChatId(chatId);
            delete.setMessageId(msgId);
            execute(delete);
        } catch (TelegramApiException e) {
            log.warn("Failed to delete message {}", msgId, e);
        }
    }
}
