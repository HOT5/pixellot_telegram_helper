package web.telegram.bot.clicker.service;

import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.PipeOutput;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import web.telegram.bot.clicker.model.UserActivity;
import web.telegram.bot.clicker.telegram.TelegramApiClient;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class FfmpegService {

    private final ExecutorService executorService;

    private final TelegramApiClient telegramApiClient;

    @Value("${ffmpeg:/usr/local/bin/ffmpeg}")
    private String FFMPEG_PATH;
    private static final String EVENT_URL = "https://content.you.pixellot.tv/requests/%s/SourceFiles/%s";
    private static final String NTT_EVENT_URL = "https://ap.you.pixellot.tv/requests/%s/SourceFiles/%s";

    public void sliceEvent(UserActivity userActivity, String eventType) {
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

                    if (eventType.equalsIgnoreCase("NTT")) {
                        createImageFromFrameNtt(userActivity.getRequestId(), cam00File);
                        createImageFromFrameNtt(userActivity.getRequestId(), cam01File);
                    } else {
                        createImageFromFrame(userActivity.getRequestId(), cam00File);
                        createImageFromFrame(userActivity.getRequestId(), cam01File);
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
                    //telegramApiClient.sendMediaGroup(List.of(pair.get(0), pair.get(1)), message.getChatId(), userActivity.getRequestId());
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
        //sendKeyboardWithMessage(message.getChatId(), "All images were sent for request: " + userActivity.getRequestId());
        deleteTmpFiles(String.format("./tmp/img/%s/", userActivity.getRequestId()));
        deleteTmpFiles(String.format("./tmp/m3u8/%s/", userActivity.getRequestId()));
    }


    private void createImageFromFrame(String requestId, String frameName) {
        String fileName = frameName.substring(0, frameName.lastIndexOf('.'));
        String outputFilePath = String.format(".\\tmp\\img\\%s\\%s.jpg", requestId, fileName);

        try {
            Path outputPath = Paths.get(outputFilePath);
            Files.createDirectories(outputPath.getParent());

            try (OutputStream outputStream = Files.newOutputStream(outputPath, StandardOpenOption.CREATE_NEW)) {
                FFmpeg.atPath(Paths.get(FFMPEG_PATH))
                        .addInput(
                                UrlInput.fromUrl(String.format(EVENT_URL, requestId, frameName))
                        )
                        .addArguments("-vsync", "2")
                        .addArguments("-ss", "1")
                        .addArguments("-vframes", "1")
                        .addOutput(PipeOutput.pumpTo(outputStream)
                                .setFormat("image2"))
                        .execute();

                log.info("FFmpeg command executed successfully!");
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void createImageFromFrameNtt(String requestId, String frameName) {
        String fileName = frameName.substring(0, frameName.lastIndexOf('.'));
        String outputFilePath = String.format(".\\tmp\\img\\%s\\%s.jpg", requestId, fileName);

        try {
            Path outputPath = Paths.get(outputFilePath);
            Files.createDirectories(outputPath.getParent());

            try (OutputStream outputStream = Files.newOutputStream(outputPath, StandardOpenOption.CREATE_NEW)) {
                FFmpeg.atPath(Paths.get(FFMPEG_PATH))
                        .addInput(
                                UrlInput.fromUrl(String.format(NTT_EVENT_URL, requestId, frameName))
                        )
                        .addArguments("-vsync", "2")
                        .addArguments("-ss", "1")
                        .addArguments("-vframes", "1")
                        .addOutput(PipeOutput.pumpTo(outputStream)
                                .setFormat("image2"))
                        .execute();

                log.info("FFmpeg command executed successfully!");
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
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
