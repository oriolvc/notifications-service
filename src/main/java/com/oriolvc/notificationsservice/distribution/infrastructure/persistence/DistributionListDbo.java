package com.oriolvc.notificationsservice.distribution.infrastructure.persistence;

import com.oriolvc.notificationsservice.shared.auditing.AuditedDbo;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.envers.Audited;

/** Row of {@code distribution_lists}; Envers keeps its history in {@code distribution_lists_aud}. */
@Entity
@Audited
@Table(name = "distribution_lists")
class DistributionListDbo extends AuditedDbo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "distribution_lists_id_seq")
    @SequenceGenerator(
            name = "distribution_lists_id_seq",
            sequenceName = "distribution_lists_id_seq",
            allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private long customerId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "notification_type_id", nullable = false, updatable = false)
    private long notificationTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false)
    private Channel channel;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** New members are persisted with the list when it is flushed. */
    @OneToMany(mappedBy = "distributionList", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<MemberDbo> members = new ArrayList<>();

    Long getId() {
        return id;
    }

    long getCustomerId() {
        return customerId;
    }

    void setCustomerId(final long customerId) {
        this.customerId = customerId;
    }

    String getName() {
        return name;
    }

    void setName(final String name) {
        this.name = name;
    }

    long getNotificationTypeId() {
        return notificationTypeId;
    }

    void setNotificationTypeId(final long notificationTypeId) {
        this.notificationTypeId = notificationTypeId;
    }

    Channel getChannel() {
        return channel;
    }

    void setChannel(final Channel channel) {
        this.channel = channel;
    }

    boolean isActive() {
        return active;
    }

    void setActive(final boolean active) {
        this.active = active;
    }

    long getVersion() {
        return version;
    }

    List<MemberDbo> getMembers() {
        return members;
    }
}
