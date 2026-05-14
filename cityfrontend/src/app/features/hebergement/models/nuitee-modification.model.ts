/**
 * Modèles "Modification de nuitées" — feature `hebergement`.
 *
 * Réponse de `GET /api/hebergement/nuitees/reservation/{reservationId}/provisoires`
 * et payload de `PATCH /api/hebergement/nuitees/montants`.
 *
 * Spec backend Phase A — Tour 45 (2026-05-11). Permet d'ajuster le montant
 * d'une nuitée tant que sa ligne de facture associée n'est pas verrouillée
 * (facture émise / payée).
 */

/** Mirror partiel de `StatutNuitee` côté backend (cf. `nuitee.model.ts`). */
export type StatutNuiteeCode = 'PREVUE' | 'CONSOMMEE' | 'FACTUREE';

/**
 * Statut backend (1 caractère) de la ligne de facture associée. `C` = closed
 * (ligne verrouillée, modification interdite) ; `O` = open (modifiable).
 */
export type StatutLigneFactureCode = 'O' | 'C';

/**
 * DTO renvoyé par le backend pour chaque nuitée d'une réservation, avec les
 * références aux entités finance liées (ligne de facture, opération de compte
 * auxiliaire client).
 */
export interface NuiteeModificationDto {
  nuiteeId: number;
  /** ISO `yyyy-MM-dd`. */
  dateNuit: string;
  /** Tarif catalogue de la nuit (lecture seule, sert de référence visuelle). */
  prixOriginal: number;
  /**
   * Montant actuellement enregistré sur la ligne de facture correspondante —
   * c'est ce champ que l'utilisateur peut modifier.
   */
  montantLigneFacture: number;
  /** Identifiant de la ligne de facture (réutilisé dans le payload PATCH). */
  ligneFactureId: number;
  /** Identifiant de l'opération de compte client (réutilisé dans le PATCH). */
  operationCompteId: number;
  statutNuitee: StatutNuiteeCode;
  /** `O` = modifiable ; `C` = verrouillée. */
  statutLigne: StatutLigneFactureCode;
}

/**
 * Entrée du payload `PATCH /api/hebergement/nuitees/montants` — une entrée
 * par nuitée modifiée. Le backend met à jour atomicquement la ligne facture
 * et l'opération compte associées.
 */
export interface NuiteeMontantUpdate {
  nuiteeId: number;
  nouveauMontant: number;
  ligneFactureId: number;
  operationCompteId: number;
}

/**
 * Réponse du PATCH — résumé du bulk update (nombre de lignes mises à jour,
 * impact total signed = somme des (nouveau - ancien)).
 */
export interface NuiteeMontantsUpdateResultat {
  updatedCount: number;
  totalImpact: number;
}
