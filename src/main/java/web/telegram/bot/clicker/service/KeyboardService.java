package web.telegram.bot.clicker.service;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.Collections;

@Component
public class KeyboardService {

    public ReplyKeyboard defaultKeyboard() {
        KeyboardRow keyboardButtons = new KeyboardRow();
        keyboardButtons.add("Slice an event");
        keyboardButtons.add("Upload complete");

        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setKeyboard(Collections.singletonList(keyboardButtons));
        return replyKeyboardMarkup;
    }

    public ReplyKeyboard deleteKeyboard() {
        ReplyKeyboardRemove replyKeyboardRemove = new ReplyKeyboardRemove();
        replyKeyboardRemove.setRemoveKeyboard(true);

        return replyKeyboardRemove;
    }

    public ReplyKeyboard eventKeyboard() {
        KeyboardRow keyboardButtons = new KeyboardRow();
        keyboardButtons.add("NTT");
        keyboardButtons.add("Regular");

        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setKeyboard(Collections.singletonList(keyboardButtons));
        return replyKeyboardMarkup;
    }

}
