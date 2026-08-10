package net.sahibnanda.portfolio.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.sahibnanda.portfolio.config.GroqProperties;
import net.sahibnanda.portfolio.exception.GroqCallException;
import net.sahibnanda.portfolio.models.GroqCallRequest;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GroqClient#callStream}. There is no MockWebServer /
 * WireMock dependency on this project's test classpath (confirmed via pom.xml),
 * and every existing {@code client/*Test} in this codebase
 * (GitHub/LeetCode/Codeforces/Groq) is a live test hitting a real endpoint
 * rather than a mocked one, so there's no established in-repo pattern for
 * mocking OkHttp responses either. Rather than add a new dependency, these
 * tests spin up a tiny raw-socket HTTP responder ({@link FakeHttpServer}, built
 * entirely from {@code java.net}/{@code java.io}, already transitively
 * available) that lets us script exact status codes, bodies, and abrupt
 * connection closes -- including the mid-stream close needed to exercise the
 * {@link IOException} wrapping path.
 */
class GroqClientStreamTest {

  private static GroqClient clientFor(final int port) {
    return new GroqClient(new GroqProperties("http://127.0.0.1:" + port + "/",
        List.of("test-api-key")));
  }

  private static GroqCallRequest sampleRequest() {
    return GroqCallRequest.builder().model("llama-3.1-8b-instant")
        .messages(List.of(GroqCallRequest.Message.builder().role("user")
            .content("hello").build()))
        .stream(true).build();
  }

  @Test
  void callStreamParsesWellFormedMultiChunkSseBody() throws IOException {
    String body =
        "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n"
            + "data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}\n\n"
            + "data: {\"choices\":[{\"delta\":{\"content\":\"!\"}}]}\n\n"
            + "data: [DONE]\n\n";

    try (FakeHttpServer server = FakeHttpServer.streaming(200, "OK", body)) {

      GroqClient client = clientFor(server.port());
      List<String> deltas = new ArrayList<>();

      String result = client.callStream(sampleRequest(), deltas::add);

      assertThat(deltas).containsExactly("Hello", " world", "!");
      assertThat(result).isEqualTo("Hello world!");
    }
  }

  @Test
  void callStreamSkipsLinesThatAreNotDataPrefixed() throws IOException {
    String body = ": keep-alive\n\n"
        + "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\n" + "\n"
        + "data: [DONE]\n\n";

    try (FakeHttpServer server = FakeHttpServer.streaming(200, "OK", body)) {

      GroqClient client = clientFor(server.port());
      List<String> deltas = new ArrayList<>();

      String result = client.callStream(sampleRequest(), deltas::add);

      assertThat(deltas).containsExactly("Hi");
      assertThat(result).isEqualTo("Hi");
    }
  }

  @Test
  void callStreamStopsAtDoneSentinelAndIgnoresTrailingChunks()
      throws IOException {
    String body = "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\n"
        + "data: [DONE]\n\n"
        + "data: {\"choices\":[{\"delta\":{\"content\":\"unreachable\"}}]}\n\n";

    try (FakeHttpServer server = FakeHttpServer.streaming(200, "OK", body)) {

      GroqClient client = clientFor(server.port());
      List<String> deltas = new ArrayList<>();

      String result = client.callStream(sampleRequest(), deltas::add);

      assertThat(deltas).containsExactly("Hi");
      assertThat(result).isEqualTo("Hi");
    }
  }

  @Test
  void callStreamThrowsGroqCallExceptionOnNonSuccessfulResponse()
      throws IOException {
    try (FakeHttpServer server = FakeHttpServer.streaming(401, "Unauthorized",
        "{\"error\":\"invalid_api_key\"}")) {

      GroqClient client = clientFor(server.port());
      List<String> deltas = new ArrayList<>();

      assertThatThrownBy(() -> client.callStream(sampleRequest(), deltas::add))
          .isInstanceOf(GroqCallException.class)
          .hasMessageContaining("Groq API request failed. HTTP 401")
          .hasMessageContaining("invalid_api_key");

      assertThat(deltas).isEmpty();
    }
  }

  @Test
  void callStreamWrapsIoExceptionFromTruncatedResponse() throws IOException {
    String body =
        "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n";

    try (FakeHttpServer server = FakeHttpServer.truncated(200, "OK", body)) {

      GroqClient client = clientFor(server.port());
      List<String> deltas = new ArrayList<>();

      assertThatThrownBy(() -> client.callStream(sampleRequest(), deltas::add))
          .isInstanceOf(GroqCallException.class)
          .hasMessage("Failed to stream from Groq API.")
          .hasCauseInstanceOf(IOException.class);
    }
  }

  /**
   * Minimal single-connection HTTP responder backed by a raw
   * {@link ServerSocket}. Accepts exactly one connection, discards the request,
   * and writes back a hand-scripted response so tests can control status codes,
   * SSE bodies, and (via {@link #truncated}) an abrupt mid-body connection
   * close that OkHttp surfaces as an {@link IOException}.
   */
  private static final class FakeHttpServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final ExecutorService executor;

    private FakeHttpServer(final ServerSocket serverSocket,
        final int statusCode, final String statusText, final String body,
        final boolean truncate) {
      this.serverSocket = serverSocket;
      this.executor = Executors.newSingleThreadExecutor();
      this.executor.submit(() -> serve(statusCode, statusText, body, truncate));
    }

    static FakeHttpServer streaming(final int statusCode,
        final String statusText, final String body) throws IOException {
      return new FakeHttpServer(newSocket(), statusCode, statusText, body,
          false);
    }

    static FakeHttpServer truncated(final int statusCode,
        final String statusText, final String body) throws IOException {
      return new FakeHttpServer(newSocket(), statusCode, statusText, body,
          true);
    }

    private static ServerSocket newSocket() throws IOException {
      return new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    }

    int port() {
      return serverSocket.getLocalPort();
    }

    private void serve(final int statusCode, final String statusText,
        final String body, final boolean truncate) {
      try (Socket socket = serverSocket.accept()) {
        consumeRequest(socket);

        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        StringBuilder headers = new StringBuilder("HTTP/1.1 " + statusCode + " "
            + statusText + "\r\n" + "Content-Type: text/event-stream\r\n");

        if (truncate) {
          // Declare a body far larger than what we actually send, then close
          // the socket early so OkHttp sees a truncated response and throws.
          headers.append("Content-Length: ").append(bodyBytes.length * 10L)
              .append("\r\n");
        } else {
          headers.append("Connection: close\r\n");
        }
        headers.append("\r\n");

        OutputStream out = socket.getOutputStream();
        out.write(headers.toString().getBytes(StandardCharsets.UTF_8));
        out.write(bodyBytes);
        out.flush();
      } catch (IOException ignored) {
        // Client closed the connection or read what it needed; nothing to
        // report from the fake server side.
      }
    }

    private static void consumeRequest(final Socket socket) {
      try {
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(socket.getInputStream(),
                StandardCharsets.UTF_8));
        String line = reader.readLine();
        while (line != null && !line.isEmpty()) {
          line = reader.readLine();
        }
      } catch (IOException ignored) {
        // Best-effort only; the response is written regardless.
      }
    }

    @Override
    public void close() throws IOException {
      executor.shutdown();
      try {
        executor.awaitTermination(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      serverSocket.close();
    }
  }
}
