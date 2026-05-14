/**
 * Personnel — entité du module ménage.
 *
 * Source de vérité (Tour 27, from-scratch — cf. CARTOGRAPHIE_MODULES.md) :
 * dérivée du backend mono-source `MENAGE/entities_dto_services_backend-menage.java`
 * (Personnel.java, lignes 12-112).
 *
 * Multi-tenant : `hotelId` lu via JWT côté serveur — jamais envoyé par le
 * client (cf. CLAUDE.md racine §6.1).
 *
 * `specialites` est stocké en JSON string côté backend (liste sérialisée
 * de codes : "chambres" | "salles" | "exterieur" | "buanderie" ...).
 * Le composant frontend gère la sérialisation `["chambres","salles"]`
 * <-> saisie texte « chambres, salles » (même approche qu'`allergenes`
 * dans `restaurant/article-form`).
 *
 * <b>Sous-tour menage D1 (fix alignement backend) :</b>
 *  - Retrait de `hotelId` (violation §6.1 : jamais exposé au client).
 *  - `dateCreation` renommée en `createdAt` + ajout `updatedAt` (alignement
 *    strict avec `PersonnelDto` côté backend qui expose les deux Instant
 *    de `AuditableEntity`).
 */
export interface Personnel {
  personnelId?: number;
  numeroEmploye: string;
  prenom: string;
  nom: string;
  telephone?: string;
  email?: string;
  /** Format ISO `YYYY-MM-DD`. */
  dateEmbauche?: string;
  /** JSON string : ex. `'["chambres","salles"]'`. */
  specialites?: string;
  actif?: boolean;
  /** Champ dérivé renvoyé par le backend (lecture seule). */
  nomComplet?: string;
  /** Format ISO `YYYY-MM-DDTHH:mm:ss` (Instant côté backend). */
  createdAt?: string;
  /** Format ISO `YYYY-MM-DDTHH:mm:ss` (Instant côté backend). */
  updatedAt?: string;
}

/**
 * Filtres optionnels pour la liste paginée du personnel.
 *
 * `actif` distingue le personnel désactivé du personnel courant.
 * `specialite` filtre par code de spécialité (côté backend, le filtre
 * fait un LIKE/JSON CONTAINS sur le champ `specialites`).
 */
export interface FiltresPersonnels {
  search?: string;
  actif?: boolean;
  specialite?: string;
}

/**
 * Codes de spécialités connus côté backend (cf. `endpoints_module_menage.txt`
 * et exemples dans `entities_dto_services_backend-menage.java`).
 *
 * Source de vérité côté serveur : table `menage.personnel.specialites`
 * (JSON). Le frontend ne valide pas la liste fermée — l'utilisateur
 * peut saisir d'autres codes ; ces enums servent surtout aux filtres
 * et aux libellés i18n.
 */
export type SpecialitePersonnel =
  | 'chambres'
  | 'salles'
  | 'exterieur'
  | 'buanderie'
  | 'lingerie'
  | 'maintenance';

export const SPECIALITES_PERSONNEL: ReadonlyArray<SpecialitePersonnel> = [
  'chambres',
  'salles',
  'exterieur',
  'buanderie',
  'lingerie',
  'maintenance',
];
