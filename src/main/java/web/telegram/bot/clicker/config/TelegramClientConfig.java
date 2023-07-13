package web.telegram.bot.clicker.config;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import web.telegram.bot.clicker.telegram.CorruptedFilesAssistanceBot;
import web.telegram.bot.clicker.telegram.handler.MessageHandler;

@Configuration
@AllArgsConstructor
public class TelegramClientConfig {

    private final TelegramBotProperties telegramBotProperties;

    @Bean
    public SetWebhook setWebhookInstance() {
        return SetWebhook.builder().url(telegramBotProperties.getWebhookPath()).build();
    }

    @Bean
    public CorruptedFilesAssistanceBot springWebhookBot(SetWebhook setWebhook,
                                                        MessageHandler messageHandler) {
        CorruptedFilesAssistanceBot bot = new CorruptedFilesAssistanceBot(setWebhook, messageHandler);

        bot.setBotPath(telegramBotProperties.getWebhookPath());
        bot.setBotUsername(telegramBotProperties.getUsername());
        bot.setBotToken(telegramBotProperties.getToken());

        return bot;
    }
}
