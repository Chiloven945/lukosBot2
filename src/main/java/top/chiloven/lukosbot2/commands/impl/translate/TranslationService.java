package top.chiloven.lukosbot2.commands.impl.translate;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import tools.jackson.databind.node.ObjectNode;
import top.chiloven.lukosbot2.config.CommandConfigProp.Translate;
import top.chiloven.lukosbot2.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static top.chiloven.lukosbot2.util.JsonUtils.MAPPER;

public class TranslationService {

    private static final String IMAGE_NAME = "libretranslate/libretranslate:latest";
    private static final String CONTAINER_NAME = "lukos-libretranslate";
    private static final int CONTAINER_PORT = 5000;
    private static final int HOST_PORT = 5000;

    private final String baseUrl;
    private final String defaultLang;

    private final HttpClient httpClient;
    private final DockerClient dockerClient;

    public TranslationService(Translate translate) {
        this.defaultLang = translate.getDefaultLang() != null
                ? translate.getDefaultLang()
                : "en";

        if (translate.getUrl() != null && !translate.getUrl().isBlank()) {
            this.baseUrl = normalizeBaseUrl(translate.getUrl());
            this.dockerClient = null;
        } else {
            this.baseUrl = "http://127.0.0.1:" + HOST_PORT;
            this.dockerClient = createDockerClient();
            ensureLibreTranslateContainer();
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 对外翻译接口
     */
    public String translate(String from, String to, String text) {
        String src = (from == null || from.isBlank()) ? "auto" : from;
        String tgt = (to == null || to.isBlank()) ? defaultLang : to;

        try {
            String body = "q=" + StringUtils.encodeTo(text)
                    + "&source=" + StringUtils.encodeTo(src)
                    + "&target=" + StringUtils.encodeTo(tgt)
                    + "&format=text";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/translate"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                return "翻译失败（HTTP " + resp.statusCode() + "）：" + resp.body();
            }

            return extractTranslatedText(resp.body());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "翻译失败：" + e.getMessage();
        }
    }

    private String extractTranslatedText(String body) {
        try {
            ObjectNode obj = MAPPER.readTree(body).asObject();
            if (obj.has("translatedText") && !obj.get("translatedText").isNull()) {
                return obj.get("translatedText").asString();
            }
            return "翻译结果缺失：" + body;
        } catch (Exception e) {
            return "解析翻译结果失败：" + body;
        }
    }

    /* ===================== Docker 部分 ===================== */

    private DockerClient createDockerClient() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }

    private void ensureLibreTranslateContainer() {
        try {
            // 先找现有容器
            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .exec();

            Container existing = containers.stream()
                    .filter(c -> {
                        String[] names = c.getNames();
                        return names != null && Arrays.asList(names).contains("/" + CONTAINER_NAME);
                    })
                    .findFirst()
                    .orElse(null);

            String containerId;
            if (existing == null) {
                dockerClient.pullImageCmd(IMAGE_NAME).start().awaitCompletion();

                Ports portBindings = new Ports();
                portBindings.bind(
                        ExposedPort.tcp(CONTAINER_PORT),
                        Ports.Binding.bindPort(HOST_PORT)
                );

                HostConfig hostConfig = HostConfig.newHostConfig()
                        .withPortBindings(portBindings);

                CreateContainerResponse created = dockerClient.createContainerCmd(IMAGE_NAME)
                        .withName(CONTAINER_NAME)
                        .withHostConfig(hostConfig)
                        .exec();

                containerId = created.getId();
            } else {
                containerId = existing.getId();
            }

            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
            if (!Boolean.TRUE.equals(inspect.getState().getRunning())) {
                dockerClient.startContainerCmd(containerId).exec();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("启动 LibreTranslate 容器时被中断", e);
        } catch (DockerException e) {
            throw new IllegalStateException("确保 LibreTranslate 容器运行失败，请确认 Docker 已安装并正在运行", e);
        }
    }

    private String normalizeBaseUrl(String url) {
        String u = url.trim();
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

}
