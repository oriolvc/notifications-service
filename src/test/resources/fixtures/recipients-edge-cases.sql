-- On top of distribution-lists.sql (AC-10). Tenant 103 has no list at all.
-- Tenant 104: only an inactive list, with an active member. Tenant 105: an active list whose only member was removed.
INSERT INTO distribution_lists (id, customer_id, name, notification_type_id, channel, active, version, created_at, created_by, updated_at, updated_by)
SELECT v.id, v.customer_id, v.name, t.id, 'EMAIL', v.active, 0, now(), 'fixture', now(), 'fixture'
FROM notification_types t,
     (VALUES (4001, 104, 'Paused alerts', false),
             (5001, 105, 'Empty alerts', true)) AS v (id, customer_id, name, active)
WHERE t.code = 'REGULATORY_DOCUMENT_MATCHED';

INSERT INTO distribution_list_members (id, distribution_list_id, address, active, version, created_at, created_by, updated_at, updated_by)
VALUES (4101, 4001, 'erin@customer104.example', true, 0, now(), 'fixture', now(), 'fixture'),
       (5101, 5001, 'frank@customer105.example', false, 0, now(), 'fixture', now(), 'fixture');
