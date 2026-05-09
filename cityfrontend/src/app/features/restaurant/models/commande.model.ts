/**
 * Commande restaurant — modèles partagés POS / cuisine.
 *
 * Source de référence (NON copiée mot-à-mot, reconstruction selon conventions
 * cityfrontend) :
 *  - `RESTAURANT/resultat_chatgpt/POS_avancé_adapté/pos.models.ts`
 *  - `PROMPTS/prompt_restaurant_pos.txt` (spec métier)
 *
 * Multi-tenant : `hotelId` n'est JAMAIS envoyé par le client (CLAUDE.md §6.1).
 *
 * Convention :
 *  - Les énumérations sont `enum string` pour matcher 1:1 le backend Java
 *    (`@JsonValue` côté Spring) — cohérence avec `StatutReservation`,
 *    `ModePaiement`, `StatutPaiement` (modules hebergement / finance).
 *  - Les `id` (commandeId, ligneId) sont optionnels en TS pour distinguer
 *    les payloads d'entrée (POST) des objets retournés.
 */

/**
 * Statut workflow d'une commande POS.
 *
 *  - `BROUILLON` : panier en cours d'édition côté POS, non envoyé en cuisine.
 *  - `VALIDEE`   : commande envoyée en cuisine (ticket cuisine imprimé).
 *  - `EN_PREPARATION` : la cuisine a accusé réception.
 *  - `PRETE`     : prête à servir.
 *  - `SERVIE`    : remise au client.
 *  - `ENCAISSEE` : facturée + payée comptant.
 *  - `REPORTEE_CHAMBRE` : portée au folio d'une réservation (facturation
 *    différée).
 *  - `ANNULEE`   : commande annulée (avant ou après envoi cuisine).
 */
export enum StatutCommande {
  BROUILLON = 'BROUILLON',
  VALIDEE = 'VALIDEE',
  EN_PREPARATION = 'EN_PREPARATION',
  PRETE = 'PRETE',
  SERVIE = 'SERVIE',
  ENCAISSEE = 'ENCAISSEE',
  REPORTEE_CHAMBRE = 'REPORTEE_CHAMBRE',
  ANNULEE = 'ANNULEE',
}

/**
 * Mode de règlement d'une commande POS.
 *
 *  - `COMPTANT` : encaissement immédiat (espèces, Bankily, carte, ...). Le
 *    mode de paiement précis est porté par `ModePaiement` (module finance)
 *    transmis dans le payload `EncaissementCommandeRequest`.
 *  - `REPORTE_CHAMBRE` : porté au folio d'une réservation. Implique une
 *    réservation sélectionnée.
 */
export enum ModeReglement {
  COMPTANT = 'COMPTANT',
  REPORTE_CHAMBRE = 'REPORTE_CHAMBRE',
}

/**
 * Une ligne de commande / panier POS.
 *
 * `cartLineId` est un identifiant local (UUID/incrément côté store) utilisé
 * uniquement par le `PosStore` pour identifier la ligne sans dépendre du
 * `articleId` (qui peut être dupliqué si on ajoute deux fois le même plat
 * avec des notes différentes — cas futur). Il n'est JAMAIS envoyé au backend.
 *
 * `articleId` est l'identifiant du plat catalogue (`ArticleMenu`).
 * `ligneId` est l'identifiant côté backend (rempli après POST).
 */
export interface LigneCommande {
  /** ID local store — non envoyé au back. */
  cartLineId: string;
  /** ID backend, rempli après POST. */
  ligneId?: number;
  articleId: number;
  /** Libellé snapshot (au cas où l'article serait renommé après commande). */
  libelle: string;
  quantite: number;
  prixUnitaire: number;
  /** Sous-total = `quantite * prixUnitaire`. Calculé localement. */
  sousTotal: number;
  /** Note de cuisine optionnelle (ex. "sans oignon"). */
  notes?: string;
}

/**
 * Commande POS — DTO retourné par le backend après POST.
 */
export interface Commande {
  commandeId?: number;
  /** ⚠️ Lecture uniquement. */
  hotelId?: number;
  numeroCommande?: string;
  statut?: StatutCommande;
  modeReglement?: ModeReglement;
  /** Client identifié (peut être null pour walk-in anonyme — version future). */
  clientId?: number;
  nomClientResolu?: string;
  /** Renseigné si `modeReglement === REPORTE_CHAMBRE`. */
  reservationId?: number;
  numeroReservation?: string;
  /** Renseigné si la commande a généré une facture. */
  factureId?: number;
  numeroFacture?: string;
  lignes: LigneCommande[];
  /** Total = somme des `sousTotal`. */
  montantTotal: number;
  commentaires?: string;
  /** ⚠️ Lecture uniquement (extrait du JWT côté serveur). */
  userId?: number;
  nomUtilisateur?: string;
  dateCreation?: string;
  dateModification?: string;
}

/**
 * Payload POST `/api/restaurant/commandes` — création d'une commande.
 *
 * `hotelId` / `userId` non transmis (JWT serveur).
 */
export interface CreerCommandeRequest {
  clientId?: number;
  reservationId?: number;
  modeReglement: ModeReglement;
  lignes: CreerLigneCommandeRequest[];
  commentaires?: string;
}

export interface CreerLigneCommandeRequest {
  articleId: number;
  quantite: number;
  prixUnitaire: number;
  notes?: string;
}

/**
 * Payload POST `/api/restaurant/commandes/{id}/encaisser-comptant` —
 * encaissement comptant d'une commande déjà créée (BROUILLON ou VALIDEE).
 *
 * Le serveur crée la facture, l'émet, encaisse le paiement avec le mode
 * fourni, retourne la commande mise à jour (statut ENCAISSEE).
 *
 * `modePaiement` est l'enum `ModePaiement` du module finance (ESPECES,
 * BANKILY, CARTE_BANCAIRE, ...). On évite l'import circulaire en typant
 * en `string` — le composant fournit la constante `ModePaiement.X`.
 */
export interface EncaissementCommandeRequest {
  modePaiement: string;
  montantPaye: number;
  referencePaiement?: string;
  commentaires?: string;
}

/**
 * Payload POST `/api/restaurant/commandes/{id}/reporter-chambre` — porte la
 * commande au folio de la réservation. La réservation doit appartenir au
 * même hôtel (vérifié serveur).
 */
export interface ReportChambreRequest {
  reservationId: number;
  commentaires?: string;
}

/**
 * Filtres optionnels pour la liste paginée des commandes (cuisine, suivi).
 */
export interface FiltresCommandes {
  search?: string;
  statut?: StatutCommande;
  clientId?: number;
  reservationId?: number;
  /** ISO `yyyy-MM-dd`. */
  dateDebut?: string;
  /** ISO `yyyy-MM-dd`. */
  dateFin?: string;
}
