/**
 * Tache — entité du module ménage (cœur du flux quotidien).
 *
 * Source de vérité (Tour 27, from-scratch — cf. CARTOGRAPHIE_MODULES.md) :
 * dérivée du backend mono-source `MENAGE/entities_dto_services_backend-menage.java`
 * (Tache.java, lignes 184-389 + enum TypeNettoyage 391-408).
 *
 * Multi-tenant : `hotelId` lu via JWT côté serveur.
 *
 * Champs dérivés/dénormalisés exposés par le backend en lecture seule :
 *  - `numeroChambre`, `nomPersonnel`, `libelleStatut`, `libellePriorite`
 *  - `dureeMinutes` est calculé en base (`heureFinReelle - heureDebutReelle`)
 *  - `enRetard` : booléen calculé à la volée
 *
 * Workflow (cf. `endpoints_module_menage.txt` §Tâches.Workflow) :
 *  1. POST `/menage/taches`            → tâche créée (statut = PLANIFIEE)
 *  2. PUT  `/menage/taches/{id}/assigner`  → personnel assigné
 *  3. PUT  `/menage/taches/{id}/commencer` → heureDebutReelle = now()
 *  4. PUT  `/menage/taches/{id}/terminer`  → heureFinReelle  = now()
 *                                            + commentaires/problèmes/matériel/note
 */

/** Type de nettoyage — aligné sur l'enum `TypeNettoyage` côté backend. */
export type TypeNettoyage = 'QUOTIDIEN' | 'GRAND_MENAGE' | 'MAINTENANCE';

export const TYPES_NETTOYAGE: ReadonlyArray<TypeNettoyage> = [
  'QUOTIDIEN',
  'GRAND_MENAGE',
  'MAINTENANCE',
];

/**
 * Niveaux de priorité — aligné sur la contrainte `@Min(1)/@Max(3)`
 * côté backend (cf. Tache.java l. 227-230 et getLibellePriorite() l. 369-376).
 *  - 1 : Normale
 *  - 2 : Urgente
 *  - 3 : Critique
 */
export type PrioriteTache = 1 | 2 | 3;

export const PRIORITES_TACHE: ReadonlyArray<PrioriteTache> = [1, 2, 3];

/**
 * Codes de statut connus côté backend (table `menage.statuts_taches`).
 * Les libellés réels viennent de la table — on ne fige pas la liste
 * côté front, juste les codes utilisés pour les filtres rapides.
 */
export type CodeStatutTache =
  | 'PLANIFIEE'
  | 'ASSIGNEE'
  | 'EN_COURS'
  | 'TERMINEE'
  | 'ANNULEE';

export interface Tache {
  tacheId?: number;
  hotelId?: number;
  chambreId: number;
  personnelId?: number;
  statutId?: number;
  typeNettoyage?: TypeNettoyage;
  priorite?: PrioriteTache;
  /** Format ISO `YYYY-MM-DD`. */
  datePlanifiee: string;
  /** Format `HH:mm` ou `HH:mm:ss`. */
  heureDebutPrevue?: string;
  /** Format `HH:mm` ou `HH:mm:ss`. */
  heureFinPrevue?: string;
  /** Format ISO `YYYY-MM-DDTHH:mm:ss`. */
  heureDebutReelle?: string;
  /** Format ISO `YYYY-MM-DDTHH:mm:ss`. */
  heureFinReelle?: string;
  /** Calculé en base, lecture seule. */
  dureeMinutes?: number;
  commentaires?: string;
  problemesDetectes?: string;
  /** JSON string : ex. `'["aspirateur","detergent"]'`. */
  materielUtilise?: string;
  dateCreation?: string;
  dateModification?: string;
  // Champs dénormalisés (lecture seule)
  numeroChambre?: string;
  nomPersonnel?: string;
  libelleStatut?: string;
  codeStatut?: CodeStatutTache | string;
  libellePriorite?: string;
  enRetard?: boolean;
  enCours?: boolean;
  terminee?: boolean;
}

/**
 * Filtres optionnels pour la liste paginée des tâches.
 *
 * Les endpoints dédiés (`/aujourd-hui`, `/en-cours`, `/en-retard`,
 * `/non-assignees`) sont des raccourcis serveur ; côté liste classique
 * on combine les filtres ici.
 */
export interface FiltresTaches {
  search?: string;
  date?: string; // YYYY-MM-DD
  personnelId?: number;
  chambreId?: number;
  statutId?: number;
  typeNettoyage?: TypeNettoyage;
  priorite?: PrioriteTache;
  /** Raccourci côté UI : si true, on appelle `/en-cours`. */
  enCours?: boolean;
  /** Raccourci côté UI : si true, on appelle `/en-retard`. */
  enRetard?: boolean;
  /** Raccourci côté UI : si true, on appelle `/non-assignees`. */
  nonAssignees?: boolean;
}

/**
 * DTO pour `PUT /menage/taches/{id}/assigner` (cf. AssignerTacheRequest.java).
 */
export interface AssignerTacheRequest {
  personnelId: number;
  commentaire?: string;
}

/**
 * DTO pour `PUT /menage/taches/{id}/terminer` (cf. TerminerTacheRequest.java).
 *
 * `noteQualite` est borné [1..5] côté backend (`@Min(1) @Max(5)`).
 */
export interface TerminerTacheRequest {
  commentaires?: string;
  problemesDetectes?: string;
  materielUtilise?: string[];
  noteQualite?: number;
}
