<div align="middle">

# lukosBot2

[![Visitors](https://api.visitorbadge.io/api/visitors?path=https%3A%2F%2Fgithub.com%2FChiloven945%2FlukosBot2&labelColor=%23444444&countColor=%23f24822&style=flat-square&labelStyle=none)](https://visitorbadge.io/status?path=https%3A%2F%2Fgithub.com%2FChiloven945%2FlukosBot2)
[![Stars](https://img.shields.io/github/stars/Chiloven945/lukosBot2?style=flat-square&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZlcnNpb249IjEiIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHBhdGggZD0iTTggLjI1YS43NS43NSAwIDAgMSAuNjczLjQxOGwxLjg4MiAzLjgxNSA0LjIxLjYxMmEuNzUuNzUgMCAwIDEgLjQxNiAxLjI3OWwtMy4wNDYgMi45Ny43MTkgNC4xOTJhLjc1MS43NTEgMCAwIDEtMS4wODguNzkxTDggMTIuMzQ3bC0zLjc2NiAxLjk4YS43NS43NSAwIDAgMS0xLjA4OC0uNzlsLjcyLTQuMTk0TC44MTggNi4zNzRhLjc1Ljc1IDAgMCAxIC40MTYtMS4yOGw0LjIxLS42MTFMNy4zMjcuNjY4QS43NS43NSAwIDAgMSA4IC4yNVoiIGZpbGw9IiNlYWM1NGYiLz48L3N2Zz4=&logoSize=auto&label=Stars&labelColor=444444&color=eac54f)](https://github.com/Chiloven945/lukosBot2/)
[![GitHub Release](https://img.shields.io/github/v/release/Chiloven945/lukosBot2?style=flat-square&labelColor=444444&label=Release&include_prereleases)](https://github.com/Chiloven945/lukosBot2/releases)
[![GitHub CI](https://img.shields.io/github/actions/workflow/status/Chiloven945/lukosBot2/ci.yml?style=flat-square&labelColor=444444&branch=master&label=GitHub%20CI&logo=github)](https://github.com/Chiloven945/lukosBot2/actions/workflows/ci.yml)
[![Telegram](https://img.shields.io/badge/Telegram-LukosBot2-2299d5?style=flat-square&logo=telegram&logoColor=2ba3df)](https://t.me/lukos945_bot)
[![Discord](https://img.shields.io/badge/Discord-LukosBot2-5662f6?style=flat-square&logo=discord&logoColor=ffffff)](https://discord.com/oauth2/authorize?client_id=1417426824618573906)

</div>

lukosBot2 is a multifunctional and multiplatform chatbot, using
the [TelegramBots](https://github.com/rubenlagus/TelegramBots) for Telegram platform,
the [JDA](https://github.com/discord-jda/JDA) for Discord platform, and the [Shiro](https://github.com/MisakaTAT/Shiro)
for QQ using [OneBot](https://github.com/botuniverse/onebot-11) protocol, analysing command with
the [brigadier](https://github.com/Mojang/brigadier). The programme is powered
by [SpringBoot](https://github.com/spring-projects/spring-boot).

This is an experimental project. I'm using it to practice my skills, and it is not guaranteed to be stable or secure.
Please use at your own risk.

## Supported Commands

The bot currently supports the following user-facing commands. Some commands may require administrator permissions or
external service configuration.

- `/24` - Play a 24-point arithmetic game.
    - `*empty*` - Start a new game.
    - `<expression>` - Submit an answer using `+`, `-`, `*`, `/`, and parentheses.
    - `giveup` - Give up the current game and show the answer.

- `/admin` - Manage bot administrators and inspect the current identity.
    - `me` - Show your platform, user ID, chat ID, and administrator status.
    - `list` - List bot administrators. Administrator only.
    - `add <platform> <userId>` - Add a bot administrator. Administrator only.
    - `remove <platform> <userId>` - Remove a bot administrator. Administrator only.

- `/bilibili` - Query Bilibili video information. Alias: `/bili`.
    - `<code|link> [-i]` - Supports AV/BV IDs, video links, and b23.tv short links. Add `-i` for more details.

- `/cave` - Echo cave: save and randomly recall text or images. Alias: `/c`.
    - `*empty*` - Recall a random entry.
    - `<number>` - Recall a specific entry.
    - `add [message]` - Add the current message, quoted message, or provided text. Administrator only.
    - `delete <number>` - Delete an entry. Administrator only.

- `/coin` - Toss coins.
    - `[count]` - Toss one or more coins. Defaults to `1`.

- `/dice` - Roll dice.
    - `[count]` - Roll one or more six-sided dice. Defaults to `1`.

- `/e621` - Query E621 posts and artist information.
    - `get artist <id|link>` - Get artist details.
    - `get post <id|link>` - Get post details.
    - `search artist <text> [page]` - Search artists.
    - `search post <text> [page]` - Search posts.
    - `search md5 <md5>` - Search a post by image MD5.

- `/echo` - Echo back the provided message.
    - `<message>` - The message to echo.

- `/github` - Query GitHub information. Alias: `/gh`.
    - `user <username>` - Get information about a user or organisation.
    - `repo <owner>/<repo>` - Get information about a repository.
    - `search <keyword> [--top=<num>] [--lang=<lang>] [--sort=<stars|updated>] [--order=<desc|asc>]` - Search
      repositories.

- `/help` - Show bot documentation.
    - `*empty*` - Show the visible command list.
    - `<command>` - Show help for a specific command.
    - `<command> <text|img>` - Force text or image help output when supported.

- `/ip` - Query IP address information.
    - `<ip_address>` - Query with the default provider priority and automatic fallback.
    - `--provider=<providers> <ip_address>` - Query with one or more specified providers, separated by commas. Available
      providers include `ipquery` and `ipsb`.

- `/luck` - Get today's luck value. Aliases: `/l`, `/jrrp`.
    - `*empty*` - Return a stable daily luck value for the current user.

- `/mcwiki` - Query Minecraft Wiki pages.
    - `<article|link>` - Get the page title and summary.
    - `md <article|link>` - Export the page as a Markdown file.
    - `ss <article|link>` - Generate a page screenshot.

- `/motd` - Query Minecraft Java server status.
    - `<address[:port]>` - Query automatically using the best available method.
    - `api <address[:port]>` - Force API-based query.
    - `direct <address[:port]>` or `self <address[:port]>` - Force direct protocol query.

- `/music` - Search music information from streaming platforms.
    - `<query>` - Search on the default available platform.
    - `<spotify|soundcloud|sc> <query>` - Search on a specific platform.
    - `link <link>` - Parse a Spotify or SoundCloud track link.

- `/ping` - Return bot status and version information.
    - `*empty*` - Show runtime status, memory usage, and component versions.

- `/player` - Query Minecraft player information.
    - `<name|uuid>` - Query player information by username or UUID.
    - `<name> -u` - Get UUID by username.
    - `<uuid> -n` - Get username by UUID.

- `/pref` - View and manage preference settings.
    - `list` - List available preference keys.
    - `get <state>` - Show the effective value.
    - `get <scope> <state>` - Show a value in `user`, `chat`, or `global` scope.
    - `set <scope> <state> <value>` - Set a scoped value. Some scopes require administrator permissions.
    - `clear <scope> <state>` - Clear a scoped value. Some scopes require administrator permissions.

- `/service` - Manage bot services and service configuration.
    - `list` - List services available in the current chat.
    - `<service>` - Toggle a service in the current chat. Administrator permission may be required.
    - `<service> <key>` - Show a service setting in the current chat.
    - `<service> <key> <value>` - Set a service setting in the current chat.
    - `global list` - List global default service states. Bot administrator only.
    - `global <service> [key] [value]` - Manage global service defaults. Bot administrator only.

- `/translate` - Translate text. Alias: `/tr`.
    - `<text>` - Translate text using automatic source-language detection.
    - `-f <from_lang> -t <to_lang> <text>` - Translate from a specified source language to a specified target language.
    - `-f <from_lang>` - Specify the source language.
    - `-t <to_lang>` - Specify the target language.

- `/wiki` - Query Wikipedia pages.
    - `<article|link>` - Generate a page screenshot.
    - `md <article|link>` - Export the page as a Markdown file.

- `/whois` - Query domain Whois information.
    - `<domain>` - Domain name to query.

## Feedback

We use GitHub Issues for bug reports and feature requests.

When reporting a bug, include your lukosBot2 version, your configuration files, and the relevant console logs so the
issue can be reproduced.

Feature proposing may be slow to respond. We may not have enough time and patient to implement some feature, but we
still welcome you to tell us what can be added or improved.

## Contributing

Contributions are welcome. Fork the repository, create a feature branch, and keep changes focused and easy to review.
When opening a Pull Request, explain what changed, why it changed, and how to test it.

Remember to follow the [Contribution Guide](https://github.com/Chiloven945/lukosBot2/blob/master/CONTRIBUTING.md) when
contributing.

The [lukosBot2 wiki](https://github.com/Chiloven945/lukosBot2/wiki) also includes some project information like
architectures and procedure designs that might be helpful for the development.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [LICENSE](LICENSE) file
for details.

This project uses the following third-party libraries. All direct dependencies are listed alphabetically, and their
licences are respected:

| Library                           | Repository / Project                                                                  | License                                                                            |
|-----------------------------------|---------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| Brigadier                         | [Mojang/brigadier](https://github.com/Mojang/brigadier)                               | [MIT](https://github.com/Mojang/brigadier/blob/master/LICENSE)                     |
| Commons Net                       | [apache/commons-net](https://github.com/apache/commons-net)                           | [Apache-2.0](https://github.com/apache/commons-net/blob/master/LICENSE.txt)        |
| Docker Java                       | [docker-java/docker-java](https://github.com/docker-java/docker-java)                 | [Apache-2.0](https://github.com/docker-java/docker-java/blob/main/LICENSE)         |
| Docker Java Transport HttpClient5 | [docker-java/docker-java](https://github.com/docker-java/docker-java)                 | [Apache-2.0](https://github.com/docker-java/docker-java/blob/main/LICENSE)         |
| Flexmark                          | [vsch/flexmark-java](https://github.com/vsch/flexmark-java)                           | [BSD-2-Clause](https://github.com/vsch/flexmark-java/blob/master/LICENSE.txt)      |
| H2 Database Engine                | [h2database/h2database](https://github.com/h2database/h2database)                     | [MPL-2.0 or EPL-1.0](https://www.h2database.com/html/license.html)                 |
| Jackson Core                      | [FasterXML/jackson-core](https://github.com/FasterXML/jackson-core)                   | [Apache-2.0](https://github.com/FasterXML/jackson-core/blob/3.x/LICENSE)           |
| Jackson Databind                  | [FasterXML/jackson-databind](https://github.com/FasterXML/jackson-databind)           | [Apache-2.0](https://github.com/FasterXML/jackson-databind/blob/3.x/LICENSE)       |
| Jackson Module Kotlin             | [FasterXML/jackson-module-kotlin](https://github.com/FasterXML/jackson-module-kotlin) | [Apache-2.0](https://github.com/FasterXML/jackson-module-kotlin/blob/3.x/LICENSE)  |
| JDA                               | [discord-jda/JDA](https://github.com/discord-jda/JDA)                                 | [Apache-2.0](https://github.com/discord-jda/JDA/blob/master/LICENSE)               |
| Jsoup                             | [jhy/jsoup](https://github.com/jhy/jsoup)                                             | [MIT](https://github.com/jhy/jsoup/blob/master/LICENSE)                            |
| Kotlin Reflect                    | [JetBrains/kotlin](https://github.com/JetBrains/kotlin)                               | [Apache-2.0](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt)  |
| Kotlinx Coroutines Core           | [Kotlin/kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines)             | [Apache-2.0](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt) |
| Log4j 2                           | [apache/logging-log4j2](https://github.com/apache/logging-log4j2)                     | [Apache-2.0](https://github.com/apache/logging-log4j2/blob/2.x/LICENSE.txt)        |
| Lombok                            | [projectlombok/lombok](https://github.com/projectlombok/lombok)                       | [MIT](https://github.com/projectlombok/lombok/blob/master/LICENSE)                 |
| OkHttp                            | [square/okhttp](https://github.com/square/okhttp)                                     | [Apache-2.0](https://github.com/square/okhttp/blob/master/LICENSE.txt)             |
| Selenium                          | [SeleniumHQ/selenium](https://github.com/SeleniumHQ/selenium)                         | [Apache-2.0](https://github.com/SeleniumHQ/selenium/blob/trunk/LICENSE)            |
| Shiro (OneBot)                    | [MisakaTAT/Shiro](https://github.com/MisakaTAT/Shiro)                                 | [AGPL-3.0](https://github.com/MisakaTAT/Shiro/blob/main/LICENSE)                   |
| SLF4J                             | [qos-ch/slf4j](https://github.com/qos-ch/slf4j)                                       | [MIT](https://github.com/qos-ch/slf4j/blob/master/slf4j-api/LICENSE.txt)           |
| SnakeYAML                         | [snakeyaml/snakeyaml](https://github.com/snakeyaml/snakeyaml)                         | [Apache-2.0](https://github.com/snakeyaml/snakeyaml/blob/master/LICENSE.txt)       |
| Spring Boot                       | [spring-projects/spring-boot](https://github.com/spring-projects/spring-boot)         | [Apache-2.0](https://github.com/spring-projects/spring-boot/blob/main/LICENSE.txt) |
| Spring Boot Actuator              | [spring-projects/spring-boot](https://github.com/spring-projects/spring-boot)         | [Apache-2.0](https://github.com/spring-projects/spring-boot/blob/main/LICENSE.txt) |
| Spring Boot JDBC                  | [spring-projects/spring-boot](https://github.com/spring-projects/spring-boot)         | [Apache-2.0](https://github.com/spring-projects/spring-boot/blob/main/LICENSE.txt) |
| TelegramBots                      | [rubenlagus/TelegramBots](https://github.com/rubenlagus/TelegramBots)                 | [MIT](https://github.com/rubenlagus/TelegramBots/blob/master/LICENSE)              |
| WebDriverManager                  | [bonigarcia/webdrivermanager](https://github.com/bonigarcia/webdrivermanager)         | [Apache-2.0](https://github.com/bonigarcia/webdrivermanager/blob/master/LICENSE)   |
| Zip4j                             | [srikanth-lingala/zip4j](https://github.com/srikanth-lingala/zip4j)                   | [Apache-2.0](https://github.com/srikanth-lingala/zip4j/blob/master/LICENSE)        |
