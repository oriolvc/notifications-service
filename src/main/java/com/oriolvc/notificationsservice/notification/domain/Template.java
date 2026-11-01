package com.oriolvc.notificationsservice.notification.domain;

import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The message of one notification type on one channel, with {@code {{name}}} placeholders for top-level parameters.
 * {@code subject} is required for EMAIL and {@code null} for channels without one (RFC 8).
 */
public record Template(Channel channel, String subject, String body) {

    static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z_][A-Za-z0-9_]*)}}");

    public Template {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(body, "body");
    }

    public Set<String> placeholders() {
        final Set<String> names = new TreeSet<>();
        Stream.of(subject, body)
                .filter(Objects::nonNull)
                .forEach(text -> PLACEHOLDER.matcher(text).results().forEach(match -> names.add(match.group(1))));
        return names;
    }
}
