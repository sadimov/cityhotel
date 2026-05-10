--liquibase formatted sql

--changeset cityprojects:001-create-core-schema splitStatements:true endDelimiter:;

-- =====================================================
-- CRÉATION DU SCHÉMA CORE ET TABLES CENTRALES
-- =====================================================

-- Création du schéma core
CREATE SCHEMA IF NOT EXISTS core;

-- =====================================================
-- TABLE DES HÔTELS (TENANT PRINCIPAL)
-- =====================================================
CREATE TABLE core.hotels (
    hotel_id BIGSERIAL PRIMARY KEY,
    hotel_code VARCHAR(10) UNIQUE NOT NULL,
    hotel_nom VARCHAR(255) NOT NULL,
    hotel_adresse TEXT,
    hotel_tel VARCHAR(50),
    logo_url VARCHAR(500),
    ville VARCHAR(100),
    pays VARCHAR(100),
    boite_postale VARCHAR(20),
    email VARCHAR(100),
    site_web VARCHAR(200),
    devise VARCHAR(3) DEFAULT 'MRU',
    fuseau_horaire VARCHAR(50) DEFAULT 'Africa/Nouakchott',
    actif BOOLEAN DEFAULT true,
    date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Commentaires pour la table hotels
COMMENT ON TABLE core.hotels IS 'Table principale des hôtels - tenant central du système multi-tenant';
COMMENT ON COLUMN core.hotels.hotel_code IS 'Code unique de l''hôtel utilisé dans les URLs et API';
COMMENT ON COLUMN core.hotels.devise IS 'Devise par défaut de l''hôtel (ISO 4217)';
COMMENT ON COLUMN core.hotels.fuseau_horaire IS 'Fuseau horaire de l''hôtel';

-- =====================================================
-- TABLE DES RÔLES SYSTÈME
-- =====================================================
CREATE TABLE core.roles (
    role_id SERIAL PRIMARY KEY,
    role_code VARCHAR(20) UNIQUE NOT NULL,
    role_nom VARCHAR(100) NOT NULL,
    description TEXT,
    permissions JSON,
    actif BOOLEAN DEFAULT true
);

-- Commentaires pour la table roles
COMMENT ON TABLE core.roles IS 'Rôles système pour la gestion des accès';
COMMENT ON COLUMN core.roles.role_code IS 'Code unique du rôle (ADMIN, GERANT, etc.)';
COMMENT ON COLUMN core.roles.permissions IS 'Permissions détaillées en format JSON';

-- =====================================================
-- TABLE DES UTILISATEURS (DBUSERS)
-- =====================================================
CREATE TABLE core.dbusers (
    user_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    nom VARCHAR(100) NOT NULL,
    telephone VARCHAR(20),
    poste VARCHAR(100),
    hotel_id BIGINT NOT NULL,
    role_id INTEGER NOT NULL,
    actif BOOLEAN DEFAULT true,
    derniere_connexion TIMESTAMP,
    tentatives_connexion INTEGER DEFAULT 0,
    compte_verrouille BOOLEAN DEFAULT false,
    mot_passe_temporaire BOOLEAN NOT NULL DEFAULT false,
    date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_dbusers_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id),
    CONSTRAINT fk_dbusers_role FOREIGN KEY (role_id) REFERENCES core.roles(role_id)
);

-- Commentaires pour la table dbusers
COMMENT ON TABLE core.dbusers IS 'Utilisateurs du système avec authentification';
COMMENT ON COLUMN core.dbusers.username IS 'Nom d''utilisateur unique pour la connexion';
COMMENT ON COLUMN core.dbusers.password_hash IS 'Hash BCrypt du mot de passe avec salt';
COMMENT ON COLUMN core.dbusers.salt IS 'Salt unique pour le hashage du mot de passe';
COMMENT ON COLUMN core.dbusers.tentatives_connexion IS 'Nombre de tentatives de connexion échouées';
COMMENT ON COLUMN core.dbusers.compte_verrouille IS 'Indique si le compte est verrouillé';

-- =====================================================
-- TABLE DES SESSIONS UTILISATEURS
-- =====================================================
CREATE TABLE core.user_sessions (
    session_id VARCHAR(128) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    hotel_id BIGINT NOT NULL,
    ip_address INET,
    user_agent TEXT,
    derniere_activite TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    actif BOOLEAN DEFAULT true,
    
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES core.dbusers(user_id),
    CONSTRAINT fk_sessions_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id)
);

-- Commentaires pour la table user_sessions
COMMENT ON TABLE core.user_sessions IS 'Sessions actives des utilisateurs pour la gestion de la concurrence';
COMMENT ON COLUMN core.user_sessions.session_id IS 'Identifiant unique de la session (UUID)';
COMMENT ON COLUMN core.user_sessions.ip_address IS 'Adresse IP du client';
COMMENT ON COLUMN core.user_sessions.user_agent IS 'User-Agent du navigateur/application';
COMMENT ON COLUMN core.user_sessions.derniere_activite IS 'Timestamp de la dernière activité de la session';

-- =====================================================
-- TABLE DES DONNÉES RÉFÉRENTIELLES
-- =====================================================
CREATE TABLE core.donnees_referentielles (
    ref_id BIGSERIAL PRIMARY KEY,
    categorie VARCHAR(50) NOT NULL,
    code VARCHAR(20),
    libelle VARCHAR(200) NOT NULL,
    libelle_en VARCHAR(200),
    libelle_ar VARCHAR(200),
    ordre_affichage INTEGER DEFAULT 0,
    actif BOOLEAN DEFAULT true
);

-- Commentaires pour la table donnees_referentielles
COMMENT ON TABLE core.donnees_referentielles IS 'Données de référence multilingues (nationalités, types d''identification, etc.)';
COMMENT ON COLUMN core.donnees_referentielles.categorie IS 'Catégorie de données (nationalite, type_identification, etc.)';
COMMENT ON COLUMN core.donnees_referentielles.code IS 'Code court pour la donnée';
COMMENT ON COLUMN core.donnees_referentielles.libelle IS 'Libellé en français';
COMMENT ON COLUMN core.donnees_referentielles.libelle_en IS 'Libellé en anglais';
COMMENT ON COLUMN core.donnees_referentielles.libelle_ar IS 'Libellé en arabe';
COMMENT ON COLUMN core.donnees_referentielles.ordre_affichage IS 'Ordre d''affichage dans les listes';

-- =====================================================
-- INDEX POUR OPTIMISATION DES PERFORMANCES
-- =====================================================

-- Index sur les tables principales
CREATE INDEX idx_hotels_code ON core.hotels(hotel_code);
CREATE INDEX idx_hotels_actif ON core.hotels(actif);
CREATE INDEX idx_hotels_ville ON core.hotels(ville);

CREATE INDEX idx_roles_code ON core.roles(role_code);
CREATE INDEX idx_roles_actif ON core.roles(actif);

CREATE INDEX idx_dbusers_username ON core.dbusers(username);
CREATE INDEX idx_dbusers_email ON core.dbusers(email);
CREATE INDEX idx_dbusers_hotel ON core.dbusers(hotel_id);
CREATE INDEX idx_dbusers_role ON core.dbusers(role_id);
CREATE INDEX idx_dbusers_actif ON core.dbusers(actif);
CREATE INDEX idx_dbusers_verrouille ON core.dbusers(compte_verrouille);
CREATE INDEX idx_dbusers_derniere_connexion ON core.dbusers(derniere_connexion);

CREATE INDEX idx_sessions_user ON core.user_sessions(user_id);
CREATE INDEX idx_sessions_hotel ON core.user_sessions(hotel_id);
CREATE INDEX idx_sessions_actif ON core.user_sessions(actif);
CREATE INDEX idx_sessions_activite ON core.user_sessions(derniere_activite);
CREATE INDEX idx_sessions_creation ON core.user_sessions(date_creation);

CREATE INDEX idx_donnees_ref_categorie ON core.donnees_referentielles(categorie);
CREATE INDEX idx_donnees_ref_code ON core.donnees_referentielles(code);
CREATE INDEX idx_donnees_ref_actif ON core.donnees_referentielles(actif);

-- =====================================================
-- TRIGGERS POUR LA MISE À JOUR AUTOMATIQUE
-- =====================================================

-- Fonction pour mettre à jour date_modification
-- Tour 7E : isolée dans son propre sous-changeset avec splitStatements:false
-- car le bloc PL/pgSQL $$ ... $$ contient des `;` internes qui cassent le
-- parser Liquibase quand splitStatements:true est actif.
--changeset cityprojects:001-2-trigger-function splitStatements:false
CREATE OR REPLACE FUNCTION core.update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.date_modification = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';
--rollback DROP FUNCTION IF EXISTS core.update_modified_column();

-- Tour 7E : reprise du splitStatements:true pour la suite (triggers + checks).
--changeset cityprojects:001-3-triggers-and-constraints splitStatements:true endDelimiter:;

-- Triggers pour la mise à jour automatique de date_modification
CREATE TRIGGER update_hotels_modtime
    BEFORE UPDATE ON core.hotels 
    FOR EACH ROW EXECUTE FUNCTION core.update_modified_column();

CREATE TRIGGER update_dbusers_modtime 
    BEFORE UPDATE ON core.dbusers 
    FOR EACH ROW EXECUTE FUNCTION core.update_modified_column();

-- =====================================================
-- CONSTRAINTS SUPPLÉMENTAIRES
-- =====================================================

-- Constraint pour valider l'email
ALTER TABLE core.hotels ADD CONSTRAINT chk_hotels_email 
    CHECK (email IS NULL OR email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$');

ALTER TABLE core.dbusers ADD CONSTRAINT chk_dbusers_email 
    CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$');

-- Constraint pour valider le code devise (ISO 4217)
ALTER TABLE core.hotels ADD CONSTRAINT chk_hotels_devise 
    CHECK (devise ~ '^[A-Z]{3}$');

-- Constraint pour valider le code hôtel (format: lettres/chiffres seulement)
ALTER TABLE core.hotels ADD CONSTRAINT chk_hotels_code_format 
    CHECK (hotel_code ~ '^[A-Z0-9]{2,10}$');

-- Constraint pour limiter les tentatives de connexion
ALTER TABLE core.dbusers ADD CONSTRAINT chk_dbusers_tentatives 
    CHECK (tentatives_connexion >= 0 AND tentatives_connexion <= 10);

-- =====================================================
-- POLITIQUES DE SÉCURITÉ RLS (Row Level Security)
-- =====================================================

-- Activer RLS sur les tables sensibles (optionnel, pour sécurité avancée)
-- ALTER TABLE core.dbusers ENABLE ROW LEVEL SECURITY;
-- ALTER TABLE core.user_sessions ENABLE ROW LEVEL SECURITY;

-- Exemple de politique RLS (à adapter selon les besoins)
-- CREATE POLICY hotel_isolation_policy ON core.dbusers
--     FOR ALL TO application_role
--     USING (hotel_id = current_setting('app.current_hotel_id')::BIGINT);

-- Tour 7E : retiré le `rollback;` final — c'était un statement SQL ROLLBACK
-- qui annulait la transaction du dernier sous-changeset. Liquibase
-- auto-génère les rollbacks pour CREATE TABLE/INDEX/TRIGGER.