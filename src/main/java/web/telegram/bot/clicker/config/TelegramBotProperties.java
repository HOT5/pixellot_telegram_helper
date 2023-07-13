package web.telegram.bot.clicker.config;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@RequiredArgsConstructor
@Configuration
@ConfigurationProperties(prefix = "telegram-bot")
public class TelegramBotProperties {
    private String username;
    private String token;
    private String webhookPath;
    private String apiUrl;
}
