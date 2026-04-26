# Contribution Guide

Thank you for contributing to this project. This document describes the conventions we expect contributors to follow
when writing code, submitting changes, and reviewing pull requests.

The goal of these rules is not to make code look “clever”. The goal is to make the project predictable: when someone
opens a file, they should quickly understand what the code does, where shared utilities live, and how a change should be
reviewed.

## Commit Messages

This project uses the **Conventional Commit** format.

Use this shape:

```text
<type>(optional scope): <short summary>
```

Common types include:

```text
fix(platform-telegram): handle missing Telegram file metadata
refactor(core): simplify command dispatch
style(image-render): reformat image renderer
chore(build): update Gradle wrapper
ci(release): add release ci
```

A good commit message explains the change from the user or maintainer’s perspective. Prefer:

```text
fix(usage): missing dark mode style
```

over:

```text
fix stuff
```

When a change has a breaking behavior change, use a footer and an exclamation mark before the colon:

```text
feat(message)!: support part messages

...

BREAKING CHANGE: method `send(String str)` is no longer usable, use `send(OutboundMessage om)` instead
```

## General Code Style

Write code that is easy to read in review. Avoid hiding intent behind overly compact formatting.

Class names use **PascalCase**:

```java
public final class CommandRegistry {

}
```

Variable names, fields, and method names use **camelCase**:

```kotlin
val commandName = "help"
var retryCount = 0
```

Interfaces use the `I` + `Name` format. This is intentional and should be kept consistent across the project.

Good examples:

```java
public interface IBotCommand {

}
```

```java
public interface IStateDefinition {

}
```

Do not introduce interface names like `BotCommand`, `StateDefinition`, or `CommandLike` when the type is meant to be an
interface in the existing project style.

## Visibility and Encapsulation

Private-purpose methods, variables, and fields should not be exposed.

Use `private` for implementation details. Do not mark something `public` just because it is convenient during
development.

Good:

```kotlin
private fun normalizeCommandName(raw: String): String {
    return raw.trim().lowercase()
}
```

Bad:

```kotlin
fun normalizeCommandName(raw: String): String {
    return raw.trim().lowercase()
}
```

In Java, avoid redundant exposure for helper methods that are not part of the public API:

```java
private String normalizeCommandName(String raw) {
    return raw.trim().toLowerCase(Locale.ROOT);
}
```

Public APIs should be intentional. If another package needs a helper, first consider whether the helper belongs in
`util`.

## Braces and Spacing

Opening braces stay on the same line as the method, function, class, interface, enum, or control-flow header.

Good:

```java
public final class Example {

}
```

```kotlin
class Example {

}
```

Bad:

```java
public final class Example
{

}
```

After a class or interface header, leave an extra blank line before the first member. Also leave a blank line before the
closing brace of the class or interface. This rule applies to type bodies, not method or function bodies.

Good Java class:

```java
public record Color(
        int r,
        int g,
        int b
) {

    public Color() {
        this(0, 0, 0);
    }

    @Override
    public String toString() {
        return String.format("(%d, %d, %d)", r, g, b);
    }

}
```

Good Kotlin class:

```kotlin
data class Color(
    val r: Int = 0,
    val g: Int = 0,
    val b: Int = 0,
) {

    override fun toString(): String {
        return "($r, $g, $b)"
    }

}
```

Methods and functions should **not** add extra blank lines immediately after the opening brace or immediately before the
closing brace.

Good Java method:

```java
public String trimText(String input) {
    return input.trim();
}
```

Good Kotlin function:

```kotlin
fun trimText(input: String): String {
    return input.trim()
}
```

Bad Java method:

```java
public String trimText(String input) {

    return input.trim();

}
```

Bad Kotlin function:

```kotlin
fun trimText(input: String): String {

    return input.trim()

}
```

This rule intentionally creates breathing room around type bodies while keeping method and function bodies compact.

## Function and Method Parameters

When a method or function has **three or more parameters**, place each parameter on its own line.

Good Java:

```java
public String method(
        String arg1,
        boolean arg2,
        int arg3
) {
    return arg1 + arg2 + arg3;
}
```

Good Kotlin:

```kotlin
fun method(
    arg1: String,
    arg2: Boolean,
    arg3: Int
): String {
    return "$arg1$arg2$arg3"
}
```

Avoid this for three or more parameters:

```java
public String method(String arg1, boolean arg2, int arg3) {
    return arg1 + arg2 + arg3;
}
```

For two parameters, a single line is acceptable when it remains readable.

## Records and Data Classes

Records and data classes should always put fields on separate lines.

Java records:

```java
public record Example(
        String field1,
        String field2
) {

}
```

Kotlin data classes:

```kotlin
data class Data(
    val a: String,
    val b: String
)
```

Do not compress records or data classes with multiple fields into one line:

```kotlin
data class Data(val a: String, val b: String)
```

The multi-line format keeps diffs smaller and makes future additions easier to review.

## Enums

Enum constants should always be placed one per line.

Good:

```java
public enum Platform {

    TELEGRAM,
    DISCORD,
    ONEBOT

}
```

Good Kotlin:

```kotlin
enum class Platform {

    TELEGRAM,
    DISCORD,
    ONEBOT

}
```

Avoid:

```java
public enum Platform {
    TELEGRAM,
    DISCORD,
    ONEBOT
}
```

## Annotation Formatting

When an annotation has more than three parameters, place each parameter on its own line.

Good:

```java

@ExampleAnnotation(
        name = "demo",
        enabled = true,
        timeout = 3000,
        retries = 2
)
public final class Example {

}
```

Kotlin:

```kotlin
@ExampleAnnotation(
    name = "demo",
    enabled = true,
    timeout = 3000,
    retries = 2
)
class Example {

}
```

For short annotations with one or two parameters, a single line is fine if it is readable.

## Stream API Formatting

When using Java Stream API, keep `.stream()` on the same line as the data source. After that, put every operation on its
own line.

Good:

```java
List<String> names = users.stream()
        .filter(User::isEnabled)
        .map(User::getName)
        .sorted()
        .toList();
```

Bad:

```java
List<String> names = users
        .stream()
        .filter(User::isEnabled).map(User::getName).sorted().toList();
```

This style makes stream pipelines easier to scan and easier to modify during reviews.

## Imports

Use normal explicit imports by default. However, when imports become repetitive, prefer broader imports according to the
following rules.

If more than four methods from the same class are used, consider importing all methods from that class.

```java
import static java.util.Comparator.*;
```

If one method from an external class is used more than four times, consider importing that method directly instead of
repeatedly qualifying it.

```java
import static java.util.Objects.requireNonNull;
```

If imports from the same package exceed four, consider importing the whole package.

```java
import java.util.*;
```

This is not a license to use wildcard imports everywhere. Use it when it improves readability and reduces noise.

## Static Utility Classes

If a Java class only contains static utility methods, it must prevent instantiation with a private constructor.

Good:

```java
public final class StringTools {

    private StringTools() {
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

}
```

In Kotlin, use `object` instead of a class with a private constructor.

Good:

```kotlin
object StringTools {

    fun normalize(value: String?): String {
        return value?.trim().orEmpty()
    }

}
```

Bad:

```kotlin
class StringTools private constructor() {

    companion object {

        fun normalize(value: String?): String {
            return value?.trim().orEmpty()
        }

    }

}
```

## Unused Lambda, Catch, and Pattern Variables

For unused variables in lambda expressions, `catch` statements, and switch patterns, use `_` where the language supports
it. This follows the intent of [JEP 456](https://openjdk.org/jeps/456).

Good Kotlin:

```kotlin
items.forEach { _ ->
    reload()
}
```

```kotlin
try {
    runTask()
} catch (_: Exception) {
    fallback()
}
```

For Java versions that support unnamed variables and patterns, prefer `_` for intentionally unused values.

Good:

```java
try {
    runTask();
} catch (Exception _) {
    fallback();
}
```

```java
for (String _ : list) run();
```

```java
return switch (text) {
    case Point(int x, int _) -> "x: " + x;
    default -> "unknown";
};
```

```java
(key, _) -> IO.println(key)
```

If the current language level or toolchain does not support unnamed variables in a specific context, use a clearly named
ignored variable such as `ignored`, but do not use misleading names.

## Kotlin and Java Boundaries

This project intentionally uses both Kotlin and Java. Do not migrate packages blindly.

### `util` Package

New code under `util` should be implemented in Kotlin unless compatibility requires Java.

The `util` package is where reusable helpers belong. If you create a helper that is likely to be useful outside the
current command or service, move it to `util`.

Good:

```kotlin
package top.chiloven.lukosbot2.util

object PathUtils {

    fun sanitizeFileName(
        raw: String?,
        fallback: String = "file"
    ): String {
        // shared implementation
    }

}
```

Then use it from feature code:

```kotlin
val safeName = PathUtils.sanitizeFileName(input)
```

Do not duplicate utility logic inside a command if an equivalent utility already exists.

Bad:

```kotlin
private fun sanitizeFileName(raw: String): String {
    return raw.replace("/", "_").replace("\\", "_")
}
```

Before writing a helper, check whether the behavior already exists in `util`. Unless there is a specific reason not to,
use the existing utility.

### `core` Package

The `core` package should remain Java unless there is a good reason to change it.

Core code is usually more stable and often interacts with Java-heavy APIs or project-level abstractions. Keeping it Java
reduces churn and preserves compatibility for central components.

Good reasons to introduce Kotlin in `core` might include:

- a new component is naturally coroutine-based;
- Kotlin significantly simplifies a complex data transformation;
- the surrounding code is already Kotlin;
- the change is part of an approved refactor.

Bad reasons include:

- “Kotlin looks nicer”;
- “I touched the file anyway”;
- “I want all packages to be Kotlin”.

## Shared Utilities and Reuse

During development, if you write a method or function that has reusable value, **extract it** to `util`.

A helper has reusable value when:

- it is not tied to one command’s business logic;
- it handles formatting, parsing, encoding, HTTP setup, time, paths, text processing, compression, or similar
  infrastructure concerns;
- it is likely to be needed by another command or service;
- it fixes a bug that could appear in more than one place.

For example, HTTP JSON request helpers should not be reimplemented inside every command or service. The project already
provides `HttpJson` for the common “GET a JSON API and parse the response” workflow, including query parameters, default
JSON headers, proxy-aware `OkHttpClient` reuse, content decoding, charset handling, JSON parsing, and root-type
validation.

Good:

```kotlin
val root = HttpJson.getObject(
    uri = "https://api.example.com/v1/search",
    params = mapOf(
        "q" to query,
        "limit" to "20"
    )
)
```

Good, when the API returns an array root:

```kotlin
val items = HttpJson.getArray(
    uri = "https://api.example.com/v1/items",
    params = mapOf(
        "page" to page.toString()
    )
)
```

Bad:

```kotlin
val request = Request.Builder()
    .url("https://api.example.com/v1/search?q=$query&limit=20")
    .get()
    .header("Accept", "application/json")
    .header("Accept-Encoding", "identity")
    .header("User-Agent", Constants.UA)
    .build()

val response = client.newCall(request).execute().use { resp ->
    if (!resp.isSuccessful) {
        throw IOException("HTTP ${resp.code}")
    }

    MAPPER.readTree(resp.body.string()).asObject()
}
```

The bad example duplicates behavior that already exists in `HttpJson`: URL building, query encoding, default headers,
proxy-aware client reuse, response decoding, charset handling, error extraction, and JSON root validation. Use
`HttpJson.getAny`, `HttpJson.getObject`, or `HttpJson.getArray` unless the call genuinely needs lower-level HTTP
behavior.

The same rule applies to path handling. The project already has `PathUtils` for safe file names, archive entry paths,
temporary sibling paths, and quiet cleanup.

Good:

```kotlin
val safeName = PathUtils.sanitizeFileName(
    name = remoteTitle,
    fallback = "download",
    maxLength = 120
)
```

Bad:

```kotlin
val safeName = remoteTitle
    .replace("/", "_")
    .replace("\\", "_")
    .replace(":", "_")
    .take(120)
```

If a suitable utility exists, use it. Do not write a second local version unless there is a documented reason.

## Command and Feature Code

Command code should focus on command behavior, not infrastructure.

For Brigadier command registration, use the project’s builder helpers instead of repeatedly spelling out raw builder
types. This keeps command trees consistent and reduces import noise.

Good:

```kotlin
dispatcher.register(
    literal(name())
        .then(
            literal("search")
                .then(
                    argument("text", StringArgumentType.string())
                        .executes { ctx ->
                            val text = StringArgumentType.getString(ctx, "text")
                            search(ctx.source, text)
                        }
                )
        )
)
```

Bad:

```kotlin
dispatcher.register(
    LiteralArgumentBuilder.literal<CommandSource>("search")
        .then(
            RequiredArgumentBuilder.argument<CommandSource, String>(
                "text",
                StringArgumentType.string()
            )
        )
)
```

When sending messages, use the project’s outbound message model instead of directly depending on one platform’s SDK from
command logic.

Good:

```java
src.send(OutboundMessage.text(src.addr(), "Done."));
```

Bad:

```java
telegramClient.execute(new SendMessage(chatId, "Done."));
```

A command can decide *what* should happen. Shared conversion, protocol, platform, HTTP, path, and state-store behavior
should stay in the appropriate `util`, `core`, `platform`, or service package.

## Configuration Classes

Configuration classes should be easy to bind from `application.yml` and easy to inspect.

Kotlin configuration models should generally use `data class` when they are primarily data holders:

```kotlin
@ConfigurationProperties(prefix = "lukos.image")
data class ImageConfig(
    var theme: String = "light"
)
```

Prefer mutable `var` properties with defaults for Spring Boot configuration binding unless constructor binding is
intentionally used and verified.

Nested config groups should also be data classes when they carry state:

```kotlin
data class Music(
    var spotify: Spotify = Spotify(),
    var soundcloud: SoundCloud = SoundCloud()
)
```

If a config group is intentionally empty, use a Kotlin `object` or `data object` rather than adding meaningless fake
fields.

## Nullability

Be explicit about nullability in Kotlin.

Use nullable types when a value may truly be absent from configuration or runtime input:

```kotlin
var accessToken: String? = null
```

Do not use nullable types simply to avoid initializing a property. Prefer defaults when a safe default exists:

```kotlin
var enabled: Boolean = false
var token: String = ""
```

When Java callers use Kotlin code, remember that Kotlin nullability affects generated method contracts. Keep public APIs
conservative and predictable.

## Examples of Preferred Formatting

### Java Method

This example follows the style used by `BrigadierUtils.registerAliases`: the method has three parameters, so each
parameter is placed on its own line. The stream source keeps `.stream()` on the same line as the data, and every stream
operation after that gets its own line.

```java
public static <S> void registerAliases(
        @NonNull CommandDispatcher<S> dispatcher,
        @NonNull String mainCommand,
        @NonNull List<String> aliases
) {
    if (aliases.isEmpty()) return;

    CommandNode<S> targetNode = dispatcher.getRoot().getChild(mainCommand);
    if (targetNode == null) {
        throw new IllegalStateException("Main command not registered yet: " + mainCommand);
    }

    aliases.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(alias -> !alias.isEmpty())
            .forEach(alias -> registerAlias(dispatcher, targetNode, alias));
}
```

### Kotlin Function

This example follows the shape of `HttpJson.getObject`: a utility function with multiple parameters should use one
parameter per line, defaults should remain visible, and the function body should not have extra blank lines after the
opening brace.

```kotlin
@Throws(IOException::class)
fun getObject(
    uri: URI,
    params: Map<String, String?>? = null,
    headers: Map<String, String>? = DEFAULT_HEADERS,
    readTimeoutMs: Int = DEFAULT_READ_TIMEOUT
): ObjectNode {
    val root = getAny(
        uri = uri,
        params = params,
        headers = headers,
        readTimeoutMs = readTimeoutMs
    )
    return root.asObjectOpt().orElseThrow {
        IllegalArgumentException("Response JSON is not an object")
    }
}
```

### Java Record

This example follows the project’s message model style, such as `Address`.

```java
public record Address(
        ChatPlatform platform,
        long chatId,
        boolean group
) {

}
```

### Kotlin Data Class

This example follows the project’s GitHub command data model style, such as `SearchParams`.

```kotlin
data class SearchParams(
    val keywords: String,
    val top: Int = 3,
    val language: String? = null,
    val sort: String? = null,
    val order: String? = null
)
```

### Java Stream

```java
List<IBotCommand> enabledCommands = commands.stream()
        .filter(IBotCommand::enabled)
        .sorted(Comparator.comparing(IBotCommand::name))
        .toList();
```

## Review Expectations

Before opening a pull request, check the following:

- The commit messages follow Conventional Commit format.
- Public APIs are intentional.
- Private helpers are marked `private`.
- Reusable helpers are placed in `util`.
- Existing `util` functions are reused instead of duplicated.
- `core` remains Java unless a Kotlin migration is justified.
- Interface names follow the `IName` convention.
- Parameters, records, data classes, annotations, streams, and enums follow the formatting rules above.
- Feature code uses shared project utilities such as `HttpJson`, `PathUtils`, `OkHttpUtils`, and Brigadier builder
  helpers instead of duplicating infrastructure logic.
- Configuration models are readable and bind cleanly from `application.yml`.

When in doubt, optimize for maintainability and review clarity. Code is read more often than it is written.
