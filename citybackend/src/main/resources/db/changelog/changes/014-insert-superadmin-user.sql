--liquibase formatted sql

--changeset cityprojects:014-insert-superadmin-user splitStatements:false
-- Tour 7E : splitStatements:false car le fichier contient des blocs DO $$ ... END $$;
-- qui cassent le parser Liquibase quand splitStatements:true. Le SQL complet est
-- envoye en simple-query batch a Postgres, qui execute les statements en sequence.
--
-- Tour 38 C8 (security hardening) :
--  - Idempotence stricte via WHERE NOT EXISTS (re-run safe).
--  - Compte demoadmin SUPPRIME (ne doit JAMAIS exister en prod).
--  - RAISE NOTICE 'Password: ...' SUPPRIMES (les logs Postgres sont scrappes par
--    SIEM/ELK : on ne peut pas y laisser un mot de passe en clair).
--  - Le superadmin est cree avec mot_passe_temporaire=true (cf. changeset 030)
--    et compte_verrouille=false. La rotation immediate est de la responsabilite
--    operationnelle au premier deploiement.
--  - Le hash BCrypt ci-dessous est un placeholder volontairement public connu
--    ("SuperAdmin123!") pour permettre l'amorcage en ENV de tests/dev. En PROD,
--    ce hash DOIT etre regenere par /auth/change-password des le premier login,
--    ou ecrase par un changeset env-specific.
-- IMPORTANT: rotate immediately after first deployment via /auth/change-password
--            (la mecanique d'endpoint change-password sera cablee dans un Tour suivant).

-- =====================================================
-- HOTEL SYSTEME ET UTILISATEUR SUPERADMIN
-- =====================================================

-- Tour 7E : pgcrypto requis pour gen_random_bytes() utilise pour generer le
-- salt_value historique (colonne droppee par 018, mais l'INSERT 014 doit
-- pouvoir s'executer avant 018).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Insertion idempotente de l'hotel SYSTEM (creation si absent)
INSERT INTO core.hotels (
    hotel_code,
    hotel_nom,
    hotel_adresse,
    ville,
    pays,
    email,
    devise,
    fuseau_horaire,
    actif
)
SELECT
    'SYSTEM',
    'Administration Systeme',
    'Systeme CityBackend',
    'Nouakchott',
    'Mauritanie',
    'admin@citybackend.com',
    'MRU',
    'Africa/Nouakchott',
    true
WHERE NOT EXISTS (
    SELECT 1 FROM core.hotels WHERE hotel_code = 'SYSTEM'
);

-- Creation idempotente du superadmin
DO $$
DECLARE
    system_hotel_id BIGINT;
    superadmin_role_id INTEGER;
    salt_value VARCHAR(100);
    password_hash VARCHAR(255);
BEGIN
    -- Sortie immediate si superadmin deja present (idempotence)
    IF EXISTS (SELECT 1 FROM core.dbusers WHERE username = 'superadmin') THEN
        RETURN;
    END IF;

    SELECT hotel_id INTO system_hotel_id
    FROM core.hotels
    WHERE hotel_code = 'SYSTEM';

    SELECT role_id INTO superadmin_role_id
    FROM core.roles
    WHERE role_code = 'SUPERADMIN';

    IF system_hotel_id IS NULL THEN
        RAISE EXCEPTION 'Hotel systeme non trouve';
    END IF;

    IF superadmin_role_id IS NULL THEN
        RAISE EXCEPTION 'Role SUPERADMIN non trouve';
    END IF;

    -- Salt historique requis par le schema 014 anterieur a 018 (drop salt).
    salt_value := encode(gen_random_bytes(32), 'base64');

    -- Hash BCrypt placeholder public ; rotation OBLIGATOIRE des le premier deploiement.
    password_hash := '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqfuKITSEiVyQzR8bLfOGba';

    INSERT INTO core.dbusers (
        username,
        email,
        password_hash,
        salt,
        prenom,
        nom,
        telephone,
        poste,
        hotel_id,
        role_id,
        actif,
        derniere_connexion,
        tentatives_connexion,
        compte_verrouille,
        mot_passe_temporaire
    ) VALUES (
        'superadmin',
        'superadmin@citybackend.com',
        password_hash,
        salt_value,
        'Super',
        'Administrateur',
        '+222 45 67 89 10',
        'Administrateur Systeme',
        system_hotel_id,
        superadmin_role_id,
        true,
        NULL,
        0,
        false,
        true
    );

    -- Tour 38 C8 : NE PAS logguer le mot de passe (RAISE NOTICE 'Password: ...' supprime).
    RAISE NOTICE 'Utilisateur superadmin cree (mot_passe_temporaire=true). Rotation immediate requise.';

END $$;
