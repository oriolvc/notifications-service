package com.oriolvc.notificationsservice.distribution.infrastructure.web;

import com.oriolvc.notificationsservice.distribution.application.AddDistributionListMember;
import com.oriolvc.notificationsservice.distribution.application.CreateDistributionList;
import com.oriolvc.notificationsservice.distribution.application.DeleteDistributionList;
import com.oriolvc.notificationsservice.distribution.application.GetDistributionList;
import com.oriolvc.notificationsservice.distribution.application.ListDistributionListMembers;
import com.oriolvc.notificationsservice.distribution.application.ListDistributionLists;
import com.oriolvc.notificationsservice.distribution.application.RemoveDistributionListMember;
import com.oriolvc.notificationsservice.distribution.application.UpdateDistributionList;
import com.oriolvc.notificationsservice.distribution.application.model.AddDistributionListMemberCommand;
import com.oriolvc.notificationsservice.distribution.application.model.CreateDistributionListCommand;
import com.oriolvc.notificationsservice.distribution.application.model.DeleteDistributionListCommand;
import com.oriolvc.notificationsservice.distribution.application.model.DistributionListView;
import com.oriolvc.notificationsservice.distribution.application.model.GetDistributionListCommand;
import com.oriolvc.notificationsservice.distribution.application.model.ListDistributionListMembersCommand;
import com.oriolvc.notificationsservice.distribution.application.model.ListDistributionListsCommand;
import com.oriolvc.notificationsservice.distribution.application.model.MemberView;
import com.oriolvc.notificationsservice.distribution.application.model.RemoveDistributionListMemberCommand;
import com.oriolvc.notificationsservice.distribution.application.model.UpdateDistributionListCommand;
import com.oriolvc.notificationsservice.generated.api.DistributionListsApi;
import com.oriolvc.notificationsservice.generated.model.AddMemberRequest;
import com.oriolvc.notificationsservice.generated.model.CreateDistributionListRequest;
import com.oriolvc.notificationsservice.generated.model.DistributionList;
import com.oriolvc.notificationsservice.generated.model.DistributionListCollection;
import com.oriolvc.notificationsservice.generated.model.Member;
import com.oriolvc.notificationsservice.generated.model.MemberCollection;
import com.oriolvc.notificationsservice.generated.model.UpdateDistributionListRequest;
import com.oriolvc.notificationsservice.shared.security.CurrentPrincipalProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Distribution lists of the caller's tenant and their members; the tenant always comes from the JWT. */
@RestController
class DistributionListController implements DistributionListsApi {

    private final CurrentPrincipalProvider principals;
    private final CreateDistributionList createDistributionList;
    private final GetDistributionList getDistributionList;
    private final ListDistributionLists listDistributionLists;
    private final UpdateDistributionList updateDistributionList;
    private final DeleteDistributionList deleteDistributionList;
    private final AddDistributionListMember addDistributionListMember;
    private final ListDistributionListMembers listDistributionListMembers;
    private final RemoveDistributionListMember removeDistributionListMember;

    DistributionListController(
            final CurrentPrincipalProvider principals,
            final CreateDistributionList createDistributionList,
            final GetDistributionList getDistributionList,
            final ListDistributionLists listDistributionLists,
            final UpdateDistributionList updateDistributionList,
            final DeleteDistributionList deleteDistributionList,
            final AddDistributionListMember addDistributionListMember,
            final ListDistributionListMembers listDistributionListMembers,
            final RemoveDistributionListMember removeDistributionListMember) {
        this.principals = principals;
        this.createDistributionList = createDistributionList;
        this.getDistributionList = getDistributionList;
        this.listDistributionLists = listDistributionLists;
        this.updateDistributionList = updateDistributionList;
        this.deleteDistributionList = deleteDistributionList;
        this.addDistributionListMember = addDistributionListMember;
        this.listDistributionListMembers = listDistributionListMembers;
        this.removeDistributionListMember = removeDistributionListMember;
    }

    @Override
    public ResponseEntity<DistributionList> createDistributionList(final CreateDistributionListRequest request) {
        final DistributionListView created = createDistributionList.execute(new CreateDistributionListCommand(
                tenantId(),
                request.getName(),
                request.getNotificationType(),
                DistributionListWebMapper.toDomain(request.getChannel())));
        final var location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(DistributionListWebMapper.toDto(created));
    }

    @Override
    public ResponseEntity<DistributionListCollection> listDistributionLists() {
        final var items = listDistributionLists.execute(new ListDistributionListsCommand(tenantId())).stream()
                .map(DistributionListWebMapper::toDto)
                .toList();
        return ResponseEntity.ok(new DistributionListCollection(items));
    }

    @Override
    public ResponseEntity<DistributionList> getDistributionList(final Long listId) {
        final DistributionListView distributionList =
                getDistributionList.execute(new GetDistributionListCommand(tenantId(), listId));
        return ResponseEntity.ok(DistributionListWebMapper.toDto(distributionList));
    }

    @Override
    public ResponseEntity<DistributionList> updateDistributionList(
            final Long listId, final UpdateDistributionListRequest request) {
        final DistributionListView updated = updateDistributionList.execute(new UpdateDistributionListCommand(
                tenantId(), listId, request.getName(), request.getActive(), request.getVersion()));
        return ResponseEntity.ok(DistributionListWebMapper.toDto(updated));
    }

    @Override
    public ResponseEntity<Void> deleteDistributionList(final Long listId) {
        deleteDistributionList.execute(new DeleteDistributionListCommand(tenantId(), listId));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Member> addDistributionListMember(final Long listId, final AddMemberRequest request) {
        final MemberView added = addDistributionListMember.execute(
                new AddDistributionListMemberCommand(tenantId(), listId, request.getAddress()));
        final var location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(added.id())
                .toUri();
        return ResponseEntity.created(location).body(DistributionListWebMapper.toDto(added));
    }

    @Override
    public ResponseEntity<MemberCollection> listDistributionListMembers(final Long listId) {
        final var items =
                listDistributionListMembers.execute(new ListDistributionListMembersCommand(tenantId(), listId)).stream()
                        .map(DistributionListWebMapper::toDto)
                        .toList();
        return ResponseEntity.ok(new MemberCollection(items));
    }

    @Override
    public ResponseEntity<Void> removeDistributionListMember(final Long listId, final Long memberId) {
        removeDistributionListMember.execute(new RemoveDistributionListMemberCommand(tenantId(), listId, memberId));
        return ResponseEntity.noContent().build();
    }

    private long tenantId() {
        return principals.current().requireTenantId();
    }
}
