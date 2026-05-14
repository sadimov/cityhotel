/**
 * Modèles "Paiement de lignes facture" — feature `hebergement`.
 *
 * DTO d'entrée / sortie pour les endpoints finance utilisés depuis la modale
 * paiements avancée du calendrier (Tour 45) :
 *  - `POST /api/finance/factures/paiement-lignes`     → encaisser des lignes
 *  - `POST /api/finance/factures/transferer-lignes`   → transférer vers une
 *                                                       autre facture
 *  - `POST /api/hebergement/reservations/{id}/check-out-express`
 *
 * Le module hebergement importe ces interfaces localement (sans re-import
 * depuis `features/finance`) pour rester self-contained et éviter une
 * dépendance circulaire entre modules lazy-loaded.
 */

import { ModePaiement } from './paiements-recap.model';

/**
 * Payload de `POST /api/finance/factures/paiement-lignes`.
 *
 * Sélectionne un ensemble de lignes à encaisser ; le backend :
 *  - ventile proportionnellement si `montant < sum(restes)` ;
 *  - bascule les factures concernées en PAYEE si `montant = sum(restes)` ;
 *  - crédite le compte auxiliaire client si `montant > sum(restes)`.
 */
export interface PaiementLignesRequest {
  /**
   * Facture racine — optionnel (le backend infère depuis `lignesIds` si
   * absent). Quand fourni, sert de vérification de cohérence.
   */
  factureId?: number;
  /** Identifiants `ligne_facture` à encaisser (≥ 1). */
  lignesIds: number[];
  /** Montant TTC à encaisser. */
  montant: number;
  modePaiement: ModePaiement;
  /** Motif libre (optionnel). */
  motif?: string;
  /** Description (libellé paiement). */
  description?: string;
  /** Identifiant client principal de la réservation/facture (audit + crédit). */
  idClient: number;
  /** Identifiant compte client auxiliaire (audit + crédit). */
  idCompteClient: number;
}

/**
 * Payload de `POST /api/finance/factures/transferer-lignes`.
 *
 * Déplace les lignes vers une facture cible (existante ou nouvelle). Si la
 * facture cible n'existe pas encore, le front passe `factureCibleId = -1` et
 * le backend crée une nouvelle facture provisoire pour le client.
 */
export interface TransfererLignesRequest {
  lignesIds: number[];
  /**
   * Facture cible. `-1` ou `null` = "créer une nouvelle facture pour ce
   * client" (convention frontend simplifiée — le backend reconnaît `-1`).
   */
  factureCibleId: number;
}

/**
 * Réponse minimale `POST /api/hebergement/reservations/{id}/check-out-express`.
 * On réutilise `Reservation` pour la mise à jour du calendrier.
 */
export interface CheckOutExpressRequest {
  societeId: number;
  clientId: number;
}

/**
 * Réponse minimale d'un paiement créé par `paiement-lignes`. On ne typifie
 * pas la totalité du DTO côté finance (hors scope hebergement) — l'UI
 * affiche un toast succès et rafraîchit la modale.
 */
export interface PaiementLignesResultat {
  paiementId: number;
  numeroPaiement: string;
  /** Montant total imputé sur les lignes. */
  montantAffecte: number;
  /**
   * Excédent appliqué au compte client (0 si pas d'excédent). Sert à
   * afficher un message dédié dans l'UI.
   */
  excedent: number;
  modePaiement: ModePaiement;
}

/**
 * Réponse minimale du transfert — l'UI rafraîchit la modale après succès.
 */
export interface TransfererLignesResultat {
  factureCibleId: number;
  factureCibleNumero: string;
  lignesTransferees: number;
}

/**
 * Payload de `POST /api/finance/factures/paiement-global` (Tour 46).
 *
 * Encaissement "global" pour une réservation — le backend défalque
 * automatiquement le montant sur les lignes factures restantes (FIFO ou
 * proportionnel selon la doctrine serveur) :
 *  - si `montant < sum(restes)` → ventilation partielle, lignes mises à jour ;
 *  - si `montant = sum(restes)` → toutes les factures basculent en `PAYEE` ;
 *  - si `montant > sum(restes)` → l'excédent est crédité au compte
 *    auxiliaire du client.
 *
 * `idCompteClient` est optionnel (0/null) — le backend l'infère depuis
 * `idClient` quand absent.
 */
export interface PaiementGlobalRequest {
  reservationId: number;
  montant: number;
  modePaiement: ModePaiement;
  motif?: string;
  description?: string;
  idClient: number;
  /** 0 ou null = backend infère depuis `idClient`. */
  idCompteClient?: number;
}

/**
 * Réponse minimale d'un paiement global créé par `paiement-global` (Tour 46).
 *
 * Aligné sur `PaiementLignesResultat` (mêmes champs) pour la compatibilité
 * UI ; le backend peut renvoyer un superset (typé `PaiementDto` côté serveur
 * mais consommé minimal côté front).
 */
export interface PaiementGlobalResultat {
  paiementId: number;
  numeroPaiement: string;
  /** Montant total imputé sur les lignes (peut être inférieur au montant
   *  saisi si excédent crédité au compte). */
  montantAffecte: number;
  /** Excédent appliqué au compte client (0 si pas d'excédent). */
  excedent: number;
  modePaiement: ModePaiement;
}
