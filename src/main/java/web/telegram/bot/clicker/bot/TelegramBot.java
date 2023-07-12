package web.telegram.bot.clicker.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Audio;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import web.telegram.bot.clicker.config.TelegramBotProperties;
import web.telegram.bot.clicker.model.UserActivity;
import web.telegram.bot.clicker.model.UserActivityState;
import web.telegram.bot.clicker.service.KeyboardService;
import web.telegram.bot.clicker.service.M3U8Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {

    private final TelegramBotProperties botProperties;
    private final KeyboardService keyboardService;
    private final M3U8Service m3U8Service;
    private static final int BATCH_SIZE = 15;

    private static final int THREAD_POOL_SIZE = 8;
    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    private final Map<Long, UserActivity> activityMap = new ConcurrentHashMap<>();

    @Override
    public String getBotUsername() {
        return botProperties.getUsername();
    }

    @Override
    public String getBotToken() {
        return botProperties.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            log.info("Process started by user: id - " + update.getMessage().getFrom().getId() + " name - " + update.getMessage().getFrom().getFirstName() + " " + update.getMessage().getFrom().getLastName());
            Message message = update.getMessage();
            if (message.getText().equalsIgnoreCase("/start")) {
                sendWelcomeMessage(message.getChatId());

            }
            if (message.getText().equalsIgnoreCase("Slice an event")) {
                if (activityMap.containsKey(message.getFrom().getId())) {
                    UserActivity deletedActivity = activityMap.remove(message.getFrom().getId());
                }

                UserActivity userActivity = new UserActivity();
                userActivity.setUserId(message.getFrom().getId());
                userActivity.setState(UserActivityState.NO_USER_ACTIVITY);
                activityMap.put(message.getFrom().getId(), userActivity);
                sendKeyboardWithMessage(message.getChatId(), "Send a request id");
            }
            if (validateRequestId(message.getText()) && checkIfUserActivityBegan(message)) {
                UserActivity userActivity = activityMap.get(message.getFrom().getId());
                if (userActivity.getState() == UserActivityState.NO_USER_ACTIVITY) {
                    userActivity.setState(UserActivityState.REQUEST_ID_PROVIDED);
                    userActivity.setRequestId(message.getText());
                    sendKeyboardWithMessage(message.getChatId(), "Please attach cam00_.m3u8 and cam01_.m3u8 and press Upload complete button");
                }
            }
            if (message.getText().equalsIgnoreCase("Upload complete") && checkIfUserActivityBegan(message)) {
                UserActivity userActivity = activityMap.get(message.getFrom().getId());
                userActivity.setState(UserActivityState.M3U8_FILES_UPLOADED);

                int minSize = Math.min(userActivity.getCam00TsFiles().size(), userActivity.getCam01TsFiles().size());
                sendKeyboardWithMessage(message.getChatId(), "How many frames you want to get? Max number for request: " + userActivity.getRequestId() + " is - " + minSize);
            }
            if (checkIfUserActivityBegan(message) && activityMap.containsKey(message.getFrom().getId())
                    && activityMap.get(message.getFrom().getId()).getState() == UserActivityState.M3U8_FILES_UPLOADED) {

                try {
                    int numberOfFrames = Integer.parseInt(message.getText());
                    UserActivity userActivity = activityMap.get(message.getFrom().getId());
                    if (numberOfFrames > Math.min(userActivity.getCam00TsFiles().size(), userActivity.getCam01TsFiles().size())) {
                        throw new IllegalArgumentException();
                    }

                    userActivity.setDesireNumberOfFrames(numberOfFrames);
                    userActivity.setState(UserActivityState.DESIRE_NUMBER_PROVIDED);
                    sendMessageWithEventKeyBoard(message.getChatId());
                } catch (IllegalArgumentException e) {
                    sendKeyboardWithMessage(message.getChatId(), "Please provide the valid number");
                }
            }
            if ((message.getText().equalsIgnoreCase("NTT") || message.getText().equalsIgnoreCase("Regular"))
                    && checkIfUserActivityBegan(message)) {
                UserActivity userActivity = activityMap.get(message.getFrom().getId());
                if (userActivity.getState() == UserActivityState.DESIRE_NUMBER_PROVIDED) {
                    sendKeyboardWithMessage(message.getChatId(), "The process is started for the request: " + userActivity.getRequestId() +
                            "\n\r You will receive the first images shortly");
                    List<String> cam00TsFiles = userActivity.getCam00TsFiles();
                    List<String> cam01TsFiles = userActivity.getCam01TsFiles();

                    int batchSize = 15; // Set the batch size

                    // Zip the files into pairs
                    List<List<String>> pairs = IntStream.range(0, userActivity.getDesireNumberOfFrames())
                            .mapToObj(i -> Arrays.asList(cam00TsFiles.get(i), cam01TsFiles.get(i)))
                            .collect(Collectors.toList());

                    // Process batches in a loop
                    for (int i = 0; i < pairs.size(); i += batchSize) {
                        int endIndex = Math.min(i + batchSize, pairs.size());
                        List<List<String>> batchPairs = pairs.subList(i, endIndex);

                        List<CompletableFuture<List<String>>> batchFutures = new ArrayList<>();

                        // Process pairs within the batch
                        for (List<String> pair : batchPairs) {
                            CompletableFuture<List<String>> pairFuture = CompletableFuture.supplyAsync(() -> {
                                String cam00File = pair.get(0);
                                String cam01File = pair.get(1);

                                if (message.getText().equalsIgnoreCase("NTT")) {
                                    m3U8Service.createImageFromFrameNtt(userActivity.getRequestId(), cam00File);
                                    m3U8Service.createImageFromFrameNtt(userActivity.getRequestId(), cam01File);
                                } else {
                                    m3U8Service.createImageFromFrame(userActivity.getRequestId(), cam00File);
                                    m3U8Service.createImageFromFrame(userActivity.getRequestId(), cam01File);
                                }

                                // Return the pair for sending via sendMediaGroup
                                return List.of(cam00File, cam01File);
                            }, executorService);

                            batchFutures.add(pairFuture);
                        }

                        // Wait for all pairs within the batch to complete
                        CompletableFuture<Void> allPairsFuture = CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]));

                        // Process the results of the pairs within the batch and send them
                        CompletableFuture<List<List<String>>> batchResultsFuture = allPairsFuture.thenApplyAsync(v -> {
                            List<List<String>> batchResults = batchFutures.stream()
                                    .map(CompletableFuture::join)
                                    .collect(Collectors.toList());

                            // Send the results via sendMediaGroup
                            for (List<String> pair : batchResults) {
                                sendMediaGroup(List.of(pair.get(0), pair.get(1)), message.getChatId(), userActivity.getRequestId());
                            }

                            return batchResults;
                        });

                        try {
                            // Get the results of the batch and handle them if needed
                            List<List<String>> batchResults = batchResultsFuture.get();
                            // Process the batch results as desired
                        } catch (InterruptedException | ExecutionException e) {
                            // Handle cancellation or exception
                            e.printStackTrace();
                        }
                    }
                    // All batches are done, send the message here
                    sendKeyboardWithMessage(message.getChatId(), "All images were sent for request: " + userActivity.getRequestId());
                    deleteTmpFiles(String.format("./tmp/img/%s/", userActivity.getRequestId()));
                    deleteTmpFiles(String.format("./tmp/m3u8/%s/", userActivity.getRequestId()));
                }
            }

        }

        if (update.hasMessage() && update.getMessage().hasDocument() && checkIfUserActivityBegan(update.getMessage())) {
            UserActivity userActivity = activityMap.get(update.getMessage().getFrom().getId());
            Long id = update.getMessage().getFrom().getId();

            Document document = update.getMessage().getDocument();
            if ((document.getFileName().equalsIgnoreCase("cam00_.m3u8")
                    || document.getFileName().equalsIgnoreCase("cam01_.m3u8"))
                    && userActivity.getState() == UserActivityState.REQUEST_ID_PROVIDED) {
                if (document.getFileName().equalsIgnoreCase("cam00_.m3u8")) {
                    userActivity.setCam00TsFiles(parseM3U8(update.getMessage().getDocument(), id));
                }
                if (document.getFileName().equalsIgnoreCase("cam01_.m3u8")) {
                    userActivity.setCam01TsFiles(parseM3U8(update.getMessage().getDocument(), id));
                }
            }
        }

        if (update.hasMessage() && update.getMessage().hasAudio() && checkIfUserActivityBegan(update.getMessage())) {
            UserActivity userActivity = activityMap.get(update.getMessage().getFrom().getId());
            Long id = update.getMessage().getFrom().getId();

            Audio document = update.getMessage().getAudio();
            if ((document.getFileName().equalsIgnoreCase("cam00_.m3u8")
                    || document.getFileName().equalsIgnoreCase("cam01_.m3u8"))
                    && userActivity.getState() == UserActivityState.REQUEST_ID_PROVIDED) {
                if (document.getFileName().equalsIgnoreCase("cam00_.m3u8")) {
                    userActivity.setCam00TsFiles(parseM3U8(update.getMessage().getAudio(), id));
                }
                if (document.getFileName().equalsIgnoreCase("cam01_.m3u8")) {
                    userActivity.setCam01TsFiles(parseM3U8(update.getMessage().getAudio(), id));
                }
            }
        }
    }

    public void sendMediaGroup(List<String> processedPairs, Long chatId, String requestId) {
        List<InputMedia> mediaGroup = processedPairs.stream()
                .map(pair -> getPhotoFromFileSystem(pair, requestId))
                .collect(Collectors.toList());

        SendMediaGroup sendMediaGroup = SendMediaGroup.builder()
                .chatId(chatId)
                .medias(mediaGroup)
                .build();

        sendMediaGroup(sendMediaGroup);
    }

    private InputMedia getPhotoFromFileSystem(String file, String requestId) {
        return InputMediaPhoto.builder()
                .media("attach://" + file)
                .mediaName(file)
                .isNewMedia(true)
                .newMediaFile(new File("tmp/img/" + requestId + "/" + file.substring(0, file.lastIndexOf('.')) + ".jpg"))
                .caption(file)
                .parseMode(ParseMode.HTML)
                .build();
    }


    private List<String> parseM3U8(Document document, Long userId) {
        String documentName = document.getFileName();
        UserActivity userActivity = activityMap.get(userId);

        GetFile getFile = new GetFile();
        getFile.setFileId(document.getFileId());
        try {
            org.telegram.telegrambots.meta.api.objects.File file = execute(getFile);
            String fileName = "./tmp/m3u8/" + userActivity.getRequestId() + "/" + documentName;
            downloadFile(file, new File(fileName));
            FileInputStream fis = new FileInputStream(fileName);
            String fileContent = IOUtils.toString(fis, StandardCharsets.UTF_8);

            List<String> tsFiles = getTsFiles(fileContent);
            log.info(tsFiles.toString());

            return tsFiles;

        } catch (TelegramApiException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private List<String> parseM3U8(Audio document, Long userId) {
        String documentName = document.getFileName();
        UserActivity userActivity = activityMap.get(userId);

        GetFile getFile = new GetFile();
        getFile.setFileId(document.getFileId());
        try {
            org.telegram.telegrambots.meta.api.objects.File file = execute(getFile);
            String fileName = "./tmp/m3u8/" + userActivity.getRequestId() + "/" + documentName;
            downloadFile(file, new File(fileName));
            FileInputStream fis = new FileInputStream(fileName);
            String fileContent = IOUtils.toString(fis, StandardCharsets.UTF_8);

            List<String> tsFiles = getTsFiles(fileContent);
            log.info(tsFiles.toString());

            return tsFiles;

        } catch (TelegramApiException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }


    private List<String> getTsFiles(String fileContent) {
        Pattern pattern = Pattern.compile("([a-zA-Z0-9_-]+\\.ts)");
        Matcher matcher = pattern.matcher(fileContent);

        List<String> tsFileNames = new ArrayList<>();
        while (matcher.find()) {
            String tsFileName = matcher.group(1);
            tsFileNames.add(tsFileName);
        }

        return tsFileNames;
    }

    private boolean checkIfUserActivityBegan(Message message) {
        if (!activityMap.containsKey(message.getFrom().getId())) {
            sendKeyboardWithMessage(message.getChatId(), "Please select an option from the menu");
            return false;
        }
        return true;
    }

    private boolean validateRequestId(String requestId) {
        String pattern = "^.{24}$";
        Pattern regexPattern = Pattern.compile(pattern);
        Matcher matcher = regexPattern.matcher(requestId);

        return matcher.matches();
    }

    private void sendMediaGroup(SendMediaGroup mediaGroup) {
        try {
            execute(mediaGroup);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendWelcomeMessage(Long chatId) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("Welcome to the Corrupted Files Assistance");
            execute(message);
            message.setChatId(chatId);
            message.setReplyMarkup(keyboardService.defaultKeyboard());
            message.setText("Follow these rules to make the bot work\n\r" +
                    "1. Press Slice an event\n\r" +
                    "2. Send a request id\n\r" +
                    "3. Shortly after send cam00_.m3u8 and cam01_.m3u8\n\r" +
                    "4. Press upload complete\n\r" +
                    "5. Select the request client type\n\r" +
                    "6. Now is time to chill, approximately it will take 1-3 min, based on the event size\n\r");
            Message sentMessage = execute(message);
            PinChatMessage pinChatMessage = new PinChatMessage();
            pinChatMessage.setChatId(chatId);
            pinChatMessage.setMessageId(sentMessage.getMessageId());
            execute(pinChatMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendKeyboardWithMessage(Long chatId, String _message) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setReplyMarkup(keyboardService.defaultKeyboard());
            message.setText(_message);
            execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendMessageWithEventKeyBoard(Long chatId) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setReplyMarkup(keyboardService.eventKeyboard());
            message.setText("Please select client type");
            execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteTmpFiles(String dirPath) {
        Path directory = Paths.get(dirPath);

        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
            for (Path path : dirStream) {
                if (Files.isDirectory(path)) {
                    deleteTmpFiles(path.toString());
                }
                Files.delete(path);
            }
            Files.delete(directory);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
