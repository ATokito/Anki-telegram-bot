package anki_telegram_bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Slf4j
@Service
public class BotSettingsService {

    @Value("${bot.settings.path:}")
    private String settingsPathOverride;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private BotSettings settings;

    private File settingsFile() {
        if (settingsPathOverride != null && !settingsPathOverride.isBlank()) {
            return new File(settingsPathOverride);
        }
        return new File(System.getProperty("user.home"), ".anki-tg-bot/settings.json");
    }

    @PostConstruct
    public void load() {
        File file = settingsFile();
        log.info("Файл настроек: {}", file.getAbsolutePath());
        if (file.exists()) {
            try {
                settings = objectMapper.readValue(file, BotSettings.class);
                log.info("Настройки загружены: cardFormat={}, languageMode={}",
                        settings.getCardFormat(), settings.getLanguageMode());
                return;
            } catch (IOException e) {
                log.warn("Не удалось прочитать файл настроек, используются значения по умолчанию", e);
            }
        } else {
            log.info("Файл настроек не найден, используются значения по умолчанию");
        }
        settings = new BotSettings();
    }

    public BotSettings get() {
        return settings;
    }

    public void save() {
        File file = settingsFile();
        try {
            file.getParentFile().mkdirs();
            objectMapper.writeValue(file, settings);
            log.info("Настройки сохранены: cardFormat={}, languageMode={}",
                    settings.getCardFormat(), settings.getLanguageMode());
        } catch (IOException e) {
            log.error("Не удалось сохранить настройки в {}", file.getAbsolutePath(), e);
        }
    }
}
