package web.telegram.bot.clicker.telegram;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.starter.SpringWebhookBot;
import web.telegram.bot.clicker.constants.BotMessageEnum;
import web.telegram.bot.clicker.telegram.handler.MessageHandler;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CorruptedFilesAssistanceBot extends SpringWebhookBot {
    String botPath;
    String botUsername;
    String botToken;

    MessageHandler messageHandler;

    public CorruptedFilesAssistanceBot(SetWebhook setWebhook, MessageHandler messageHandler) {
        super(setWebhook);
        this.messageHandler = messageHandler;
    }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        try {
            return handleUpdate(update);
        } catch (IllegalArgumentException e) {
            return new SendMessage(update.getMessage().getChatId().toString(),
                    BotMessageEnum.EXCEPTION_ILLEGAL_MESSAGE.getMessage());
        } catch (Exception e) {
            return new SendMessage(update.getMessage().getChatId().toString(),
                    BotMessageEnum.UNEXPECTED_ERROR_MESSAGE.getMessage());
        }
    }

    public BotApiMethod<?> handleRequestFrom(Update update){
        Message message = update.getMessage();
        if (message != null) {
            return messageHandler.answerMessage(message);
        }
        return null;
    }

    private BotApiMethod<?> handleUpdate(Update update) {
        Message message = update.getMessage();
        if (message != null) {
            return messageHandler.answerMessage(message);
        }
        return null;
    }
}
