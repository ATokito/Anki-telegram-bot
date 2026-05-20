package anki_telegram_bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Slf4j
@Service
public class BotSettingsService {

    @Value("${bot.settings.path:bot-settings.json}")
    private String settingsPath;

    @Autowired
    private ObjectMapper objectMapper;

    private BotSettings settings;

    @PostConstruct
    public void load() {
        File file = new File(settingsPath);
        if (file.exists()) {
            try {
                settings = objectMapper.readValue(file, BotSettings.class);
                log.info("Настройки загружены: cardFormat={}, languageMode={}",
                        settings.getCardFormat(), settings.getLanguageMode());
                return;
            } catch (IOException e) {
                log.warn("Не удалось прочитать файл настроек, используются значения по умолчанию", e);
            }
        }
        settings = new BotSettings();
    }

    public BotSettings get() {
        return settings;
    }

    public void save() {
        try {
            objectMapper.writeValue(new File(settingsPath), settings);
        } catch (IOException e) {
            log.error("Не удалось сохранить настройки", e);
        }
    }
}
