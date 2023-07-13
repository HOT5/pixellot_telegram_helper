package web.telegram.bot.clicker.telegram;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.api.objects.ApiResponse;
import org.telegram.telegrambots.meta.api.objects.Document;
import web.telegram.bot.clicker.config.TelegramBotProperties;
import web.telegram.bot.clicker.exception.TelegramFileNotFoundException;

import java.io.File;
import java.io.FileOutputStream;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;

@Service
public class TelegramApiClient {
    private final String url;
    private final String token;

    private final RestTemplate restTemplate;

    public TelegramApiClient(TelegramBotProperties properties) {
        this.url = properties.getApiUrl();
        this.token = properties.getToken();
        this.restTemplate = new RestTemplate();
    }

    public File getDocumentFile(Document document, String requestId) {
        try {
            return restTemplate.execute(
                    Objects.requireNonNull(getDocumentTelegramFileUrl(document.getFileId())),
                    HttpMethod.GET,
                    null,
                    clientHttpResponse -> {
                        String fileName = "./tmp/m3u8/" + requestId + "/" + document.getFileName();
                        File m3u8File = new File(fileName);
                        StreamUtils.copy(clientHttpResponse.getBody(), new FileOutputStream(m3u8File));
                        return m3u8File;
                    });
        } catch (Exception e) {
            throw new TelegramFileNotFoundException();
        }
    }

    public void sendMediaGroup(List<String> fileNames, Long chatId) {
        String apiUrl = "https://api.telegram.org/bot" + token + "/sendMediaGroup";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("chat_id", chatId);
//        for (int i = 0; i < fileNames.size(); i++) {
//            FileSystemResource fileResource = new FileSystemResource(files.get(i));
//            requestBody.add("media[" + i + "]", fileResource);
//        }

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, requestEntity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            // Media group sent successfully
        } else {
            // Handle the error case
        }
    }

    private String getDocumentTelegramFileUrl(String fileId) {
        try {
            ResponseEntity<ApiResponse<org.telegram.telegrambots.meta.api.objects.File>> response = restTemplate.exchange(
                    MessageFormat.format("{0}bot{1}/getFile?file_id={2}", url, token, fileId),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    }
            );
            return Objects.requireNonNull(response.getBody()).getResult().getFileUrl(this.token);
        } catch (Exception e) {
            throw new TelegramFileNotFoundException();
        }
    }
}