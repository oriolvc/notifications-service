package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.application.model.MemberView;
import com.oriolvc.notificationsservice.distribution.domain.Member;

final class MemberViews {

    private MemberViews() {}

    static MemberView of(final Member member) {
        return new MemberView(
                member.id(),
                member.address(),
                member.isActive(),
                member.audit().createdAt(),
                member.audit().createdBy(),
                member.audit().updatedAt(),
                member.audit().updatedBy());
    }
}
