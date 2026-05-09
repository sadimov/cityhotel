--
-- PostgreSQL database dump
--

\restrict G6Fx5AOfMkguWPYgC328WbKs0Bv9Dq3zoufaGNEW4YR5TWq2XwFgBFXYDYRXHKf

-- Dumped from database version 15.17
-- Dumped by pg_dump version 15.17

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
-- Name: clients; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA clients;


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
-- Name: calculer_totaux_facture(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.calculer_totaux_facture() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    UPDATE finance.factures 
    SET montant_ht = (
            SELECT COALESCE(SUM(montant_ht), 0) 
            FROM finance.lignes_factures 
            WHERE facture_id = COALESCE(NEW.facture_id, OLD.facture_id)
        ),
        montant_tva = (
            SELECT COALESCE(SUM(montant_tva), 0) 
            FROM finance.lignes_factures 
            WHERE facture_id = COALESCE(NEW.facture_id, OLD.facture_id)
        ),
        date_modification = CURRENT_TIMESTAMP
    WHERE facture_id = COALESCE(NEW.facture_id, OLD.facture_id);
    
    RETURN COALESCE(NEW, OLD);
END;
$$;


--
-- Name: generer_numero_document(bigint, character varying, character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.generer_numero_document(p_hotel_id bigint, p_type_document character varying, p_prefixe character varying DEFAULT ''::character varying) RETURNS character varying
    LANGUAGE plpgsql
    AS $_$
DECLARE
    v_numero INTEGER;
    v_annee VARCHAR(4);
    v_numero_final VARCHAR(20);
    v_pattern VARCHAR(50);
    v_hotel_code VARCHAR(10);
BEGIN
    -- Obtenir l'année courante
    v_annee := EXTRACT(YEAR FROM CURRENT_DATE)::VARCHAR;
    
    -- Récupérer le code de l'hôtel pour le préfixe
    SELECT hotel_code INTO v_hotel_code 
    FROM core.hotels 
    WHERE hotel_id = p_hotel_id;
    
    -- Construire le préfixe final
    IF p_prefixe = '' THEN
        p_prefixe := v_hotel_code;
    END IF;
    
    -- Obtenir le prochain numéro de séquence selon le type de document
    CASE p_type_document
        WHEN 'facture' THEN
            SELECT COALESCE(MAX(numero_sequence), 0) + 1 INTO v_numero
            FROM finance.factures 
            WHERE hotel_id = p_hotel_id 
            AND EXTRACT(YEAR FROM date_facture) = EXTRACT(YEAR FROM CURRENT_DATE);
            
            v_numero_final := p_prefixe || '-FAC-' || v_annee || '-' || LPAD(v_numero::VARCHAR, 6, '0');
            
        WHEN 'reservation' THEN
            -- Extraire le dernier numéro des réservations de l'année courante
            SELECT COALESCE(MAX(
                CAST(
                    SUBSTRING(
                        numero_reservation 
                        FROM '[0-9]+$'  -- Récupère les chiffres à la fin
                    ) AS INTEGER
                )
            ), 0) + 1 INTO v_numero
            FROM hebergement.reservations 
            WHERE hotel_id = p_hotel_id 
            AND EXTRACT(YEAR FROM date_creation) = EXTRACT(YEAR FROM CURRENT_DATE);
            
            v_numero_final := p_prefixe || '-RES-' || v_annee || '-' || LPAD(v_numero::VARCHAR, 6, '0');
            
        WHEN 'paiement' THEN
            SELECT COALESCE(MAX(
                CAST(
                    SUBSTRING(
                        numero_paiement 
                        FROM '[0-9]+$'
                    ) AS INTEGER
                )
            ), 0) + 1 INTO v_numero
            FROM finance.paiements 
            WHERE hotel_id = p_hotel_id 
            AND EXTRACT(YEAR FROM date_paiement) = EXTRACT(YEAR FROM CURRENT_DATE);
            
            v_numero_final := p_prefixe || '-PAY-' || v_annee || '-' || LPAD(v_numero::VARCHAR, 6, '0');
            
        WHEN 'bon_commande' THEN
            SELECT COALESCE(MAX(
                CAST(
                    SUBSTRING(
                        numero_bon 
                        FROM '[0-9]+$'
                    ) AS INTEGER
                )
            ), 0) + 1 INTO v_numero
            FROM inventory.bons_commande 
            WHERE hotel_id = p_hotel_id 
            AND EXTRACT(YEAR FROM date_commande) = EXTRACT(YEAR FROM CURRENT_DATE);
            
            v_numero_final := p_prefixe || '-BC-' || v_annee || '-' || LPAD(v_numero::VARCHAR, 6, '0');
            
        WHEN 'bon_sortie' THEN
            SELECT COALESCE(MAX(
                CAST(
                    SUBSTRING(
                        numero_bon 
                        FROM '[0-9]+$'
                    ) AS INTEGER
                )
            ), 0) + 1 INTO v_numero
            FROM inventory.bons_sortie 
            WHERE hotel_id = p_hotel_id 
            AND EXTRACT(YEAR FROM date_sortie) = EXTRACT(YEAR FROM CURRENT_DATE);
            
            v_numero_final := p_prefixe || '-BS-' || v_annee || '-' || LPAD(v_numero::VARCHAR, 6, '0');
            
        WHEN 'commande_restaurant' THEN
            SELECT COALESCE(MAX(
                CAST(
                    SUBSTRING(
                        numero_commande 
                        FROM '[0-9]+$'
                    ) AS INTEGER
                )
            ), 0) + 1 INTO v_numero
            FROM restaurant.commandes 
            WHERE hotel_id = p_hotel_id 
            AND DATE(date_commande) = CURRENT_DATE; -- Numérotation journalière pour le restaurant
            
            v_numero_final := p_prefixe || '-CMD-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD(v_numero::VARCHAR, 4, '0');
            
        WHEN 'client' THEN
            SELECT COALESCE(MAX(
                CAST(
                    SUBSTRING(
                        numero_client 
                        FROM '[0-9]+$'
                    ) AS INTEGER
                )
            ), 0) + 1 INTO v_numero
            FROM clients.clients 
            WHERE hotel_id = p_hotel_id;
            
            v_numero_final := p_prefixe || '-CLI-' || LPAD(v_numero::VARCHAR, 8, '0');
            
        WHEN 'compte' THEN
            -- Génération du numéro de compte selon les normes comptables
            -- Format: HHOOO-TTTTT-NNNNNNN (HH=hôtel, OOO=classe, TTT=type, NNN=numéro)
            SELECT COALESCE(MAX(
                CAST(
                    RIGHT(numero_compte, 7) AS INTEGER
                )
            ), 0) + 1 INTO v_numero
            FROM finance.comptes 
            WHERE hotel_id = p_hotel_id;
            
            -- Classe 411: Clients, 401: Fournisseurs, 421: Personnel
            v_numero_final := LPAD(p_hotel_id::VARCHAR, 2, '0') || '411-00001-' || LPAD(v_numero::VARCHAR, 7, '0');
            
        WHEN 'reservation_salle' THEN
            SELECT COALESCE(MAX(
                CAST(
                    SUBSTRING(
                        numero_reservation 
                        FROM '[0-9]+$'
                    ) AS INTEGER
                )
            ), 0) + 1 INTO v_numero
            FROM hebergement.reservations_salles 
            WHERE hotel_id = p_hotel_id 
            AND EXTRACT(YEAR FROM date_creation) = EXTRACT(YEAR FROM CURRENT_DATE);
            
            v_numero_final := p_prefixe || '-SALE-' || v_annee || '-' || LPAD(v_numero::VARCHAR, 6, '0');
            
        ELSE
            RAISE EXCEPTION 'Type de document non supporté: %', p_type_document;
    END CASE;
    
    RETURN v_numero_final;
    
EXCEPTION
    WHEN OTHERS THEN
        RAISE EXCEPTION 'Erreur lors de la génération du numéro de document: %', SQLERRM;
END;
$_$;


--
-- Name: gerer_mouvement_stock(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.gerer_mouvement_stock() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_produit_info RECORD;
    v_alerte_existe BOOLEAN;
BEGIN
    -- Récupération des informations du produit
    SELECT 
        nom_produit, 
        unite_mesure, 
        seuil_alerte, 
        seuil_critique, 
        hotel_id,
        code_produit
    INTO v_produit_info
    FROM inventory.produits 
    WHERE produit_id = NEW.produit_id;
    
    -- Vérification que le produit existe
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Produit avec ID % non trouvé', NEW.produit_id;
    END IF;
    
    -- Mise à jour du stock actuel du produit
    UPDATE inventory.produits 
    SET stock_actuel = NEW.stock_apres,
        date_modification = CURRENT_TIMESTAMP
    WHERE produit_id = NEW.produit_id;
    
    -- Vérification si une alerte non traitée existe déjà pour ce produit aujourd'hui
    SELECT EXISTS(
        SELECT 1 
        FROM reporting.alertes a 
        WHERE a.hotel_id = NEW.hotel_id 
        AND a.type_alerte IN ('stock_critique', 'stock_faible')
        AND a.donnees_contexte->>'produit_id' = NEW.produit_id::text
        AND a.traitee = false
        AND a.date_creation >= CURRENT_DATE
    ) INTO v_alerte_existe;
    
    -- Génération d'alertes si nécessaire et pas d'alerte existante
    IF NOT v_alerte_existe AND v_produit_info.seuil_alerte > 0 THEN
        
        -- Alerte stock critique
        IF NEW.stock_apres <= v_produit_info.seuil_critique THEN
            INSERT INTO reporting.alertes (
                hotel_id, 
                type_alerte, 
                niveau, 
                titre, 
                message, 
                donnees_contexte,
                user_id
            ) VALUES (
                NEW.hotel_id,
                'stock_critique',
                'critical',
                'STOCK CRITIQUE - Action immédiate requise',
                'Le produit "' || v_produit_info.nom_produit || '" (' || v_produit_info.code_produit || ') a un stock critique de ' || NEW.stock_apres || ' ' || v_produit_info.unite_mesure || '. Réapprovisionnement urgent nécessaire !',
                json_build_object(
                    'produit_id', NEW.produit_id,
                    'code_produit', v_produit_info.code_produit,
                    'nom_produit', v_produit_info.nom_produit,
                    'stock_actuel', NEW.stock_apres,
                    'stock_precedent', NEW.stock_avant,
                    'seuil_alerte', v_produit_info.seuil_alerte,
                    'seuil_critique', v_produit_info.seuil_critique,
                    'type_mouvement', NEW.type_mouvement,
                    'reference_document', NEW.reference_document,
                    'date_mouvement', NEW.date_mouvement,
                    'unite_mesure', v_produit_info.unite_mesure
                ),
                NEW.user_id
            );
            
        -- Alerte stock faible
        ELSIF NEW.stock_apres <= v_produit_info.seuil_alerte THEN
            INSERT INTO reporting.alertes (
                hotel_id, 
                type_alerte, 
                niveau, 
                titre, 
                message, 
                donnees_contexte,
                user_id
            ) VALUES (
                NEW.hotel_id,
                'stock_faible',
                'warning',
                'Stock faible détecté',
                'Le produit "' || v_produit_info.nom_produit || '" (' || v_produit_info.code_produit || ') a un stock de ' || NEW.stock_apres || ' ' || v_produit_info.unite_mesure || '. Seuil d''alerte atteint, prévoir un réapprovisionnement.',
                json_build_object(
                    'produit_id', NEW.produit_id,
                    'code_produit', v_produit_info.code_produit,
                    'nom_produit', v_produit_info.nom_produit,
                    'stock_actuel', NEW.stock_apres,
                    'stock_precedent', NEW.stock_avant,
                    'seuil_alerte', v_produit_info.seuil_alerte,
                    'seuil_critique', v_produit_info.seuil_critique,
                    'type_mouvement', NEW.type_mouvement,
                    'reference_document', NEW.reference_document,
                    'date_mouvement', NEW.date_mouvement,
                    'unite_mesure', v_produit_info.unite_mesure
                ),
                NEW.user_id
            );
        END IF;
    END IF;
    
    -- Rafraîchissement de la vue matérialisée des stocks critiques si nécessaire
    -- Seulement si le stock passe sous le seuil d'alerte ou en ressort
    IF (NEW.stock_apres <= v_produit_info.seuil_alerte AND NEW.stock_avant > v_produit_info.seuil_alerte) OR
       (NEW.stock_apres > v_produit_info.seuil_alerte AND NEW.stock_avant <= v_produit_info.seuil_alerte) THEN
        
        -- Rafraîchissement asynchrone pour éviter les blocages
        PERFORM pg_notify('refresh_stocks_critiques', NEW.hotel_id::text);
    END IF;
    
    RETURN NEW;
    
EXCEPTION
    WHEN OTHERS THEN
        -- Log l'erreur mais ne bloque pas la transaction
        RAISE WARNING 'Erreur dans gerer_mouvement_stock pour produit_id % (hotel_id %): %', 
                      NEW.produit_id, NEW.hotel_id, SQLERRM;
        
        -- Insérer une alerte d'erreur système
        INSERT INTO reporting.alertes (
            hotel_id, 
            type_alerte, 
            niveau, 
            titre, 
            message, 
            donnees_contexte
        ) VALUES (
            NEW.hotel_id,
            'erreur_systeme',
            'error',
            'Erreur lors de la gestion du mouvement de stock',
            'Une erreur s''est produite lors du traitement du mouvement de stock pour le produit ID ' || NEW.produit_id || ': ' || SQLERRM,
            json_build_object(
                'produit_id', NEW.produit_id,
                'erreur', SQLERRM,
                'sqlstate', SQLSTATE,
                'fonction', 'gerer_mouvement_stock',
                'timestamp', CURRENT_TIMESTAMP
            )
        );
        
        RETURN NEW;
END;
$$;


--
-- Name: gerer_operation_compte(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.gerer_operation_compte() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    -- Mise à jour du solde du compte
    UPDATE finance.comptes 
    SET solde_actuel = NEW.solde_apres,
        date_modification = CURRENT_TIMESTAMP
    WHERE compte_id = NEW.compte_id;
    
    RETURN NEW;
END;
$$;


--
-- Name: reinitialiser_compteurs_annee(bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.reinitialiser_compteurs_annee(p_hotel_id bigint DEFAULT NULL::bigint) RETURNS void
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_hotel_record RECORD;
BEGIN
    -- Si hotel_id spécifié, traiter uniquement cet hôtel, sinon tous
    FOR v_hotel_record IN 
        SELECT hotel_id, hotel_code 
        FROM core.hotels 
        WHERE (p_hotel_id IS NULL OR hotel_id = p_hotel_id)
        AND actif = true
    LOOP
        -- Log de réinitialisation
        INSERT INTO reporting.alertes (
            hotel_id, 
            type_alerte, 
            niveau, 
            titre, 
            message
        ) VALUES (
            v_hotel_record.hotel_id,
            'system',
            'info',
            'Réinitialisation des compteurs annuels',
            'Compteurs de numérotation réinitialisés pour l''année ' || EXTRACT(YEAR FROM CURRENT_DATE)
        );
    END LOOP;
    
    RAISE NOTICE 'Compteurs annuels réinitialisés pour % hôtel(s)', 
        CASE WHEN p_hotel_id IS NULL THEN 'tous les' ELSE '1' END;
END;
$$;


--
-- Name: update_timestamp(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_timestamp() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.date_modification = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;


--
-- Name: verifier_unicite_numero(bigint, character varying, character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.verifier_unicite_numero(p_hotel_id bigint, p_type_document character varying, p_numero character varying) RETURNS boolean
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_count INTEGER := 0;
BEGIN
    CASE p_type_document
        WHEN 'facture' THEN
            SELECT COUNT(*) INTO v_count
            FROM finance.factures 
            WHERE hotel_id = p_hotel_id AND numero_facture = p_numero;
            
        WHEN 'reservation' THEN
            SELECT COUNT(*) INTO v_count
            FROM hebergement.reservations 
            WHERE hotel_id = p_hotel_id AND numero_reservation = p_numero;
            
        WHEN 'paiement' THEN
            SELECT COUNT(*) INTO v_count
            FROM finance.paiements 
            WHERE hotel_id = p_hotel_id AND numero_paiement = p_numero;
            
        WHEN 'bon_commande' THEN
            SELECT COUNT(*) INTO v_count
            FROM inventory.bons_commande 
            WHERE hotel_id = p_hotel_id AND numero_bon = p_numero;
            
        WHEN 'bon_sortie' THEN
            SELECT COUNT(*) INTO v_count
            FROM inventory.bons_sortie 
            WHERE hotel_id = p_hotel_id AND numero_bon = p_numero;
            
        WHEN 'commande_restaurant' THEN
            SELECT COUNT(*) INTO v_count
            FROM restaurant.commandes 
            WHERE hotel_id = p_hotel_id AND numero_commande = p_numero;
            
        WHEN 'client' THEN
            SELECT COUNT(*) INTO v_count
            FROM clients.clients 
            WHERE hotel_id = p_hotel_id AND numero_client = p_numero;
            
        WHEN 'compte' THEN
            SELECT COUNT(*) INTO v_count
            FROM finance.comptes 
            WHERE hotel_id = p_hotel_id AND numero_compte = p_numero;
            
        ELSE
            RAISE EXCEPTION 'Type de document non supporté: %', p_type_document;
    END CASE;
    
    RETURN v_count = 0;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: clients; Type: TABLE; Schema: clients; Owner: -
--

CREATE TABLE clients.clients (
    client_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    numero_client character varying(20) NOT NULL,
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
    actif boolean DEFAULT true,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    date_modification timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: clients_client_id_seq; Type: SEQUENCE; Schema: clients; Owner: -
--

CREATE SEQUENCE clients.clients_client_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: clients_client_id_seq; Type: SEQUENCE OWNED BY; Schema: clients; Owner: -
--

ALTER SEQUENCE clients.clients_client_id_seq OWNED BY clients.clients.client_id;


--
-- Name: societes; Type: TABLE; Schema: clients; Owner: -
--

CREATE TABLE clients.societes (
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
    actif boolean DEFAULT true,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    date_modification timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: societes_societe_id_seq; Type: SEQUENCE; Schema: clients; Owner: -
--

CREATE SEQUENCE clients.societes_societe_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: societes_societe_id_seq; Type: SEQUENCE OWNED BY; Schema: clients; Owner: -
--

ALTER SEQUENCE clients.societes_societe_id_seq OWNED BY clients.societes.societe_id;


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
    date_modification timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


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
    date_modification timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


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
    ip_address character varying(200),
    user_agent text,
    derniere_activite timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    actif boolean DEFAULT true
);


--
-- Name: affectations_paiements; Type: TABLE; Schema: finance; Owner: -
--

CREATE TABLE finance.affectations_paiements (
    affectation_id bigint NOT NULL,
    paiement_id bigint NOT NULL,
    facture_id bigint NOT NULL,
    montant_affecte numeric(15,2) NOT NULL,
    date_affectation timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_affectations_montant CHECK ((montant_affecte > (0)::numeric))
);


--
-- Name: affectations_paiements_affectation_id_seq; Type: SEQUENCE; Schema: finance; Owner: -
--

CREATE SEQUENCE finance.affectations_paiements_affectation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: affectations_paiements_affectation_id_seq; Type: SEQUENCE OWNED BY; Schema: finance; Owner: -
--

ALTER SEQUENCE finance.affectations_paiements_affectation_id_seq OWNED BY finance.affectations_paiements.affectation_id;


--
-- Name: comptes; Type: TABLE; Schema: finance; Owner: -
--

CREATE TABLE finance.comptes (
    compte_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    numero_compte character varying(20) NOT NULL,
    type_compte character varying(20) NOT NULL,
    client_id bigint,
    societe_id bigint,
    fournisseur_id bigint,
    solde_actuel numeric(15,2) DEFAULT 0,
    credit_limite numeric(15,2) DEFAULT 0,
    statut character varying(20) DEFAULT 'actif'::character varying,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    date_modification timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_comptes_type CHECK (((((type_compte)::text = 'client'::text) AND (client_id IS NOT NULL) AND (societe_id IS NULL) AND (fournisseur_id IS NULL)) OR (((type_compte)::text = 'societe'::text) AND (societe_id IS NOT NULL) AND (client_id IS NULL) AND (fournisseur_id IS NULL)) OR (((type_compte)::text = 'fournisseur'::text) AND (fournisseur_id IS NOT NULL) AND (client_id IS NULL) AND (societe_id IS NULL))))
);


--
-- Name: comptes_compte_id_seq; Type: SEQUENCE; Schema: finance; Owner: -
--

CREATE SEQUENCE finance.comptes_compte_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: comptes_compte_id_seq; Type: SEQUENCE OWNED BY; Schema: finance; Owner: -
--

ALTER SEQUENCE finance.comptes_compte_id_seq OWNED BY finance.comptes.compte_id;


--
-- Name: factures; Type: TABLE; Schema: finance; Owner: -
--

CREATE TABLE finance.factures (
    facture_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    numero_facture character varying(20) NOT NULL,
    numero_sequence integer NOT NULL,
    type_facture character varying(20) DEFAULT 'client'::character varying,
    compte_id bigint NOT NULL,
    client_id bigint,
    societe_id bigint,
    reservation_id bigint,
    date_facture date DEFAULT CURRENT_DATE,
    date_echeance date,
    montant_ht numeric(15,2) DEFAULT 0,
    montant_tva numeric(15,2) DEFAULT 0,
    montant_ttc numeric(15,2) GENERATED ALWAYS AS ((montant_ht + montant_tva)) STORED,
    montant_paye numeric(15,2) DEFAULT 0,
    montant_restant numeric(15,2) GENERATED ALWAYS AS (((montant_ht + montant_tva) - montant_paye)) STORED,
    statut_paiement character varying(20) DEFAULT 'impayee'::character varying,
    devise character varying(3) DEFAULT 'MRU'::character varying,
    taux_change numeric(10,6) DEFAULT 1,
    commentaires text,
    facture_annulee boolean DEFAULT false,
    facture_reference_id bigint,
    user_id bigint NOT NULL,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    date_modification timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: factures_facture_id_seq; Type: SEQUENCE; Schema: finance; Owner: -
--

CREATE SEQUENCE finance.factures_facture_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: factures_facture_id_seq; Type: SEQUENCE OWNED BY; Schema: finance; Owner: -
--

ALTER SEQUENCE finance.factures_facture_id_seq OWNED BY finance.factures.facture_id;


--
-- Name: lignes_factures; Type: TABLE; Schema: finance; Owner: -
--

CREATE TABLE finance.lignes_factures (
    ligne_id bigint NOT NULL,
    facture_id bigint NOT NULL,
    type_ligne character varying(20) NOT NULL,
    service_id bigint,
    produit_id bigint,
    nuitee_id bigint,
    commande_id bigint,
    description character varying(500) NOT NULL,
    quantite numeric(10,3) NOT NULL,
    prix_unitaire numeric(10,2) NOT NULL,
    montant_ht numeric(12,2) GENERATED ALWAYS AS ((quantite * prix_unitaire)) STORED,
    taux_tva numeric(5,2) DEFAULT 0,
    montant_tva numeric(12,2) GENERATED ALWAYS AS ((((quantite * prix_unitaire) * taux_tva) / (100)::numeric)) STORED,
    montant_ttc numeric(12,2) GENERATED ALWAYS AS (((quantite * prix_unitaire) + (((quantite * prix_unitaire) * taux_tva) / (100)::numeric))) STORED,
    reduction_pourcentage numeric(5,2) DEFAULT 0,
    montant_paye numeric(12,2) DEFAULT 0,
    statut_paiement character varying(20) DEFAULT 'impayee'::character varying,
    date_prestation date,
    CONSTRAINT chk_lignes_quantite CHECK ((quantite > (0)::numeric))
);


--
-- Name: lignes_factures_ligne_id_seq; Type: SEQUENCE; Schema: finance; Owner: -
--

CREATE SEQUENCE finance.lignes_factures_ligne_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lignes_factures_ligne_id_seq; Type: SEQUENCE OWNED BY; Schema: finance; Owner: -
--

ALTER SEQUENCE finance.lignes_factures_ligne_id_seq OWNED BY finance.lignes_factures.ligne_id;


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
    date_operation timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    date_valeur date DEFAULT CURRENT_DATE,
    user_id bigint NOT NULL,
    CONSTRAINT chk_operations_montant CHECK ((montant > (0)::numeric))
);


--
-- Name: operations_comptes_operation_id_seq; Type: SEQUENCE; Schema: finance; Owner: -
--

CREATE SEQUENCE finance.operations_comptes_operation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: operations_comptes_operation_id_seq; Type: SEQUENCE OWNED BY; Schema: finance; Owner: -
--

ALTER SEQUENCE finance.operations_comptes_operation_id_seq OWNED BY finance.operations_comptes.operation_id;


--
-- Name: paiements; Type: TABLE; Schema: finance; Owner: -
--

CREATE TABLE finance.paiements (
    paiement_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    numero_paiement character varying(20) NOT NULL,
    compte_id bigint NOT NULL,
    montant_total numeric(15,2) NOT NULL,
    devise character varying(3) DEFAULT 'MRU'::character varying,
    taux_change numeric(10,6) DEFAULT 1,
    mode_paiement character varying(20) NOT NULL,
    reference_paiement character varying(100),
    date_paiement date DEFAULT CURRENT_DATE,
    date_valeur date,
    statut character varying(20) DEFAULT 'valide'::character varying,
    commentaires text,
    user_id bigint NOT NULL,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_paiements_montant CHECK ((montant_total > (0)::numeric))
);


--
-- Name: paiements_paiement_id_seq; Type: SEQUENCE; Schema: finance; Owner: -
--

CREATE SEQUENCE finance.paiements_paiement_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: paiements_paiement_id_seq; Type: SEQUENCE OWNED BY; Schema: finance; Owner: -
--

ALTER SEQUENCE finance.paiements_paiement_id_seq OWNED BY finance.paiements.paiement_id;


--
-- Name: services_facturables; Type: TABLE; Schema: finance; Owner: -
--

CREATE TABLE finance.services_facturables (
    service_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    code_service character varying(20) NOT NULL,
    nom_service character varying(200) NOT NULL,
    nom_service_en character varying(200),
    nom_service_ar character varying(200),
    description text,
    prix_unitaire numeric(10,2) NOT NULL,
    unite character varying(20) DEFAULT 'unité'::character varying,
    taux_tva numeric(5,2) DEFAULT 0,
    categorie character varying(50),
    actif boolean DEFAULT true
);


--
-- Name: services_facturables_service_id_seq; Type: SEQUENCE; Schema: finance; Owner: -
--

CREATE SEQUENCE finance.services_facturables_service_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: services_facturables_service_id_seq; Type: SEQUENCE OWNED BY; Schema: finance; Owner: -
--

ALTER SEQUENCE finance.services_facturables_service_id_seq OWNED BY finance.services_facturables.service_id;


--
-- Name: chambres; Type: TABLE; Schema: hebergement; Owner: -
--

CREATE TABLE hebergement.chambres (
    chambre_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    numero_chambre character varying(10) NOT NULL,
    type_id bigint NOT NULL,
    etage integer,
    statut character varying(20) DEFAULT 'disponible'::character varying,
    nb_lits integer NOT NULL,
    nb_personnes_max integer NOT NULL,
    equipements json,
    description text,
    actif boolean DEFAULT true,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: chambres_chambre_id_seq; Type: SEQUENCE; Schema: hebergement; Owner: -
--

CREATE SEQUENCE hebergement.chambres_chambre_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: chambres_chambre_id_seq; Type: SEQUENCE OWNED BY; Schema: hebergement; Owner: -
--

ALTER SEQUENCE hebergement.chambres_chambre_id_seq OWNED BY hebergement.chambres.chambre_id;


--
-- Name: nuitees; Type: TABLE; Schema: hebergement; Owner: -
--

CREATE TABLE hebergement.nuitees (
    nuitee_id bigint NOT NULL,
    reservation_id bigint NOT NULL,
    chambre_id bigint NOT NULL,
    date_nuit date NOT NULL,
    prix_nuit numeric(10,2) NOT NULL,
    taxe_sejour numeric(10,2) DEFAULT 0,
    statut character varying(20) DEFAULT 'prevue'::character varying
);


--
-- Name: nuitees_nuitee_id_seq; Type: SEQUENCE; Schema: hebergement; Owner: -
--

CREATE SEQUENCE hebergement.nuitees_nuitee_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: nuitees_nuitee_id_seq; Type: SEQUENCE OWNED BY; Schema: hebergement; Owner: -
--

ALTER SEQUENCE hebergement.nuitees_nuitee_id_seq OWNED BY hebergement.nuitees.nuitee_id;


--
-- Name: reservations; Type: TABLE; Schema: hebergement; Owner: -
--

CREATE TABLE hebergement.reservations (
    reservation_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    numero_reservation character varying(20) NOT NULL,
    client_principal_id bigint NOT NULL,
    societe_id bigint,
    date_arrivee date NOT NULL,
    date_depart date NOT NULL,
    nb_nuits integer GENERATED ALWAYS AS ((date_depart - date_arrivee)) STORED,
    nb_adultes integer DEFAULT 1,
    nb_enfants integer DEFAULT 0,
    statut character varying(20) DEFAULT 'confirmee'::character varying,
    motif_sejour character varying(100),
    commentaires text,
    reduction_pourcentage numeric(5,2) DEFAULT 0,
    montant_total numeric(12,2) DEFAULT 0,
    user_id bigint NOT NULL,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    date_modification timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_reservations_dates CHECK ((date_depart > date_arrivee))
);


--
-- Name: reservations_chambres; Type: TABLE; Schema: hebergement; Owner: -
--

CREATE TABLE hebergement.reservations_chambres (
    reservation_chambre_id bigint NOT NULL,
    reservation_id bigint NOT NULL,
    chambre_id bigint NOT NULL,
    date_debut date NOT NULL,
    date_fin date NOT NULL,
    prix_nuit numeric(10,2) NOT NULL
);


--
-- Name: reservations_chambres_reservation_chambre_id_seq; Type: SEQUENCE; Schema: hebergement; Owner: -
--

CREATE SEQUENCE hebergement.reservations_chambres_reservation_chambre_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reservations_chambres_reservation_chambre_id_seq; Type: SEQUENCE OWNED BY; Schema: hebergement; Owner: -
--

ALTER SEQUENCE hebergement.reservations_chambres_reservation_chambre_id_seq OWNED BY hebergement.reservations_chambres.reservation_chambre_id;


--
-- Name: reservations_clients; Type: TABLE; Schema: hebergement; Owner: -
--

CREATE TABLE hebergement.reservations_clients (
    reservation_client_id bigint NOT NULL,
    reservation_id bigint NOT NULL,
    client_id bigint NOT NULL,
    chambre_id bigint,
    est_payant boolean DEFAULT true,
    pourcentage_charge numeric(5,2) DEFAULT 100.00
);


--
-- Name: reservations_clients_reservation_client_id_seq; Type: SEQUENCE; Schema: hebergement; Owner: -
--

CREATE SEQUENCE hebergement.reservations_clients_reservation_client_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reservations_clients_reservation_client_id_seq; Type: SEQUENCE OWNED BY; Schema: hebergement; Owner: -
--

ALTER SEQUENCE hebergement.reservations_clients_reservation_client_id_seq OWNED BY hebergement.reservations_clients.reservation_client_id;


--
-- Name: reservations_reservation_id_seq; Type: SEQUENCE; Schema: hebergement; Owner: -
--

CREATE SEQUENCE hebergement.reservations_reservation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reservations_reservation_id_seq; Type: SEQUENCE OWNED BY; Schema: hebergement; Owner: -
--

ALTER SEQUENCE hebergement.reservations_reservation_id_seq OWNED BY hebergement.reservations.reservation_id;


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
    actif boolean DEFAULT true
);


--
-- Name: tarifs_chambres_tarif_id_seq; Type: SEQUENCE; Schema: hebergement; Owner: -
--

CREATE SEQUENCE hebergement.tarifs_chambres_tarif_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tarifs_chambres_tarif_id_seq; Type: SEQUENCE OWNED BY; Schema: hebergement; Owner: -
--

ALTER SEQUENCE hebergement.tarifs_chambres_tarif_id_seq OWNED BY hebergement.tarifs_chambres.tarif_id;


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
    actif boolean DEFAULT true
);


--
-- Name: types_chambres_type_id_seq; Type: SEQUENCE; Schema: hebergement; Owner: -
--

CREATE SEQUENCE hebergement.types_chambres_type_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: types_chambres_type_id_seq; Type: SEQUENCE OWNED BY; Schema: hebergement; Owner: -
--

ALTER SEQUENCE hebergement.types_chambres_type_id_seq OWNED BY hebergement.types_chambres.type_id;


--
-- Name: bons_commande; Type: TABLE; Schema: inventory; Owner: -
--

CREATE TABLE inventory.bons_commande (
    bon_commande_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    numero_bon character varying(20) NOT NULL,
    fournisseur_id bigint NOT NULL,
    statut character varying(20) DEFAULT 'brouillon'::character varying,
    date_commande date DEFAULT CURRENT_DATE,
    date_livraison_prevue date,
    date_livraison_reelle date,
    montant_total numeric(12,2) DEFAULT 0,
    montant_tva numeric(12,2) DEFAULT 0,
    commentaires text,
    user_id bigint NOT NULL,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: bons_commande_bon_commande_id_seq; Type: SEQUENCE; Schema: inventory; Owner: -
--

CREATE SEQUENCE inventory.bons_commande_bon_commande_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bons_commande_bon_commande_id_seq; Type: SEQUENCE OWNED BY; Schema: inventory; Owner: -
--

ALTER SEQUENCE inventory.bons_commande_bon_commande_id_seq OWNED BY inventory.bons_commande.bon_commande_id;


--
-- Name: bons_sortie; Type: TABLE; Schema: inventory; Owner: -
--

CREATE TABLE inventory.bons_sortie (
    bon_sortie_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    numero_bon character varying(20) NOT NULL,
    destination character varying(100) NOT NULL,
    statut character varying(20) DEFAULT 'brouillon'::character varying,
    date_sortie date DEFAULT CURRENT_DATE,
    commentaires text,
    user_id bigint NOT NULL,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: bons_sortie_bon_sortie_id_seq; Type: SEQUENCE; Schema: inventory; Owner: -
--

CREATE SEQUENCE inventory.bons_sortie_bon_sortie_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bons_sortie_bon_sortie_id_seq; Type: SEQUENCE OWNED BY; Schema: inventory; Owner: -
--

ALTER SEQUENCE inventory.bons_sortie_bon_sortie_id_seq OWNED BY inventory.bons_sortie.bon_sortie_id;


--
-- Name: categories_produits; Type: TABLE; Schema: inventory; Owner: -
--

CREATE TABLE inventory.categories_produits (
    categorie_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    code_categorie character varying(10) NOT NULL,
    nom_categorie character varying(100) NOT NULL,
    description text,
    actif boolean DEFAULT true,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: categories_produits_categorie_id_seq; Type: SEQUENCE; Schema: inventory; Owner: -
--

CREATE SEQUENCE inventory.categories_produits_categorie_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: categories_produits_categorie_id_seq; Type: SEQUENCE OWNED BY; Schema: inventory; Owner: -
--

ALTER SEQUENCE inventory.categories_produits_categorie_id_seq OWNED BY inventory.categories_produits.categorie_id;


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
    actif boolean DEFAULT true,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: fournisseurs_fournisseur_id_seq; Type: SEQUENCE; Schema: inventory; Owner: -
--

CREATE SEQUENCE inventory.fournisseurs_fournisseur_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: fournisseurs_fournisseur_id_seq; Type: SEQUENCE OWNED BY; Schema: inventory; Owner: -
--

ALTER SEQUENCE inventory.fournisseurs_fournisseur_id_seq OWNED BY inventory.fournisseurs.fournisseur_id;


--
-- Name: lignes_bons_commande; Type: TABLE; Schema: inventory; Owner: -
--

CREATE TABLE inventory.lignes_bons_commande (
    ligne_id bigint NOT NULL,
    bon_commande_id bigint NOT NULL,
    produit_id bigint NOT NULL,
    quantite_commandee integer NOT NULL,
    quantite_recue integer DEFAULT 0,
    prix_unitaire numeric(10,2) NOT NULL,
    sous_total numeric(12,2) GENERATED ALWAYS AS (((quantite_commandee)::numeric * prix_unitaire)) STORED,
    date_reception date,
    CONSTRAINT chk_lignes_quantite CHECK ((quantite_commandee > 0))
);


--
-- Name: lignes_bons_commande_ligne_id_seq; Type: SEQUENCE; Schema: inventory; Owner: -
--

CREATE SEQUENCE inventory.lignes_bons_commande_ligne_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lignes_bons_commande_ligne_id_seq; Type: SEQUENCE OWNED BY; Schema: inventory; Owner: -
--

ALTER SEQUENCE inventory.lignes_bons_commande_ligne_id_seq OWNED BY inventory.lignes_bons_commande.ligne_id;


--
-- Name: lignes_bons_sortie; Type: TABLE; Schema: inventory; Owner: -
--

CREATE TABLE inventory.lignes_bons_sortie (
    ligne_id bigint NOT NULL,
    bon_sortie_id bigint NOT NULL,
    produit_id bigint NOT NULL,
    quantite_demandee integer NOT NULL,
    quantite_servie integer DEFAULT 0,
    commentaires text,
    CONSTRAINT chk_lignes_sortie_quantite CHECK ((quantite_demandee > 0))
);


--
-- Name: lignes_bons_sortie_ligne_id_seq; Type: SEQUENCE; Schema: inventory; Owner: -
--

CREATE SEQUENCE inventory.lignes_bons_sortie_ligne_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lignes_bons_sortie_ligne_id_seq; Type: SEQUENCE OWNED BY; Schema: inventory; Owner: -
--

ALTER SEQUENCE inventory.lignes_bons_sortie_ligne_id_seq OWNED BY inventory.lignes_bons_sortie.ligne_id;


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
    valeur_mouvement numeric(12,2) GENERATED ALWAYS AS (((quantite)::numeric * prix_unitaire)) STORED,
    stock_avant integer NOT NULL,
    stock_apres integer NOT NULL,
    reference_document character varying(50),
    commentaire text,
    user_id bigint NOT NULL,
    date_mouvement timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: mouvements_stock_mouvement_id_seq; Type: SEQUENCE; Schema: inventory; Owner: -
--

CREATE SEQUENCE inventory.mouvements_stock_mouvement_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mouvements_stock_mouvement_id_seq; Type: SEQUENCE OWNED BY; Schema: inventory; Owner: -
--

ALTER SEQUENCE inventory.mouvements_stock_mouvement_id_seq OWNED BY inventory.mouvements_stock.mouvement_id;


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
    seuil_alerte integer DEFAULT 0,
    seuil_critique integer DEFAULT 0,
    stock_actuel integer DEFAULT 0,
    valeur_stock numeric(12,2) GENERATED ALWAYS AS (((stock_actuel)::numeric * prix_unitaire)) STORED,
    fournisseur_principal_id bigint,
    est_facturable boolean DEFAULT false,
    actif boolean DEFAULT true,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    date_modification timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: produits_produit_id_seq; Type: SEQUENCE; Schema: inventory; Owner: -
--

CREATE SEQUENCE inventory.produits_produit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: produits_produit_id_seq; Type: SEQUENCE OWNED BY; Schema: inventory; Owner: -
--

ALTER SEQUENCE inventory.produits_produit_id_seq OWNED BY inventory.produits.produit_id;


--
-- Name: historique; Type: TABLE; Schema: menage; Owner: -
--

CREATE TABLE menage.historique (
    historique_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    tache_id bigint,
    chambre_id bigint NOT NULL,
    personnel_id bigint,
    action character varying(50) NOT NULL,
    ancien_statut character varying(50),
    nouveau_statut character varying(50),
    commentaire text,
    user_id bigint,
    timestamp_action timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: historique_historique_id_seq; Type: SEQUENCE; Schema: menage; Owner: -
--

CREATE SEQUENCE menage.historique_historique_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: historique_historique_id_seq; Type: SEQUENCE OWNED BY; Schema: menage; Owner: -
--

ALTER SEQUENCE menage.historique_historique_id_seq OWNED BY menage.historique.historique_id;


--
-- Name: personnel; Type: TABLE; Schema: menage; Owner: -
--

CREATE TABLE menage.personnel (
    personnel_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    numero_employe character varying(20) NOT NULL,
    prenom character varying(100) NOT NULL,
    nom character varying(100) NOT NULL,
    telephone character varying(20),
    email character varying(100),
    date_embauche date,
    specialites json,
    actif boolean DEFAULT true,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: personnel_personnel_id_seq; Type: SEQUENCE; Schema: menage; Owner: -
--

CREATE SEQUENCE menage.personnel_personnel_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: personnel_personnel_id_seq; Type: SEQUENCE OWNED BY; Schema: menage; Owner: -
--

ALTER SEQUENCE menage.personnel_personnel_id_seq OWNED BY menage.personnel.personnel_id;


--
-- Name: planning; Type: TABLE; Schema: menage; Owner: -
--

CREATE TABLE menage.planning (
    planning_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    personnel_id bigint NOT NULL,
    date_travail date NOT NULL,
    heure_debut time without time zone NOT NULL,
    heure_fin time without time zone NOT NULL,
    disponible boolean DEFAULT true,
    commentaires text,
    CONSTRAINT chk_planning_heures CHECK ((heure_fin > heure_debut))
);


--
-- Name: planning_planning_id_seq; Type: SEQUENCE; Schema: menage; Owner: -
--

CREATE SEQUENCE menage.planning_planning_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: planning_planning_id_seq; Type: SEQUENCE OWNED BY; Schema: menage; Owner: -
--

ALTER SEQUENCE menage.planning_planning_id_seq OWNED BY menage.planning.planning_id;


--
-- Name: statuts_taches; Type: TABLE; Schema: menage; Owner: -
--

CREATE TABLE menage.statuts_taches (
    statut_id integer NOT NULL,
    code_statut character varying(20) NOT NULL,
    libelle character varying(100) NOT NULL,
    libelle_en character varying(100),
    libelle_ar character varying(100),
    couleur character varying(7),
    ordre integer DEFAULT 0
);


--
-- Name: statuts_taches_statut_id_seq; Type: SEQUENCE; Schema: menage; Owner: -
--

CREATE SEQUENCE menage.statuts_taches_statut_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: statuts_taches_statut_id_seq; Type: SEQUENCE OWNED BY; Schema: menage; Owner: -
--

ALTER SEQUENCE menage.statuts_taches_statut_id_seq OWNED BY menage.statuts_taches.statut_id;


--
-- Name: taches; Type: TABLE; Schema: menage; Owner: -
--

CREATE TABLE menage.taches (
    tache_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    chambre_id bigint NOT NULL,
    personnel_id bigint,
    statut_id integer DEFAULT 1,
    type_nettoyage character varying(20) DEFAULT 'quotidien'::character varying,
    priorite integer DEFAULT 1,
    date_planifiee date NOT NULL,
    heure_debut_prevue time without time zone,
    heure_fin_prevue time without time zone,
    heure_debut_reelle timestamp without time zone,
    heure_fin_reelle timestamp without time zone,
    duree_minutes integer GENERATED ALWAYS AS (
CASE
    WHEN ((heure_debut_reelle IS NOT NULL) AND (heure_fin_reelle IS NOT NULL)) THEN (EXTRACT(epoch FROM (heure_fin_reelle - heure_debut_reelle)) / (60)::numeric)
    ELSE NULL::numeric
END) STORED,
    commentaires text,
    problemes_detectes text,
    materiel_utilise json,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    date_modification timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_taches_priorite CHECK (((priorite >= 1) AND (priorite <= 3)))
);


--
-- Name: taches_tache_id_seq; Type: SEQUENCE; Schema: menage; Owner: -
--

CREATE SEQUENCE menage.taches_tache_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: taches_tache_id_seq; Type: SEQUENCE OWNED BY; Schema: menage; Owner: -
--

ALTER SEQUENCE menage.taches_tache_id_seq OWNED BY menage.taches.tache_id;


--
-- Name: alertes; Type: TABLE; Schema: reporting; Owner: -
--

CREATE TABLE reporting.alertes (
    alerte_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    type_alerte character varying(50) NOT NULL,
    niveau character varying(20) DEFAULT 'info'::character varying,
    titre character varying(200) NOT NULL,
    message text NOT NULL,
    donnees_contexte json,
    lue boolean DEFAULT false,
    traitee boolean DEFAULT false,
    user_id bigint,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    date_lecture timestamp without time zone,
    date_traitement timestamp without time zone
);


--
-- Name: alertes_alerte_id_seq; Type: SEQUENCE; Schema: reporting; Owner: -
--

CREATE SEQUENCE reporting.alertes_alerte_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: alertes_alerte_id_seq; Type: SEQUENCE OWNED BY; Schema: reporting; Owner: -
--

ALTER SEQUENCE reporting.alertes_alerte_id_seq OWNED BY reporting.alertes.alerte_id;


--
-- Name: chambres_disponibilite; Type: MATERIALIZED VIEW; Schema: reporting; Owner: -
--

CREATE MATERIALIZED VIEW reporting.chambres_disponibilite AS
 SELECT c.hotel_id,
    c.chambre_id,
    c.numero_chambre,
    c.type_id,
    tc.type_nom,
    c.statut,
    COALESCE(prochaine_reservation.date_arrivee, '2099-12-31'::date) AS prochaine_occupation,
    COALESCE(reservation_actuelle.date_depart, '1900-01-01'::date) AS fin_occupation_actuelle
   FROM (((hebergement.chambres c
     LEFT JOIN hebergement.types_chambres tc ON ((c.type_id = tc.type_id)))
     LEFT JOIN LATERAL ( SELECT min(rc.date_debut) AS date_arrivee
           FROM (hebergement.reservations_chambres rc
             JOIN hebergement.reservations r ON ((rc.reservation_id = r.reservation_id)))
          WHERE ((rc.chambre_id = c.chambre_id) AND (rc.date_debut > CURRENT_DATE) AND ((r.statut)::text = ANY ((ARRAY['confirmee'::character varying, 'arrivee'::character varying])::text[])))) prochaine_reservation ON (true))
     LEFT JOIN LATERAL ( SELECT max(rc.date_fin) AS date_depart
           FROM (hebergement.reservations_chambres rc
             JOIN hebergement.reservations r ON ((rc.reservation_id = r.reservation_id)))
          WHERE ((rc.chambre_id = c.chambre_id) AND (rc.date_debut <= CURRENT_DATE) AND (rc.date_fin > CURRENT_DATE) AND ((r.statut)::text = ANY ((ARRAY['confirmee'::character varying, 'arrivee'::character varying])::text[])))) reservation_actuelle ON (true))
  WHERE (c.actif = true)
  WITH NO DATA;


--
-- Name: dashboard_financier; Type: TABLE; Schema: reporting; Owner: -
--

CREATE TABLE reporting.dashboard_financier (
    dashboard_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    date_rapport date NOT NULL,
    ca_hebergement numeric(15,2) DEFAULT 0,
    ca_restauration numeric(15,2) DEFAULT 0,
    ca_services numeric(15,2) DEFAULT 0,
    ca_total numeric(15,2) GENERATED ALWAYS AS (((ca_hebergement + ca_restauration) + ca_services)) STORED,
    nb_nuitees integer DEFAULT 0,
    taux_occupation numeric(5,2) DEFAULT 0,
    prix_moyen_nuit numeric(10,2) DEFAULT 0,
    nb_clients integer DEFAULT 0,
    date_calcul timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: dashboard_financier_dashboard_id_seq; Type: SEQUENCE; Schema: reporting; Owner: -
--

CREATE SEQUENCE reporting.dashboard_financier_dashboard_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dashboard_financier_dashboard_id_seq; Type: SEQUENCE OWNED BY; Schema: reporting; Owner: -
--

ALTER SEQUENCE reporting.dashboard_financier_dashboard_id_seq OWNED BY reporting.dashboard_financier.dashboard_id;


--
-- Name: stocks_critiques; Type: MATERIALIZED VIEW; Schema: reporting; Owner: -
--

CREATE MATERIALIZED VIEW reporting.stocks_critiques AS
 SELECT p.hotel_id,
    p.produit_id,
    p.code_produit,
    p.nom_produit,
    p.stock_actuel,
    p.seuil_alerte,
    p.seuil_critique,
    cp.nom_categorie,
        CASE
            WHEN (p.stock_actuel <= p.seuil_critique) THEN 'critique'::text
            WHEN (p.stock_actuel <= p.seuil_alerte) THEN 'alerte'::text
            ELSE 'normal'::text
        END AS niveau_stock,
    f.nom_fournisseur
   FROM ((inventory.produits p
     JOIN inventory.categories_produits cp ON ((p.categorie_id = cp.categorie_id)))
     LEFT JOIN inventory.fournisseurs f ON ((p.fournisseur_principal_id = f.fournisseur_id)))
  WHERE ((p.actif = true) AND ((p.stock_actuel <= p.seuil_alerte) OR (p.seuil_alerte = 0)))
  WITH NO DATA;


--
-- Name: articles_menu; Type: TABLE; Schema: restaurant; Owner: -
--

CREATE TABLE restaurant.articles_menu (
    article_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    categorie_id bigint NOT NULL,
    code_article character varying(20),
    nom_article character varying(200) NOT NULL,
    nom_article_en character varying(200),
    nom_article_ar character varying(200),
    description text,
    description_en text,
    description_ar text,
    prix numeric(10,2) NOT NULL,
    cout_ingredients numeric(10,2) DEFAULT 0,
    temps_preparation integer,
    allergenes json,
    image_url character varying(500),
    disponible boolean DEFAULT true,
    actif boolean DEFAULT true,
    date_creation timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    date_modification timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: articles_menu_article_id_seq; Type: SEQUENCE; Schema: restaurant; Owner: -
--

CREATE SEQUENCE restaurant.articles_menu_article_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: articles_menu_article_id_seq; Type: SEQUENCE OWNED BY; Schema: restaurant; Owner: -
--

ALTER SEQUENCE restaurant.articles_menu_article_id_seq OWNED BY restaurant.articles_menu.article_id;


--
-- Name: categories_menu; Type: TABLE; Schema: restaurant; Owner: -
--

CREATE TABLE restaurant.categories_menu (
    categorie_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    nom_categorie character varying(100) NOT NULL,
    nom_categorie_en character varying(100),
    nom_categorie_ar character varying(100),
    description text,
    ordre_affichage integer DEFAULT 0,
    actif boolean DEFAULT true
);


--
-- Name: categories_menu_categorie_id_seq; Type: SEQUENCE; Schema: restaurant; Owner: -
--

CREATE SEQUENCE restaurant.categories_menu_categorie_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: categories_menu_categorie_id_seq; Type: SEQUENCE OWNED BY; Schema: restaurant; Owner: -
--

ALTER SEQUENCE restaurant.categories_menu_categorie_id_seq OWNED BY restaurant.categories_menu.categorie_id;


--
-- Name: commandes; Type: TABLE; Schema: restaurant; Owner: -
--

CREATE TABLE restaurant.commandes (
    commande_id bigint NOT NULL,
    hotel_id bigint NOT NULL,
    numero_commande character varying(20) NOT NULL,
    client_id bigint,
    reservation_id bigint,
    table_numero character varying(10),
    serveur_id bigint,
    statut character varying(20) DEFAULT 'en_attente'::character varying,
    type_service character varying(20) DEFAULT 'sur_place'::character varying,
    montant_total numeric(10,2) DEFAULT 0,
    montant_tva numeric(10,2) DEFAULT 0,
    commentaires text,
    date_commande timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    date_service timestamp without time zone
);


--
-- Name: commandes_commande_id_seq; Type: SEQUENCE; Schema: restaurant; Owner: -
--

CREATE SEQUENCE restaurant.commandes_commande_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: commandes_commande_id_seq; Type: SEQUENCE OWNED BY; Schema: restaurant; Owner: -
--

ALTER SEQUENCE restaurant.commandes_commande_id_seq OWNED BY restaurant.commandes.commande_id;


--
-- Name: lignes_commandes; Type: TABLE; Schema: restaurant; Owner: -
--

CREATE TABLE restaurant.lignes_commandes (
    ligne_id bigint NOT NULL,
    commande_id bigint NOT NULL,
    article_id bigint NOT NULL,
    quantite integer NOT NULL,
    prix_unitaire numeric(10,2) NOT NULL,
    sous_total numeric(10,2) GENERATED ALWAYS AS (((quantite)::numeric * prix_unitaire)) STORED,
    commentaires text,
    statut character varying(20) DEFAULT 'commandee'::character varying,
    CONSTRAINT chk_lignes_quantite CHECK ((quantite > 0))
);


--
-- Name: lignes_commandes_ligne_id_seq; Type: SEQUENCE; Schema: restaurant; Owner: -
--

CREATE SEQUENCE restaurant.lignes_commandes_ligne_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lignes_commandes_ligne_id_seq; Type: SEQUENCE OWNED BY; Schema: restaurant; Owner: -
--

ALTER SEQUENCE restaurant.lignes_commandes_ligne_id_seq OWNED BY restaurant.lignes_commandes.ligne_id;


--
-- Name: clients client_id; Type: DEFAULT; Schema: clients; Owner: -
--

ALTER TABLE ONLY clients.clients ALTER COLUMN client_id SET DEFAULT nextval('clients.clients_client_id_seq'::regclass);


--
-- Name: societes societe_id; Type: DEFAULT; Schema: clients; Owner: -
--

ALTER TABLE ONLY clients.societes ALTER COLUMN societe_id SET DEFAULT nextval('clients.societes_societe_id_seq'::regclass);


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
-- Name: affectations_paiements affectation_id; Type: DEFAULT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.affectations_paiements ALTER COLUMN affectation_id SET DEFAULT nextval('finance.affectations_paiements_affectation_id_seq'::regclass);


--
-- Name: comptes compte_id; Type: DEFAULT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.comptes ALTER COLUMN compte_id SET DEFAULT nextval('finance.comptes_compte_id_seq'::regclass);


--
-- Name: factures facture_id; Type: DEFAULT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.factures ALTER COLUMN facture_id SET DEFAULT nextval('finance.factures_facture_id_seq'::regclass);


--
-- Name: lignes_factures ligne_id; Type: DEFAULT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.lignes_factures ALTER COLUMN ligne_id SET DEFAULT nextval('finance.lignes_factures_ligne_id_seq'::regclass);


--
-- Name: operations_comptes operation_id; Type: DEFAULT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.operations_comptes ALTER COLUMN operation_id SET DEFAULT nextval('finance.operations_comptes_operation_id_seq'::regclass);


--
-- Name: paiements paiement_id; Type: DEFAULT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.paiements ALTER COLUMN paiement_id SET DEFAULT nextval('finance.paiements_paiement_id_seq'::regclass);


--
-- Name: services_facturables service_id; Type: DEFAULT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.services_facturables ALTER COLUMN service_id SET DEFAULT nextval('finance.services_facturables_service_id_seq'::regclass);


--
-- Name: chambres chambre_id; Type: DEFAULT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.chambres ALTER COLUMN chambre_id SET DEFAULT nextval('hebergement.chambres_chambre_id_seq'::regclass);


--
-- Name: nuitees nuitee_id; Type: DEFAULT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.nuitees ALTER COLUMN nuitee_id SET DEFAULT nextval('hebergement.nuitees_nuitee_id_seq'::regclass);


--
-- Name: reservations reservation_id; Type: DEFAULT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations ALTER COLUMN reservation_id SET DEFAULT nextval('hebergement.reservations_reservation_id_seq'::regclass);


--
-- Name: reservations_chambres reservation_chambre_id; Type: DEFAULT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations_chambres ALTER COLUMN reservation_chambre_id SET DEFAULT nextval('hebergement.reservations_chambres_reservation_chambre_id_seq'::regclass);


--
-- Name: reservations_clients reservation_client_id; Type: DEFAULT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations_clients ALTER COLUMN reservation_client_id SET DEFAULT nextval('hebergement.reservations_clients_reservation_client_id_seq'::regclass);


--
-- Name: tarifs_chambres tarif_id; Type: DEFAULT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.tarifs_chambres ALTER COLUMN tarif_id SET DEFAULT nextval('hebergement.tarifs_chambres_tarif_id_seq'::regclass);


--
-- Name: types_chambres type_id; Type: DEFAULT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.types_chambres ALTER COLUMN type_id SET DEFAULT nextval('hebergement.types_chambres_type_id_seq'::regclass);


--
-- Name: bons_commande bon_commande_id; Type: DEFAULT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.bons_commande ALTER COLUMN bon_commande_id SET DEFAULT nextval('inventory.bons_commande_bon_commande_id_seq'::regclass);


--
-- Name: bons_sortie bon_sortie_id; Type: DEFAULT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.bons_sortie ALTER COLUMN bon_sortie_id SET DEFAULT nextval('inventory.bons_sortie_bon_sortie_id_seq'::regclass);


--
-- Name: categories_produits categorie_id; Type: DEFAULT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.categories_produits ALTER COLUMN categorie_id SET DEFAULT nextval('inventory.categories_produits_categorie_id_seq'::regclass);


--
-- Name: fournisseurs fournisseur_id; Type: DEFAULT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.fournisseurs ALTER COLUMN fournisseur_id SET DEFAULT nextval('inventory.fournisseurs_fournisseur_id_seq'::regclass);


--
-- Name: lignes_bons_commande ligne_id; Type: DEFAULT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.lignes_bons_commande ALTER COLUMN ligne_id SET DEFAULT nextval('inventory.lignes_bons_commande_ligne_id_seq'::regclass);


--
-- Name: lignes_bons_sortie ligne_id; Type: DEFAULT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.lignes_bons_sortie ALTER COLUMN ligne_id SET DEFAULT nextval('inventory.lignes_bons_sortie_ligne_id_seq'::regclass);


--
-- Name: mouvements_stock mouvement_id; Type: DEFAULT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.mouvements_stock ALTER COLUMN mouvement_id SET DEFAULT nextval('inventory.mouvements_stock_mouvement_id_seq'::regclass);


--
-- Name: produits produit_id; Type: DEFAULT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.produits ALTER COLUMN produit_id SET DEFAULT nextval('inventory.produits_produit_id_seq'::regclass);


--
-- Name: historique historique_id; Type: DEFAULT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.historique ALTER COLUMN historique_id SET DEFAULT nextval('menage.historique_historique_id_seq'::regclass);


--
-- Name: personnel personnel_id; Type: DEFAULT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.personnel ALTER COLUMN personnel_id SET DEFAULT nextval('menage.personnel_personnel_id_seq'::regclass);


--
-- Name: planning planning_id; Type: DEFAULT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.planning ALTER COLUMN planning_id SET DEFAULT nextval('menage.planning_planning_id_seq'::regclass);


--
-- Name: statuts_taches statut_id; Type: DEFAULT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.statuts_taches ALTER COLUMN statut_id SET DEFAULT nextval('menage.statuts_taches_statut_id_seq'::regclass);


--
-- Name: taches tache_id; Type: DEFAULT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.taches ALTER COLUMN tache_id SET DEFAULT nextval('menage.taches_tache_id_seq'::regclass);


--
-- Name: alertes alerte_id; Type: DEFAULT; Schema: reporting; Owner: -
--

ALTER TABLE ONLY reporting.alertes ALTER COLUMN alerte_id SET DEFAULT nextval('reporting.alertes_alerte_id_seq'::regclass);


--
-- Name: dashboard_financier dashboard_id; Type: DEFAULT; Schema: reporting; Owner: -
--

ALTER TABLE ONLY reporting.dashboard_financier ALTER COLUMN dashboard_id SET DEFAULT nextval('reporting.dashboard_financier_dashboard_id_seq'::regclass);


--
-- Name: articles_menu article_id; Type: DEFAULT; Schema: restaurant; Owner: -
--

ALTER TABLE ONLY restaurant.articles_menu ALTER COLUMN article_id SET DEFAULT nextval('restaurant.articles_menu_article_id_seq'::regclass);


--
-- Name: categories_menu categorie_id; Type: DEFAULT; Schema: restaurant; Owner: -
--

ALTER TABLE ONLY restaurant.categories_menu ALTER COLUMN categorie_id SET DEFAULT nextval('restaurant.categories_menu_categorie_id_seq'::regclass);


--
-- Name: commandes commande_id; Type: DEFAULT; Schema: restaurant; Owner: -
--

ALTER TABLE ONLY restaurant.commandes ALTER COLUMN commande_id SET DEFAULT nextval('restaurant.commandes_commande_id_seq'::regclass);


--
-- Name: lignes_commandes ligne_id; Type: DEFAULT; Schema: restaurant; Owner: -
--

ALTER TABLE ONLY restaurant.lignes_commandes ALTER COLUMN ligne_id SET DEFAULT nextval('restaurant.lignes_commandes_ligne_id_seq'::regclass);


--
-- Name: clients clients_numero_client_key; Type: CONSTRAINT; Schema: clients; Owner: -
--

ALTER TABLE ONLY clients.clients
    ADD CONSTRAINT clients_numero_client_key UNIQUE (numero_client);


--
-- Name: clients clients_pkey; Type: CONSTRAINT; Schema: clients; Owner: -
--

ALTER TABLE ONLY clients.clients
    ADD CONSTRAINT clients_pkey PRIMARY KEY (client_id);


--
-- Name: societes societes_pkey; Type: CONSTRAINT; Schema: clients; Owner: -
--

ALTER TABLE ONLY clients.societes
    ADD CONSTRAINT societes_pkey PRIMARY KEY (societe_id);


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
-- Name: affectations_paiements affectations_paiements_pkey; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.affectations_paiements
    ADD CONSTRAINT affectations_paiements_pkey PRIMARY KEY (affectation_id);


--
-- Name: comptes comptes_numero_compte_key; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.comptes
    ADD CONSTRAINT comptes_numero_compte_key UNIQUE (numero_compte);


--
-- Name: comptes comptes_pkey; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.comptes
    ADD CONSTRAINT comptes_pkey PRIMARY KEY (compte_id);


--
-- Name: factures factures_numero_facture_key; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.factures
    ADD CONSTRAINT factures_numero_facture_key UNIQUE (numero_facture);


--
-- Name: factures factures_pkey; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.factures
    ADD CONSTRAINT factures_pkey PRIMARY KEY (facture_id);


--
-- Name: lignes_factures lignes_factures_pkey; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.lignes_factures
    ADD CONSTRAINT lignes_factures_pkey PRIMARY KEY (ligne_id);


--
-- Name: operations_comptes operations_comptes_pkey; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.operations_comptes
    ADD CONSTRAINT operations_comptes_pkey PRIMARY KEY (operation_id);


--
-- Name: paiements paiements_numero_paiement_key; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.paiements
    ADD CONSTRAINT paiements_numero_paiement_key UNIQUE (numero_paiement);


--
-- Name: paiements paiements_pkey; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.paiements
    ADD CONSTRAINT paiements_pkey PRIMARY KEY (paiement_id);


--
-- Name: services_facturables services_facturables_pkey; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.services_facturables
    ADD CONSTRAINT services_facturables_pkey PRIMARY KEY (service_id);


--
-- Name: affectations_paiements uk_affectations; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.affectations_paiements
    ADD CONSTRAINT uk_affectations UNIQUE (paiement_id, facture_id);


--
-- Name: factures uk_factures_sequence; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.factures
    ADD CONSTRAINT uk_factures_sequence UNIQUE (hotel_id, numero_sequence);


--
-- Name: services_facturables uk_services_code; Type: CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.services_facturables
    ADD CONSTRAINT uk_services_code UNIQUE (hotel_id, code_service);


--
-- Name: chambres chambres_pkey; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.chambres
    ADD CONSTRAINT chambres_pkey PRIMARY KEY (chambre_id);


--
-- Name: nuitees nuitees_pkey; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.nuitees
    ADD CONSTRAINT nuitees_pkey PRIMARY KEY (nuitee_id);


--
-- Name: reservations_chambres reservations_chambres_pkey; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations_chambres
    ADD CONSTRAINT reservations_chambres_pkey PRIMARY KEY (reservation_chambre_id);


--
-- Name: reservations_clients reservations_clients_pkey; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations_clients
    ADD CONSTRAINT reservations_clients_pkey PRIMARY KEY (reservation_client_id);


--
-- Name: reservations reservations_numero_reservation_key; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations
    ADD CONSTRAINT reservations_numero_reservation_key UNIQUE (numero_reservation);


--
-- Name: reservations reservations_pkey; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations
    ADD CONSTRAINT reservations_pkey PRIMARY KEY (reservation_id);


--
-- Name: tarifs_chambres tarifs_chambres_pkey; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.tarifs_chambres
    ADD CONSTRAINT tarifs_chambres_pkey PRIMARY KEY (tarif_id);


--
-- Name: types_chambres types_chambres_pkey; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.types_chambres
    ADD CONSTRAINT types_chambres_pkey PRIMARY KEY (type_id);


--
-- Name: chambres uk_chambres_numero; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.chambres
    ADD CONSTRAINT uk_chambres_numero UNIQUE (hotel_id, numero_chambre);


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
-- Name: types_chambres uk_types_chambres_code; Type: CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.types_chambres
    ADD CONSTRAINT uk_types_chambres_code UNIQUE (hotel_id, type_code);


--
-- Name: bons_commande bons_commande_numero_bon_key; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.bons_commande
    ADD CONSTRAINT bons_commande_numero_bon_key UNIQUE (numero_bon);


--
-- Name: bons_commande bons_commande_pkey; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.bons_commande
    ADD CONSTRAINT bons_commande_pkey PRIMARY KEY (bon_commande_id);


--
-- Name: bons_sortie bons_sortie_numero_bon_key; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.bons_sortie
    ADD CONSTRAINT bons_sortie_numero_bon_key UNIQUE (numero_bon);


--
-- Name: bons_sortie bons_sortie_pkey; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.bons_sortie
    ADD CONSTRAINT bons_sortie_pkey PRIMARY KEY (bon_sortie_id);


--
-- Name: categories_produits categories_produits_pkey; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.categories_produits
    ADD CONSTRAINT categories_produits_pkey PRIMARY KEY (categorie_id);


--
-- Name: fournisseurs fournisseurs_pkey; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.fournisseurs
    ADD CONSTRAINT fournisseurs_pkey PRIMARY KEY (fournisseur_id);


--
-- Name: lignes_bons_commande lignes_bons_commande_pkey; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.lignes_bons_commande
    ADD CONSTRAINT lignes_bons_commande_pkey PRIMARY KEY (ligne_id);


--
-- Name: lignes_bons_sortie lignes_bons_sortie_pkey; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.lignes_bons_sortie
    ADD CONSTRAINT lignes_bons_sortie_pkey PRIMARY KEY (ligne_id);


--
-- Name: mouvements_stock mouvements_stock_pkey; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.mouvements_stock
    ADD CONSTRAINT mouvements_stock_pkey PRIMARY KEY (mouvement_id);


--
-- Name: produits produits_code_produit_key; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.produits
    ADD CONSTRAINT produits_code_produit_key UNIQUE (code_produit);


--
-- Name: produits produits_pkey; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.produits
    ADD CONSTRAINT produits_pkey PRIMARY KEY (produit_id);


--
-- Name: categories_produits uk_categories_code; Type: CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.categories_produits
    ADD CONSTRAINT uk_categories_code UNIQUE (hotel_id, code_categorie);


--
-- Name: historique historique_pkey; Type: CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.historique
    ADD CONSTRAINT historique_pkey PRIMARY KEY (historique_id);


--
-- Name: personnel personnel_pkey; Type: CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.personnel
    ADD CONSTRAINT personnel_pkey PRIMARY KEY (personnel_id);


--
-- Name: planning planning_pkey; Type: CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.planning
    ADD CONSTRAINT planning_pkey PRIMARY KEY (planning_id);


--
-- Name: statuts_taches statuts_taches_code_statut_key; Type: CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.statuts_taches
    ADD CONSTRAINT statuts_taches_code_statut_key UNIQUE (code_statut);


--
-- Name: statuts_taches statuts_taches_pkey; Type: CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.statuts_taches
    ADD CONSTRAINT statuts_taches_pkey PRIMARY KEY (statut_id);


--
-- Name: taches taches_pkey; Type: CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.taches
    ADD CONSTRAINT taches_pkey PRIMARY KEY (tache_id);


--
-- Name: personnel uk_personnel_numero; Type: CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.personnel
    ADD CONSTRAINT uk_personnel_numero UNIQUE (hotel_id, numero_employe);


--
-- Name: planning uk_planning_personnel_date; Type: CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.planning
    ADD CONSTRAINT uk_planning_personnel_date UNIQUE (personnel_id, date_travail);


--
-- Name: alertes alertes_pkey; Type: CONSTRAINT; Schema: reporting; Owner: -
--

ALTER TABLE ONLY reporting.alertes
    ADD CONSTRAINT alertes_pkey PRIMARY KEY (alerte_id);


--
-- Name: dashboard_financier dashboard_financier_pkey; Type: CONSTRAINT; Schema: reporting; Owner: -
--

ALTER TABLE ONLY reporting.dashboard_financier
    ADD CONSTRAINT dashboard_financier_pkey PRIMARY KEY (dashboard_id);


--
-- Name: dashboard_financier uk_dashboard_hotel_date; Type: CONSTRAINT; Schema: reporting; Owner: -
--

ALTER TABLE ONLY reporting.dashboard_financier
    ADD CONSTRAINT uk_dashboard_hotel_date UNIQUE (hotel_id, date_rapport);


--
-- Name: articles_menu articles_menu_pkey; Type: CONSTRAINT; Schema: restaurant; Owner: -
--

ALTER TABLE ONLY restaurant.articles_menu
    ADD CONSTRAINT articles_menu_pkey PRIMARY KEY (article_id);


--
-- Name: categories_menu categories_menu_pkey; Type: CONSTRAINT; Schema: restaurant; Owner: -
--

ALTER TABLE ONLY restaurant.categories_menu
    ADD CONSTRAINT categories_menu_pkey PRIMARY KEY (categorie_id);


--
-- Name: commandes commandes_numero_commande_key; Type: CONSTRAINT; Schema: restaurant; Owner: -
--

ALTER TABLE ONLY restaurant.commandes
    ADD CONSTRAINT commandes_numero_commande_key UNIQUE (numero_commande);


--
-- Name: commandes commandes_pkey; Type: CONSTRAINT; Schema: restaurant; Owner: -
--

ALTER TABLE ONLY restaurant.commandes
    ADD CONSTRAINT commandes_pkey PRIMARY KEY (commande_id);


--
-- Name: lignes_commandes lignes_commandes_pkey; Type: CONSTRAINT; Schema: restaurant; Owner: -
--

ALTER TABLE ONLY restaurant.lignes_commandes
    ADD CONSTRAINT lignes_commandes_pkey PRIMARY KEY (ligne_id);


--
-- Name: articles_menu uk_articles_code; Type: CONSTRAINT; Schema: restaurant; Owner: -
--

ALTER TABLE ONLY restaurant.articles_menu
    ADD CONSTRAINT uk_articles_code UNIQUE (hotel_id, code_article);


--
-- Name: idx_clients_actif; Type: INDEX; Schema: clients; Owner: -
--

CREATE INDEX idx_clients_actif ON clients.clients USING btree (hotel_id, actif);


--
-- Name: idx_clients_email; Type: INDEX; Schema: clients; Owner: -
--

CREATE INDEX idx_clients_email ON clients.clients USING btree (hotel_id, email);


--
-- Name: idx_clients_hotel; Type: INDEX; Schema: clients; Owner: -
--

CREATE INDEX idx_clients_hotel ON clients.clients USING btree (hotel_id);


--
-- Name: idx_clients_identification; Type: INDEX; Schema: clients; Owner: -
--

CREATE INDEX idx_clients_identification ON clients.clients USING btree (hotel_id, numero_identification);


--
-- Name: idx_clients_nom; Type: INDEX; Schema: clients; Owner: -
--

CREATE INDEX idx_clients_nom ON clients.clients USING btree (hotel_id, nom, prenom);


--
-- Name: idx_clients_numero; Type: INDEX; Schema: clients; Owner: -
--

CREATE INDEX idx_clients_numero ON clients.clients USING btree (numero_client);


--
-- Name: idx_clients_search; Type: INDEX; Schema: clients; Owner: -
--

CREATE INDEX idx_clients_search ON clients.clients USING btree (hotel_id, nom, prenom, email, telephone);


--
-- Name: idx_clients_societe; Type: INDEX; Schema: clients; Owner: -
--

CREATE INDEX idx_clients_societe ON clients.clients USING btree (societe_id);


--
-- Name: idx_clients_telephone; Type: INDEX; Schema: clients; Owner: -
--

CREATE INDEX idx_clients_telephone ON clients.clients USING btree (hotel_id, telephone);


--
-- Name: idx_societes_actif; Type: INDEX; Schema: clients; Owner: -
--

CREATE INDEX idx_societes_actif ON clients.societes USING btree (hotel_id, actif);


--
-- Name: idx_societes_hotel; Type: INDEX; Schema: clients; Owner: -
--

CREATE INDEX idx_societes_hotel ON clients.societes USING btree (hotel_id);


--
-- Name: idx_societes_nom; Type: INDEX; Schema: clients; Owner: -
--

CREATE INDEX idx_societes_nom ON clients.societes USING btree (hotel_id, societe_nom);


--
-- Name: idx_societes_search; Type: INDEX; Schema: clients; Owner: -
--

CREATE INDEX idx_societes_search ON clients.societes USING btree (hotel_id, societe_nom, contact_principal);


--
-- Name: idx_dbusers_actif; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_dbusers_actif ON core.dbusers USING btree (actif);


--
-- Name: idx_dbusers_email; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_dbusers_email ON core.dbusers USING btree (email);


--
-- Name: idx_dbusers_hotel; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_dbusers_hotel ON core.dbusers USING btree (hotel_id);


--
-- Name: idx_dbusers_hotel_actif; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_dbusers_hotel_actif ON core.dbusers USING btree (hotel_id, actif);


--
-- Name: idx_dbusers_role; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_dbusers_role ON core.dbusers USING btree (role_id);


--
-- Name: idx_dbusers_username; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_dbusers_username ON core.dbusers USING btree (username);


--
-- Name: idx_hotels_actif; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_hotels_actif ON core.hotels USING btree (actif);


--
-- Name: idx_hotels_code; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_hotels_code ON core.hotels USING btree (hotel_code);


--
-- Name: idx_hotels_pays_ville; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_hotels_pays_ville ON core.hotels USING btree (pays, ville);


--
-- Name: idx_ref_actif; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_ref_actif ON core.donnees_referentielles USING btree (actif);


--
-- Name: idx_ref_categorie; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_ref_categorie ON core.donnees_referentielles USING btree (categorie);


--
-- Name: idx_ref_categorie_actif; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_ref_categorie_actif ON core.donnees_referentielles USING btree (categorie, actif);


--
-- Name: idx_ref_code; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_ref_code ON core.donnees_referentielles USING btree (code);


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
-- Name: idx_sessions_hotel; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_sessions_hotel ON core.user_sessions USING btree (hotel_id);


--
-- Name: idx_sessions_user; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_sessions_user ON core.user_sessions USING btree (user_id);


--
-- Name: idx_affectations_facture; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_affectations_facture ON finance.affectations_paiements USING btree (facture_id);


--
-- Name: idx_affectations_paiement; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_affectations_paiement ON finance.affectations_paiements USING btree (paiement_id);


--
-- Name: idx_comptes_client; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_comptes_client ON finance.comptes USING btree (client_id);


--
-- Name: idx_comptes_fournisseur; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_comptes_fournisseur ON finance.comptes USING btree (fournisseur_id);


--
-- Name: idx_comptes_hotel; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_comptes_hotel ON finance.comptes USING btree (hotel_id);


--
-- Name: idx_comptes_numero; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_comptes_numero ON finance.comptes USING btree (numero_compte);


--
-- Name: idx_comptes_societe; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_comptes_societe ON finance.comptes USING btree (societe_id);


--
-- Name: idx_comptes_solde; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_comptes_solde ON finance.comptes USING btree (hotel_id, solde_actuel);


--
-- Name: idx_comptes_statut; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_comptes_statut ON finance.comptes USING btree (hotel_id, statut);


--
-- Name: idx_comptes_type; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_comptes_type ON finance.comptes USING btree (hotel_id, type_compte);


--
-- Name: idx_factures_client; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_factures_client ON finance.factures USING btree (client_id);


--
-- Name: idx_factures_compte; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_factures_compte ON finance.factures USING btree (compte_id);


--
-- Name: idx_factures_date; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_factures_date ON finance.factures USING btree (hotel_id, date_facture);


--
-- Name: idx_factures_echeance; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_factures_echeance ON finance.factures USING btree (hotel_id, date_echeance, statut_paiement);


--
-- Name: idx_factures_hotel; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_factures_hotel ON finance.factures USING btree (hotel_id);


--
-- Name: idx_factures_montant; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_factures_montant ON finance.factures USING btree (hotel_id, montant_ttc);


--
-- Name: idx_factures_numero; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_factures_numero ON finance.factures USING btree (numero_facture);


--
-- Name: idx_factures_reservation; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_factures_reservation ON finance.factures USING btree (reservation_id);


--
-- Name: idx_factures_search; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_factures_search ON finance.factures USING btree (hotel_id, numero_facture, date_facture, statut_paiement);


--
-- Name: idx_factures_sequence; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_factures_sequence ON finance.factures USING btree (hotel_id, numero_sequence);


--
-- Name: idx_factures_societe; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_factures_societe ON finance.factures USING btree (societe_id);


--
-- Name: idx_factures_statut; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_factures_statut ON finance.factures USING btree (hotel_id, statut_paiement);


--
-- Name: idx_factures_user; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_factures_user ON finance.factures USING btree (user_id);


--
-- Name: idx_lignes_commande; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_lignes_commande ON finance.lignes_factures USING btree (commande_id);


--
-- Name: idx_lignes_date; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_lignes_date ON finance.lignes_factures USING btree (date_prestation);


--
-- Name: idx_lignes_facture; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_lignes_facture ON finance.lignes_factures USING btree (facture_id);


--
-- Name: idx_lignes_nuitee; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_lignes_nuitee ON finance.lignes_factures USING btree (nuitee_id);


--
-- Name: idx_lignes_produit; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_lignes_produit ON finance.lignes_factures USING btree (produit_id);


--
-- Name: idx_lignes_service; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_lignes_service ON finance.lignes_factures USING btree (service_id);


--
-- Name: idx_lignes_statut; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_lignes_statut ON finance.lignes_factures USING btree (statut_paiement);


--
-- Name: idx_lignes_type; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_lignes_type ON finance.lignes_factures USING btree (type_ligne);


--
-- Name: idx_operations_compte; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_operations_compte ON finance.operations_comptes USING btree (compte_id);


--
-- Name: idx_operations_compte_date; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_operations_compte_date ON finance.operations_comptes USING btree (compte_id, date_operation);


--
-- Name: idx_operations_date; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_operations_date ON finance.operations_comptes USING btree (hotel_id, date_operation);


--
-- Name: idx_operations_facture; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_operations_facture ON finance.operations_comptes USING btree (facture_id);


--
-- Name: idx_operations_hotel; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_operations_hotel ON finance.operations_comptes USING btree (hotel_id);


--
-- Name: idx_operations_paiement; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_operations_paiement ON finance.operations_comptes USING btree (paiement_id);


--
-- Name: idx_operations_type; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_operations_type ON finance.operations_comptes USING btree (hotel_id, type_operation);


--
-- Name: idx_operations_user; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_operations_user ON finance.operations_comptes USING btree (user_id);


--
-- Name: idx_operations_valeur; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_operations_valeur ON finance.operations_comptes USING btree (hotel_id, date_valeur);


--
-- Name: idx_paiements_compte; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_paiements_compte ON finance.paiements USING btree (compte_id);


--
-- Name: idx_paiements_date; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_paiements_date ON finance.paiements USING btree (hotel_id, date_paiement);


--
-- Name: idx_paiements_hotel; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_paiements_hotel ON finance.paiements USING btree (hotel_id);


--
-- Name: idx_paiements_mode; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_paiements_mode ON finance.paiements USING btree (hotel_id, mode_paiement);


--
-- Name: idx_paiements_montant; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_paiements_montant ON finance.paiements USING btree (hotel_id, montant_total);


--
-- Name: idx_paiements_numero; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_paiements_numero ON finance.paiements USING btree (numero_paiement);


--
-- Name: idx_paiements_statut; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_paiements_statut ON finance.paiements USING btree (hotel_id, statut);


--
-- Name: idx_paiements_user; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_paiements_user ON finance.paiements USING btree (user_id);


--
-- Name: idx_services_actif; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_services_actif ON finance.services_facturables USING btree (hotel_id, actif);


--
-- Name: idx_services_categorie; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_services_categorie ON finance.services_facturables USING btree (hotel_id, categorie);


--
-- Name: idx_services_code; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_services_code ON finance.services_facturables USING btree (hotel_id, code_service);


--
-- Name: idx_services_hotel; Type: INDEX; Schema: finance; Owner: -
--

CREATE INDEX idx_services_hotel ON finance.services_facturables USING btree (hotel_id);


--
-- Name: idx_chambres_actif; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_chambres_actif ON hebergement.chambres USING btree (hotel_id, actif);


--
-- Name: idx_chambres_disponibles; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_chambres_disponibles ON hebergement.chambres USING btree (hotel_id, statut, actif);


--
-- Name: idx_chambres_etage; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_chambres_etage ON hebergement.chambres USING btree (hotel_id, etage);


--
-- Name: idx_chambres_hotel; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_chambres_hotel ON hebergement.chambres USING btree (hotel_id);


--
-- Name: idx_chambres_numero; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_chambres_numero ON hebergement.chambres USING btree (hotel_id, numero_chambre);


--
-- Name: idx_chambres_statut; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_chambres_statut ON hebergement.chambres USING btree (hotel_id, statut);


--
-- Name: idx_chambres_type; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_chambres_type ON hebergement.chambres USING btree (type_id);


--
-- Name: idx_nuitees_chambre; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_nuitees_chambre ON hebergement.nuitees USING btree (chambre_id);


--
-- Name: idx_nuitees_chambre_date; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_nuitees_chambre_date ON hebergement.nuitees USING btree (chambre_id, date_nuit);


--
-- Name: idx_nuitees_date; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_nuitees_date ON hebergement.nuitees USING btree (date_nuit);


--
-- Name: idx_nuitees_facturation; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_nuitees_facturation ON hebergement.nuitees USING btree (statut, date_nuit);


--
-- Name: idx_nuitees_reservation; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_nuitees_reservation ON hebergement.nuitees USING btree (reservation_id);


--
-- Name: idx_nuitees_statut; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_nuitees_statut ON hebergement.nuitees USING btree (statut);


--
-- Name: idx_res_chambres_chambre; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_res_chambres_chambre ON hebergement.reservations_chambres USING btree (chambre_id);


--
-- Name: idx_res_chambres_dates; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_res_chambres_dates ON hebergement.reservations_chambres USING btree (chambre_id, date_debut, date_fin);


--
-- Name: idx_res_chambres_disponibilite; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_res_chambres_disponibilite ON hebergement.reservations_chambres USING btree (chambre_id, date_debut, date_fin);


--
-- Name: idx_res_chambres_reservation; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_res_chambres_reservation ON hebergement.reservations_chambres USING btree (reservation_id);


--
-- Name: idx_res_clients_chambre; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_res_clients_chambre ON hebergement.reservations_clients USING btree (chambre_id);


--
-- Name: idx_res_clients_client; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_res_clients_client ON hebergement.reservations_clients USING btree (client_id);


--
-- Name: idx_res_clients_reservation; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_res_clients_reservation ON hebergement.reservations_clients USING btree (reservation_id);


--
-- Name: idx_reservations_arrivee; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_reservations_arrivee ON hebergement.reservations USING btree (hotel_id, date_arrivee, statut);


--
-- Name: idx_reservations_client; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_reservations_client ON hebergement.reservations USING btree (client_principal_id);


--
-- Name: idx_reservations_dates; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_reservations_dates ON hebergement.reservations USING btree (hotel_id, date_arrivee, date_depart);


--
-- Name: idx_reservations_depart; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_reservations_depart ON hebergement.reservations USING btree (hotel_id, date_depart, statut);


--
-- Name: idx_reservations_hotel; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_reservations_hotel ON hebergement.reservations USING btree (hotel_id);


--
-- Name: idx_reservations_numero; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_reservations_numero ON hebergement.reservations USING btree (numero_reservation);


--
-- Name: idx_reservations_search; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_reservations_search ON hebergement.reservations USING btree (hotel_id, numero_reservation, date_arrivee, statut);


--
-- Name: idx_reservations_societe; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_reservations_societe ON hebergement.reservations USING btree (societe_id);


--
-- Name: idx_reservations_statut; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_reservations_statut ON hebergement.reservations USING btree (hotel_id, statut);


--
-- Name: idx_reservations_user; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_reservations_user ON hebergement.reservations USING btree (user_id);


--
-- Name: idx_tarifs_actif; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_tarifs_actif ON hebergement.tarifs_chambres USING btree (hotel_id, actif);


--
-- Name: idx_tarifs_hotel; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_tarifs_hotel ON hebergement.tarifs_chambres USING btree (hotel_id);


--
-- Name: idx_tarifs_periode; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_tarifs_periode ON hebergement.tarifs_chambres USING btree (date_debut, date_fin);


--
-- Name: idx_tarifs_type; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_tarifs_type ON hebergement.tarifs_chambres USING btree (type_id);


--
-- Name: idx_types_chambres_actif; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_types_chambres_actif ON hebergement.types_chambres USING btree (hotel_id, actif);


--
-- Name: idx_types_chambres_hotel; Type: INDEX; Schema: hebergement; Owner: -
--

CREATE INDEX idx_types_chambres_hotel ON hebergement.types_chambres USING btree (hotel_id);


--
-- Name: idx_bons_commande_date; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_bons_commande_date ON inventory.bons_commande USING btree (hotel_id, date_commande);


--
-- Name: idx_bons_commande_fournisseur; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_bons_commande_fournisseur ON inventory.bons_commande USING btree (fournisseur_id);


--
-- Name: idx_bons_commande_hotel; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_bons_commande_hotel ON inventory.bons_commande USING btree (hotel_id);


--
-- Name: idx_bons_commande_numero; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_bons_commande_numero ON inventory.bons_commande USING btree (numero_bon);


--
-- Name: idx_bons_commande_statut; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_bons_commande_statut ON inventory.bons_commande USING btree (hotel_id, statut);


--
-- Name: idx_bons_commande_user; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_bons_commande_user ON inventory.bons_commande USING btree (user_id);


--
-- Name: idx_bons_sortie_date; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_bons_sortie_date ON inventory.bons_sortie USING btree (hotel_id, date_sortie);


--
-- Name: idx_bons_sortie_destination; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_bons_sortie_destination ON inventory.bons_sortie USING btree (hotel_id, destination);


--
-- Name: idx_bons_sortie_hotel; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_bons_sortie_hotel ON inventory.bons_sortie USING btree (hotel_id);


--
-- Name: idx_bons_sortie_numero; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_bons_sortie_numero ON inventory.bons_sortie USING btree (numero_bon);


--
-- Name: idx_bons_sortie_statut; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_bons_sortie_statut ON inventory.bons_sortie USING btree (hotel_id, statut);


--
-- Name: idx_bons_sortie_user; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_bons_sortie_user ON inventory.bons_sortie USING btree (user_id);


--
-- Name: idx_categories_actif; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_categories_actif ON inventory.categories_produits USING btree (hotel_id, actif);


--
-- Name: idx_categories_code; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_categories_code ON inventory.categories_produits USING btree (hotel_id, code_categorie);


--
-- Name: idx_categories_hotel; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_categories_hotel ON inventory.categories_produits USING btree (hotel_id);


--
-- Name: idx_categories_nom; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_categories_nom ON inventory.categories_produits USING btree (hotel_id, nom_categorie);


--
-- Name: idx_fournisseurs_actif; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_fournisseurs_actif ON inventory.fournisseurs USING btree (hotel_id, actif);


--
-- Name: idx_fournisseurs_hotel; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_fournisseurs_hotel ON inventory.fournisseurs USING btree (hotel_id);


--
-- Name: idx_fournisseurs_nom; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_fournisseurs_nom ON inventory.fournisseurs USING btree (hotel_id, nom_fournisseur);


--
-- Name: idx_lignes_bon_commande; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_lignes_bon_commande ON inventory.lignes_bons_commande USING btree (bon_commande_id);


--
-- Name: idx_lignes_bon_sortie; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_lignes_bon_sortie ON inventory.lignes_bons_sortie USING btree (bon_sortie_id);


--
-- Name: idx_lignes_produit; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_lignes_produit ON inventory.lignes_bons_commande USING btree (produit_id);


--
-- Name: idx_lignes_sortie_produit; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_lignes_sortie_produit ON inventory.lignes_bons_sortie USING btree (produit_id);


--
-- Name: idx_mouvements_date; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_mouvements_date ON inventory.mouvements_stock USING btree (hotel_id, date_mouvement);


--
-- Name: idx_mouvements_hotel; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_mouvements_hotel ON inventory.mouvements_stock USING btree (hotel_id);


--
-- Name: idx_mouvements_produit; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_mouvements_produit ON inventory.mouvements_stock USING btree (produit_id);


--
-- Name: idx_mouvements_produit_date; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_mouvements_produit_date ON inventory.mouvements_stock USING btree (produit_id, date_mouvement);


--
-- Name: idx_mouvements_reference; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_mouvements_reference ON inventory.mouvements_stock USING btree (reference_document);


--
-- Name: idx_mouvements_type; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_mouvements_type ON inventory.mouvements_stock USING btree (hotel_id, type_mouvement);


--
-- Name: idx_mouvements_user; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_mouvements_user ON inventory.mouvements_stock USING btree (user_id);


--
-- Name: idx_produits_alerte; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_produits_alerte ON inventory.produits USING btree (hotel_id, stock_actuel, seuil_alerte);


--
-- Name: idx_produits_categorie; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_produits_categorie ON inventory.produits USING btree (categorie_id);


--
-- Name: idx_produits_code; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_produits_code ON inventory.produits USING btree (code_produit);


--
-- Name: idx_produits_facturable; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_produits_facturable ON inventory.produits USING btree (hotel_id, est_facturable, actif);


--
-- Name: idx_produits_fournisseur; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_produits_fournisseur ON inventory.produits USING btree (fournisseur_principal_id);


--
-- Name: idx_produits_hotel; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_produits_hotel ON inventory.produits USING btree (hotel_id);


--
-- Name: idx_produits_nom; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_produits_nom ON inventory.produits USING btree (hotel_id, nom_produit);


--
-- Name: idx_produits_search; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_produits_search ON inventory.produits USING btree (hotel_id, nom_produit, code_produit, description);


--
-- Name: idx_produits_stock; Type: INDEX; Schema: inventory; Owner: -
--

CREATE INDEX idx_produits_stock ON inventory.produits USING btree (hotel_id, stock_actuel);


--
-- Name: idx_historique_action; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_historique_action ON menage.historique USING btree (action);


--
-- Name: idx_historique_chambre; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_historique_chambre ON menage.historique USING btree (chambre_id);


--
-- Name: idx_historique_hotel; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_historique_hotel ON menage.historique USING btree (hotel_id);


--
-- Name: idx_historique_personnel; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_historique_personnel ON menage.historique USING btree (personnel_id);


--
-- Name: idx_historique_tache; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_historique_tache ON menage.historique USING btree (tache_id);


--
-- Name: idx_historique_timestamp; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_historique_timestamp ON menage.historique USING btree (hotel_id, timestamp_action);


--
-- Name: idx_historique_user; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_historique_user ON menage.historique USING btree (user_id);


--
-- Name: idx_personnel_actif; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_personnel_actif ON menage.personnel USING btree (hotel_id, actif);


--
-- Name: idx_personnel_hotel; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_personnel_hotel ON menage.personnel USING btree (hotel_id);


--
-- Name: idx_personnel_nom; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_personnel_nom ON menage.personnel USING btree (hotel_id, nom, prenom);


--
-- Name: idx_personnel_numero; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_personnel_numero ON menage.personnel USING btree (hotel_id, numero_employe);


--
-- Name: idx_planning_date; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_planning_date ON menage.planning USING btree (hotel_id, date_travail);


--
-- Name: idx_planning_disponible; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_planning_disponible ON menage.planning USING btree (hotel_id, date_travail, disponible);


--
-- Name: idx_planning_hotel; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_planning_hotel ON menage.planning USING btree (hotel_id);


--
-- Name: idx_planning_personnel; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_planning_personnel ON menage.planning USING btree (personnel_id);


--
-- Name: idx_statuts_code; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_statuts_code ON menage.statuts_taches USING btree (code_statut);


--
-- Name: idx_taches_chambre; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_taches_chambre ON menage.taches USING btree (chambre_id);


--
-- Name: idx_taches_chambre_date; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_taches_chambre_date ON menage.taches USING btree (chambre_id, date_planifiee, statut_id);


--
-- Name: idx_taches_date; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_taches_date ON menage.taches USING btree (hotel_id, date_planifiee);


--
-- Name: idx_taches_hotel; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_taches_hotel ON menage.taches USING btree (hotel_id);


--
-- Name: idx_taches_personnel; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_taches_personnel ON menage.taches USING btree (personnel_id);


--
-- Name: idx_taches_personnel_date; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_taches_personnel_date ON menage.taches USING btree (personnel_id, date_planifiee);


--
-- Name: idx_taches_planning; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_taches_planning ON menage.taches USING btree (hotel_id, date_planifiee, statut_id);


--
-- Name: idx_taches_priorite; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_taches_priorite ON menage.taches USING btree (hotel_id, priorite, date_planifiee);


--
-- Name: idx_taches_statut; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_taches_statut ON menage.taches USING btree (statut_id);


--
-- Name: idx_taches_type; Type: INDEX; Schema: menage; Owner: -
--

CREATE INDEX idx_taches_type ON menage.taches USING btree (hotel_id, type_nettoyage);


--
-- Name: idx_alertes_date; Type: INDEX; Schema: reporting; Owner: -
--

CREATE INDEX idx_alertes_date ON reporting.alertes USING btree (hotel_id, date_creation);


--
-- Name: idx_alertes_hotel; Type: INDEX; Schema: reporting; Owner: -
--

CREATE INDEX idx_alertes_hotel ON reporting.alertes USING btree (hotel_id);


--
-- Name: idx_alertes_niveau; Type: INDEX; Schema: reporting; Owner: -
--

CREATE INDEX idx_alertes_niveau ON reporting.alertes USING btree (hotel_id, niveau);


--
-- Name: idx_alertes_statut; Type: INDEX; Schema: reporting; Owner: -
--

CREATE INDEX idx_alertes_statut ON reporting.alertes USING btree (hotel_id, lue, traitee);


--
-- Name: idx_alertes_type; Type: INDEX; Schema: reporting; Owner: -
--

CREATE INDEX idx_alertes_type ON reporting.alertes USING btree (hotel_id, type_alerte);


--
-- Name: idx_alertes_user; Type: INDEX; Schema: reporting; Owner: -
--

CREATE INDEX idx_alertes_user ON reporting.alertes USING btree (user_id);


--
-- Name: idx_dashboard_ca; Type: INDEX; Schema: reporting; Owner: -
--

CREATE INDEX idx_dashboard_ca ON reporting.dashboard_financier USING btree (hotel_id, ca_total);


--
-- Name: idx_dashboard_date; Type: INDEX; Schema: reporting; Owner: -
--

CREATE INDEX idx_dashboard_date ON reporting.dashboard_financier USING btree (hotel_id, date_rapport);


--
-- Name: idx_dashboard_hotel; Type: INDEX; Schema: reporting; Owner: -
--

CREATE INDEX idx_dashboard_hotel ON reporting.dashboard_financier USING btree (hotel_id);


--
-- Name: idx_dashboard_occupation; Type: INDEX; Schema: reporting; Owner: -
--

CREATE INDEX idx_dashboard_occupation ON reporting.dashboard_financier USING btree (hotel_id, taux_occupation);


--
-- Name: idx_mv_chambres_disponibilite; Type: INDEX; Schema: reporting; Owner: -
--

CREATE UNIQUE INDEX idx_mv_chambres_disponibilite ON reporting.chambres_disponibilite USING btree (chambre_id);


--
-- Name: idx_mv_chambres_hotel; Type: INDEX; Schema: reporting; Owner: -
--

CREATE INDEX idx_mv_chambres_hotel ON reporting.chambres_disponibilite USING btree (hotel_id);


--
-- Name: idx_mv_chambres_statut; Type: INDEX; Schema: reporting; Owner: -
--

CREATE INDEX idx_mv_chambres_statut ON reporting.chambres_disponibilite USING btree (hotel_id, statut);


--
-- Name: idx_mv_stocks_critiques; Type: INDEX; Schema: reporting; Owner: -
--

CREATE UNIQUE INDEX idx_mv_stocks_critiques ON reporting.stocks_critiques USING btree (produit_id);


--
-- Name: idx_mv_stocks_hotel; Type: INDEX; Schema: reporting; Owner: -
--

CREATE INDEX idx_mv_stocks_hotel ON reporting.stocks_critiques USING btree (hotel_id);


--
-- Name: idx_mv_stocks_niveau; Type: INDEX; Schema: reporting; Owner: -
--

CREATE INDEX idx_mv_stocks_niveau ON reporting.stocks_critiques USING btree (hotel_id, niveau_stock);


--
-- Name: idx_articles_categorie; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_articles_categorie ON restaurant.articles_menu USING btree (categorie_id);


--
-- Name: idx_articles_code; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_articles_code ON restaurant.articles_menu USING btree (hotel_id, code_article);


--
-- Name: idx_articles_disponible; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_articles_disponible ON restaurant.articles_menu USING btree (hotel_id, disponible, actif);


--
-- Name: idx_articles_hotel; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_articles_hotel ON restaurant.articles_menu USING btree (hotel_id);


--
-- Name: idx_articles_nom; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_articles_nom ON restaurant.articles_menu USING btree (hotel_id, nom_article);


--
-- Name: idx_articles_prix; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_articles_prix ON restaurant.articles_menu USING btree (hotel_id, prix);


--
-- Name: idx_articles_search; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_articles_search ON restaurant.articles_menu USING btree (hotel_id, nom_article, description);


--
-- Name: idx_categories_actif; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_categories_actif ON restaurant.categories_menu USING btree (hotel_id, actif);


--
-- Name: idx_categories_hotel; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_categories_hotel ON restaurant.categories_menu USING btree (hotel_id);


--
-- Name: idx_categories_ordre; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_categories_ordre ON restaurant.categories_menu USING btree (hotel_id, ordre_affichage);


--
-- Name: idx_commandes_client; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_commandes_client ON restaurant.commandes USING btree (client_id);


--
-- Name: idx_commandes_date; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_commandes_date ON restaurant.commandes USING btree (hotel_id, date_commande);


--
-- Name: idx_commandes_hotel; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_commandes_hotel ON restaurant.commandes USING btree (hotel_id);


--
-- Name: idx_commandes_numero; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_commandes_numero ON restaurant.commandes USING btree (numero_commande);


--
-- Name: idx_commandes_reservation; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_commandes_reservation ON restaurant.commandes USING btree (reservation_id);


--
-- Name: idx_commandes_serveur; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_commandes_serveur ON restaurant.commandes USING btree (serveur_id);


--
-- Name: idx_commandes_statut; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_commandes_statut ON restaurant.commandes USING btree (hotel_id, statut);


--
-- Name: idx_commandes_table; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_commandes_table ON restaurant.commandes USING btree (hotel_id, table_numero, statut);


--
-- Name: idx_lignes_article; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_lignes_article ON restaurant.lignes_commandes USING btree (article_id);


--
-- Name: idx_lignes_commande; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_lignes_commande ON restaurant.lignes_commandes USING btree (commande_id);


--
-- Name: idx_lignes_statut; Type: INDEX; Schema: restaurant; Owner: -
--

CREATE INDEX idx_lignes_statut ON restaurant.lignes_commandes USING btree (statut);


--
-- Name: clients trigger_update_clients; Type: TRIGGER; Schema: clients; Owner: -
--

CREATE TRIGGER trigger_update_clients BEFORE UPDATE ON clients.clients FOR EACH ROW EXECUTE FUNCTION public.update_timestamp();


--
-- Name: societes trigger_update_societes; Type: TRIGGER; Schema: clients; Owner: -
--

CREATE TRIGGER trigger_update_societes BEFORE UPDATE ON clients.societes FOR EACH ROW EXECUTE FUNCTION public.update_timestamp();


--
-- Name: dbusers trigger_update_dbusers; Type: TRIGGER; Schema: core; Owner: -
--

CREATE TRIGGER trigger_update_dbusers BEFORE UPDATE ON core.dbusers FOR EACH ROW EXECUTE FUNCTION public.update_timestamp();


--
-- Name: hotels trigger_update_hotels; Type: TRIGGER; Schema: core; Owner: -
--

CREATE TRIGGER trigger_update_hotels BEFORE UPDATE ON core.hotels FOR EACH ROW EXECUTE FUNCTION public.update_timestamp();


--
-- Name: comptes trigger_update_comptes; Type: TRIGGER; Schema: finance; Owner: -
--

CREATE TRIGGER trigger_update_comptes BEFORE UPDATE ON finance.comptes FOR EACH ROW EXECUTE FUNCTION public.update_timestamp();


--
-- Name: factures trigger_update_factures; Type: TRIGGER; Schema: finance; Owner: -
--

CREATE TRIGGER trigger_update_factures BEFORE UPDATE ON finance.factures FOR EACH ROW EXECUTE FUNCTION public.update_timestamp();


--
-- Name: reservations trigger_update_reservations; Type: TRIGGER; Schema: hebergement; Owner: -
--

CREATE TRIGGER trigger_update_reservations BEFORE UPDATE ON hebergement.reservations FOR EACH ROW EXECUTE FUNCTION public.update_timestamp();


--
-- Name: produits trigger_update_produits; Type: TRIGGER; Schema: inventory; Owner: -
--

CREATE TRIGGER trigger_update_produits BEFORE UPDATE ON inventory.produits FOR EACH ROW EXECUTE FUNCTION public.update_timestamp();


--
-- Name: taches trigger_update_taches; Type: TRIGGER; Schema: menage; Owner: -
--

CREATE TRIGGER trigger_update_taches BEFORE UPDATE ON menage.taches FOR EACH ROW EXECUTE FUNCTION public.update_timestamp();


--
-- Name: clients fk_clients_hotel; Type: FK CONSTRAINT; Schema: clients; Owner: -
--

ALTER TABLE ONLY clients.clients
    ADD CONSTRAINT fk_clients_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: clients fk_clients_nationalite; Type: FK CONSTRAINT; Schema: clients; Owner: -
--

ALTER TABLE ONLY clients.clients
    ADD CONSTRAINT fk_clients_nationalite FOREIGN KEY (nationalite_id) REFERENCES core.donnees_referentielles(ref_id);


--
-- Name: clients fk_clients_societe; Type: FK CONSTRAINT; Schema: clients; Owner: -
--

ALTER TABLE ONLY clients.clients
    ADD CONSTRAINT fk_clients_societe FOREIGN KEY (societe_id) REFERENCES clients.societes(societe_id);


--
-- Name: clients fk_clients_type_id; Type: FK CONSTRAINT; Schema: clients; Owner: -
--

ALTER TABLE ONLY clients.clients
    ADD CONSTRAINT fk_clients_type_id FOREIGN KEY (type_identification_id) REFERENCES core.donnees_referentielles(ref_id);


--
-- Name: societes fk_societes_hotel; Type: FK CONSTRAINT; Schema: clients; Owner: -
--

ALTER TABLE ONLY clients.societes
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
    ADD CONSTRAINT fk_comptes_client FOREIGN KEY (client_id) REFERENCES clients.clients(client_id);


--
-- Name: comptes fk_comptes_fournisseur; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.comptes
    ADD CONSTRAINT fk_comptes_fournisseur FOREIGN KEY (fournisseur_id) REFERENCES inventory.fournisseurs(fournisseur_id);


--
-- Name: comptes fk_comptes_hotel; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.comptes
    ADD CONSTRAINT fk_comptes_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: comptes fk_comptes_societe; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.comptes
    ADD CONSTRAINT fk_comptes_societe FOREIGN KEY (societe_id) REFERENCES clients.societes(societe_id);


--
-- Name: factures fk_factures_client; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.factures
    ADD CONSTRAINT fk_factures_client FOREIGN KEY (client_id) REFERENCES clients.clients(client_id);


--
-- Name: factures fk_factures_compte; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.factures
    ADD CONSTRAINT fk_factures_compte FOREIGN KEY (compte_id) REFERENCES finance.comptes(compte_id);


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
    ADD CONSTRAINT fk_factures_reservation FOREIGN KEY (reservation_id) REFERENCES hebergement.reservations(reservation_id);


--
-- Name: factures fk_factures_societe; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.factures
    ADD CONSTRAINT fk_factures_societe FOREIGN KEY (societe_id) REFERENCES clients.societes(societe_id);


--
-- Name: factures fk_factures_user; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.factures
    ADD CONSTRAINT fk_factures_user FOREIGN KEY (user_id) REFERENCES core.dbusers(user_id);


--
-- Name: lignes_factures fk_lignes_commande; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.lignes_factures
    ADD CONSTRAINT fk_lignes_commande FOREIGN KEY (commande_id) REFERENCES restaurant.commandes(commande_id);


--
-- Name: lignes_factures fk_lignes_facture; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.lignes_factures
    ADD CONSTRAINT fk_lignes_facture FOREIGN KEY (facture_id) REFERENCES finance.factures(facture_id) ON DELETE CASCADE;


--
-- Name: lignes_factures fk_lignes_nuitee; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.lignes_factures
    ADD CONSTRAINT fk_lignes_nuitee FOREIGN KEY (nuitee_id) REFERENCES hebergement.nuitees(nuitee_id);


--
-- Name: lignes_factures fk_lignes_produit; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.lignes_factures
    ADD CONSTRAINT fk_lignes_produit FOREIGN KEY (produit_id) REFERENCES inventory.produits(produit_id);


--
-- Name: lignes_factures fk_lignes_service; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.lignes_factures
    ADD CONSTRAINT fk_lignes_service FOREIGN KEY (service_id) REFERENCES finance.services_facturables(service_id);


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
-- Name: services_facturables fk_services_hotel; Type: FK CONSTRAINT; Schema: finance; Owner: -
--

ALTER TABLE ONLY finance.services_facturables
    ADD CONSTRAINT fk_services_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


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
    ADD CONSTRAINT fk_res_clients_client FOREIGN KEY (client_id) REFERENCES clients.clients(client_id);


--
-- Name: reservations_clients fk_res_clients_reservation; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations_clients
    ADD CONSTRAINT fk_res_clients_reservation FOREIGN KEY (reservation_id) REFERENCES hebergement.reservations(reservation_id) ON DELETE CASCADE;


--
-- Name: reservations fk_reservations_client; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations
    ADD CONSTRAINT fk_reservations_client FOREIGN KEY (client_principal_id) REFERENCES clients.clients(client_id);


--
-- Name: reservations fk_reservations_hotel; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations
    ADD CONSTRAINT fk_reservations_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: reservations fk_reservations_societe; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations
    ADD CONSTRAINT fk_reservations_societe FOREIGN KEY (societe_id) REFERENCES clients.societes(societe_id);


--
-- Name: reservations fk_reservations_user; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.reservations
    ADD CONSTRAINT fk_reservations_user FOREIGN KEY (user_id) REFERENCES core.dbusers(user_id);


--
-- Name: tarifs_chambres fk_tarifs_hotel; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.tarifs_chambres
    ADD CONSTRAINT fk_tarifs_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: tarifs_chambres fk_tarifs_type; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.tarifs_chambres
    ADD CONSTRAINT fk_tarifs_type FOREIGN KEY (type_id) REFERENCES hebergement.types_chambres(type_id);


--
-- Name: types_chambres fk_types_chambres_hotel; Type: FK CONSTRAINT; Schema: hebergement; Owner: -
--

ALTER TABLE ONLY hebergement.types_chambres
    ADD CONSTRAINT fk_types_chambres_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


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
-- Name: categories_produits fk_categories_hotel; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.categories_produits
    ADD CONSTRAINT fk_categories_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: fournisseurs fk_fournisseurs_hotel; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.fournisseurs
    ADD CONSTRAINT fk_fournisseurs_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: lignes_bons_commande fk_lignes_bon_commande; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.lignes_bons_commande
    ADD CONSTRAINT fk_lignes_bon_commande FOREIGN KEY (bon_commande_id) REFERENCES inventory.bons_commande(bon_commande_id) ON DELETE CASCADE;


--
-- Name: lignes_bons_sortie fk_lignes_bon_sortie; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.lignes_bons_sortie
    ADD CONSTRAINT fk_lignes_bon_sortie FOREIGN KEY (bon_sortie_id) REFERENCES inventory.bons_sortie(bon_sortie_id) ON DELETE CASCADE;


--
-- Name: lignes_bons_commande fk_lignes_produit; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.lignes_bons_commande
    ADD CONSTRAINT fk_lignes_produit FOREIGN KEY (produit_id) REFERENCES inventory.produits(produit_id);


--
-- Name: lignes_bons_sortie fk_lignes_sortie_produit; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.lignes_bons_sortie
    ADD CONSTRAINT fk_lignes_sortie_produit FOREIGN KEY (produit_id) REFERENCES inventory.produits(produit_id);


--
-- Name: mouvements_stock fk_mouvements_hotel; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.mouvements_stock
    ADD CONSTRAINT fk_mouvements_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: mouvements_stock fk_mouvements_produit; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.mouvements_stock
    ADD CONSTRAINT fk_mouvements_produit FOREIGN KEY (produit_id) REFERENCES inventory.produits(produit_id);


--
-- Name: mouvements_stock fk_mouvements_user; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.mouvements_stock
    ADD CONSTRAINT fk_mouvements_user FOREIGN KEY (user_id) REFERENCES core.dbusers(user_id);


--
-- Name: produits fk_produits_categorie; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.produits
    ADD CONSTRAINT fk_produits_categorie FOREIGN KEY (categorie_id) REFERENCES inventory.categories_produits(categorie_id);


--
-- Name: produits fk_produits_fournisseur; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.produits
    ADD CONSTRAINT fk_produits_fournisseur FOREIGN KEY (fournisseur_principal_id) REFERENCES inventory.fournisseurs(fournisseur_id);


--
-- Name: produits fk_produits_hotel; Type: FK CONSTRAINT; Schema: inventory; Owner: -
--

ALTER TABLE ONLY inventory.produits
    ADD CONSTRAINT fk_produits_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: historique fk_historique_chambre; Type: FK CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.historique
    ADD CONSTRAINT fk_historique_chambre FOREIGN KEY (chambre_id) REFERENCES hebergement.chambres(chambre_id);


--
-- Name: historique fk_historique_hotel; Type: FK CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.historique
    ADD CONSTRAINT fk_historique_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: historique fk_historique_personnel; Type: FK CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.historique
    ADD CONSTRAINT fk_historique_personnel FOREIGN KEY (personnel_id) REFERENCES menage.personnel(personnel_id);


--
-- Name: historique fk_historique_tache; Type: FK CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.historique
    ADD CONSTRAINT fk_historique_tache FOREIGN KEY (tache_id) REFERENCES menage.taches(tache_id);


--
-- Name: historique fk_historique_user; Type: FK CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.historique
    ADD CONSTRAINT fk_historique_user FOREIGN KEY (user_id) REFERENCES core.dbusers(user_id);


--
-- Name: personnel fk_personnel_hotel; Type: FK CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.personnel
    ADD CONSTRAINT fk_personnel_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: planning fk_planning_hotel; Type: FK CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.planning
    ADD CONSTRAINT fk_planning_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: planning fk_planning_personnel; Type: FK CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.planning
    ADD CONSTRAINT fk_planning_personnel FOREIGN KEY (personnel_id) REFERENCES menage.personnel(personnel_id);


--
-- Name: taches fk_taches_chambre; Type: FK CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.taches
    ADD CONSTRAINT fk_taches_chambre FOREIGN KEY (chambre_id) REFERENCES hebergement.chambres(chambre_id);


--
-- Name: taches fk_taches_hotel; Type: FK CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.taches
    ADD CONSTRAINT fk_taches_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: taches fk_taches_personnel; Type: FK CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.taches
    ADD CONSTRAINT fk_taches_personnel FOREIGN KEY (personnel_id) REFERENCES menage.personnel(personnel_id);


--
-- Name: taches fk_taches_statut; Type: FK CONSTRAINT; Schema: menage; Owner: -
--

ALTER TABLE ONLY menage.taches
    ADD CONSTRAINT fk_taches_statut FOREIGN KEY (statut_id) REFERENCES menage.statuts_taches(statut_id);


--
-- Name: alertes fk_alertes_hotel; Type: FK CONSTRAINT; Schema: reporting; Owner: -
--

ALTER TABLE ONLY reporting.alertes
    ADD CONSTRAINT fk_alertes_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: alertes fk_alertes_user; Type: FK CONSTRAINT; Schema: reporting; Owner: -
--

ALTER TABLE ONLY reporting.alertes
    ADD CONSTRAINT fk_alertes_user FOREIGN KEY (user_id) REFERENCES core.dbusers(user_id);


--
-- Name: dashboard_financier fk_dashboard_hotel; Type: FK CONSTRAINT; Schema: reporting; Owner: -
--

ALTER TABLE ONLY reporting.dashboard_financier
    ADD CONSTRAINT fk_dashboard_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: articles_menu fk_articles_categorie; Type: FK CONSTRAINT; Schema: restaurant; Owner: -
--

ALTER TABLE ONLY restaurant.articles_menu
    ADD CONSTRAINT fk_articles_categorie FOREIGN KEY (categorie_id) REFERENCES restaurant.categories_menu(categorie_id);


--
-- Name: articles_menu fk_articles_hotel; Type: FK CONSTRAINT; Schema: restaurant; Owner: -
--

ALTER TABLE ONLY restaurant.articles_menu
    ADD CONSTRAINT fk_articles_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: categories_menu fk_categories_hotel; Type: FK CONSTRAINT; Schema: restaurant; Owner: -
--

ALTER TABLE ONLY restaurant.categories_menu
    ADD CONSTRAINT fk_categories_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: commandes fk_commandes_client; Type: FK CONSTRAINT; Schema: restaurant; Owner: -
--

ALTER TABLE ONLY restaurant.commandes
    ADD CONSTRAINT fk_commandes_client FOREIGN KEY (client_id) REFERENCES clients.clients(client_id);


--
-- Name: commandes fk_commandes_hotel; Type: FK CONSTRAINT; Schema: restaurant; Owner: -
--

ALTER TABLE ONLY restaurant.commandes
    ADD CONSTRAINT fk_commandes_hotel FOREIGN KEY (hotel_id) REFERENCES core.hotels(hotel_id);


--
-- Name: commandes fk_commandes_reservation; Type: FK CONSTRAINT; Schema: restaurant; Owner: -
--

ALTER TABLE ONLY restaurant.commandes
    ADD CONSTRAINT fk_commandes_reservation FOREIGN KEY (reservation_id) REFERENCES hebergement.reservations(reservation_id);


--
-- Name: commandes fk_commandes_serveur; Type: FK CONSTRAINT; Schema: restaurant; Owner: -
--

ALTER TABLE ONLY restaurant.commandes
    ADD CONSTRAINT fk_commandes_serveur FOREIGN KEY (serveur_id) REFERENCES core.dbusers(user_id);


--
-- Name: lignes_commandes fk_lignes_article; Type: FK CONSTRAINT; Schema: restaurant; Owner: -
--

ALTER TABLE ONLY restaurant.lignes_commandes
    ADD CONSTRAINT fk_lignes_article FOREIGN KEY (article_id) REFERENCES restaurant.articles_menu(article_id);


--
-- Name: lignes_commandes fk_lignes_commande; Type: FK CONSTRAINT; Schema: restaurant; Owner: -
--

ALTER TABLE ONLY restaurant.lignes_commandes
    ADD CONSTRAINT fk_lignes_commande FOREIGN KEY (commande_id) REFERENCES restaurant.commandes(commande_id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict G6Fx5AOfMkguWPYgC328WbKs0Bv9Dq3zoufaGNEW4YR5TWq2XwFgBFXYDYRXHKf

