package web.telegram.bot.clicker.telegram.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Message;
import web.telegram.bot.clicker.model.UserActivity;
import web.telegram.bot.clicker.model.UserActivityState;
import web.telegram.bot.clicker.service.M3U8Service;
import web.telegram.bot.clicker.service.UserActivityService;

@Component
@RequiredArgsConstructor
public class DocumentHandlerService {

    private final UserActivityService userActivityService;
    private final M3U8Service m3U8Service;

    public void handleDocument(Message message) {
        Long userId = message.getFrom().getId();

        if (userActivityService.checkIfUserActivityBegan(userId)) {
            UserActivity userActivity = userActivityService.getUser(userId);

            Document document = message.getDocument();
            String fileName = document.getFileName();
            String requestId = userActivity.getRequestId();

            if ((fileName.equalsIgnoreCase("cam00_.m3u8")
                    || fileName.equalsIgnoreCase("cam01_.m3u8"))
                    && userActivityService.checkIfUserStateIsEqualsTo(userId, UserActivityState.REQUEST_ID_PROVIDED)) {
                if (fileName.equalsIgnoreCase("cam00_.m3u8")) {
                    userActivity.setCam00TsFiles(m3U8Service.parseM3U8(message.getDocument(), requestId));
                }
                if (fileName.equalsIgnoreCase("cam01_.m3u8")) {
                    userActivity.setCam01TsFiles(m3U8Service.parseM3U8(message.getDocument(), requestId));
                }
            }
        }
    }
}