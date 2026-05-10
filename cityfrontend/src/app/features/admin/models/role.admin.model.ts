/**
 * Role — modèle administratif (lecture seule).
 *
 * Source de vérité : table `core.roles` côté backend. Les rôles sont
 * un référentiel maintenu par migration Liquibase ; l'UI admin se
 * contente de les afficher (consultation), pas de les éditer.
 *
 * Codes connus (cf. CLAUDE.md racine §6.3 / `roles_utilisateurs.txt`) :
 *  `SUPERADMIN`, `ADMIN`, `GERANT`, `RECEPTION`, `RESTAURANT`, `RESREC`,
 *  `MAGASIN`, `MENAGE`, `NIGHTAUDIT`.
 */
export interface Role {
  roleId: number;
  code: string;
  nom: string;
  description?: string;
  /**
   * Permissions associées (éventuellement vide selon la stratégie
   * RBAC du backend). Affichées en expand/collapse côté UI.
   */
  permissions?: string[];
  /** Format ISO `YYYY-MM-DDTHH:mm:ss`. */
  dateCreation?: string;
}
