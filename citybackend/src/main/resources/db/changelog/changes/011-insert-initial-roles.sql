--liquibase formatted sql

--changeset cityprojects:011-insert-initial-roles splitStatements:true endDelimiter:;

-- =====================================================
-- INSERTION DES RÔLES SYSTÈME INITIAUX
-- =====================================================

-- Rôle SUPERADMIN - Administration système complète
INSERT INTO core.roles (role_code, role_nom, description, permissions, actif) VALUES 
('SUPERADMIN', 'Super Administrateur', 
 'Accès complet au système, gestion de tous les hôtels et utilisateurs', 
 '{
    "system": ["manage_all_hotels", "manage_system_users", "view_system_stats", "manage_system_config"],
    "hotel": ["create", "read", "update", "delete", "manage_all"],
    "user": ["create", "read", "update", "delete", "manage_all_hotels"],
    "role": ["create", "read", "update", "delete"],
    "reservation": ["create", "read", "update", "delete", "manage_all_hotels"],
    "client": ["create", "read", "update", "delete", "manage_all_hotels"],
    "restaurant": ["create", "read", "update", "delete", "manage_all_hotels"],
    "inventory": ["create", "read", "update", "delete", "manage_all_hotels"],
    "finance": ["create", "read", "update", "delete", "manage_all_hotels"],
    "menage": ["create", "read", "update", "delete", "manage_all_hotels"],
    "reporting": ["view_all", "export_all", "manage_all_hotels"]
 }', 
 true);

-- Rôle ADMIN - Administration d'un hôtel
INSERT INTO core.roles (role_code, role_nom, description, permissions, actif) VALUES 
('ADMIN', 'Administrateur Hôtel', 
 'Administration complète d''un hôtel spécifique', 
 '{
    "hotel": ["read", "update", "manage_own"],
    "user": ["create", "read", "update", "delete", "manage_own_hotel"],
    "role": ["read"],
    "reservation": ["create", "read", "update", "delete", "manage_own_hotel"],
    "client": ["create", "read", "update", "delete", "manage_own_hotel"],
    "restaurant": ["create", "read", "update", "delete", "manage_own_hotel"],
    "inventory": ["create", "read", "update", "delete", "manage_own_hotel"],
    "finance": ["create", "read", "update", "delete", "manage_own_hotel"],
    "menage": ["create", "read", "update", "delete", "manage_own_hotel"],
    "reporting": ["view_all", "export_all", "manage_own_hotel"]
 }', 
 true);

-- Rôle GERANT - Gestion opérationnelle
INSERT INTO core.roles (role_code, role_nom, description, permissions, actif) VALUES 
('GERANT', 'Gérant', 
 'Gestion opérationnelle complète de l''hôtel', 
 '{
    "hotel": ["read", "update_limited"],
    "user": ["read", "update_limited"],
    "reservation": ["create", "read", "update", "delete"],
    "client": ["create", "read", "update", "delete"],
    "restaurant": ["create", "read", "update", "delete"],
    "inventory": ["create", "read", "update", "delete"],
    "finance": ["create", "read", "update", "delete"],
    "menage": ["create", "read", "update", "delete"],
    "reporting": ["view_all", "export_all"]
 }', 
 true);

-- Rôle RECEPTION - Réception et réservations
INSERT INTO core.roles (role_code, role_nom, description, permissions, actif) VALUES 
('RECEPTION', 'Réceptionniste', 
 'Gestion de la réception, réservations et clients', 
 '{
    "hotel": ["read"],
    "user": ["read_limited"],
    "reservation": ["create", "read", "update", "delete"],
    "client": ["create", "read", "update", "delete"],
    "restaurant": ["read", "create_orders"],
    "inventory": ["read"],
    "finance": ["create_invoices", "read", "receive_payments"],
    "menage": ["read", "update_room_status"],
    "reporting": ["view_reservations", "view_clients", "view_financial_basic"]
 }', 
 true);

-- Rôle RESTAURANT - Gestion restaurant
INSERT INTO core.roles (role_code, role_nom, description, permissions, actif) VALUES 
('RESTAURANT', 'Personnel Restaurant', 
 'Gestion du restaurant et point de vente', 
 '{
    "hotel": ["read"],
    "user": ["read_limited"],
    "reservation": ["read"],
    "client": ["read", "create_limited"],
    "restaurant": ["create", "read", "update", "delete"],
    "inventory": ["read", "create_requests"],
    "finance": ["create_invoices", "read_limited"],
    "menage": ["read"],
    "reporting": ["view_restaurant", "view_sales"]
 }', 
 true);

-- Rôle RESREC - Réception + Restaurant
INSERT INTO core.roles (role_code, role_nom, description, permissions, actif) VALUES 
('RESREC', 'Réception + Restaurant', 
 'Accès combiné réception et restaurant', 
 '{
    "hotel": ["read"],
    "user": ["read_limited"],
    "reservation": ["create", "read", "update", "delete"],
    "client": ["create", "read", "update", "delete"],
    "restaurant": ["create", "read", "update", "delete"],
    "inventory": ["read", "create_requests"],
    "finance": ["create_invoices", "read", "receive_payments"],
    "menage": ["read", "update_room_status"],
    "reporting": ["view_reservations", "view_clients", "view_restaurant", "view_financial_basic"]
 }', 
 true);

-- =====================================================
-- VÉRIFICATION DES RÔLES INSÉRÉS
-- =====================================================

-- Tour 7E : isolé dans un sous-changeset avec splitStatements:false
-- car le bloc DO $$ ... $$ contient des `;` internes qui cassent
-- le parser Liquibase quand splitStatements:true est actif.
--changeset cityprojects:011-2-verify-roles splitStatements:false
DO $$
DECLARE
    role_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO role_count FROM core.roles WHERE actif = true;

    IF role_count != 6 THEN
        RAISE EXCEPTION 'Erreur: % rôles insérés au lieu de 6 attendus', role_count;
    END IF;

    RAISE NOTICE 'Succès: % rôles système insérés correctement', role_count;
END $$;
--rollback SELECT 1;