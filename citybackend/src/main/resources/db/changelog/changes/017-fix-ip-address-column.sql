--liquibase formatted sql

--changeset cityprojects:017-fix-ip-address-column splitStatements:false
-- Tour 7E : ce changeset historique convertissait core.user_sessions.ip_address
-- de VARCHAR vers INET sur d'anciennes bases. Le 001-create-core-schema.sql
-- crée désormais directement la colonne en INET, ce changeset est donc no-op
-- sur toute nouvelle base.
--
-- Conservé pour : (a) historique Liquibase, (b) ajout de l'index gist sur ip_address,
-- (c) contrainte CHECK NOT NULL.

-- Index pour les performances de recherche par IP (idempotent)
CREATE INDEX IF NOT EXISTS idx_user_sessions_ip_address
    ON core.user_sessions USING gist(ip_address inet_ops);

-- Commentaire de colonne
COMMENT ON COLUMN core.user_sessions.ip_address IS 'Adresse IP du client (type PostgreSQL inet)';

-- Contrainte de validation : ip_address NOT NULL.
-- Posée via DO bloc + EXCEPTION pour idempotence (si déjà existante, ne rien faire).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_ip_address_valid'
          AND conrelid = 'core.user_sessions'::regclass
    ) THEN
        ALTER TABLE core.user_sessions
            ADD CONSTRAINT chk_ip_address_valid CHECK (ip_address IS NOT NULL);
    END IF;
END $$;

--rollback DROP INDEX IF EXISTS core.idx_user_sessions_ip_address;
--rollback ALTER TABLE core.user_sessions DROP CONSTRAINT IF EXISTS chk_ip_address_valid;
