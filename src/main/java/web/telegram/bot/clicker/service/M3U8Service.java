package web.telegram.bot.clicker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Document;
import web.telegram.bot.clicker.telegram.TelegramApiClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class M3U8Service {

    private final TelegramApiClient telegramApiClient;

    public List<String> parseM3U8(Document document, String requestId) {
        try {
            File downloadedFile = telegramApiClient.getDocumentFile(document, requestId);

            FileInputStream fis = new FileInputStream(downloadedFile);
            String fileContent = IOUtils.toString(fis, StandardCharsets.UTF_8);

            List<String> tsFiles = getTsFiles(fileContent);
            log.info(tsFiles.toString());

            return tsFiles;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
}