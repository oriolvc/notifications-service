package com.oriolvc.notificationsservice.notification.infrastructure.channel.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.oriolvc.notificationsservice.ScriptedSmtpServer;
import com.oriolvc.notificationsservice.notification.domain.AttemptOutcome;
import com.oriolvc.notificationsservice.notification.domain.Delivery;
import com.oriolvc.notificationsservice.notification.domain.DeliveryStatus;
import com.oriolvc.notificationsservice.notification.domain.RenderedMessage;
import com.oriolvc.notificationsservice.notification.domain.SendResult;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Real SMTP exchanges: GreenMail for success, a scripted server for rejections, a closed port for an outage. */
class EmailChannelSenderTest {

    private final GreenMail greenMail = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));

    @AfterEach
    void stopGreenMail() {
        greenMail.stop();
    }

    @Test
    void sendsOnePlainTextMessageAndReturnsItsMessageId() throws Exception {
        greenMail.start();

        final SendResult result = sender(greenMail.getSmtp().getPort()).send(delivery("alice@customer101.example"));

        assertThat(result.outcome()).isEqualTo(AttemptOutcome.SUCCESS);
        final MimeMessage received = greenMail.getReceivedMessages()[0];
        assertThat(result.providerMessageId()).isNotBlank().isEqualTo(received.getMessageID());
        assertThat(received.getAllRecipients())
                .extracting(Object::toString)
                .containsExactly("alice@customer101.example");
        assertThat(received.getFrom()).extracting(Object::toString).containsExactly("notifications@test.example");
        assertThat(received.getSubject()).isEqualTo("FDA warning letter – Qualifyze Spain");
        assertThat(received.getContentType()).startsWith("text/plain");
        assertThat(((String) received.getContent()).strip()).isEqualTo("Qualifyze Spain, …");
    }

    @Test
    void anSmtp5xxReplyIsPermanent() {
        try (ScriptedSmtpServer server = new ScriptedSmtpServer("550 5.1.1 Mailbox unknown")) {
            final SendResult result = sender(server.port()).send(delivery("nobody@customer101.example"));

            assertThat(result.outcome()).isEqualTo(AttemptOutcome.PERMANENT_ERROR);
            assertThat(result.errorCode()).isEqualTo("SMTP_550");
            assertThat(result.errorMessage()).contains("Mailbox unknown");
        }
    }

    @Test
    void anSmtp4xxReplyIsRetryable() {
        try (ScriptedSmtpServer server = new ScriptedSmtpServer("451 4.3.0 Try again later")) {
            final SendResult result = sender(server.port()).send(delivery("alice@customer101.example"));

            assertThat(result.outcome()).isEqualTo(AttemptOutcome.RETRYABLE_ERROR);
            assertThat(result.errorCode()).isEqualTo("SMTP_451");
        }
    }

    @Test
    void anUnreachableServerIsRetryable() {
        final int closedPort;
        try (ScriptedSmtpServer server = new ScriptedSmtpServer("250 OK")) {
            closedPort = server.port();
        }

        final SendResult result = sender(closedPort).send(delivery("alice@customer101.example"));

        assertThat(result.outcome()).isEqualTo(AttemptOutcome.RETRYABLE_ERROR);
        assertThat(result.errorCode()).isEqualTo("CONNECTION");
    }

    @Test
    void anUnusableAddressIsPermanent() {
        final SendResult result = sender(1).send(delivery("not an address"));

        assertThat(result.outcome()).isEqualTo(AttemptOutcome.PERMANENT_ERROR);
        assertThat(result.errorCode()).isEqualTo("INVALID_MESSAGE");
    }

    @Test
    void classifiesSpringMailExceptions() {
        assertThat(EmailChannelSender.classify(new MailParseException("bad")).errorCode())
                .isEqualTo("INVALID_MESSAGE");
        assertThat(EmailChannelSender.classify(new MailAuthenticationException("denied")))
                .extracting(SendResult::outcome, SendResult::errorCode)
                .containsExactly(AttemptOutcome.RETRYABLE_ERROR, "SMTP_AUTHENTICATION");
        assertThat(EmailChannelSender.classify(new MailSendException(Map.of())))
                .extracting(SendResult::outcome, SendResult::errorCode)
                .containsExactly(AttemptOutcome.RETRYABLE_ERROR, "UNKNOWN");
        assertThat(EmailChannelSender.classify(new MailSendException("x".repeat(5000)))
                        .errorMessage())
                .hasSize(EmailChannelSender.MAX_ERROR_MESSAGE_LENGTH);
    }

    private static EmailChannelSender sender(final int port) {
        final JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("127.0.0.1");
        mailSender.setPort(port);
        final Properties properties = new Properties();
        properties.put("mail.smtp.connectiontimeout", "2000");
        properties.put("mail.smtp.timeout", "2000");
        mailSender.setJavaMailProperties(properties);
        return new EmailChannelSender(mailSender, "notifications@test.example");
    }

    private static Delivery delivery(final String address) {
        return Delivery.restore(
                1,
                1001,
                1101,
                Channel.EMAIL,
                address,
                1,
                new RenderedMessage("FDA warning letter – Qualifyze Spain", "Qualifyze Spain, …"),
                DeliveryStatus.PENDING,
                Instant.parse("2026-11-02T10:00:00Z"),
                null,
                null,
                List.of());
    }
}
