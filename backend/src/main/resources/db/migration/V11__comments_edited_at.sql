-- Author edit/delete of own comments (HTS-039, EP-09 stretch — relaxes FR-C6 immutability).
-- edited_at is NULL until the comment is first edited; a non-null value drives the "edited"
-- indicator in the UI. Nullable so existing rows stay valid without a backfill.
ALTER TABLE comments ADD COLUMN edited_at TIMESTAMPTZ;
