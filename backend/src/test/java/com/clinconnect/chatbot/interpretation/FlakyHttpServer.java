package com.clinconnect.chatbot.interpretation;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A tiny, dependency-free fake HTTP peer for {@link PythonInterpretationClientTest}: accepts
 * real TCP connections on an ephemeral loopback port and, per connection, either closes
 * abruptly with no HTTP response at all (simulating a genuine transport-level failure —
 * connection reset — deterministically, no timing races) or writes a minimal valid HTTP/1.1
 * response. Plain sockets rather than a library (e.g. WireMock): this project avoids adding a
 * dependency unless a phase explicitly requires one (see planning/PHASE-STATUS.md Phase 3
 * deviation #2 for the same reasoning applied to {@code RestClient}).
 */
final class FlakyHttpServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final Thread acceptThread;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final int abruptFailures;
    private final int errorStatus;
    private final String successBody;
    private volatile boolean running = true;

    /**
     * @param abruptFailures number of leading connections to accept then close with no response
     * @param errorStatus    if > 0, every connection past {@code abruptFailures} gets this HTTP
     *                       status instead of a 200; ignored when 0
     * @param successBody    the JSON body returned on a non-error, non-abrupt-failure response
     */
    FlakyHttpServer(int abruptFailures, int errorStatus, String successBody) throws IOException {
        this.abruptFailures = abruptFailures;
        this.errorStatus = errorStatus;
        this.successBody = successBody;
        this.serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        this.acceptThread = new Thread(this::acceptLoop, "flaky-http-server");
        this.acceptThread.setDaemon(true);
        this.acceptThread.start();
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    int requestCount() {
        return requestCount.get();
    }

    private void acceptLoop() {
        while (running) {
            try (Socket socket = serverSocket.accept()) {
                int n = requestCount.incrementAndGet();
                if (n <= abruptFailures) {
                    continue; // try-with-resources closes the socket: no HTTP response at all
                }
                int status = errorStatus > 0 ? errorStatus : 200;
                String body = errorStatus > 0 ? "{}" : successBody;
                writeResponse(socket, status, body);
            } catch (IOException e) {
                // Expected on shutdown when close() closes the listening socket mid-accept().
            }
        }
    }

    private static void writeResponse(Socket socket, int status, String body) throws IOException {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + status + " X\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(bodyBytes);
        out.flush();
    }

    @Override
    public void close() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
