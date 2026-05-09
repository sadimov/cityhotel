--liquibase formatted sql

--changeset cityprojects:014-insert-superadmin-user splitStatements:false
-- Tour 7E : splitStatements:false car le fichier contient 3 blocs DO $$ ... END $$;
-- qui cassent le parser Liquibase quand splitStatements:true. Le SQL complet est
-- envoyé en simple-query batch à Postgres, qui exécute les statements en séquence.

-- =====================================================
-- CRÉATION DE L'HÔTEL SYSTÈME ET UTILISATEUR SUPERADMIN
-- =====================================================

-- Tour 7E : pgcrypto requis pour gen_random_bytes() utilisé plus bas
-- pour générer le `salt_value` historique (colonne droppée par 018,
-- mais l'INSERT 014 doit pouvoir s'exécuter avant 018).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Insérer l'hôtel système pour le superadmin
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
) VALUES (
    'SYSTEM',
    'Administration Système',
    'Système CityBackend',
    'Nouakchott',
    'Mauritanie',
    'admin@citybackend.com',
    'MRU',
    'Africa/Nouakchott',
    true
);

-- Récupérer l'ID de l'hôtel système et du rôle SUPERADMIN
DO $$
DECLARE
    system_hotel_id BIGINT;
    superadmin_role_id INTEGER;
    salt_value VARCHAR(100);
    password_hash VARCHAR(255);
BEGIN
    -- Récupérer l'ID de l'hôtel système
    SELECT hotel_id INTO system_hotel_id 
    FROM core.hotels 
    WHERE hotel_code = 'SYSTEM';
    
    -- Récupérer l'ID du rôle SUPERADMIN
    SELECT role_id INTO superadmin_role_id 
    FROM core.roles 
    WHERE role_code = 'SUPERADMIN';
    
    -- Vérifier que les IDs ont été trouvés
    IF system_hotel_id IS NULL THEN
        RAISE EXCEPTION 'Hôtel système non trouvé';
    END IF;
    
    IF superadmin_role_id IS NULL THEN
        RAISE EXCEPTION 'Rôle SUPERADMIN non trouvé';
    END IF;
    
    -- Générer un salt unique (en production, cela sera fait par l'application)
    salt_value := encode(gen_random_bytes(32), 'base64');
    
    -- Hash du mot de passe "SuperAdmin123!" avec BCrypt simulation
    -- En production, ce sera fait par l'application Spring Boot
    -- Hash BCrypt pour "SuperAdmin123!" avec salt intégré
    password_hash := '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqfuKITSEiVyQzR8bLfOGba';
    
    -- Insérer l'utilisateur superadmin
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
        compte_verrouille
    ) VALUES (
        'superadmin',
        'superadmin@citybackend.com',
        password_hash,
        salt_value,
        'Super',
        'Administrateur',
        '+222 45 67 89 10',
        'Administrateur Système',
        system_hotel_id,
        superadmin_role_id,
        true,
        NULL,
        0,
        false
    );
    
    RAISE NOTICE 'Utilisateur superadmin créé avec succès';
    RAISE NOTICE 'Username: superadmin';
    RAISE NOTICE 'Password: SuperAdmin123!';
    RAISE NOTICE 'Hotel ID: %', system_hotel_id;
    RAISE NOTICE 'Role ID: %', superadmin_role_id;
    
END $$;

-- =====================================================
-- CRÉATION D'UN HÔTEL DE DÉMONSTRATION (OPTIONNEL)
-- =====================================================

-- Insérer un hôtel de démonstration
INSERT INTO core.hotels (
    hotel_code, 
    hotel_nom, 
    hotel_adresse, 
    hotel_tel,
    ville, 
    pays, 
    boite_postale,
    email, 
    site_web,
    devise, 
    fuseau_horaire, 
    actif
) VALUES (
    'DEMO01',
    'Hôtel de Démonstration',
    'Avenue de l''Indépendance, Nouakchott',
    '+222 45 25 67 89',
    'Nouakchott',
    'Mauritanie',
    'BP 1234',
    'contact@hotel-demo.mr',
    'www.hotel-demo.mr',
    'MRU',
    'Africa/Nouakchott',
    true
);

-- Créer un utilisateur admin pour l'hôtel de démonstration
DO $$
DECLARE
    demo_hotel_id BIGINT;
    admin_role_id INTEGER;
    salt_value VARCHAR(100);
    password_hash VARCHAR(255);
BEGIN
    -- Récupérer l'ID de l'hôtel de démo
    SELECT hotel_id INTO demo_hotel_id 
    FROM core.hotels 
    WHERE hotel_code = 'DEMO01';
    
    -- Récupérer l'ID du rôle ADMIN
    SELECT role_id INTO admin_role_id 
    FROM core.roles 
    WHERE role_code = 'ADMIN';
    
    -- Vérifier que les IDs ont été trouvés
    IF demo_hotel_id IS NULL THEN
        RAISE EXCEPTION 'Hôtel de démonstration non trouvé';
    END IF;
    
    IF admin_role_id IS NULL THEN
        RAISE EXCEPTION 'Rôle ADMIN non trouvé';
    END IF;
    
    -- Générer un salt unique
    salt_value := encode(gen_random_bytes(32), 'base64');
    
    -- Hash du mot de passe "DemoAdmin123!" 
    password_hash := '$2a$12$8UzBtE1qF3mRvS7VtLZjHugKoJQf7qDfwLkMrKJ9pX5xN2VmWqBCS';
    
    -- Insérer l'utilisateur admin de démo
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
        compte_verrouille
    ) VALUES (
        'demoadmin',
        'admin@hotel-demo.mr',
        password_hash,
        salt_value,
        'Admin',
        'Démonstration',
        '+222 45 25 67 90',
        'Administrateur Hôtel',
        demo_hotel_id,
        admin_role_id,
        true,
        NULL,
        0,
        false
    );
    
    RAISE NOTICE 'Utilisateur admin de démonstration créé avec succès';
    RAISE NOTICE 'Username: demoadmin';
    RAISE NOTICE 'Password: DemoAdmin123!';
    RAISE NOTICE 'Hotel: DEMO01 - Hôtel de Démonstration';
    
END $$;

-- =====================================================
-- VÉRIFICATION FINALE
-- =====================================================

-- Vérifier que les utilisateurs ont été créés correctement
DO $$
DECLARE
    user_count INTEGER;
    hotel_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO user_count FROM core.dbusers WHERE actif = true;
    SELECT COUNT(*) INTO hotel_count FROM core.hotels WHERE actif = true;
    
    RAISE NOTICE 'Configuration initiale terminée:';
    RAISE NOTICE '- % hôtels créés', hotel_count;
    RAISE NOTICE '- % utilisateurs créés', user_count;
    RAISE NOTICE '';
    RAISE NOTICE 'Comptes de connexion disponibles:';
    RAISE NOTICE '1. superadmin / SuperAdmin123! (accès système complet)';
    RAISE NOTICE '2. demoadmin / DemoAdmin123! (admin hôtel de démo)';
    RAISE NOTICE '';
    RAISE NOTICE 'Vous pouvez maintenant démarrer l''application Spring Boot';
    
END $$;