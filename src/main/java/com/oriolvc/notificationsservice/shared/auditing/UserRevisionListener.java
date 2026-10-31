package com.oriolvc.notificationsservice.shared.auditing;

import org.hibernate.envers.RevisionListener;

/** Stores the same user id as Spring Data auditing on every Envers revision. */
class UserRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        ((RevisionInfo) revisionEntity).recordUser(Auditor.current());
    }
}
