<div align="middle">

# lukosBot2

[![Visitors](https://api.visitorbadge.io/api/visitors?path=https%3A%2F%2Fgithub.com%2FChiloven945%2FlukosBot2&labelColor=%23444444&countColor=%23f24822&style=flat-square&labelStyle=none)](https://visitorbadge.io/status?path=https%3A%2F%2Fgithub.com%2FChiloven945%2FlukosBot2)
[![Stars](https://img.shields.io/github/stars/Chiloven945/lukosBot2?style=flat-square&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZlcnNpb249IjEiIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHBhdGggZD0iTTggLjI1YS43NS43NSAwIDAgMSAuNjczLjQxOGwxLjg4MiAzLjgxNSA0LjIxLjYxMmEuNzUuNzUgMCAwIDEgLjQxNiAxLjI3OWwtMy4wNDYgMi45Ny43MTkgNC4xOTJhLjc1MS43NTEgMCAwIDEtMS4wODguNzkxTDggMTIuMzQ3bC0zLjc2NiAxLjk4YS43NS43NSAwIDAgMS0xLjA4OC0uNzlsLjcyLTQuMTk0TC44MTggNi4zNzRhLjc1Ljc1IDAgMCAxIC40MTYtMS4yOGw0LjIxLS42MTFMNy4zMjcuNjY4QS43NS43NSAwIDAgMSA4IC4yNVoiIGZpbGw9IiNlYWM1NGYiLz48L3N2Zz4=&logoSize=auto&label=Stars&labelColor=444444&color=eac54f)](https://github.com/Chiloven945/lukosBot2/)
[![GitHub CI](https://img.shields.io/github/actions/workflow/status/Chiloven945/lukosBot2/maven.yml?style=flat-square&labelColor=444444&branch=master&label=GitHub%20CI&logo=github)](https://github.com/Chiloven945/lukosBot2/actions/workflows/maven.yml)
[![Telegram](https://img.shields.io/badge/Telegram-LukosBot-2299d5?style=flat-square&logo=telegram&logoColor=2ba3df)](https://t.me/lukos945_bot)
[![Discord](https://img.shields.io/badge/Discord-LukosBot2-5662f6?style=flat-square&logo=discord&logoColor=ffffff)](https://discord.com/oauth2/authorize?client_id=1417426824618573906)

</div>

lukosBot2 is a multifunctional and multiplatform chatbot, using
the [TelegramBots](https://github.com/rubenlagus/TelegramBots) for Telegram platform, the JDA for Discord platform, and
the Shiro for QQ using OneBot protocol, analysing command
with the [brigadier](https://github.com/Mojang/brigadier).

This is an experimental project. I'm using it to practice my skills, and it is not guaranteed to be stable or secure.
Please use at your own risk.

## Supported Commands

The bot currently supports the following commands:

- `/help` - Show the documentation for the bot.
    - `<command>` - Get help for a specific command.
- `/github` - GitHub related commands.
    - `search <keywords>` - Search for repositories, issues, or users.
    - `repo <owner>/<repo>` - Get information about a repository.
    - `user <username>` - Get information about a user/organisation.
- `/ping` - check if the bot is alive.
- `/echo` (WIP) - Echo back the provided message.
    - `<message>` - The message to echo.

## Planned Features

- `/weather` - Get weather information for a specific location.
- `/ping` - Check the bot's responsiveness.
- `/translate` - Translate text between languages.
- `/music` - Search and play music.
- `/cave` - Echo cave...

## Contributing

If you want to contribute to this project, feel free to open an issue or a pull request. Contributions are welcome!

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [LICENSE](LICENSE) file for details.

This project uses the following third-party libraries. All dependencies are compatible with AGPL-3.0, and their licenses are respected:

- **Brigadier**
  - Repository: [Mojang/brigadier](https://github.com/Mojang/brigadier)
  - License: [MIT](https://github.com/Mojang/brigadier/blob/master/LICENSE)

- **Flexmark**
  - Repository: [vsch/flexmark-java](https://github.com/vsch/flexmark-java)
  - License: [BSD-2-Clause](https://github.com/vsch/flexmark-java/blob/master/LICENSE.txt)

- **Gson**
  - Repository: [google/gson](https://github.com/google/gson)
  - License: [Apache-2.0](https://github.com/google/gson/blob/main/LICENSE)

- **JDA**
  - Repository: [discord-jda/JDA](https://github.com/discord-jda/JDA)
  - License: [Apache-2.0](https://github.com/discord-jda/JDA/blob/master/LICENSE)

- **Jsoup**
  - Repository: [jhy/jsoup](https://github.com/jhy/jsoup)
  - License: [MIT](https://github.com/jhy/jsoup/blob/master/LICENSE)

- **Log4j2**
  - Repository: [apache/logging-log4j2](https://github.com/apache/logging-log4j2)
  - License: [Apache-2.0](https://github.com/apache/logging-log4j2/blob/2.x/LICENSE.txt)

- **Lombok**
  - Repository: [projectlombok/lombok](https://github.com/projectlombok/lombok)
  - License: [MIT](https://github.com/projectlombok/lombok/blob/master/LICENSE)

- **Selenium**
  - Repository: [SeleniumHQ/selenium](https://github.com/SeleniumHQ/selenium)
  - License: [Apache-2.0](https://github.com/SeleniumHQ/selenium/blob/trunk/LICENSE)

- **Shiro (OneBot)**
  - Repository: [MisakaTAT/Shiro](https://github.com/mikuac/shiro)
  - License: [AGPL-3.0](https://github.com/MisakaTAT/Shiro/blob/main/LICENSE)

- **SLF4J**
  - Repository: [qos-ch/slf4j](https://github.com/qos-ch/slf4j)
  - License: [MIT](https://github.com/qos-ch/slf4j/blob/master/LICENSE.txt)

- **SnakeYAML**
  - Repository: [snakeyaml/snakeyaml](https://github.com/snakeyaml/snakeyaml)
  - License: [Apache-2.0](https://github.com/snakeyaml/snakeyaml/blob/master/LICENSE.txt)

- **Spring Boot**
  - Repository: [spring-projects/spring-boot](https://github.com/spring-projects/spring-boot)
  - License: [Apache-2.0](https://github.com/spring-projects/spring-boot/blob/main/LICENSE.txt)

- **TelegramBots**
  - Repository: [rubenlagus/TelegramBots](https://github.com/rubenlagus/TelegramBots)
  - License: [MIT](https://github.com/rubenlagus/TelegramBots/blob/master/LICENSE)

- **WebDriverManager**
  - Repository: [bonigarcia/webdrivermanager](https://github.com/bonigarcia/webdrivermanager)
  - License: [Apache-2.0](https://github.com/bonigarcia/webdrivermanager/blob/master/LICENSE)