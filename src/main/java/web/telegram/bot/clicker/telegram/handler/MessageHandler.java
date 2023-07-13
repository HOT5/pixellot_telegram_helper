package web.telegram.bot.clicker.telegram.handler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import web.telegram.bot.clicker.constants.BotMessageEnum;
import web.telegram.bot.clicker.model.UserActivityState;
import web.telegram.bot.clicker.service.UserActivityService;
import web.telegram.bot.clicker.telegram.TelegramApiClient;
import web.telegram.bot.clicker.telegram.keyboards.ReplyKeyboardMaker;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class MessageHandler {
    DocumentHandlerService documentHandlerService;
    UserActivityService userActivityService;

    TelegramApiClient telegramApiClient;
    ReplyKeyboardMaker replyKeyboardMaker;

    public BotApiMethod<?> answerMessage(Message message) {
        Long userId = message.getFrom().getId();
        String chatId = message.getChatId().toString();

        log.info("Process started by user: id - " + userId + " name - " + message.getFrom().getFirstName() + " " + message.getFrom().getLastName());

        if (message.hasDocument()) {
            documentHandlerService.handleDocument(message);
        }

        String inputText = message.getText();

        if (inputText == null) {
            throw new IllegalArgumentException();
        } else if (inputText.equalsIgnoreCase("/start")) {
            return getStartMessage(chatId);
        } else if (message.getText().equalsIgnoreCase("Slice an event")) {
            if (userActivityService.checkIfUserActivityBegan(userId)) {
                userActivityService.removeUserFromActivityMap(userId);
            }
            userActivityService.createNewUserActivity(userId);
            return null;
        } else if (validateRequestId(message.getText()) && userActivityService.checkIfUserActivityBegan(userId)) {
            if (userActivityService.checkIfUserStateIsEqualsTo(userId, UserActivityState.NO_USER_ACTIVITY)) {
                userActivityService.setUserActivity(userId, UserActivityState.REQUEST_ID_PROVIDED);
                userActivityService.setRequestId(userId, message.getText());

                return new SendMessage(chatId, BotMessageEnum.ATTACH_M3U8_MESSAGE.getMessage());
            }
        } else if (message.getText().equalsIgnoreCase("Upload complete") &&
                userActivityService.checkIfUserActivityBegan(userId)) {
            userActivityService.setUserActivity(userId, UserActivityState.M3U8_FILES_UPLOADED);

            int minSize = Math.min(userActivityService.getCam00TsFilesSize(userId), userActivityService.getCam01TsFilesSize(userId));
            return new SendMessage(chatId, "How many frames you want to get? Max number for request: " + userActivityService.getRequestId(userId) + " is - " + minSize);
        } else if (userActivityService.checkIfUserActivityBegan(userId) &&
                userActivityService.checkIfUserStateIsEqualsTo(userId, UserActivityState.M3U8_FILES_UPLOADED)) {
            try {
                int numberOfFrames = Integer.parseInt(message.getText());
                if (numberOfFrames > Math.min(userActivityService.getCam00TsFilesSize(userId), userActivityService.getCam01TsFilesSize(userId))) {
                    throw new IllegalArgumentException();
                }

                userActivityService.setDesireNumberOfFrames(userId, numberOfFrames);
                userActivityService.setUserActivity(userId, UserActivityState.DESIRE_NUMBER_PROVIDED);

                return getEventMessage(chatId);
            } catch (IllegalArgumentException e) {
                return new SendMessage(chatId, "Please provide the valid number");
            }
        } else if ((message.getText().equalsIgnoreCase("NTT") ||
                message.getText().equalsIgnoreCase("Regular")) &&
                userActivityService.checkIfUserActivityBegan(userId) &&
                userActivityService.checkIfUserStateIsEqualsTo(userId, UserActivityState.DESIRE_NUMBER_PROVIDED)) {

            return new SendMessage(chatId, "The process is started for the request: " + userActivityService.getRequestId(userId) +
                    "\n\r You will receive the first images shortly");

        } else {
            return new SendMessage(chatId, BotMessageEnum.NON_COMMAND_MESSAGE.getMessage());
        }

        return null;
    }

    private SendMessage getStartMessage (String chatId){
        SendMessage sendMessage = new SendMessage(chatId, BotMessageEnum.WELCOME_MESSAGE.getMessage());
        sendMessage.enableMarkdown(true);
        sendMessage.setReplyMarkup(replyKeyboardMaker.getMainMenuKeyboard());
        return sendMessage;
    }

    private SendMessage getEventMessage (String chatId){
        SendMessage sendMessage = new SendMessage(chatId, "Please select client type");
        sendMessage.setReplyMarkup(replyKeyboardMaker.getEventTypeKeyboard());

        return sendMessage;
    }


    private boolean validateRequestId (String requestId){
        String pattern = "^.{24}$";
        Pattern regexPattern = Pattern.compile(pattern);
        Matcher matcher = regexPattern.matcher(requestId);

        return matcher.matches();
    }
}
