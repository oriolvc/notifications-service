-- Tenant 101: an active and an inactive EMAIL list for REGULATORY_DOCUMENT_MATCHED.
-- Tenant 102: one active EMAIL list.
-- Explicit ids far above the sequences, so lists created by the test never collide.
INSERT INTO distribution_lists (id, customer_id, name, notification_type_id, channel, active, version, created_at, created_by, updated_at, updated_by)
SELECT v.id, v.customer_id, v.name, t.id, 'EMAIL', v.active, 0, now(), 'fixture', now(), 'fixture'
FROM notification_types t,
     (VALUES (1001, 101, 'Compliance team', true),
             (1002, 101, 'Old compliance team', false),
             (2001, 102, 'Quality alerts', true)) AS v (id, customer_id, name, active)
WHERE t.code = 'REGULATORY_DOCUMENT_MATCHED';
