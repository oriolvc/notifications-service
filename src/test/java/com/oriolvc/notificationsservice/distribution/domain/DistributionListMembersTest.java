package com.oriolvc.notificationsservice.distribution.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oriolvc.notificationsservice.distribution.domain.exceptions.InvalidAddressException;
import com.oriolvc.notificationsservice.distribution.domain.exceptions.MemberAlreadyActiveException;
import com.oriolvc.notificationsservice.distribution.domain.exceptions.MemberLimitReachedException;
import com.oriolvc.notificationsservice.distribution.domain.exceptions.MemberNotFoundException;
import com.oriolvc.notificationsservice.distribution.domain.ports.AddressValidator;
import com.oriolvc.notificationsservice.shared.domain.AuditMetadata;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class DistributionListMembersTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(Instant.EPOCH, "u", Instant.EPOCH, "u");

    /** Accepts anything containing an {@code @}; the real rules are tested in {@code EmailAddressValidatorTest}. */
    private static final AddressValidator EMAIL = new AddressValidator() {
        @Override
        public Channel channel() {
            return Channel.EMAIL;
        }

        @Override
        public void validate(final String address) {
            if (!address.contains("@")) {
                throw new InvalidAddressException(Channel.EMAIL);
            }
        }
    };

    @Test
    void addsATrimmedActiveMember() {
        final DistributionList distributionList = list(List.of());

        final Member member = distributionList.addMember("  ana@example.com ", EMAIL);

        assertThat(member.address()).isEqualTo("ana@example.com");
        assertThat(member.isActive()).isTrue();
        assertThat(member.id()).isNull();
        assertThat(distributionList.members()).containsExactly(member);
    }

    @Test
    void rejectsAnInvalidAddressWithoutAddingIt() {
        final DistributionList distributionList = list(List.of());

        assertThatThrownBy(() -> distributionList.addMember("not-an-address", EMAIL))
                .isInstanceOf(InvalidAddressException.class);
        assertThatThrownBy(() -> distributionList.addMember(null, EMAIL)).isInstanceOf(InvalidAddressException.class);
        assertThat(distributionList.members()).isEmpty();
    }

    @Test
    void anAlreadyActiveAddressIsAConflictIgnoringCase() {
        final DistributionList distributionList = list(List.of(Member.restore(1, "ana@example.com", true, AUDIT)));

        assertThatThrownBy(() -> distributionList.addMember("ANA@example.com", EMAIL))
                .isInstanceOf(MemberAlreadyActiveException.class);
    }

    @Test
    void reAddingARemovedAddressReactivatesTheSameMember() {
        final DistributionList distributionList = list(List.of(Member.restore(1, "ana@example.com", false, AUDIT)));

        final Member member = distributionList.addMember("Ana@Example.com", EMAIL);

        assertThat(member.id()).isEqualTo(1L);
        assertThat(member.address()).isEqualTo("ana@example.com");
        assertThat(member.isActive()).isTrue();
        assertThat(distributionList.members()).hasSize(1);
    }

    @Test
    void atMostOneHundredActiveMembers() {
        final List<Member> members = new ArrayList<>(LongStream.rangeClosed(1, 100)
                .mapToObj(id -> Member.restore(id, "m" + id + "@example.com", true, AUDIT))
                .toList());
        members.add(Member.restore(101, "removed@example.com", false, AUDIT));
        final DistributionList distributionList = list(members);

        assertThatThrownBy(() -> distributionList.addMember("new@example.com", EMAIL))
                .isInstanceOf(MemberLimitReachedException.class);
        assertThatThrownBy(() -> distributionList.addMember("removed@example.com", EMAIL))
                .isInstanceOf(MemberLimitReachedException.class);

        distributionList.removeMember(1);
        assertThat(distributionList.addMember("new@example.com", EMAIL).isActive())
                .isTrue();
    }

    @Test
    void removingIsASoftDeleteAndIdempotent() {
        final DistributionList distributionList = list(List.of(
                Member.restore(1, "ana@example.com", true, AUDIT), Member.restore(2, "bo@example.com", true, AUDIT)));

        distributionList.removeMember(1);
        distributionList.removeMember(1);

        assertThat(distributionList.member(1).isActive()).isFalse();
        assertThat(distributionList.members()).hasSize(2);
        assertThat(distributionList.activeMembers()).extracting(Member::id).containsExactly(2L);
    }

    @Test
    void anUnknownMemberIsNotFound() {
        final DistributionList distributionList = list(List.of(Member.restore(1, "ana@example.com", true, AUDIT)));

        assertThatThrownBy(() -> distributionList.removeMember(9)).isInstanceOf(MemberNotFoundException.class);
        assertThat(DistributionList.create(101, "New", 1, Channel.EMAIL).memberWithAddress("ana@example.com"))
                .isEmpty();
    }

    @Test
    void theValidatorMustBeForTheListsChannel() {
        final AddressValidator otherChannel = new AddressValidator() {
            @Override
            public Channel channel() {
                return null;
            }

            @Override
            public void validate(final String address) {}
        };

        assertThatThrownBy(() -> list(List.of()).addMember("ana@example.com", otherChannel))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DistributionList list(final List<Member> members) {
        return DistributionList.restore(7, 101, "Compliance", 1, Channel.EMAIL, true, 0, AUDIT, members);
    }
}
