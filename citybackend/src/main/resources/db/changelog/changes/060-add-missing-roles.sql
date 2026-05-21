--liquibase formatted sql

--changeset cityprojects:060-add-missing-roles splitStatements:true endDelimiter:;

-- ============================================================================
-- AJOUT DES RÔLES MANQUANTS — MAGASIN, MENAGE, NIGHTAUDIT
-- ============================================================================
--
-- Contexte : le seed initial `011-insert-initial-roles.sql` n'a inséré que 6
-- des 9 rôles documentés dans CLAUDE.md racine §6.3 / `roles_utilisateurs.txt`.
-- Les rôles MAGASIN, MENAGE, NIGHTAUDIT sont référencés par les composants
-- (TacheController.@PreAuthorize, sidebar, RoleGuard front, ...) mais
-- absents de la table core.roles → impossible de les attribuer à un user.
--
-- Effet pratique observé : dans le formulaire « Mon hôtel > Nouvel utilisateur »
-- de l'ADMIN d'hôtel, la liste des rôles était soit vide (BD non seedée) soit
-- incomplète (3 rôles manquants).
--
-- Ce changeset est IDEMPOTENT (ON CONFLICT DO NOTHING sur role_code) :
-- - Si la BD a déjà ces rôles → no-op
-- - Si la BD n'a pas les 6 initiaux → ce changeset ne suffit pas (lancer
--   le changeset 011 d'abord)
-- - Si la BD a les 6 initiaux mais pas les 3 nouveaux → les ajoute proprement
-- ============================================================================

-- Rôle MAGASIN — gestion des stocks et bons de commande/sortie
INSERT INTO core.roles (role_code, role_nom, description, permissions, actif) VALUES
('MAGASIN', 'Magasinier',
 'Gestion des stocks, bons de commande, bons de sortie et fournisseurs',
 '{
    "hotel": ["read"],
    "user": ["read_limited"],
    "reservation": ["read"],
    "client": ["read"],
    "restaurant": ["read"],
    "inventory": ["create", "read", "update", "delete"],
    "finance": ["read_limited"],
    "menage": ["read"],
    "reporting": ["view_inventory", "view_stock"]
 }',
 true)
ON CONFLICT (role_code) DO NOTHING;

-- Rôle MENAGE — agent ménage / housekeeping
INSERT INTO core.roles (role_code, role_nom, description, permissions, actif) VALUES
('MENAGE', 'Agent ménage',
 'Personnel ménage : exécution des tâches de nettoyage assignées',
 '{
    "hotel": ["read"],
    "user": ["read_self"],
    "reservation": ["read_limited"],
    "client": ["read_limited"],
    "restaurant": ["read_limited"],
    "inventory": ["read_limited"],
    "finance": [],
    "menage": ["read_own", "update_own_status", "update_room_status"],
    "reporting": ["view_menage_own"]
 }',
 true)
ON CONFLICT (role_code) DO NOTHING;

-- Rôle NIGHTAUDIT — auditeur de nuit
INSERT INTO core.roles (role_code, role_nom, description, permissions, actif) VALUES
('NIGHTAUDIT', 'Auditeur de nuit',
 'Lancement et validation du night audit quotidien, génération nuitées no-show',
 '{
    "hotel": ["read"],
    "user": ["read_limited"],
    "reservation": ["read", "update_status", "create_nuitees"],
    "client": ["read"],
    "restaurant": ["read"],
    "inventory": ["read"],
    "finance": ["read", "create_invoices_auto"],
    "menage": ["read"],
    "reporting": ["view_night_audit", "export_night_audit"]
 }',
 true)
ON CONFLICT (role_code) DO NOTHING;

-- ============================================================================
-- VÉRIFICATION : on doit avoir au minimum 9 rôles actifs après ce changeset
-- ============================================================================

--changeset cityprojects:060-2-verify-roles splitStatements:false
DO $$
DECLARE
    role_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO role_count FROM core.roles WHERE actif = true;

    IF role_count < 9 THEN
        RAISE EXCEPTION 'Erreur post-seed : seulement % rôles actifs trouvés, 9 attendus (SUPERADMIN, ADMIN, GERANT, RECEPTION, RESTAURANT, RESREC, MAGASIN, MENAGE, NIGHTAUDIT)', role_count;
    END IF;

    RAISE NOTICE 'OK : % rôles actifs présents (>= 9 requis)', role_count;
END $$;
--rollback SELECT 1;
