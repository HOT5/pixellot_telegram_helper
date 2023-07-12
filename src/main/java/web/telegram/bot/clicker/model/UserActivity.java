package web.telegram.bot.clicker.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserActivity {

    private Long userId;
    private UserActivityState state;
    private String requestId;
    private List<String> cam00TsFiles = new ArrayList<>();
    private List<String> cam01TsFiles = new ArrayList<>();
    private int desireNumberOfFrames;
}
