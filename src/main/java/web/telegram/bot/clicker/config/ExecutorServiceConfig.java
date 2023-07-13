package web.telegram.bot.clicker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorServiceConfig {

    private static final int THREAD_POOL_SIZE = 8;

    @Bean
    public ExecutorService executorService() {
        return Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }
}
