package com.oriolvc.notificationsservice.shared.auditing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/** Envers revision: when and by whom a set of audited changes was made. */
@Entity
@Table(name = "revinfo")
@RevisionEntity(UserRevisionListener.class)
public class RevisionInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "revinfo_rev_seq")
    @SequenceGenerator(name = "revinfo_rev_seq", sequenceName = "revinfo_rev_seq", allocationSize = 1)
    @RevisionNumber
    @Column(name = "rev")
    private Long rev;

    @RevisionTimestamp
    @Column(name = "rev_timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "user_id", nullable = false)
    private String userId;

    protected RevisionInfo() {}

    public Long rev() {
        return rev;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String userId() {
        return userId;
    }

    void recordUser(String userId) {
        this.userId = userId;
    }
}
