package chiloven.lukosbot2.platforms.onebot;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;

final class OneBotStack implements AutoCloseable {
    final WebSocket ws;

    OneBotStack(String wsUrl, String accessToken, WebSocket.Listener listener) {
        HttpClient c = HttpClient.newHttpClient();
        WebSocket.Builder b = c.newWebSocketBuilder();
        if (accessToken != null && !accessToken.isBlank()) b.header("Authorization", "Bearer " + accessToken);
        this.ws = b.buildAsync(URI.create(wsUrl), listener).join();
    }

    @Override
    public void close() {
        if (ws != null) ws.abort();
    }
}
