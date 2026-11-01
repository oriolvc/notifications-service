package com.oriolvc.notificationsservice.distribution.infrastructure.web;

import com.oriolvc.notificationsservice.distribution.application.CreateDistributionList;
import com.oriolvc.notificationsservice.distribution.application.DeleteDistributionList;
import com.oriolvc.notificationsservice.distribution.application.GetDistributionList;
import com.oriolvc.notificationsservice.distribution.application.ListDistributionLists;
import com.oriolvc.notificationsservice.distribution.application.UpdateDistributionList;
import com.oriolvc.notificationsservice.distribution.application.model.CreateDistributionListCommand;
import com.oriolvc.notificationsservice.distribution.application.model.DeleteDistributionListCommand;
import com.oriolvc.notificationsservice.distribution.application.model.DistributionListView;
import com.oriolvc.notificationsservice.distribution.application.model.GetDistributionListCommand;
import com.oriolvc.notificationsservice.distribution.application.model.ListDistributionListsCommand;
import com.oriolvc.notificationsservice.distribution.application.model.UpdateDistributionListCommand;
import com.oriolvc.notificationsservice.generated.api.DistributionListsApi;
import com.oriolvc.notificationsservice.generated.model.CreateDistributionListRequest;
import com.oriolvc.notificationsservice.generated.model.DistributionList;
import com.oriolvc.notificationsservice.generated.model.DistributionListCollection;
import com.oriolvc.notificationsservice.generated.model.UpdateDistributionListRequest;
import com.oriolvc.notificationsservice.shared.security.CurrentPrincipalProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Distribution lists of the caller's tenant; the tenant always comes from the JWT. Member operations still answer 501
 * through the generated default methods (T5).
 */
@RestController
class DistributionListController implements DistributionListsApi {

    private final CurrentPrincipalProvider principals;
    private final CreateDistributionList createDistributionList;
    private final GetDistributionList getDistributionList;
    private final ListDistributionLists listDistributionLists;
    private final UpdateDistributionList updateDistributionList;
    private final DeleteDistributionList deleteDistributionList;

    DistributionListController(
            CurrentPrincipalProvider principals,
            CreateDistributionList createDistributionList,
            GetDistributionList getDistributionList,
            ListDistributionLists listDistributionLists,
            UpdateDistributionList updateDistributionList,
            DeleteDistributionList deleteDistributionList) {
        this.principals = principals;
        this.createDistributionList = createDistributionList;
        this.getDistributionList = getDistributionList;
        this.listDistributionLists = listDistributionLists;
        this.updateDistributionList = updateDistributionList;
        this.deleteDistributionList = deleteDistributionList;
    }

    @Override
    public ResponseEntity<DistributionList> createDistributionList(CreateDistributionListRequest request) {
        DistributionListView created = createDistributionList.execute(new CreateDistributionListCommand(
                tenantId(),
                request.getName(),
                request.getNotificationType(),
                DistributionListWebMapper.toDomain(request.getChannel())));
        var location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(DistributionListWebMapper.toDto(created));
    }

    @Override
    public ResponseEntity<DistributionListCollection> listDistributionLists() {
        var items = listDistributionLists.execute(new ListDistributionListsCommand(tenantId())).stream()
                .map(DistributionListWebMapper::toDto)
                .toList();
        return ResponseEntity.ok(new DistributionListCollection(items));
    }

    @Override
    public ResponseEntity<DistributionList> getDistributionList(Long listId) {
        DistributionListView distributionList =
                getDistributionList.execute(new GetDistributionListCommand(tenantId(), listId));
        return ResponseEntity.ok(DistributionListWebMapper.toDto(distributionList));
    }

    @Override
    public ResponseEntity<DistributionList> updateDistributionList(Long listId, UpdateDistributionListRequest request) {
        DistributionListView updated = updateDistributionList.execute(new UpdateDistributionListCommand(
                tenantId(), listId, request.getName(), request.getActive(), request.getVersion()));
        return ResponseEntity.ok(DistributionListWebMapper.toDto(updated));
    }

    @Override
    public ResponseEntity<Void> deleteDistributionList(Long listId) {
        deleteDistributionList.execute(new DeleteDistributionListCommand(tenantId(), listId));
        return ResponseEntity.noContent().build();
    }

    private long tenantId() {
        return principals.current().requireTenantId();
    }
}
