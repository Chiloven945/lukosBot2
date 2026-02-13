package top.chiloven.lukosbot2;

import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Log4j2
public class Main {
    static void main(String[] args) {
        log.info("""
                        Starting lukosBot2 {} ...
                         __       __  __  __  __   _____   ____    ____     _____   ______    ___    \s
                        /\\ \\     /\\ \\/\\ \\/\\ \\/\\ \\ /\\  __`\\/\\  _`\\ /\\  _`\\  /\\  __`\\/\\__  _\\ /'___`\\  \s
                        \\ \\ \\    \\ \\ \\ \\ \\ \\ \\/'/'\\ \\ \\/\\ \\ \\,\\L\\_\\ \\ \\L\\ \\\\ \\ \\/\\ \\/_/\\ \\//\\_\\ /\\ \\ \s
                         \\ \\ \\  __\\ \\ \\ \\ \\ \\ , <  \\ \\ \\ \\ \\/_\\__ \\\\ \\  _ <'\\ \\ \\ \\ \\ \\ \\ \\\\/_/// /__\s
                          \\ \\ \\L\\ \\\\ \\ \\_\\ \\ \\ \\\\`\\ \\ \\ \\_\\ \\/\\ \\L\\ \\ \\ \\L\\ \\\\ \\ \\_\\ \\ \\ \\ \\  // /_\\ \\
                           \\ \\____/ \\ \\_____\\ \\_\\ \\_\\\\ \\_____\\ `\\____\\ \\____/ \\ \\_____\\ \\ \\_\\/\\______/
                            \\/___/   \\/_____/\\/_/\\/_/ \\/_____/\\/_____/\\/___/   \\/_____/  \\/_/\\/_____/\s"""
                , Constants.VERSION
        );
        SpringApplication.run(Main.class, args);
    }
}
