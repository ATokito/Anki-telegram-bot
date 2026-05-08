package anki_telegram_bot;

import anki_telegram_bot.cards.CardData;
import anki_telegram_bot.cards.CardRenderer;
import anki_telegram_bot.cards.GeminiCardService;
import anki_telegram_bot.export.AnkiConnectExporter;
import anki_telegram_bot.export.ToFileExporter;
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

import java.io.IOException;
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
    private ToFileExporter toFileExporter;
    @Autowired
    private AnkiConnectExporter ankiConnectExporter;

    private final Map<Long, CardData> pendingCards = new ConcurrentHashMap<>();
    private final Map<Long, String> pendingWords = new ConcurrentHashMap<>();
    private final Map<Long, Language> pendingLangs = new ConcurrentHashMap<>();

    @Value("${bot.username}")
    private String botUsername;

    @Value("${bot.token}")
    private String botToken;

    @Value("${bot.allowed.chat.id}")
    private long allowedChatId;

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

            Language lang = detector.detect(text);
            pendingWords.put(chatId, text);
            pendingLangs.put(chatId, lang);

            try {
                sendTranslationKeyboard(chatId, lang);
            } catch (TelegramApiException e) {
                log.error("Failed to send translation keyboard", e);
            }
        }

        if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        }
    }

    private InlineKeyboardMarkup buildKeyboard() {
        InlineKeyboardButton addButton = new InlineKeyboardButton("✓ Добавить");
        addButton.setCallbackData("add");

        InlineKeyboardButton skipButton = new InlineKeyboardButton("✕ Пропустить");
        skipButton.setCallbackData("skip");

        List<InlineKeyboardButton> row = List.of(addButton, skipButton);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        return markup;
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
                            toFileExporter.export(card);
                            ankiConnectExporter.export(card);
                            ankiConnectExporter.sync();
                            pendingCards.remove(chatId);
                            removeKeyboard(chatId, messageId);
                            sendText(chatId, "✓ Карточка сохранена");
                            answerCallback(callback.getId(), "");
                        } catch (IOException e) {
                            sendText(chatId, "Ошибка сохранения");
                        } catch (Exception e) {
                            sendText(chatId, "❌ Ошибка: " + e.getMessage());
                        }
                    }
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

                            DeleteMessage delete = new DeleteMessage();
                            delete.setChatId(chatId);
                            delete.setMessageId(messageId);
                            execute(delete);

                            int loadingMessageId = sendText(chatId, "⏳ Перевод выполняется...").getMessageId();
                            try {
                                CardData card = cardService.generateCard(word, sourceLang, targetLang);
                                pendingCards.put(chatId, card);

                                DeleteMessage deleteLoading = new DeleteMessage();
                                deleteLoading.setChatId(chatId);
                                deleteLoading.setMessageId(loadingMessageId);
                                execute(deleteLoading);

                                String reply = cardRenderer.render(card);
                                SendMessage message = new SendMessage();
                                message.setChatId(chatId);
                                message.setText(reply);
                                message.setReplyMarkup(buildKeyboard());
                                execute(message);
                                pendingWords.remove(chatId);
                                pendingLangs.remove(chatId);
                            } catch (RuntimeException e) {
                                deleteMessage(chatId, loadingMessageId);
                                sendText(chatId, "❌ " + e.getMessage());
                                pendingWords.remove(chatId);
                                pendingLangs.remove(chatId);
                            }
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