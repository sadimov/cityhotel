--liquibase formatted sql

--changeset cityprojects:018-drop-salt-column-dbusers splitStatements:true endDelimiter:;
--comment: Suppression de la colonne salt obsolete sur core.dbusers. BCrypt embarque le salt dans le hash ($2a$10$<salt+hash>), donc cette colonne separee n'a plus d'utilite depuis la migration de l'auth en BCrypt. L'entite DBUser.java ne la mappe pas -- au 1er INSERT prod, le NOT NULL aurait casse.

-- =====================================================
-- DROP COLONNE SALT OBSOLETE SUR CORE.DBUSERS
-- BCrypt embarque le salt dans le hash : la colonne separee
-- est inutile et bloque les INSERT (NOT NULL sans DEFAULT,
-- non mappee par l'entite DBUser.java).
-- =====================================================

ALTER TABLE core.dbusers DROP COLUMN salt;

--rollback ALTER TABLE core.dbusers ADD COLUMN salt VARCHAR(100);
--rollback comment: rollback partiel -- la contrainte NOT NULL initiale n'est pas restaurable sans backfill manuel (les lignes existantes n'auraient pas de valeur de salt). La colonne est reintroduite en nullable.
