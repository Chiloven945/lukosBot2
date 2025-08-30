<div align="middle">

# lukosBot2

[![Stars](https://img.shields.io/github/stars/Chiloven945/lukosBot2?style=flat&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZlcnNpb249IjEiIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHBhdGggZD0iTTggLjI1YS43NS43NSAwIDAgMSAuNjczLjQxOGwxLjg4MiAzLjgxNSA0LjIxLjYxMmEuNzUuNzUgMCAwIDEgLjQxNiAxLjI3OWwtMy4wNDYgMi45Ny43MTkgNC4xOTJhLjc1MS43NTEgMCAwIDEtMS4wODguNzkxTDggMTIuMzQ3bC0zLjc2NiAxLjk4YS43NS43NSAwIDAgMS0xLjA4OC0uNzlsLjcyLTQuMTk0TC44MTggNi4zNzRhLjc1Ljc1IDAgMCAxIC40MTYtMS4yOGw0LjIxLS42MTFMNy4zMjcuNjY4QS43NS43NSAwIDAgMSA4IC4yNVoiIGZpbGw9IiNlYWM1NGYiLz48L3N2Zz4=&logoSize=auto&label=Stars&labelColor=444444&color=eac54f)](https://github.com/Chiloven945/lukosBot2/)
[![GitHub CI](https://img.shields.io/github/actions/workflow/status/Chiloven945/lukosBot2/maven.yml?style=flat&labelColor=444444&branch=master&label=GitHub%20CI&logo=github)](https://github.com/Chiloven945/lukosBot2/actions/workflows/maven.yml)
[![Telegram](https://img.shields.io/badge/Telegram-LukosBot-2299d5?style=flat&logo=telegram&logoColor=2ba3df)](https://t.me/lukos945_bot)

</div>

lukosBot2 is a multifunctional and multiplatform chatbot, using [TelegramBots](https://github.com/rubenlagus/TelegramBots) and the OneBot protocol, analysing commands
with [brigadier](https://github.com/Mojang/brigadier).

This is an experimental project. I'm using it to practice my skills, and it is not guaranteed to be stable or secure. Use at your own risk.

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

- **Gson**
    - Repository: [google/gson](https://github.com/google/gson)
    - License: [Apache-2.0](https://github.com/google/gson/blob/main/LICENSE)

- **Log4j2**
    - Repository: [apache/logging-log4j2](https://github.com/apache/logging-log4j2)
    - License: [Apache-2.0](https://github.com/apache/logging-log4j2/blob/2.x/LICENSE.txt)

- **SLF4J**
    - Repository: [qos-ch/slf4j](https://github.com/qos-ch/slf4j)
    - License: [MIT](https://github.com/qos-ch/slf4j/blob/master/LICENSE.txt)

- **TelegramBots**
    - Repository: [rubenlagus/TelegramBots](https://github.com/rubenlagus/TelegramBots)
    - License: [MIT](https://github.com/rubenlagus/TelegramBots/blob/master/LICENSE)

- **Maven Assembly Plugin**
    - Repository: [apache/maven-assembly-plugin](https://github.com/apache/maven-assembly-plugin)
    - License: [Apache-2.0](https://github.com/apache/maven-assembly-plugin/blob/master/LICENSE)
