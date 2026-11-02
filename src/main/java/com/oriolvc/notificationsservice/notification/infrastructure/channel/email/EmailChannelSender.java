package com.oriolvc.notificationsservice.notification.infrastructure.channel.email;

import com.oriolvc.notificationsservice.notification.application.ports.ChannelSender;
import com.oriolvc.notificationsservice.notification.domain.Delivery;
import com.oriolvc.notificationsservice.notification.domain.SendResult;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends one plain-text email per delivery over SMTP (D-8) and stores its {@code Message-ID}. Failures are classified
 * (RFC 9): an SMTP 5xx reply or an unusable message is permanent; an SMTP 4xx reply, a connection problem, a timeout or
 * anything unrecognised is retryable.
 */
@Component
class EmailChannelSender implements ChannelSender {

    /** Provider messages are kept for the audit trail, bounded. */
    static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final JavaMailSender mailSender;
    private final String from;

    EmailChannelSender(final JavaMailSender mailSender, @Value("${notifications.mail.from}") final String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public SendResult send(final Delivery delivery) {
        final MimeMessage message = mailSender.createMimeMessage();
        try {
            final MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(delivery.address());
            helper.setSubject(delivery.content().subject());
            helper.setText(delivery.content().body(), false);
            mailSender.send(message);
            return SendResult.success(message.getMessageID());
        } catch (final MessagingException e) {
            return SendResult.permanent("INVALID_MESSAGE", message(e));
        } catch (final MailException e) {
            return classify(e);
        }
    }

    static SendResult classify(final MailException exception) {
        if (exception instanceof MailParseException || exception instanceof MailPreparationException) {
            return SendResult.permanent("INVALID_MESSAGE", message(exception));
        }
        if (exception instanceof MailAuthenticationException) {
            // Our credentials, not the message: retrying helps once they are fixed
            return SendResult.retryable("SMTP_AUTHENTICATION", message(exception));
        }
        final Optional<Integer> replyCode = smtpReplyCode(exception);
        if (replyCode.isPresent()) {
            final String code = "SMTP_" + replyCode.get();
            return replyCode.get() >= 500
                    ? SendResult.permanent(code, message(exception))
                    : SendResult.retryable(code, message(exception));
        }
        if (causes(exception).stream()
                .anyMatch(cause -> cause instanceof ConnectException
                        || cause instanceof UnknownHostException
                        || cause instanceof SocketTimeoutException)) {
            return SendResult.retryable("CONNECTION", message(exception));
        }
        return SendResult.retryable("UNKNOWN", message(exception));
    }

    /** The SMTP reply code of a rejected MAIL FROM, RCPT TO or DATA, wherever Spring or Angus nested it. */
    private static Optional<Integer> smtpReplyCode(final MailException exception) {
        for (final Throwable cause : causes(exception)) {
            if (cause instanceof SMTPAddressFailedException failed) {
                return Optional.of(failed.getReturnCode());
            }
            if (cause instanceof SMTPSendFailedException failed) {
                return Optional.of(failed.getReturnCode());
            }
        }
        return Optional.empty();
    }

    /** The exception and everything it wraps: causes, Angus' next exceptions and Spring's per-message failures. */
    private static Set<Throwable> causes(final Throwable exception) {
        final Set<Throwable> seen = new HashSet<>();
        final Deque<Throwable> pending = new ArrayDeque<>(List.of(exception));
        while (!pending.isEmpty()) {
            final Throwable current = pending.pop();
            if (!seen.add(current)) {
                continue;
            }
            final List<Throwable> wrapped = new ArrayList<>();
            wrapped.add(current.getCause());
            if (current instanceof MessagingException messaging) {
                wrapped.add(messaging.getNextException());
            }
            if (current instanceof MailSendException send) {
                wrapped.addAll(List.of(send.getMessageExceptions()));
            }
            wrapped.stream().filter(Objects::nonNull).forEach(pending::push);
        }
        return seen;
    }

    private static String message(final Exception exception) {
        final String message = String.valueOf(exception.getMessage());
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH ? message : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
