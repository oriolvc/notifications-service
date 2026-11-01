package com.oriolvc.notificationsservice.distribution.infrastructure.persistence;

import com.oriolvc.notificationsservice.shared.auditing.AuditedDbo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.envers.Audited;

/** Row of {@code distribution_list_members}; Envers keeps its history in {@code distribution_list_members_aud}. */
@Entity
@Audited
@Table(name = "distribution_list_members")
class MemberDbo extends AuditedDbo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "distribution_list_members_id_seq")
    @SequenceGenerator(
            name = "distribution_list_members_id_seq",
            sequenceName = "distribution_list_members_id_seq",
            allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "distribution_list_id", nullable = false, updatable = false)
    private DistributionListDbo distributionList;

    @Column(name = "address", nullable = false, updatable = false)
    private String address;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected MemberDbo() {}

    MemberDbo(final DistributionListDbo distributionList, final String address, final boolean active) {
        this.distributionList = distributionList;
        this.address = address;
        this.active = active;
    }

    Long getId() {
        return id;
    }

    String getAddress() {
        return address;
    }

    boolean isActive() {
        return active;
    }

    void setActive(final boolean active) {
        this.active = active;
    }
}
