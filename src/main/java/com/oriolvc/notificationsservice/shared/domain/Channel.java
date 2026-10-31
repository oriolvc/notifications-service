package com.oriolvc.notificationsservice.shared.domain;

/** Delivery channel. Adding one also widens the {@code CHECK (channel IN (...))} constraints by migration. */
public enum Channel {
    EMAIL
}
