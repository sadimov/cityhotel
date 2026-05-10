/**
 * User — modèle administratif (vue SUPERADMIN cross-hotel).
 *
 * Source de vérité : table `core.DBUsers` côté backend (cf. CLAUDE.md
 * racine §5 — table = `DBUsers`, jamais `users`).
 *
 * Le SUPERADMIN gère les utilisateurs de tous les hôtels ; le `hotelId`
 * est explicite dans l'URL des endpoints `/hotels/{hotelId}/users` —
 * seule exception au principe « le client n'envoie jamais de hotelId »
 * (cf. CLAUDE.md racine §6.1). Ce hotelId-là provient d'une route
 * SUPERADMIN-only, pas d'un payload utilisateur arbitraire.
 *
 * `username` et `password` sont immutables / non re-éditables après
 * création (un reset password se fait via un endpoint dédié qui
 * retourne le nouveau mot de passe en clair affiché par SweetAlert2).
 */
export interface User {
  userId?: number;
  hotelId?: number;
  /** Code de l'hôtel d'appartenance (lecture seule, dérivé serveur). */
  hotelCode?: string;
  /** Nom de l'hôtel d'appartenance (lecture seule, dérivé serveur). */
  hotelNom?: string;
  username: string;
  email: string;
  prenom: string;
  nom: string;
  /** Champ dérivé serveur (lecture seule). */
  nomComplet?: string;
  telephone?: string;
  roleId?: number;
  /** Code du rôle (`SUPERADMIN`, `ADMIN`, `GERANT`, ...). */
  roleCode?: string;
  /** Libellé localisé du rôle (lecture seule, dérivé serveur). */
  roleNom?: string;
  /** Format ISO `YYYY-MM-DDTHH:mm:ss`. */
  derniereConnexion?: string;
  /** Format ISO `YYYY-MM-DDTHH:mm:ss`. */
  dateCreation?: string;
  actif?: boolean;
  verrouille?: boolean;
  /**
   * Mot de passe brut — utilisé UNIQUEMENT en création (POST). Jamais
   * renvoyé par le serveur. Toujours `undefined` côté GET.
   */
  password?: string;
}

/**
 * Filtres pour la liste cross-hotel des utilisateurs.
 *
 * - `hotelId` : si fourni, restreint la liste à un hôtel donné. Pré-
 *   alimenté quand on arrive depuis l'écran hôtels via `?hotelId=`.
 * - `roleCode` : restreint à un rôle (`SUPERADMIN`, `ADMIN`, ...).
 * - `actif` : `true` = actifs uniquement, `false` = désactivés
 *   uniquement, `undefined` = tous.
 */
export interface FiltresUsers {
  search?: string;
  hotelId?: number;
  roleCode?: string;
  actif?: boolean;
}

/**
 * Réponse de l'endpoint `POST /reset-password` — mot de passe brut
 * généré par le serveur, à afficher une seule fois côté UI puis
 * jeté à la fermeture du modal.
 */
export interface ResetPasswordResponse {
  username: string;
  newPassword: string;
}
