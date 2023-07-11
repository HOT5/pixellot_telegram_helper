package web.telegram.bot.clicker.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
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
import java.util.*;
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
            log.info("Process started by user: id - " + update.getMessage().getFrom().getId() + " name - " + update.getMessage().getFrom().getFirstName() + " " + update.getMessage().getFrom().getLastName() );
            Message message = update.getMessage();
            if (message.getText().equalsIgnoreCase("/start")) {
                sendWelcomeMessage(message.getChatId());

            }
            if (message.getText().equalsIgnoreCase("Slice an event")) {
                if (activityMap.containsKey(message.getFrom().getId())) {
                    UserActivity deletedActivity = activityMap.remove(message.getFrom().getId());


                    deleteTmpFiles(String.format("./tmp/img/%s/", deletedActivity.getRequestId()));
                    deleteTmpFiles(String.format("./tmp/m3u8/%s/", deletedActivity.getRequestId()));
                }

                UserActivity userActivity = new UserActivity();
                userActivity.setUserId(message.getFrom().getId());
                userActivity.setState(UserActivityState.NO_USER_ACTIVITY);
                activityMap.put(message.getFrom().getId(), userActivity);
            }
            if (validateRequestId(message.getText()) && checkIfUserActivityBegan(message)) {
                UserActivity userActivity = activityMap.get(message.getFrom().getId());
                if (userActivity.getState() == UserActivityState.NO_USER_ACTIVITY) {
                    userActivity.setState(UserActivityState.REQUEST_ID_PROVIDED);
                    userActivity.setRequestId(message.getText());
                }
            }
            if (message.getText().equalsIgnoreCase("Upload complete") && checkIfUserActivityBegan(message)) {
                UserActivity userActivity = activityMap.get(message.getFrom().getId());
                userActivity.setState(UserActivityState.M3U8_FILES_UPLOADED);
                sendMessageWithEventKeyBoard(message.getChatId());
            }
            if ((message.getText().equalsIgnoreCase("NTT") || message.getText().equalsIgnoreCase("Regular"))
                    && checkIfUserActivityBegan(message)) {
                UserActivity userActivity = activityMap.get(message.getFrom().getId());
                if (userActivity.getState() == UserActivityState.M3U8_FILES_UPLOADED) {
                    int minSize = Math.min(userActivity.getCam00TsFiles().size(), userActivity.getCam01TsFiles().size());

                    List<CompletableFuture<List<String>>> futures = IntStream.range(0, minSize)
                            .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                                List<String> pair = Arrays.asList(userActivity.getCam00TsFiles().get(i), userActivity.getCam01TsFiles().get(i));
                                if (message.getText().equalsIgnoreCase("NTT")) {
                                    m3U8Service.createImageFromFrameNtt(userActivity.getRequestId(), pair.get(0));
                                    m3U8Service.createImageFromFrameNtt(userActivity.getRequestId(), pair.get(1));
                                } else {
                                    m3U8Service.createImageFromFrame(userActivity.getRequestId(), pair.get(0));
                                    m3U8Service.createImageFromFrame(userActivity.getRequestId(), pair.get(1));
                                }
                                return pair;
                            }, executorService))
                            .collect(Collectors.toList());

                    // Handle remaining elements in mediaGroup1 (if any)
                    futures.addAll(userActivity.getCam00TsFiles().subList(minSize, userActivity.getCam00TsFiles().size())
                            .stream()
                            .map(filename -> CompletableFuture.supplyAsync(() -> {
                                if (message.getText().equalsIgnoreCase("NTT")) {
                                    m3U8Service.createImageFromFrameNtt(userActivity.getRequestId(), filename);
                                } else {
                                    m3U8Service.createImageFromFrame(userActivity.getRequestId(), filename);
                                }
                                return Collections.singletonList(filename);
                            }, executorService))
                            .toList());

                    // Handle remaining elements in mediaGroup2 (if any)
                    futures.addAll(userActivity.getCam01TsFiles().subList(minSize, userActivity.getCam01TsFiles().size())
                            .stream()
                            .map(filename -> CompletableFuture.supplyAsync(() -> {
                                if (message.getText().equalsIgnoreCase("NTT")) {
                                    m3U8Service.createImageFromFrameNtt(userActivity.getRequestId(), filename);
                                } else {
                                    m3U8Service.createImageFromFrame(userActivity.getRequestId(), filename);
                                }
                                return Collections.singletonList(filename);
                            }, executorService))
                            .collect(Collectors.toList()));

                    CompletableFuture<List<List<String>>> allFutures = CompletableFuture.allOf(
                                    futures.toArray(new CompletableFuture[0])
                            )
                            .thenApplyAsync(v -> futures.stream()
                                    .map(CompletableFuture::join)
                                    .collect(Collectors.toList()));

                    try {
                        List<List<String>> mergedMediaGroups = allFutures.get();
                        // Process mergedMediaGroups in the desired order
                        for (List<String> pair : mergedMediaGroups) {
                            sendMediaGroup(List.of(pair.get(0), pair.get(1)), message.getChatId(), userActivity.getRequestId());
                            Thread.sleep(450);
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        // Handle cancellation or exception
                        e.printStackTrace();
                    }
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
                    userActivity.setCam01TsFiles(parseM3U8(update.getChannelPost().getDocument(), id));
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
            message.setReplyMarkup(keyboardService.defaultKeyboard());
            message.setText("Welcome to the Corrupted Files Assistance");
            execute(message);
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
