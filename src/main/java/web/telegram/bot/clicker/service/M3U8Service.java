package web.telegram.bot.clicker.service;

import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.PipeOutput;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Component
@Slf4j
@RequiredArgsConstructor
public class M3U8Service {

    private static final String FFMPEG_PATH = "C:\\Users\\Danylo_Ikol\\Downloads\\ffmpeg-6.0-essentials_build\\ffmpeg-6.0-essentials_build\\bin";
    private static final String EVENT_URL = "https://content.you.pixellot.tv/requests/%s/SourceFiles/%s";
    private static final String NTT_EVENT_URL = "https://ap.you.pixellot.tv/requests/%s/SourceFiles/%s";


    public void createImageFromFrame(String requestId, String frameName) {
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

    public void createImageFromFrameNtt(String requestId, String frameName) {
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
}