package com.oriolvc.notificationsservice;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/** An in-memory SMTP server shared like the Postgres container; {@code spring.mail.port} points to it (the host is in the test profile). */
@TestConfiguration(proxyBeanMethods = false)
class GreenMailConfiguration {

    @Bean(initMethod = "start", destroyMethod = "stop")
    GreenMail greenMail() {
        return new GreenMail(new ServerSetup(freePort(), "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
    }

    @Bean
    DynamicPropertyRegistrar mailProperties(final GreenMail greenMail) {
        return registry ->
                registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
    }

    static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
