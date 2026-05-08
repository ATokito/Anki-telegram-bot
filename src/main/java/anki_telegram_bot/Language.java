package anki_telegram_bot;

public enum Language {
    JAPANESE("JP"),
    RUSSIAN("RU"),
    ENGLISH("EN"),
    UNKNOWN("UNKNOWN");

    private final String code;

    Language(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static Language fromCode(String code) {
        if (code == null) return UNKNOWN;
        for (Language lang : values()) {
            if (lang.code.equals(code)) return lang;
        }
        return UNKNOWN;
    }

    public String toRussian() {
        return switch (this) {
            case JAPANESE -> "японский";
            case ENGLISH -> "английский";
            case RUSSIAN -> "русский";
            case UNKNOWN -> "неизвестный";
        };
    }
}