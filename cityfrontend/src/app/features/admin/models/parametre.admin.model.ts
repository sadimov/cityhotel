/**
 * Parametre — modèle administratif (clé/valeur par catégorie).
 *
 * Source de vérité : table `core.parametres` côté backend. Les
 * paramètres globaux (TVA, devise, durée de session, format de
 * numérotation, etc.) sont édités par SUPERADMIN.
 *
 * `modifiable` est un drapeau serveur :
 *  - `false` : paramètre verrouillé (ex. seed de schéma, identité
 *    d'application), le formulaire est désactivé côté UI et
 *    l'endpoint backend retourne `400` si on tente quand même.
 *  - `true`  : paramètre éditable.
 *
 * `categorie` regroupe les paramètres dans la liste (group-by côté UI).
 */
export interface Parametre {
  parametreId?: number;
  cle: string;
  valeur: string;
  description?: string;
  categorie?: string;
  /** `STRING`, `NUMBER`, `BOOLEAN`, `JSON` (purement informatif côté UI). */
  type?: string;
  modifiable?: boolean;
  /** Format ISO `YYYY-MM-DDTHH:mm:ss`. */
  dateCreation?: string;
  /** Format ISO `YYYY-MM-DDTHH:mm:ss`. */
  dateModification?: string;
}

/**
 * Filtres pour la liste paginée des paramètres.
 *
 *  - `categorie` : filtre exact par catégorie.
 *  - `modifiable` : `true` = paramètres éditables uniquement,
 *    `false` = verrouillés uniquement, `undefined` = tous.
 */
export interface FiltresParametres {
  search?: string;
  categorie?: string;
  modifiable?: boolean;
}
