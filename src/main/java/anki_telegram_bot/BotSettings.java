package anki_telegram_bot;

public class BotSettings {

    private String cardFormat = "reverse";
    private String languageMode = "MULTILINGUAL";

    public String getCardFormat() { return cardFormat; }
    public void setCardFormat(String cardFormat) { this.cardFormat = cardFormat; }

    public String getLanguageMode() { return languageMode; }
    public void setLanguageMode(String languageMode) { this.languageMode = languageMode; }
}
