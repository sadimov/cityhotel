--
-- PostgreSQL database dump
--

-- Dumped from database version 15.4
-- Dumped by pg_dump version 15.4

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: client; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA client;


--
-- Name: core; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA core;


--
-- Name: finance; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA finance;


--
-- Name: hebergement; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA hebergement;


--
-- Name: inventory; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA inventory;


--
-- Name: menage; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA menage;


--
-- Name: reporting; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA reporting;


--
-- Name: restaurant; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA restaurant;


--
-- Name: btree_gist; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;


--
-- Name: EXTENSION btree_gist; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION btree_gist IS 'support for indexing common datatypes in GiST';


--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: update_modified_column(); Type: FUNCTION; Schema: core; Owner: -
--

CREATE FUNCTION core.update_modified_column() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.date_modification = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;


--
-- Name: check_pivot_tenant_coherence_affectations_paiements(); Type: FUNCTION; Schema: finance; Owner: -
--

CREATE FUNCTION finance.check_pivot_tenant_coherence_affectations_paiements() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    paiement_hotel BIGINT;
    facture_hotel  BIGINT;
BEGIN
    SELECT hotel_id INTO paiement_hotel FROM finance.paiements
        WHERE paiement_id = NEW.paiement_id;
    SELECT hotel_id INTO facture_hotel FROM finance.factures
        WHERE facture_id = NEW.facture_id;
    IF paiement_hotel IS NULL THEN
        RAISE EXCEPTION 'Cross-tenant affectation rejected: paiement_id=% not found',
            NEW.paiement_id;
    END IF;
    IF facture_hotel IS NULL THEN
        RAISE EXCEPTION 'Cross-tenant affectation rejected: facture_id=% not found',
            NEW.facture_id;
    END IF;
    IF paiement_hotel IS DISTINCT FROM facture_hotel THEN
        RAISE EXCEPTION 'Cross-tenant affectation rejected: paiement.hotel_id=% facture.hotel_id=%',
            paiement_hotel, facture_hotel;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: check_pivot_tenant_coherence_lignes_factures(); Type: FUNCTION; Schema: finance; Owner: -
--

CREATE FUNCTION finance.check_pivot_tenant_coherence_lignes_factures() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    facture_hotel BIGINT;
    nuitee_hotel  BIGINT;
    produit_hotel BIGINT;
BEGIN
    SELECT hotel_id INTO facture_hotel FROM finance.factures
        WHERE facture_id = NEW.facture_id;
    IF facture_hotel IS NULL THEN
        RAISE EXCEPTION 'Cross-tenant facture line rejected: parent facture_id=% not found',
            NEW.facture_id;
    END IF;

    IF NEW.nuitee_id IS NOT NULL THEN
        SELECT hotel_id INTO nuitee_hotel FROM hebergement.nuitees
            WHERE nuitee_id = NEW.nuitee_id;
        IF facture_hotel IS DISTINCT FROM nuitee_hotel THEN
            RAISE EXCEPTION 'Cross-tenant facture line rejected: facture.hotel_id=% nuitee.hotel_id=%',
                facture_hotel, nuitee_hotel;
        END IF;
    END IF;

    IF NEW.produit_id IS NOT NULL THEN
        SELECT hotel_id INTO produit_hotel FROM inventory.produits
            WHERE produit_id = NEW.produit_id;
        IF facture_hotel IS DISTINCT FROM produit_hotel THEN
            RAISE EXCEPTION 'Cross-tenant facture line rejected: facture.hotel_id=% produit.hotel_id=%',
                facture_hotel, produit_hotel;
        END IF;
    END IF;

    -- TODO restaurant : valider commande_id/service_id quand le module sera integre.

    RETURN NEW;
END;
$$;


--
-- Name: check_pivot_tenant_coherence_operations_comptes(); Type: FUNCTION; Schema: finance; Owner: -
--

CREATE FUNCTION finance.check_pivot_tenant_coherence_operations_comptes() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    compte_hotel   BIGINT;
    facture_hotel  BIGINT;
    paiement_hotel BIGINT;
BEGIN
    SELECT hotel_id INTO compte_hotel FROM finance.comptes
        WHERE compte_id = NEW.compte_id;
    IF compte_hotel IS NULL THEN
        RAISE EXCEPTION 'Cross-tenant operation rejected: compte_id=% not found',
            NEW.compte_id;
    END IF;
    IF NEW.hotel_id IS DISTINCT FROM compte_hotel THEN
        RAISE EXCEPTION 'Cross-tenant operation rejected: operation.hotel_id=% compte.hotel_id=%',
            NEW.hotel_id, compte_hotel;
    END IF;

    IF NEW.facture_id IS NOT NULL THEN
        SELECT hotel_id INTO facture_hotel FROM finance.factures
            WHERE facture_id = NEW.facture_id;
        IF NEW.hotel_id IS DISTINCT FROM facture_hotel THEN
            RAISE EXCEPTION 'Cross-tenant operation rejected: operation.hotel_id=% facture.hotel_id=%',
                NEW.hotel_id, facture_hotel;
        END IF;
    END IF;

    IF NEW.paiement_id IS NOT NULL THEN
        SELECT hotel_id INTO paiement_hotel FROM finance.paiements
            WHERE paiement_id = NEW.paiement_id;
        IF NEW.hotel_id IS DISTINCT FROM paiement_hotel THEN
            RAISE EXCEPTION 'Cross-tenant operation rejected: operation.hotel_id=% paiement.hotel_id=%',
                NEW.hotel_id, paiement_hotel;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;


--
-- Name: check_pivot_tenant_coherence_paiements(); Type: FUNCTION; Schema: finance; Owner: -
--

CREATE FUNCTION finance.check_pivot_tenant_coherence_paiements() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    compte_hotel BIGINT;
BEGIN
    IF NEW.compte_id IS NOT NULL THEN
        SELECT hotel_id INTO compte_hotel FROM finance.comptes
            WHERE compte_id = NEW.compte_id;
        IF NEW.hotel_id IS DISTINCT FROM compte_hotel THEN
            RAISE EXCEPTION 'Cross-tenant paiement rejected: paiement.hotel_id=% compte.hotel_id=%',
                NEW.hotel_id, compte_hotel;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: check_pivot_tenant_coherence_nuitees(); Type: FUNCTION; Schema: hebergement; Owner: -
--

CREATE FUNCTION hebergement.check_pivot_tenant_coherence_nuitees() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    res_hotel BIGINT;
    cha_hotel BIGINT;
BEGIN
    SELECT hotel_id INTO res_hotel FROM hebergement.reservations
        WHERE reservation_id = NEW.reservation_id;
    SELECT hotel_id INTO cha_hotel FROM hebergement.chambres
        WHERE chambre_id = NEW.chambre_id;
    IF NEW.hotel_id IS DISTINCT FROM res_hotel
       OR NEW.hotel_id IS DISTINCT FROM cha_hotel THEN
        RAISE EXCEPTION 'Cross-tenant pivot rejected: nuitee.hotel_id=% reservation.hotel_id=% chambre.hotel_id=%',
            NEW.hotel_id, res_hotel, cha_hotel;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: check_pivot_tenant_coherence_resch(); Type: FUNCTION; Schema: hebergement; Owner: -
--

CREATE FUNCTION hebergement.check_pivot_tenant_coherence_resch() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    res_hotel BIGINT;
    cha_hotel BIGINT;
BEGIN
    SELECT hotel_id INTO res_hotel FROM hebergement.reservations
        WHERE reservation_id = NEW.reservation_id;
    SELECT hotel_id INTO cha_hotel FROM hebergement.chambres
        WHERE chambre_id = NEW.chambre_id;
    IF NEW.hotel_id IS DISTINCT FROM res_hotel
       OR NEW.hotel_id IS DISTINCT FROM cha_hotel THEN
        RAISE EXCEPTION 'Cross-tenant pivot rejected: pivot.hotel_id=% reservation.hotel_id=% chambre.hotel_id=%',
            NEW.hotel_id, res_hotel, cha_hotel;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: check_pivot_tenant_coherence_resclients(); Type: FUNCTION; Schema: hebergement; Owner: -
--

CREATE FUNCTION hebergement.check_pivot_tenant_coherence_resclients() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    res_hotel BIGINT;
    cli_hotel BIGINT;
    cha_hotel BIGINT;
BEGIN
    SELECT hotel_id INTO res_hotel FROM hebergement.reservations
        WHERE reservation_id = NEW.reservation_id;
    SELECT hotel_id INTO cli_hotel FROM client.clients
        WHERE client_id = NEW.client_id;
    IF NEW.hotel_id IS DISTINCT FROM res_hotel
       OR NEW.hotel_id IS DISTINCT FROM cli_hotel THEN
        RAISE EXCEPTION 'Cross-tenant pivot rejected: pivot.hotel_id=% reservation.hotel_id=% client.hotel_id=%',
            NEW.hotel_id, res_hotel, cli_hotel;
    END IF;
    IF NEW.chambre_id IS NOT NULL THEN
        SELECT hotel_id INTO cha_hotel FROM hebergement.chambres
            WHERE chambre_id = NEW.chambre_id;
        IF NEW.hotel_id IS DISTINCT FROM cha_hotel THEN
            RAISE EXCEPTION 'Cross-tenant pivot rejected: pivot.hotel_id=% chambre.hotel_id=%',
                NEW.hotel_id, cha_hotel;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: clients; Type: TABLE; Schema: client; Owner: -
--

CREATE TABLE client.clients (
    client_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    numero_client character varying(40) NOT NULL,
    prenom character varying(100) NOT NULL,
    nom character varying(100) NOT NULL,
    nationalite_id bigint,
    telephone character varying(20),
    email character varying(100),
    adresse text,
    ville character varying(100),
    pays character varying(100),
    type_identification_id bigint,
    numero_identification character varying(50),
    date_naissance date,
    societe_id bigint,
    actif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(80) NOT NULL,
    updated_by character varying(80),
    CONSTRAINT chk_clients_hotel_id_positive CHECK ((hotel_id > 0))
);


--
-- Name: clients_client_id_seq; Type: SEQUENCE; Schema: client; Owner: -
--

ALTER TABLE client.clients ALTER COLUMN client_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME client.clients_client_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: societes; Type: TABLE; Schema: client; Owner: -
--

CREATE TABLE client.societes (
    societe_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    societe_nom character varying(255) NOT NULL,
    siret character varying(20),
    adresse text,
    ville character varying(100),
    pays character varying(100),
    telephone character varying(20),
    email character varying(100),
    contact_principal character varying(200),
    actif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(80) NOT NULL,
    updated_by character varying(80),
    CONSTRAINT chk_societes_hotel_id_positive CHECK ((hotel_id > 0))
);


--
-- Name: societes_societe_id_seq; Type: SEQUENCE; Schema: client; Owner: -
--

ALTER TABLE client.societes ALTER COLUMN societe_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME client.societes_societe_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: dbusers; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.dbusers (
    user_id bigint NOT NULL,
    username character varying(100) NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    prenom character varying(100) NOT NULL,
    nom character varying(100) NOT NULL,
    telephone character varying(20),
    poste character varying(100),
    hotel_id bigint NOT NULL,
    role_id integer NOT NULL,
    actif boolean DEFAULT true,
    derniere_connexion timestamp without time zone,
    tentatives_connexion integer DEFAULT 0,
    compte_verrouille boolean DEFAULT false,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    date_modification timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_dbusers_email CHECK (((email)::text ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'::text)),
    CONSTRAINT chk_dbusers_tentatives CHECK (((tentatives_connexion >= 0) AND (tentatives_connexion <= 10)))
);


--
-- Name: TABLE dbusers; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON TABLE core.dbusers IS 'Utilisateurs du système avec authentification';


--
-- Name: COLUMN dbusers.username; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON COLUMN core.dbusers.username IS 'Nom d''utilisateur unique pour la connexion';


--
-- Name: COLUMN dbusers.password_hash; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON COLUMN core.dbusers.password_hash IS 'Hash BCrypt du mot de passe avec salt';


--
-- Name: COLUMN dbusers.tentatives_connexion; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON COLUMN core.dbusers.tentatives_connexion IS 'Nombre de tentatives de connexion échouées';


--
-- Name: COLUMN dbusers.compte_verrouille; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON COLUMN core.dbusers.compte_verrouille IS 'Indique si le compte est verrouillé';


--
-- Name: dbusers_user_id_seq; Type: SEQUENCE; Schema: core; Owner: -
--

CREATE SEQUENCE core.dbusers_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dbusers_user_id_seq; Type: SEQUENCE OWNED BY; Schema: core; Owner: -
--

ALTER SEQUENCE core.dbusers_user_id_seq OWNED BY core.dbusers.user_id;


--
-- Name: donnees_referentielles; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.donnees_referentielles (
    ref_id bigint NOT NULL,
    categorie character varying(50) NOT NULL,
    code character varying(20),
    libelle character varying(200) NOT NULL,
    libelle_en character varying(200),
    libelle_ar character varying(200),
    ordre_affichage integer DEFAULT 0,
    actif boolean DEFAULT true
);


--
-- Name: TABLE donnees_referentielles; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON TABLE core.donnees_referentielles IS 'Données de référence multilingues (nationalités, types d''identification, etc.)';


--
-- Name: COLUMN donnees_referentielles.categorie; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON COLUMN core.donnees_referentielles.categorie IS 'Catégorie de données (nationalite, type_identification, etc.)';


--
-- Name: COLUMN donnees_referentielles.code; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON COLUMN core.donnees_referentielles.code IS 'Code court pour la donnée';


--
-- Name: COLUMN donnees_referentielles.libelle; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON COLUMN core.donnees_referentielles.libelle IS 'Libellé en français';


--
-- Name: COLUMN donnees_referentielles.libelle_en; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON COLUMN core.donnees_referentielles.libelle_en IS 'Libellé en anglais';


--
-- Name: COLUMN donnees_referentielles.libelle_ar; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON COLUMN core.donnees_referentielles.libelle_ar IS 'Libellé en arabe';


--
-- Name: COLUMN donnees_referentielles.ordre_affichage; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON COLUMN core.donnees_referentielles.ordre_affichage IS 'Ordre d''affichage dans les listes';


--
-- Name: donnees_referentielles_ref_id_seq; Type: SEQUENCE; Schema: core; Owner: -
--

CREATE SEQUENCE core.donnees_referentielles_ref_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: donnees_referentielles_ref_id_seq; Type: SEQUENCE OWNED BY; Schema: core; Owner: -
--

ALTER SEQUENCE core.donnees_referentielles_ref_id_seq OWNED BY core.donnees_referentielles.ref_id;


--
-- Name: hotels; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.hotels (
    hotel_id bigint NOT NULL,
    hotel_code character varying(10) NOT NULL,
    hotel_nom character varying(255) NOT NULL,
    hotel_adresse text,
    hotel_tel character varying(50),
    logo_url character varying(500),
    ville character varying(100),
    pays character varying(100),
    boite_postale character varying(20),
    email character varying(100),
    site_web character varying(200),
    devise character varying(3) DEFAULT 'MRU'::character varying,
    fuseau_horaire character varying(50) DEFAULT 'Africa/Nouakchott'::character varying,
    actif boolean DEFAULT true,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    date_modification timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    code_pays character varying(2) DEFAULT 'MR'::character varying NOT NULL,
    CONSTRAINT chk_hotels_code_format CHECK (((hotel_code)::text ~ '^[A-Z0-9]{2,10}$'::text)),
    CONSTRAINT chk_hotels_devise CHECK (((devise)::text ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT chk_hotels_email CHECK (((email IS NULL) OR ((email)::text ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'::text)))
);


--
-- Name: TABLE hotels; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON TABLE core.hotels IS 'Table principale des hôtels - tenant central du système multi-tenant';


--
-- Name: COLUMN hotels.hotel_code; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON COLUMN core.hotels.hotel_code IS 'Code unique de l''hôtel utilisé dans les URLs et API';


--
-- Name: COLUMN hotels.devise; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON COLUMN core.hotels.devise IS 'Devise par défaut de l''hôtel (ISO 4217)';


--
-- Name: COLUMN hotels.fuseau_horaire; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON COLUMN core.hotels.fuseau_horaire IS 'Fuseau horaire de l''hôtel';


--
-- Name: hotels_hotel_id_seq; Type: SEQUENCE; Schema: core; Owner: -
--

CREATE SEQUENCE core.hotels_hotel_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: hotels_hotel_id_seq; Type: SEQUENCE OWNED BY; Schema: core; Owner: -
--

ALTER SEQUENCE core.hotels_hotel_id_seq OWNED BY core.hotels.hotel_id;


--
-- Name: roles; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.roles (
    role_id integer NOT NULL,
    role_code character varying(20) NOT NULL,
    role_nom character varying(100) NOT NULL,
    description text,
    permissions json,
    actif boolean DEFAULT true
);


--
-- Name: TABLE roles; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON TABLE core.roles IS 'Rôles système pour la gestion des accès';


--
-- Name: COLUMN roles.role_code; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON COLUMN core.roles.role_code IS 'Code unique du rôle (ADMIN, GERANT, etc.)';


--
-- Name: COLUMN roles.permissions; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON COLUMN core.roles.permissions IS 'Permissions détaillées en format JSON';


--
-- Name: roles_role_id_seq; Type: SEQUENCE; Schema: core; Owner: -
--

CREATE SEQUENCE core.roles_role_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles_role_id_seq; Type: SEQUENCE OWNED BY; Schema: core; Owner: -
--

ALTER SEQUENCE core.roles_role_id_seq OWNED BY core.roles.role_id;


--
-- Name: user_sessions; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.user_sessions (
    session_id character varying(128) NOT NULL,
    user_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    ip_address inet,
    user_agent text,
    derniere_activite timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    actif boolean DEFAULT true,
    CONSTRAINT chk_ip_address_valid CHECK ((ip_address IS NOT NULL))
);


--
-- Name: TABLE user_sessions; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON TABLE core.user_sessions IS 'Sessions actives des utilisateurs pour la gestion de la concurrence';


--
-- Name: COLUMN user_sessions.session_id; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON COLUMN core.user_sessions.session_id IS 'Identifiant unique de la session (UUID)';


--
-- Name: COLUMN user_sessions.ip_address; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON COLUMN core.user_sessions.ip_address IS 'Adresse IP du client (type PostgreSQL inet)';


--
-- Name: COLUMN user_sessions.user_agent; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON COLUMN core.user_sessions.user_agent IS 'User-Agent du navigateur/application';


--
-- Name: COLUMN user_sessions.derniere_activite; Type: COMMENT; Schema: core; Owner: -
--

COMMENT ON COLUMN core.user_sessions.derniere_activite IS 'Timestamp de la dernière activité de la session';


--
-- Name: affectations_paiements; Type: TABLE; Schema: finance; Owner: -
--

CREATE TABLE finance.affectations_paiements (
    affectation_id bigint NOT NULL,
    paiement_id bigint NOT NULL,
    facture_id bigint NOT NULL,
    montant_affecte numeric(15,2) NOT NULL,
    date_affectation timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_affectations_montant_positive CHECK ((montant_affecte > (0)::numeric))
);


--
-- Name: affectations_paiements_affectation_id_seq; Type: SEQUENCE; Schema: finance; Owner: -
--

ALTER TABLE finance.affectations_paiements ALTER COLUMN affectation_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME finance.affectations_paiements_affectation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: comptes; Type: TABLE; Schema: finance; Owner: -
--

CREATE TABLE finance.comptes (
    compte_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    numero_compte character varying(40) NOT NULL,
    type_compte character varying(20) NOT NULL,
    client_id bigint,
    societe_id bigint,
    solde_actuel numeric(15,2) DEFAULT 0 NOT NULL,
    credit_limite numeric(15,2) DEFAULT 0 NOT NULL,
    statut character varying(20) DEFAULT 'ACTIF'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(80) DEFAULT 'system'::character varying NOT NULL,
    updated_by character varying(80),
    CONSTRAINT chk_comptes_hotel_id_positive CHECK ((hotel_id > 0))
);


--
-- Name: comptes_compte_id_seq; Type: SEQUENCE; Schema: finance; Owner: -
--

ALTER TABLE finance.comptes ALTER COLUMN compte_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME finance.comptes_compte_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: factures; Type: TABLE; Schema: finance; Owner: -
--

CREATE TABLE finance.factures (
    facture_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    numero_facture character varying(40) NOT NULL,
    type_facture character varying(20) DEFAULT 'FACTURE'::character varying NOT NULL,
    compte_id bigint,
    client_id bigint,
    societe_id bigint,
    reservation_id bigint,
    fournisseur_id bigint,
    facture_reference_id bigint,
    date_facture date DEFAULT CURRENT_DATE NOT NULL,
    date_echeance date,
    montant_ht numeric(15,2) DEFAULT 0 NOT NULL,
    montant_tva numeric(15,2) DEFAULT 0 NOT NULL,
    montant_ttc numeric(15,2) DEFAULT 0 NOT NULL,
    montant_paye numeric(15,2) DEFAULT 0 NOT NULL,
    statut character varying(20) DEFAULT 'BROUILLON'::character varying NOT NULL,
    devise character varying(3) DEFAULT 'MRU'::character varying NOT NULL,
    commentaires text,
    user_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(80) DEFAULT 'system'::character varying NOT NULL,
    updated_by character varying(80),
    CONSTRAINT chk_factures_hotel_id_positive CHECK ((hotel_id > 0)),
    CONSTRAINT chk_factures_montants_coherence CHECK (((montant_paye >= (0)::numeric) AND (montant_paye <= (montant_ttc + 0.01))))
);


--
-- Name: factures_facture_id_seq; Type: SEQUENCE; Schema: finance; Owner: -
--

ALTER TABLE finance.factures ALTER COLUMN facture_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME finance.factures_facture_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: lignes_factures; Type: TABLE; Schema: finance; Owner: -
--

CREATE TABLE finance.lignes_factures (
    ligne_facture_id bigint NOT NULL,
    facture_id bigint NOT NULL,
    type_ligne character varying(20) NOT NULL,
    nuitee_id bigint,
    produit_id bigint,
    commande_id bigint,
    service_id bigint,
    libelle character varying(500) NOT NULL,
    quantite numeric(10,3) DEFAULT 1 NOT NULL,
    prix_unitaire numeric(15,2) DEFAULT 0 NOT NULL,
    taux_tva numeric(5,2) DEFAULT 0 NOT NULL,
    montant_ht numeric(15,2) DEFAULT 0 NOT NULL,
    montant_tva numeric(15,2) DEFAULT 0 NOT NULL,
    montant_ttc numeric(15,2) DEFAULT 0 NOT NULL,
    date_prestation date,
    CONSTRAINT chk_lignes_factures_prix_positif CHECK ((prix_unitaire >= (0)::numeric)),
    CONSTRAINT chk_lignes_factures_quantite_positive CHECK ((quantite > (0)::numeric))
);


--
-- Name: lignes_factures_ligne_facture_id_seq; Type: SEQUENCE; Schema: finance; Owner: -
--

ALTER TABLE finance.lignes_factures ALTER COLUMN ligne_facture_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME finance.lignes_factures_ligne_facture_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: numerotation_sequence; Type: TABLE; Schema: finance; Owner: -
--

CREATE TABLE finance.numerotation_sequence (
    id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    type character varying(10) NOT NULL,
    exercice integer NOT NULL,
    last_value bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(80) DEFAULT 'system'::character varying NOT NULL,
    updated_by character varying(80),
    CONSTRAINT chk_numerotation_hotel_id_positive CHECK ((hotel_id > 0))
);


--
-- Name: numerotation_sequence_id_seq; Type: SEQUENCE; Schema: finance; Owner: -
--

ALTER TABLE finance.numerotation_sequence ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME finance.numerotation_sequence_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: operations_comptes; Type: TABLE; Schema: finance; Owner: -
--

CREATE TABLE finance.operations_comptes (
    operation_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    compte_id bigint NOT NULL,
    type_operation character varying(20) NOT NULL,
    montant numeric(15,2) NOT NULL,
    libelle character varying(500) NOT NULL,
    facture_id bigint,
    paiement_id bigint,
    solde_avant numeric(15,2) NOT NULL,
    solde_apres numeric(15,2) NOT NULL,
    date_operation timestamp with time zone DEFAULT now() NOT NULL,
    user_id bigint NOT NULL,
    CONSTRAINT chk_operations_hotel_id_positive CHECK ((hotel_id > 0)),
    CONSTRAINT chk_operations_montant_positive CHECK ((montant > (0)::numeric))
);


--
-- Name: operations_comptes_operation_id_seq; Type: SEQUENCE; Schema: finance; Owner: -
--

ALTER TABLE finance.operations_comptes ALTER COLUMN operation_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME finance.operations_comptes_operation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: paiements; Type: TABLE; Schema: finance; Owner: -
--

CREATE TABLE finance.paiements (
    paiement_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    numero_paiement character varying(40) NOT NULL,
    compte_id bigint,
    montant_total numeric(15,2) NOT NULL,
    devise character varying(3) DEFAULT 'MRU'::character varying NOT NULL,
    mode_paiement character varying(20) NOT NULL,
    reference_paiement character varying(100),
    date_paiement date DEFAULT CURRENT_DATE NOT NULL,
    statut character varying(20) DEFAULT 'VALIDE'::character varying NOT NULL,
    commentaires text,
    user_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(80) DEFAULT 'system'::character varying NOT NULL,
    updated_by character varying(80),
    CONSTRAINT chk_paiements_hotel_id_positive CHECK ((hotel_id > 0)),
    CONSTRAINT chk_paiements_montant_positive CHECK ((montant_total > (0)::numeric))
);


--
-- Name: paiements_paiement_id_seq; Type: SEQUENCE; Schema: finance; Owner: -
--

ALTER TABLE finance.paiements ALTER COLUMN paiement_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME finance.paiements_paiement_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: chambres; Type: TABLE; Schema: hebergement; Owner: -
--

CREATE TABLE hebergement.chambres (
    chambre_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    numero_chambre character varying(10) NOT NULL,
    type_id bigint NOT NULL,
    etage integer,
    statut character varying(20) DEFAULT 'DISPONIBLE'::character varying NOT NULL,
    nb_lits integer NOT NULL,
    nb_personnes_max integer NOT NULL,
    equipements text,
    description text,
    actif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(80) DEFAULT 'system'::character varying NOT NULL,
    updated_by character varying(80),
    CONSTRAINT chk_chambres_hotel_id_positive CHECK ((hotel_id > 0))
);


--
-- Name: chambres_chambre_id_seq; Type: SEQUENCE; Schema: hebergement; Owner: -
--

ALTER TABLE hebergement.chambres ALTER COLUMN chambre_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME hebergement.chambres_chambre_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: nuitees; Type: TABLE; Schema: hebergement; Owner: -
--

CREATE TABLE hebergement.nuitees (
    nuitee_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    reservation_id bigint NOT NULL,
    chambre_id bigint NOT NULL,
    date_nuit date NOT NULL,
    prix_nuit numeric(10,2) NOT NULL,
    taxe_sejour numeric(10,2) DEFAULT 0 NOT NULL,
    statut character varying(20) DEFAULT 'PREVUE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(80) DEFAULT 'system'::character varying NOT NULL,
    updated_by character varying(80),
    facture_id bigint,
    ligne_facture_id bigint,
    CONSTRAINT chk_nuitees_hotel_id_positive CHECK ((hotel_id > 0))
);


--
-- Name: nuitees_nuitee_id_seq; Type: SEQUENCE; Schema: hebergement; Owner: -
--

ALTER TABLE hebergement.nuitees ALTER COLUMN nuitee_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME hebergement.nuitees_nuitee_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: reservations; Type: TABLE; Schema: hebergement; Owner: -
--

CREATE TABLE hebergement.reservations (
    reservation_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    numero_reservation character varying(40) NOT NULL,
    client_principal_id bigint NOT NULL,
    societe_id bigint,
    date_arrivee date NOT NULL,
    date_depart date NOT NULL,
    nb_nuits integer,
    nb_adultes integer DEFAULT 1 NOT NULL,
    nb_enfants integer DEFAULT 0 NOT NULL,
    statut character varying(20) DEFAULT 'CONFIRMEE'::character varying NOT NULL,
    motif_sejour character varying(100),
    commentaires text,
    reduction_pourcentage numeric(5,2) DEFAULT 0,
    montant_total numeric(12,2) DEFAULT 0,
    user_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(80) DEFAULT 'system'::character varying NOT NULL,
    updated_by character varying(80),
    facture_id bigint,
    CONSTRAINT chk_reservations_dates CHECK ((date_depart > date_arrivee)),
    CONSTRAINT chk_reservations_hotel_id_positive CHECK ((hotel_id > 0))
);


--
-- Name: reservations_chambres; Type: TABLE; Schema: hebergement; Owner: -
--

CREATE TABLE hebergement.reservations_chambres (
    reservation_chambre_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    reservation_id bigint NOT NULL,
    chambre_id bigint NOT NULL,
    date_debut date NOT NULL,
    date_fin date NOT NULL,
    prix_nuit numeric(10,2) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(80) DEFAULT 'system'::character varying NOT NULL,
    updated_by character varying(80),
    CONSTRAINT chk_res_chambres_dates CHECK ((date_fin > date_debut)),
    CONSTRAINT chk_res_chambres_hotel_id_positive CHECK ((hotel_id > 0))
);


--
-- Name: reservations_chambres_reservation_chambre_id_seq; Type: SEQUENCE; Schema: hebergement; Owner: -
--

ALTER TABLE hebergement.reservations_chambres ALTER COLUMN reservation_chambre_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME hebergement.reservations_chambres_reservation_chambre_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: reservations_clients; Type: TABLE; Schema: hebergement; Owner: -
--

CREATE TABLE hebergement.reservations_clients (
    reservation_client_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    reservation_id bigint NOT NULL,
    client_id bigint NOT NULL,
    chambre_id bigint,
    est_payant boolean DEFAULT true NOT NULL,
    pourcentage_charge numeric(5,2) DEFAULT 100 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(80) DEFAULT 'system'::character varying NOT NULL,
    updated_by character varying(80),
    CONSTRAINT chk_res_clients_hotel_id_positive CHECK ((hotel_id > 0))
);


--
-- Name: reservations_clients_reservation_client_id_seq; Type: SEQUENCE; Schema: hebergement; Owner: -
--

ALTER TABLE hebergement.reservations_clients ALTER COLUMN reservation_client_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME hebergement.reservations_clients_reservation_client_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: reservations_reservation_id_seq; Type: SEQUENCE; Schema: hebergement; Owner: -
--

ALTER TABLE hebergement.reservations ALTER COLUMN reservation_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME hebergement.reservations_reservation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tarifs_chambres; Type: TABLE; Schema: hebergement; Owner: -
--

CREATE TABLE hebergement.tarifs_chambres (
    tarif_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    type_id bigint NOT NULL,
    nom_tarif character varying(100) NOT NULL,
    date_debut date NOT NULL,
    date_fin date,
    prix_nuit numeric(10,2) NOT NULL,
    prix_weekend numeric(10,2),
    actif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(80) DEFAULT 'system'::character varying NOT NULL,
    updated_by character varying(80),
    CONSTRAINT chk_tarifs_chambres_hotel_id_positive CHECK ((hotel_id > 0)),
    CONSTRAINT chk_tarifs_dates CHECK (((date_fin IS NULL) OR (date_fin >= date_debut)))
);


--
-- Name: tarifs_chambres_tarif_id_seq; Type: SEQUENCE; Schema: hebergement; Owner: -
--

ALTER TABLE hebergement.tarifs_chambres ALTER COLUMN tarif_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME hebergement.tarifs_chambres_tarif_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: types_chambres; Type: TABLE; Schema: hebergement; Owner: -
--

CREATE TABLE hebergement.types_chambres (
    type_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    type_code character varying(10) NOT NULL,
    type_nom character varying(100) NOT NULL,
    description text,
    superficie numeric(6,2),
    nb_lits_max integer NOT NULL,
    nb_personnes_max integer NOT NULL,
    prix_base numeric(10,2) DEFAULT 0,
    actif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(80) DEFAULT 'system'::character varying NOT NULL,
    updated_by character varying(80),
    CONSTRAINT chk_types_chambres_hotel_id_positive CHECK ((hotel_id > 0))
);


--
-- Name: types_chambres_type_id_seq; Type: SEQUENCE; Schema: hebergement; Owner: -
--

ALTER TABLE hebergement.types_chambres ALTER COLUMN type_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME hebergement.types_chambres_type_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: bons_commande; Type: TABLE; Schema: inventory; Owner: -
--

CREATE TABLE inventory.bons_commande (
    bon_commande_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    numero_bc character varying(40) NOT NULL,
    fournisseur_id bigint NOT NULL,
    statut character varying(20) DEFAULT 'BROUILLON'::character varying NOT NULL,
    date_commande date NOT NULL,
    date_livraison_prevue date,
    date_livraison_reelle date,
    montant_total numeric(12,2) DEFAULT 0 NOT NULL,
    montant_tva numeric(12,2) DEFAULT 0 NOT NULL,
    commentaires text,
    user_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(80) DEFAULT 'system'::character varying NOT NULL,
    updated_by character varying(80),
    facture_fournisseur_id bigint,
    CONSTRAINT chk_bons_commande_hotel_id_positive CHECK ((hotel_id > 0))
);


--
-- Name: bons_commande_bon_commande_id_seq; Type: SEQUENCE; Schema: inventory; Owner: -
--

ALTER TABLE inventory.bons_commande ALTER COLUMN bon_commande_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME inventory.bons_commande_bon_commande_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: bons_sortie; Type: TABLE; Schema: inventory; Owner: -
--

CREATE TABLE inventory.bons_sortie (
    bon_sortie_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    numero_bs character varying(40) NOT NULL,
    destination character varying(100) NOT NULL,
    statut character varying(20) DEFAULT 'BROUILLON'::character varying NOT NULL,
    date_sortie date NOT NULL,
    commentaires text,
    user_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(80) DEFAULT 'system'::character varying NOT NULL,
    updated_by character varying(80),
    CONSTRAINT chk_bons_sortie_hotel_id_positive CHECK ((hotel_id > 0))
);


--
-- Name: bons_sortie_bon_sortie_id_seq; Type: SEQUENCE; Schema: inventory; Owner: -
--

ALTER TABLE inventory.bons_sortie ALTER COLUMN bon_sortie_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME inventory.bons_sortie_bon_sortie_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: categories_produits; Type: TABLE; Schema: inventory; Owner: -
--

CREATE TABLE inventory.categories_produits (
    categorie_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    code_categorie character varying(10) NOT NULL,
    nom_categorie character varying(100) NOT NULL,
    description text,
    actif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(80) DEFAULT 'system'::character varying NOT NULL,
    updated_by character varying(80),
    CONSTRAINT chk_categories_produits_hotel_id_positive CHECK ((hotel_id > 0))
);


--
-- Name: categories_produits_categorie_id_seq; Type: SEQUENCE; Schema: inventory; Owner: -
--

ALTER TABLE inventory.categories_produits ALTER COLUMN categorie_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME inventory.categories_produits_categorie_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: fournisseurs; Type: TABLE; Schema: inventory; Owner: -
--

CREATE TABLE inventory.fournisseurs (
    fournisseur_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    nom_fournisseur character varying(255) NOT NULL,
    contact_principal character varying(200),
    telephone character varying(20),
    email character varying(100),
    adresse text,
    ville character varying(100),
    pays character varying(100),
    conditions_paiement text,
    actif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(80) DEFAULT 'system'::character varying NOT NULL,
    updated_by character varying(80),
    CONSTRAINT chk_fournisseurs_hotel_id_positive CHECK ((hotel_id > 0))
);


--
-- Name: fournisseurs_fournisseur_id_seq; Type: SEQUENCE; Schema: inventory; Owner: -
--

ALTER TABLE inventory.fournisseurs ALTER COLUMN fournisseur_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME inventory.fournisseurs_fournisseur_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: lignes_bons_commande; Type: TABLE; Schema: inventory; Owner: -
--

CREATE TABLE inventory.lignes_bons_commande (
    ligne_id bigint NOT NULL,
    bon_commande_id bigint NOT NULL,
    produit_id bigint NOT NULL,
    quantite_commandee integer NOT NULL,
    quantite_recue integer DEFAULT 0 NOT NULL,
    prix_unitaire numeric(10,2) NOT NULL,
    date_reception date
);


--
-- Name: lignes_bons_commande_ligne_id_seq; Type: SEQUENCE; Schema: inventory; Owner: -
--

ALTER TABLE inventory.lignes_bons_commande ALTER COLUMN ligne_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME inventory.lignes_bons_commande_ligne_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: lignes_bons_sortie; Type: TABLE; Schema: inventory; Owner: -
--

CREATE TABLE inventory.lignes_bons_sortie (
    ligne_id bigint NOT NULL,
    bon_sortie_id bigint NOT NULL,
    produit_id bigint NOT NULL,
    quantite_demandee integer NOT NULL,
    quantite_servie integer DEFAULT 0 NOT NULL,
    commentaires text
);


--
-- Name: lignes_bons_sortie_ligne_id_seq; Type: SEQUENCE; Schema: inventory; Owner: -
--

ALTER TABLE inventory.lignes_bons_sortie ALTER COLUMN ligne_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME inventory.lignes_bons_sortie_ligne_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: mouvements_stock; Type: TABLE; Schema: inventory; Owner: -
--

CREATE TABLE inventory.mouvements_stock (
    mouvement_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    produit_id bigint NOT NULL,
    type_mouvement character varying(20) NOT NULL,
    quantite integer NOT NULL,
    prix_unitaire numeric(10,2),
    stock_avant integer NOT NULL,
    stock_apres integer NOT NULL,
    reference_document character varying(50),
    commentaire text,
    user_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(80) DEFAULT 'system'::character varying NOT NULL,
    updated_by character varying(80),
    CONSTRAINT chk_mouvements_stock_hotel_id_positive CHECK ((hotel_id > 0))
);


--
-- Name: mouvements_stock_mouvement_id_seq; Type: SEQUENCE; Schema: inventory; Owner: -
--

ALTER TABLE inventory.mouvements_stock ALTER COLUMN mouvement_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME inventory.mouvements_stock_mouvement_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: produits; Type: TABLE; Schema: inventory; Owner: -
--

CREATE TABLE inventory.produits (
    produit_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    code_produit character varying(20) NOT NULL,
    nom_produit character varying(255) NOT NULL,
    description text,
    categorie_id bigint NOT NULL,
    unite_mesure character varying(20) NOT NULL,
    prix_unitaire numeric(10,2) DEFAULT 0,
    seuil_alerte integer DEFAULT 0 NOT NULL,
    seuil_critique integer DEFAULT 0 NOT NULL,
    stock_actuel integer DEFAULT 0 NOT NULL,
    fournisseur_principal_id bigint,
    est_facturable boolean DEFAULT false NOT NULL,
    actif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(80) DEFAULT 'system'::character varying NOT NULL,
    updated_by character varying(80),
    CONSTRAINT chk_produits_hotel_id_positive CHECK ((hotel_id > 0)),
    CONSTRAINT chk_produits_stock_actuel_positive CHECK ((stock_actuel >= 0))
);


--
-- Name: produits_produit_id_seq; Type: SEQUENCE; Schema: inventory; Owner: -
--

ALTER TABLE inventory.produits ALTER COLUMN produit_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME inventory.produits_produit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: databasechangelog; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.databasechangelog (
    id character varying(255) NOT NULL,
    author character varying(255) NOT NULL,
    filename character varying(255) NOT NULL,
    dateexecuted timestamp without time zone NOT NULL,
    orderexecuted integer NOT NULL,
    exectype character varying(10) NOT NULL,
    md5sum character varying(35),
    description character varying(255),
    comments character varying(255),
    tag character varying(255),
    liquibase character varying(20),
    contexts character varying(255),
    labels character varying(255),
    deployment_id character varying(10)
);


--
-- Name: databasechangeloglock; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.databasechangeloglock (
    id integer NOT NULL,
    locked boolean NOT NULL,
    lockgranted timestamp without time zone,
    lockedby character varying(255)
);


--
-- Name: dbusers user_id; Type: DEFAULT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.dbusers ALTER COLUMN user_id SET DEFAULT nextval('core.dbusers_user_id_seq'::regclass);


--
-- Name: donnees_referentielles ref_id; Type: DEFAULT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.donnees_referentielles ALTER COLUMN ref_id SET DEFAULT nextval('core.donnees_referentielles_ref_id_seq'::regclass);


--
-- Name: hotels hotel_id; Type: DEFAULT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.hotels ALTER COLUMN hotel_id SET DEFAULT nextval('core.hotels_hotel_id_seq'::regclass);


--
-- Name: roles role_id; Type: DEFAULT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.roles ALTER COLUMN role_id SET DEFAULT nextval('core.roles_role_id_seq'::regclass);


--
-- Name: clients pk_clients; Type: CONSTRAINT; Schema: client; Owner: -
--

ALTER TABLE ONLY client.clients
    ADD CONSTRAINT pk_clients PRIMARY KEY (client_id);


--
-- Name: societes pk_societes; Type: CONSTRAINT; Schema: client; Owner: -
--

ALTER TABLE ONLY client.societes
    ADD CONSTRAINT pk_societes PRIMARY KEY (societe_id);


--
-- Name: clients uk_clients_hotel_numero; Type: CONSTRAINT; Schema: client; Owner: -
--

ALTER TABLE ONLY client.clients
    ADD CONSTRAINT uk_clients_hotel_numero UNIQUE (hotel_id, numero_client);


--
-- Name: dbusers dbusers_email_key; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.dbusers
    ADD CONSTRAINT dbusers_email_key UNIQUE (email);


--
-- Name: dbusers dbusers_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.dbusers
    ADD CONSTRAINT dbusers_pkey PRIMARY KEY (user_id);


--
-- Name: dbusers dbusers_username_key; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.dbusers
    ADD CONSTRAINT dbusers_username_key UNIQUE (username);


--
-- Name: donnees_referentielles donnees_referentielles_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.donnees_referentielles
    ADD CONSTRAINT donnees_referentielles_pkey PRIMARY KEY (ref_id);


--
-- Name: hotels hotels_hotel_code_key; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.hotels
    ADD CONSTRAINT hotels_hotel_code_key UNIQUE (hotel_code);


--
-- Name: hotels hotels_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.hotels
    ADD CONSTRAINT hotels_pkey PRIMARY KEY (hotel_id);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (role_id);


--
-- Name: roles roles_role_code_key; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.roles
    ADD CONSTRAINT roles_role_code_key UNIQUE (role_code);


--
-- Name: user_sessions user_sessions_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.user_sessions
    ADD CONSTRAINT user_sessions_pkey PRIMARY KEY (session_id);


--
-- Name: affectations_paiements pk_affectations_paiements; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.affectations_paiements
    ADD CONSTRAINT pk_affectations_paiements PRIMARY KEY (affectation_id);


--
-- Name: comptes pk_comptes; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.comptes
    ADD CONSTRAINT pk_comptes PRIMARY KEY (compte_id);


--
-- Name: factures pk_factures; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.factures
    ADD CONSTRAINT pk_factures PRIMARY KEY (facture_id);


--
-- Name: lignes_factures pk_lignes_factures; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.lignes_factures
    ADD CONSTRAINT pk_lignes_factures PRIMARY KEY (ligne_facture_id);


--
-- Name: numerotation_sequence pk_numerotation_sequence; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.numerotation_sequence
    ADD CONSTRAINT pk_numerotation_sequence PRIMARY KEY (id);


--
-- Name: operations_comptes pk_operations_comptes; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.operations_comptes
    ADD CONSTRAINT pk_operations_comptes PRIMARY KEY (operation_id);


--
-- Name: paiements pk_paiements; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.paiements
    ADD CONSTRAINT pk_paiements PRIMARY KEY (paiement_id);


--
-- Name: affectations_paiements uk_affectations_paiement_facture; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.affectations_paiements
    ADD CONSTRAINT uk_affectations_paiement_facture UNIQUE (paiement_id, facture_id);


--
-- Name: comptes uk_comptes_hotel_numero; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.comptes
    ADD CONSTRAINT uk_comptes_hotel_numero UNIQUE (hotel_id, numero_compte);


--
-- Name: factures uk_factures_hotel_numero; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.factures
    ADD CONSTRAINT uk_factures_hotel_numero UNIQUE (hotel_id, numero_facture);


--
-- Name: numerotation_sequence uk_numerotation_hotel_type_exercice; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.numerotation_sequence
    ADD CONSTRAINT uk_numerotation_hotel_type_exercice UNIQUE (hotel_id, type, exercice);


--
-- Name: paiements uk_paiements_hotel_numero; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.paiements
    ADD CONSTRAINT uk_paiements_hotel_numero UNIQUE (hotel_id, numero_paiement);


--
-- Name: reservations_chambres exc_no_double_booking; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations_chambres
    ADD CONSTRAINT exc_no_double_booking EXCLUDE USING gist (chambre_id WITH =, daterange(date_debut, date_fin, '[)'::text) WITH &&) WHERE ((date_fin > date_debut));


--
-- Name: chambres pk_chambres; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.chambres
    ADD CONSTRAINT pk_chambres PRIMARY KEY (chambre_id);


--
-- Name: nuitees pk_nuitees; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.nuitees
    ADD CONSTRAINT pk_nuitees PRIMARY KEY (nuitee_id);


--
-- Name: reservations pk_reservations; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations
    ADD CONSTRAINT pk_reservations PRIMARY KEY (reservation_id);


--
-- Name: reservations_chambres pk_reservations_chambres; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations_chambres
    ADD CONSTRAINT pk_reservations_chambres PRIMARY KEY (reservation_chambre_id);


--
-- Name: reservations_clients pk_reservations_clients; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations_clients
    ADD CONSTRAINT pk_reservations_clients PRIMARY KEY (reservation_client_id);


--
-- Name: tarifs_chambres pk_tarifs_chambres; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.tarifs_chambres
    ADD CONSTRAINT pk_tarifs_chambres PRIMARY KEY (tarif_id);


--
-- Name: types_chambres pk_types_chambres; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.types_chambres
    ADD CONSTRAINT pk_types_chambres PRIMARY KEY (type_id);


--
-- Name: chambres uk_chambres_hotel_numero; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.chambres
    ADD CONSTRAINT uk_chambres_hotel_numero UNIQUE (hotel_id, numero_chambre);


--
-- Name: nuitees uk_nuitees; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.nuitees
    ADD CONSTRAINT uk_nuitees UNIQUE (reservation_id, chambre_id, date_nuit);


--
-- Name: reservations_chambres uk_res_chambres; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations_chambres
    ADD CONSTRAINT uk_res_chambres UNIQUE (reservation_id, chambre_id, date_debut);


--
-- Name: reservations_clients uk_res_clients; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations_clients
    ADD CONSTRAINT uk_res_clients UNIQUE (reservation_id, client_id);


--
-- Name: reservations uk_reservations_hotel_numero; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations
    ADD CONSTRAINT uk_reservations_hotel_numero UNIQUE (hotel_id, numero_reservation);


--
-- Name: types_chambres uk_types_chambres_hotel_code; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.types_chambres
    ADD CONSTRAINT uk_types_chambres_hotel_code UNIQUE (hotel_id, type_code);


--
-- Name: bons_commande pk_bons_commande; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.bons_commande
    ADD CONSTRAINT pk_bons_commande PRIMARY KEY (bon_commande_id);


--
-- Name: bons_sortie pk_bons_sortie; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.bons_sortie
    ADD CONSTRAINT pk_bons_sortie PRIMARY KEY (bon_sortie_id);


--
-- Name: categories_produits pk_categories_produits; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.categories_produits
    ADD CONSTRAINT pk_categories_produits PRIMARY KEY (categorie_id);


--
-- Name: fournisseurs pk_fournisseurs; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.fournisseurs
    ADD CONSTRAINT pk_fournisseurs PRIMARY KEY (fournisseur_id);


--
-- Name: lignes_bons_commande pk_lignes_bons_commande; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.lignes_bons_commande
    ADD CONSTRAINT pk_lignes_bons_commande PRIMARY KEY (ligne_id);


--
-- Name: lignes_bons_sortie pk_lignes_bons_sortie; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.lignes_bons_sortie
    ADD CONSTRAINT pk_lignes_bons_sortie PRIMARY KEY (ligne_id);


--
-- Name: mouvements_stock pk_mouvements_stock; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.mouvements_stock
    ADD CONSTRAINT pk_mouvements_stock PRIMARY KEY (mouvement_id);


--
-- Name: produits pk_produits; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.produits
    ADD CONSTRAINT pk_produits PRIMARY KEY (produit_id);


--
-- Name: bons_commande uk_bons_commande_hotel_numero; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.bons_commande
    ADD CONSTRAINT uk_bons_commande_hotel_numero UNIQUE (hotel_id, numero_bc);


--
-- Name: bons_sortie uk_bons_sortie_hotel_numero; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.bons_sortie
    ADD CONSTRAINT uk_bons_sortie_hotel_numero UNIQUE (hotel_id, numero_bs);


--
-- Name: categories_produits uk_categories_produits_hotel_code; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.categories_produits
    ADD CONSTRAINT uk_categories_produits_hotel_code UNIQUE (hotel_id, code_categorie);


--
-- Name: produits uk_produits_hotel_code; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.produits
    ADD CONSTRAINT uk_produits_hotel_code UNIQUE (hotel_id, code_produit);


--
-- Name: databasechangeloglock databasechangeloglock_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.databasechangeloglock
    ADD CONSTRAINT databasechangeloglock_pkey PRIMARY KEY (id);


--
-- Name: ix_clients_hotel_actif; Type: INDEX; Schema: client; Owner: -
--

CREATE INDEX ix_clients_hotel_actif ON client.clients USING btree (hotel_id, actif);


--
-- Name: ix_clients_hotel_email; Type: INDEX; Schema: client; Owner: -
--

CREATE INDEX ix_clients_hotel_email ON client.clients USING btree (hotel_id, email);


--
-- Name: ix_clients_hotel_nom; Type: INDEX; Schema: client; Owner: -
--

CREATE INDEX ix_clients_hotel_nom ON client.clients USING btree (hotel_id, nom, prenom);


--
-- Name: ix_clients_societe; Type: INDEX; Schema: client; Owner: -
--

CREATE INDEX ix_clients_societe ON client.clients USING btree (societe_id);


--
-- Name: ix_societes_hotel_actif; Type: INDEX; Schema: client; Owner: -
--

CREATE INDEX ix_societes_hotel_actif ON client.societes USING btree (hotel_id, actif);


--
-- Name: ix_societes_hotel_nom; Type: INDEX; Schema: client; Owner: -
--

CREATE INDEX ix_societes_hotel_nom ON client.societes USING btree (hotel_id, societe_nom);


--
-- Name: idx_dbusers_actif; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_dbusers_actif ON core.dbusers USING btree (actif);


--
-- Name: idx_dbusers_derniere_connexion; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_dbusers_derniere_connexion ON core.dbusers USING btree (derniere_connexion);


--
-- Name: idx_dbusers_email; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_dbusers_email ON core.dbusers USING btree (email);


--
-- Name: idx_dbusers_hotel; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_dbusers_hotel ON core.dbusers USING btree (hotel_id);


--
-- Name: idx_dbusers_role; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_dbusers_role ON core.dbusers USING btree (role_id);


--
-- Name: idx_dbusers_username; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_dbusers_username ON core.dbusers USING btree (username);


--
-- Name: idx_dbusers_verrouille; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_dbusers_verrouille ON core.dbusers USING btree (compte_verrouille);


--
-- Name: idx_donnees_ref_actif; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_donnees_ref_actif ON core.donnees_referentielles USING btree (actif);


--
-- Name: idx_donnees_ref_categorie; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_donnees_ref_categorie ON core.donnees_referentielles USING btree (categorie);


--
-- Name: idx_donnees_ref_code; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_donnees_ref_code ON core.donnees_referentielles USING btree (code);


--
-- Name: idx_hotels_actif; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_hotels_actif ON core.hotels USING btree (actif);


--
-- Name: idx_hotels_code; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_hotels_code ON core.hotels USING btree (hotel_code);


--
-- Name: idx_hotels_ville; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_hotels_ville ON core.hotels USING btree (ville);


--
-- Name: idx_roles_actif; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_roles_actif ON core.roles USING btree (actif);


--
-- Name: idx_roles_code; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_roles_code ON core.roles USING btree (role_code);


--
-- Name: idx_sessions_actif; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_sessions_actif ON core.user_sessions USING btree (actif);


--
-- Name: idx_sessions_activite; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_sessions_activite ON core.user_sessions USING btree (derniere_activite);


--
-- Name: idx_sessions_creation; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_sessions_creation ON core.user_sessions USING btree (date_creation);


--
-- Name: idx_sessions_hotel; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_sessions_hotel ON core.user_sessions USING btree (hotel_id);


--
-- Name: idx_sessions_user; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_sessions_user ON core.user_sessions USING btree (user_id);


--
-- Name: idx_user_sessions_ip_address; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_user_sessions_ip_address ON core.user_sessions USING gist (ip_address inet_ops);


--
-- Name: ix_affectations_facture; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX ix_affectations_facture ON finance.affectations_paiements USING btree (facture_id);


--
-- Name: ix_comptes_hotel_client; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX ix_comptes_hotel_client ON finance.comptes USING btree (hotel_id, client_id);


--
-- Name: ix_comptes_hotel_societe; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX ix_comptes_hotel_societe ON finance.comptes USING btree (hotel_id, societe_id);


--
-- Name: ix_factures_fournisseur; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX ix_factures_fournisseur ON finance.factures USING btree (fournisseur_id);


--
-- Name: ix_factures_hotel_client; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX ix_factures_hotel_client ON finance.factures USING btree (hotel_id, client_id);


--
-- Name: ix_factures_hotel_date; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX ix_factures_hotel_date ON finance.factures USING btree (hotel_id, date_facture);


--
-- Name: ix_factures_hotel_reservation; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX ix_factures_hotel_reservation ON finance.factures USING btree (hotel_id, reservation_id);


--
-- Name: ix_factures_hotel_statut; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX ix_factures_hotel_statut ON finance.factures USING btree (hotel_id, statut);


--
-- Name: ix_lignes_factures_facture; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX ix_lignes_factures_facture ON finance.lignes_factures USING btree (facture_id);


--
-- Name: ix_lignes_factures_nuitee; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX ix_lignes_factures_nuitee ON finance.lignes_factures USING btree (nuitee_id);


--
-- Name: ix_lignes_factures_produit; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX ix_lignes_factures_produit ON finance.lignes_factures USING btree (produit_id);


--
-- Name: ix_operations_facture; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX ix_operations_facture ON finance.operations_comptes USING btree (facture_id);


--
-- Name: ix_operations_hotel_compte; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX ix_operations_hotel_compte ON finance.operations_comptes USING btree (hotel_id, compte_id);


--
-- Name: ix_operations_hotel_date; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX ix_operations_hotel_date ON finance.operations_comptes USING btree (hotel_id, date_operation);


--
-- Name: ix_operations_paiement; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX ix_operations_paiement ON finance.operations_comptes USING btree (paiement_id);


--
-- Name: ix_paiements_compte; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX ix_paiements_compte ON finance.paiements USING btree (compte_id);


--
-- Name: ix_paiements_hotel_date; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX ix_paiements_hotel_date ON finance.paiements USING btree (hotel_id, date_paiement);


--
-- Name: ix_paiements_hotel_statut; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX ix_paiements_hotel_statut ON finance.paiements USING btree (hotel_id, statut);


--
-- Name: ix_chambres_hotel_statut; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_chambres_hotel_statut ON hebergement.chambres USING btree (hotel_id, statut);


--
-- Name: ix_chambres_hotel_type; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_chambres_hotel_type ON hebergement.chambres USING btree (hotel_id, type_id);


--
-- Name: ix_nuitees_chambre; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_nuitees_chambre ON hebergement.nuitees USING btree (chambre_id);


--
-- Name: ix_nuitees_facture; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_nuitees_facture ON hebergement.nuitees USING btree (facture_id);


--
-- Name: ix_nuitees_hotel_date; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_nuitees_hotel_date ON hebergement.nuitees USING btree (hotel_id, date_nuit);


--
-- Name: ix_nuitees_hotel_statut; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_nuitees_hotel_statut ON hebergement.nuitees USING btree (hotel_id, statut);


--
-- Name: ix_nuitees_reservation_statut; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_nuitees_reservation_statut ON hebergement.nuitees USING btree (reservation_id, statut);


--
-- Name: ix_res_chambres_chambre_periode; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_res_chambres_chambre_periode ON hebergement.reservations_chambres USING btree (chambre_id, date_debut, date_fin);


--
-- Name: ix_res_chambres_hotel_reservation; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_res_chambres_hotel_reservation ON hebergement.reservations_chambres USING btree (hotel_id, reservation_id);


--
-- Name: ix_res_clients_chambre; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_res_clients_chambre ON hebergement.reservations_clients USING btree (chambre_id);


--
-- Name: ix_res_clients_client; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_res_clients_client ON hebergement.reservations_clients USING btree (client_id);


--
-- Name: ix_res_clients_hotel_reservation; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_res_clients_hotel_reservation ON hebergement.reservations_clients USING btree (hotel_id, reservation_id);


--
-- Name: ix_reservations_client; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_reservations_client ON hebergement.reservations USING btree (hotel_id, client_principal_id);


--
-- Name: ix_reservations_facture; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_reservations_facture ON hebergement.reservations USING btree (facture_id);


--
-- Name: ix_reservations_hotel_dates; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_reservations_hotel_dates ON hebergement.reservations USING btree (hotel_id, date_arrivee, date_depart);


--
-- Name: ix_reservations_hotel_statut; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_reservations_hotel_statut ON hebergement.reservations USING btree (hotel_id, statut);


--
-- Name: ix_reservations_user; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_reservations_user ON hebergement.reservations USING btree (user_id);


--
-- Name: ix_tarifs_hotel_type_actif; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_tarifs_hotel_type_actif ON hebergement.tarifs_chambres USING btree (hotel_id, type_id, actif);


--
-- Name: ix_tarifs_hotel_type_periode; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_tarifs_hotel_type_periode ON hebergement.tarifs_chambres USING btree (hotel_id, type_id, date_debut);


--
-- Name: ix_types_chambres_hotel_actif; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX ix_types_chambres_hotel_actif ON hebergement.types_chambres USING btree (hotel_id, actif);


--
-- Name: ix_bons_commande_facture; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX ix_bons_commande_facture ON inventory.bons_commande USING btree (facture_fournisseur_id);


--
-- Name: ix_bons_commande_hotel_fournisseur; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX ix_bons_commande_hotel_fournisseur ON inventory.bons_commande USING btree (hotel_id, fournisseur_id);


--
-- Name: ix_bons_commande_hotel_statut; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX ix_bons_commande_hotel_statut ON inventory.bons_commande USING btree (hotel_id, statut);


--
-- Name: ix_bons_commande_user; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX ix_bons_commande_user ON inventory.bons_commande USING btree (user_id);


--
-- Name: ix_bons_sortie_hotel_destination; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX ix_bons_sortie_hotel_destination ON inventory.bons_sortie USING btree (hotel_id, destination);


--
-- Name: ix_bons_sortie_hotel_statut; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX ix_bons_sortie_hotel_statut ON inventory.bons_sortie USING btree (hotel_id, statut);


--
-- Name: ix_bons_sortie_user; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX ix_bons_sortie_user ON inventory.bons_sortie USING btree (user_id);


--
-- Name: ix_categories_produits_hotel_actif; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX ix_categories_produits_hotel_actif ON inventory.categories_produits USING btree (hotel_id, actif);


--
-- Name: ix_fournisseurs_hotel_actif; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX ix_fournisseurs_hotel_actif ON inventory.fournisseurs USING btree (hotel_id, actif);


--
-- Name: ix_fournisseurs_hotel_nom; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX ix_fournisseurs_hotel_nom ON inventory.fournisseurs USING btree (hotel_id, nom_fournisseur);


--
-- Name: ix_lignes_bc_bon; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX ix_lignes_bc_bon ON inventory.lignes_bons_commande USING btree (bon_commande_id);


--
-- Name: ix_lignes_bs_bon; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX ix_lignes_bs_bon ON inventory.lignes_bons_sortie USING btree (bon_sortie_id);


--
-- Name: ix_mouvements_stock_hotel_produit; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX ix_mouvements_stock_hotel_produit ON inventory.mouvements_stock USING btree (hotel_id, produit_id);


--
-- Name: ix_mouvements_stock_hotel_type; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX ix_mouvements_stock_hotel_type ON inventory.mouvements_stock USING btree (hotel_id, type_mouvement);


--
-- Name: ix_mouvements_stock_user; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX ix_mouvements_stock_user ON inventory.mouvements_stock USING btree (user_id);


--
-- Name: ix_produits_fournisseur_principal; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX ix_produits_fournisseur_principal ON inventory.produits USING btree (fournisseur_principal_id);


--
-- Name: ix_produits_hotel_actif; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX ix_produits_hotel_actif ON inventory.produits USING btree (hotel_id, actif);


--
-- Name: ix_produits_hotel_categorie; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX ix_produits_hotel_categorie ON inventory.produits USING btree (hotel_id, categorie_id);


--
-- Name: ix_produits_hotel_nom; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX ix_produits_hotel_nom ON inventory.produits USING btree (hotel_id, nom_produit);


--
-- Name: dbusers update_dbusers_modtime; Type: TRIGGER; Schema: core; Owner: -
--

CREATE TRIGGER update_dbusers_modtime BEFORE UPDATE ON core.dbusers FOR EACH ROW EXECUTE FUNCTION core.update_modified_column();


--
-- Name: hotels update_hotels_modtime; Type: TRIGGER; Schema: core; Owner: -
--

CREATE TRIGGER update_hotels_modtime BEFORE UPDATE ON core.hotels FOR EACH ROW EXECUTE FUNCTION core.update_modified_column();


--
-- Name: affectations_paiements trg_check_pivot_tenant_affectations_paiements; Type: TRIGGER; Schema: finance; Owner: -
--

CREATE TRIGGER trg_check_pivot_tenant_affectations_paiements BEFORE INSERT OR UPDATE ON finance.affectations_paiements FOR EACH ROW EXECUTE FUNCTION finance.check_pivot_tenant_coherence_affectations_paiements();


--
-- Name: lignes_factures trg_check_pivot_tenant_lignes_factures; Type: TRIGGER; Schema: finance; Owner: -
--

CREATE TRIGGER trg_check_pivot_tenant_lignes_factures BEFORE INSERT OR UPDATE ON finance.lignes_factures FOR EACH ROW EXECUTE FUNCTION finance.check_pivot_tenant_coherence_lignes_factures();


--
-- Name: operations_comptes trg_check_pivot_tenant_operations_comptes; Type: TRIGGER; Schema: finance; Owner: -
--

CREATE TRIGGER trg_check_pivot_tenant_operations_comptes BEFORE INSERT OR UPDATE ON finance.operations_comptes FOR EACH ROW EXECUTE FUNCTION finance.check_pivot_tenant_coherence_operations_comptes();


--
-- Name: paiements trg_check_pivot_tenant_paiements; Type: TRIGGER; Schema: finance; Owner: -
--

CREATE TRIGGER trg_check_pivot_tenant_paiements BEFORE INSERT OR UPDATE ON finance.paiements FOR EACH ROW EXECUTE FUNCTION finance.check_pivot_tenant_coherence_paiements();


--
-- Name: nuitees trg_check_pivot_tenant_nuitees; Type: TRIGGER; Schema: hebergement; Owner: -
--

CREATE TRIGGER trg_check_pivot_tenant_nuitees BEFORE INSERT OR UPDATE ON hebergement.nuitees FOR EACH ROW EXECUTE FUNCTION hebergement.check_pivot_tenant_coherence_nuitees();


--
-- Name: reservations_chambres trg_check_pivot_tenant_resch; Type: TRIGGER; Schema: hebergement; Owner: -
--

CREATE TRIGGER trg_check_pivot_tenant_resch BEFORE INSERT OR UPDATE ON hebergement.reservations_chambres FOR EACH ROW EXECUTE FUNCTION hebergement.check_pivot_tenant_coherence_resch();


--
-- Name: reservations_clients trg_check_pivot_tenant_resclients; Type: TRIGGER; Schema: hebergement; Owner: -
--

CREATE TRIGGER trg_check_pivot_tenant_resclients BEFORE INSERT OR UPDATE ON hebergement.reservations_clients FOR EACH ROW EXECUTE FUNCTION hebergement.check_pivot_tenant_coherence_resclients();


--
-- Name: clients fk_clients_hotel; Type: FK CONSTRAINT; Schema: client; Owner: -
--

ALTER TABLE ONLY client.clients
    ADD CONSTRAINT fk_clients_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: clients fk_clients_societe; Type: FK CONSTRAINT; Schema: client; Owner: -
--

ALTER TABLE ONLY client.clients
    ADD CONSTRAINT fk_clients_societe FOREIGN KEY (societe_id) REFERENCES client.societes(societe_id) ON DELETE SET NULL;


--
-- Name: societes fk_societes_hotel; Type: FK CONSTRAINT; Schema: client; Owner: -
--

ALTER TABLE ONLY client.societes
    ADD CONSTRAINT fk_societes_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: dbusers fk_dbusers_hotel; Type: FK CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.dbusers
    ADD CONSTRAINT fk_dbusers_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: dbusers fk_dbusers_role; Type: FK CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.dbusers
    ADD CONSTRAINT fk_dbusers_role FOREIGN KEY (role_id) REFERENCES core.roles(role_id);


--
-- Name: user_sessions fk_sessions_hotel; Type: FK CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.user_sessions
    ADD CONSTRAINT fk_sessions_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: user_sessions fk_sessions_user; Type: FK CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.user_sessions
    ADD CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES core.dbusers(user_id);


--
-- Name: affectations_paiements fk_affectations_facture; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.affectations_paiements
    ADD CONSTRAINT fk_affectations_facture FOREIGN KEY (facture_id) REFERENCES finance.factures(facture_id);


--
-- Name: affectations_paiements fk_affectations_paiement; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.affectations_paiements
    ADD CONSTRAINT fk_affectations_paiement FOREIGN KEY (paiement_id) REFERENCES finance.paiements(paiement_id) ON DELETE CASCADE;


--
-- Name: comptes fk_comptes_client; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.comptes
    ADD CONSTRAINT fk_comptes_client FOREIGN KEY (client_id) REFERENCES client.clients(client_id);


--
-- Name: comptes fk_comptes_hotel; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.comptes
    ADD CONSTRAINT fk_comptes_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: comptes fk_comptes_societe; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.comptes
    ADD CONSTRAINT fk_comptes_societe FOREIGN KEY (societe_id) REFERENCES client.societes(societe_id);


--
-- Name: factures fk_factures_client; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.factures
    ADD CONSTRAINT fk_factures_client FOREIGN KEY (client_id) REFERENCES client.clients(client_id);


--
-- Name: factures fk_factures_compte; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.factures
    ADD CONSTRAINT fk_factures_compte FOREIGN KEY (compte_id) REFERENCES finance.comptes(compte_id);


--
-- Name: factures fk_factures_fournisseur; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.factures
    ADD CONSTRAINT fk_factures_fournisseur FOREIGN KEY (fournisseur_id) REFERENCES inventory.fournisseurs(fournisseur_id) ON DELETE SET NULL;


--
-- Name: factures fk_factures_hotel; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.factures
    ADD CONSTRAINT fk_factures_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: factures fk_factures_reference; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.factures
    ADD CONSTRAINT fk_factures_reference FOREIGN KEY (facture_reference_id) REFERENCES finance.factures(facture_id);


--
-- Name: factures fk_factures_reservation; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.factures
    ADD CONSTRAINT fk_factures_reservation FOREIGN KEY (reservation_id) REFERENCES hebergement.reservations(reservation_id) ON DELETE SET NULL;


--
-- Name: factures fk_factures_societe; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.factures
    ADD CONSTRAINT fk_factures_societe FOREIGN KEY (societe_id) REFERENCES client.societes(societe_id);


--
-- Name: factures fk_factures_user; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.factures
    ADD CONSTRAINT fk_factures_user FOREIGN KEY (user_id) REFERENCES core.dbusers(user_id);


--
-- Name: lignes_factures fk_lignes_factures_facture; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.lignes_factures
    ADD CONSTRAINT fk_lignes_factures_facture FOREIGN KEY (facture_id) REFERENCES finance.factures(facture_id) ON DELETE CASCADE;


--
-- Name: lignes_factures fk_lignes_factures_produit; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.lignes_factures
    ADD CONSTRAINT fk_lignes_factures_produit FOREIGN KEY (produit_id) REFERENCES inventory.produits(produit_id);


--
-- Name: numerotation_sequence fk_numerotation_hotel; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.numerotation_sequence
    ADD CONSTRAINT fk_numerotation_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: operations_comptes fk_operations_compte; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.operations_comptes
    ADD CONSTRAINT fk_operations_compte FOREIGN KEY (compte_id) REFERENCES finance.comptes(compte_id);


--
-- Name: operations_comptes fk_operations_facture; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.operations_comptes
    ADD CONSTRAINT fk_operations_facture FOREIGN KEY (facture_id) REFERENCES finance.factures(facture_id);


--
-- Name: operations_comptes fk_operations_hotel; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.operations_comptes
    ADD CONSTRAINT fk_operations_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: operations_comptes fk_operations_paiement; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.operations_comptes
    ADD CONSTRAINT fk_operations_paiement FOREIGN KEY (paiement_id) REFERENCES finance.paiements(paiement_id);


--
-- Name: operations_comptes fk_operations_user; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.operations_comptes
    ADD CONSTRAINT fk_operations_user FOREIGN KEY (user_id) REFERENCES core.dbusers(user_id);


--
-- Name: paiements fk_paiements_compte; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.paiements
    ADD CONSTRAINT fk_paiements_compte FOREIGN KEY (compte_id) REFERENCES finance.comptes(compte_id);


--
-- Name: paiements fk_paiements_hotel; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.paiements
    ADD CONSTRAINT fk_paiements_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: paiements fk_paiements_user; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.paiements
    ADD CONSTRAINT fk_paiements_user FOREIGN KEY (user_id) REFERENCES core.dbusers(user_id);


--
-- Name: chambres fk_chambres_hotel; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.chambres
    ADD CONSTRAINT fk_chambres_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: chambres fk_chambres_type; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.chambres
    ADD CONSTRAINT fk_chambres_type FOREIGN KEY (type_id) REFERENCES hebergement.types_chambres(type_id);


--
-- Name: nuitees fk_nuitees_chambre; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.nuitees
    ADD CONSTRAINT fk_nuitees_chambre FOREIGN KEY (chambre_id) REFERENCES hebergement.chambres(chambre_id);


--
-- Name: nuitees fk_nuitees_facture; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.nuitees
    ADD CONSTRAINT fk_nuitees_facture FOREIGN KEY (facture_id) REFERENCES finance.factures(facture_id) ON DELETE SET NULL;


--
-- Name: nuitees fk_nuitees_hotel; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.nuitees
    ADD CONSTRAINT fk_nuitees_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: nuitees fk_nuitees_ligne_facture; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.nuitees
    ADD CONSTRAINT fk_nuitees_ligne_facture FOREIGN KEY (ligne_facture_id) REFERENCES finance.lignes_factures(ligne_facture_id) ON DELETE SET NULL;


--
-- Name: nuitees fk_nuitees_reservation; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.nuitees
    ADD CONSTRAINT fk_nuitees_reservation FOREIGN KEY (reservation_id) REFERENCES hebergement.reservations(reservation_id) ON DELETE CASCADE;


--
-- Name: reservations_chambres fk_res_chambres_chambre; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations_chambres
    ADD CONSTRAINT fk_res_chambres_chambre FOREIGN KEY (chambre_id) REFERENCES hebergement.chambres(chambre_id);


--
-- Name: reservations_chambres fk_res_chambres_hotel; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations_chambres
    ADD CONSTRAINT fk_res_chambres_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: reservations_chambres fk_res_chambres_reservation; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations_chambres
    ADD CONSTRAINT fk_res_chambres_reservation FOREIGN KEY (reservation_id) REFERENCES hebergement.reservations(reservation_id) ON DELETE CASCADE;


--
-- Name: reservations_clients fk_res_clients_chambre; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations_clients
    ADD CONSTRAINT fk_res_clients_chambre FOREIGN KEY (chambre_id) REFERENCES hebergement.chambres(chambre_id);


--
-- Name: reservations_clients fk_res_clients_client; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations_clients
    ADD CONSTRAINT fk_res_clients_client FOREIGN KEY (client_id) REFERENCES client.clients(client_id);


--
-- Name: reservations_clients fk_res_clients_hotel; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations_clients
    ADD CONSTRAINT fk_res_clients_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: reservations_clients fk_res_clients_reservation; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations_clients
    ADD CONSTRAINT fk_res_clients_reservation FOREIGN KEY (reservation_id) REFERENCES hebergement.reservations(reservation_id) ON DELETE CASCADE;


--
-- Name: reservations fk_reservations_client_principal; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations
    ADD CONSTRAINT fk_reservations_client_principal FOREIGN KEY (client_principal_id) REFERENCES client.clients(client_id);


--
-- Name: reservations fk_reservations_facture; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations
    ADD CONSTRAINT fk_reservations_facture FOREIGN KEY (facture_id) REFERENCES finance.factures(facture_id) ON DELETE SET NULL;


--
-- Name: reservations fk_reservations_hotel; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations
    ADD CONSTRAINT fk_reservations_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: reservations fk_reservations_societe; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations
    ADD CONSTRAINT fk_reservations_societe FOREIGN KEY (societe_id) REFERENCES client.societes(societe_id) ON DELETE SET NULL;


--
-- Name: reservations fk_reservations_user; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations
    ADD CONSTRAINT fk_reservations_user FOREIGN KEY (user_id) REFERENCES core.dbusers(user_id);


--
-- Name: tarifs_chambres fk_tarifs_chambres_hotel; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.tarifs_chambres
    ADD CONSTRAINT fk_tarifs_chambres_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: tarifs_chambres fk_tarifs_chambres_type; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.tarifs_chambres
    ADD CONSTRAINT fk_tarifs_chambres_type FOREIGN KEY (type_id) REFERENCES hebergement.types_chambres(type_id);


--
-- Name: types_chambres fk_types_chambres_hotel; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.types_chambres
    ADD CONSTRAINT fk_types_chambres_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: bons_commande fk_bons_commande_facture; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.bons_commande
    ADD CONSTRAINT fk_bons_commande_facture FOREIGN KEY (facture_fournisseur_id) REFERENCES finance.factures(facture_id) ON DELETE SET NULL;


--
-- Name: bons_commande fk_bons_commande_fournisseur; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.bons_commande
    ADD CONSTRAINT fk_bons_commande_fournisseur FOREIGN KEY (fournisseur_id) REFERENCES inventory.fournisseurs(fournisseur_id);


--
-- Name: bons_commande fk_bons_commande_hotel; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.bons_commande
    ADD CONSTRAINT fk_bons_commande_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: bons_commande fk_bons_commande_user; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.bons_commande
    ADD CONSTRAINT fk_bons_commande_user FOREIGN KEY (user_id) REFERENCES core.dbusers(user_id);


--
-- Name: bons_sortie fk_bons_sortie_hotel; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.bons_sortie
    ADD CONSTRAINT fk_bons_sortie_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: bons_sortie fk_bons_sortie_user; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.bons_sortie
    ADD CONSTRAINT fk_bons_sortie_user FOREIGN KEY (user_id) REFERENCES core.dbusers(user_id);


--
-- Name: categories_produits fk_categories_produits_hotel; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.categories_produits
    ADD CONSTRAINT fk_categories_produits_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: fournisseurs fk_fournisseurs_hotel; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.fournisseurs
    ADD CONSTRAINT fk_fournisseurs_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: lignes_bons_commande fk_lignes_bc_bon; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.lignes_bons_commande
    ADD CONSTRAINT fk_lignes_bc_bon FOREIGN KEY (bon_commande_id) REFERENCES inventory.bons_commande(bon_commande_id) ON DELETE CASCADE;


--
-- Name: lignes_bons_commande fk_lignes_bc_produit; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.lignes_bons_commande
    ADD CONSTRAINT fk_lignes_bc_produit FOREIGN KEY (produit_id) REFERENCES inventory.produits(produit_id);


--
-- Name: lignes_bons_sortie fk_lignes_bs_bon; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.lignes_bons_sortie
    ADD CONSTRAINT fk_lignes_bs_bon FOREIGN KEY (bon_sortie_id) REFERENCES inventory.bons_sortie(bon_sortie_id) ON DELETE CASCADE;


--
-- Name: lignes_bons_sortie fk_lignes_bs_produit; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.lignes_bons_sortie
    ADD CONSTRAINT fk_lignes_bs_produit FOREIGN KEY (produit_id) REFERENCES inventory.produits(produit_id);


--
-- Name: mouvements_stock fk_mouvements_stock_hotel; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.mouvements_stock
    ADD CONSTRAINT fk_mouvements_stock_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: mouvements_stock fk_mouvements_stock_produit; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.mouvements_stock
    ADD CONSTRAINT fk_mouvements_stock_produit FOREIGN KEY (produit_id) REFERENCES inventory.produits(produit_id);


--
-- Name: mouvements_stock fk_mouvements_stock_user; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.mouvements_stock
    ADD CONSTRAINT fk_mouvements_stock_user FOREIGN KEY (user_id) REFERENCES core.dbusers(user_id);


--
-- Name: produits fk_produits_categorie; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.produits
    ADD CONSTRAINT fk_produits_categorie FOREIGN KEY (categorie_id) REFERENCES inventory.categories_produits(categorie_id);


--
-- Name: produits fk_produits_fournisseur_principal; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.produits
    ADD CONSTRAINT fk_produits_fournisseur_principal FOREIGN KEY (fournisseur_principal_id) REFERENCES inventory.fournisseurs(fournisseur_id);


--
-- Name: produits fk_produits_hotel; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.produits
    ADD CONSTRAINT fk_produits_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- PostgreSQL database dump complete
--

