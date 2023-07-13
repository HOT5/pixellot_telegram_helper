package web.telegram.bot.clicker.constants;

public enum BotMessageEnum {
    WELCOME_MESSAGE(
            "Follow these rules to make the bot work\n" +
                    "1. Press Slice an event\n" +
                    "2. Send a request id\n" +
                    "3. Shortly after send cam00_.m3u8 and cam01_.m3u8\n" +
                    "4. Press upload complete\n" +
                    "5. Select the request client type\n" +
                    "6. Now is time to chill, approximately it will take 1-3 min, based on the event size\n"),
    SLICE_MESSAGE("Send a request id"),
    ATTACH_M3U8_MESSAGE("Please attach cam00_.m3u8 and cam01_.m3u8 and press Upload complete button"),
    NON_COMMAND_MESSAGE("Please select an option from the menu"),
    EXCEPTION_ILLEGAL_MESSAGE("Please send only the .m3u8 files"),
    UNEXPECTED_ERROR_MESSAGE("Unexpected error");

    private final String message;

    BotMessageEnum(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
