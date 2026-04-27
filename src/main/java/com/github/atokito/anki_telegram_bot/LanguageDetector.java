package com.github.atokito.anki_telegram_bot;

import org.springframework.stereotype.Component;

@Component
public class LanguageDetector {

    public Language detect(String word) {
        if (word == null || word.isBlank()) return Language.UNKNOWN;

        for (char c : word.toCharArray()) {
            if (isJapanese(c)) return Language.JAPANESE;
        }
        for (char c : word.toCharArray()) {
            if (isRussian(c)) return Language.RUSSIAN;
        }
        return Language.ENGLISH;
    }

    private boolean isJapanese(char c) {
        return (c >= '\u3040' && c <= '\u309F') || // хирагана
                (c >= '\u30A0' && c <= '\u30FF') || // катакана
                (c >= '\u4E00' && c <= '\u9FFF');   // кандзи
    }

    private boolean isRussian(char c) {
        return c >= '\u0400' && c <= '\u04FF';
    }
}