package com.oriolvc.notificationsservice;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * A minimal SMTP server that answers every {@code RCPT TO} with a fixed reply, e.g. {@code 550 5.1.1 Mailbox unknown}.
 * GreenMail accepts every recipient, so SMTP rejections are simulated with this.
 */
public final class ScriptedSmtpServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final String rcptReply;

    public ScriptedSmtpServer(final String rcptReply) {
        this.rcptReply = rcptReply;
        try {
            serverSocket = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        Thread.ofVirtual().start(this::acceptConnections);
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    private void acceptConnections() {
        while (!serverSocket.isClosed()) {
            try {
                final Socket socket = serverSocket.accept();
                Thread.ofVirtual().start(() -> converse(socket));
            } catch (final SocketException closed) {
                return;
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private void converse(final Socket socket) {
        try (socket;
                BufferedReader in =
                        new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.US_ASCII)) {
            reply(out, "220 scripted ESMTP");
            String line;
            while ((line = in.readLine()) != null) {
                final String command =
                        line.length() < 4 ? line : line.substring(0, 4).toUpperCase(Locale.ROOT);
                switch (command) {
                    case "RCPT" -> reply(out, rcptReply);
                    case "DATA" -> reply(out, "554 5.5.1 No valid recipients");
                    case "QUIT" -> {
                        reply(out, "221 Bye");
                        return;
                    }
                    default -> reply(out, "250 OK");
                }
            }
        } catch (final IOException ignored) {
            // The client went away
        }
    }

    private static void reply(final PrintWriter out, final String line) {
        out.print(line + "\r\n");
        out.flush();
    }

    @Override
    public void close() {
        try {
            serverSocket.close();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
