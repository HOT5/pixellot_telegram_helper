package web.telegram.bot.clicker.service;

import org.springframework.stereotype.Component;
import web.telegram.bot.clicker.model.UserActivity;
import web.telegram.bot.clicker.model.UserActivityState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserActivityService {

    private final Map<Long, UserActivity> activityMap = new ConcurrentHashMap<>();


    public UserActivity getUser(Long userId) {
        return activityMap.get(userId);
    }

    public boolean checkIfUserActivityBegan(Long userId) {
        return activityMap.containsKey(userId);
    }

    public boolean checkIfUserStateIsEqualsTo(Long userId, UserActivityState userActivityState) {
        return getUser(userId).getState() == userActivityState;
    }

    public void removeUserFromActivityMap(Long userId) {
        activityMap.remove(userId);
    }

    public void createNewUserActivity(Long userId) {
        UserActivity userActivity = new UserActivity();

        userActivity.setUserId(userId);
        userActivity.setState(UserActivityState.NO_USER_ACTIVITY);

        activityMap.put(userId, userActivity);
    }

    public void setUserActivity(Long userId, UserActivityState userActivityState) {
        getUser(userId).setState(userActivityState);
    }

    public void setRequestId(Long userId, String requestId) {
        getUser(userId).setRequestId(requestId);
    }

    public int getCam00TsFilesSize(Long userId) {
        return getUser(userId).getCam00TsFiles().size();
    }

    public int getCam01TsFilesSize(Long userId) {
        return getUser(userId).getCam01TsFiles().size();
    }

    public String getRequestId(Long userId) {
        return getUser(userId).getRequestId();
    }

    public void setDesireNumberOfFrames(Long userId, int numberOfFrames){
        getUser(userId).setDesireNumberOfFrames(numberOfFrames);
    }
}
