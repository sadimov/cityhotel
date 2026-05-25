/**
 * Modèles Tarification — feature `hebergement`.
 *
 * Trois interfaces :
 *  1. `TarifChambre`        — DTO de sortie (lecture).
 *  2. `TarifChambreCreate`  — DTO d'entrée (création / mise à jour).
 *  3. `MontantCalculDto`    — réponse de l'endpoint de calcul de séjour.
 *
 * Source backend (Tour 44 Phase 1) :
 *  - `dto/hebergement/TarifChambreDto.java`
 *  - `dto/hebergement/TarifChambreCreateDto.java`
 *  - `dto/hebergement/MontantCalculDto.java` + `MontantCalculDetailDto.java`
 *
 * Tour 44 Phase 2 (2026-05-11) : consommation calendrier — proposition de prix
 * réelle lors de la création de réservation (remplace l'estimation
 * `TypeChambre.prixBase × nbNuits`).
 *
 * Tour CRUD tarifs (2026-05-25) : ajout des modèles CRUD complet (front).
 *
 * NB : pas de TVA en palier 1 — `montantTtc === montantHt` côté backend tant
 * que la TVA n'est pas activée.
 *
 * ⚠️ Le champ `prixWeekend` existe côté backend mais N'EST PAS exposé dans le
 * formulaire UI (consigne user 2026-05-25 : majoration weekend désactivée, un
 * seul prix de nuit). Le front omet la clé dans le payload create/update.
 */

/**
 * Origine du prix retourné pour une nuit :
 *  - `tarif`     : tarif saisonnier explicite (TarifChambre actif sur la date)
 *  - `fallback`  : `TypeChambre.prixBase` (aucun tarif saisonnier applicable)
 */
export type MontantCalculOrigine = 'tarif' | 'fallback';

export interface MontantCalculDetailDto {
  /** ISO `yyyy-MM-dd`. */
  date: string;
  /** Prix unitaire de la nuit (MRU). */
  prix: number;
  origine: MontantCalculOrigine;
}

export interface MontantCalculDto {
  typeChambreId: number;
  totalNuits: number;
  montantHt: number;
  montantTtc: number;
  detail: MontantCalculDetailDto[];
}

/**
 * Tarif saisonnier renvoyé en lecture (record backend `TarifChambreDto`).
 *
 * `prixWeekend` reste typé pour rester aligné sur le contrat serveur mais
 * n'est pas exposé dans le formulaire (cf. consigne user 2026-05-25).
 */
export interface TarifChambre {
  tarifId?: number;
  typeId: number;
  nomTarif: string;
  /** ISO `yyyy-MM-dd`. */
  dateDebut: string;
  /** ISO `yyyy-MM-dd` ou `null`/absent = "jusqu'à nouvel ordre". */
  dateFin?: string | null;
  /** MRU. */
  prixNuit: number;
  /** MRU — désactivé côté UI (consigne 2026-05-25). */
  prixWeekend?: number | null;
  priorite?: number;
  actif?: boolean;
}

/**
 * Payload de création / mise à jour. `hotelId` jamais transmis — backend
 * l'extrait du JWT (CLAUDE.md racine §6.1).
 *
 * `prixWeekend` est volontairement omis : le frontend n'envoie pas la clé.
 */
export interface TarifChambreCreate {
  typeId: number;
  nomTarif: string;
  dateDebut: string;
  dateFin?: string | null;
  prixNuit: number;
  priorite?: number;
  actif?: boolean;
}
