/**
 * Hotel — modèle administratif (vue SUPERADMIN).
 *
 * Source de vérité : `core.hotels` côté backend. Le SUPERADMIN voit
 * tous les hôtels du SaaS ; pour les autres rôles, le hotelId est
 * lu du JWT et l'écran admin n'est pas accessible (cf. SuperAdminGuard).
 *
 * `code` est immutable après création (clé business utilisée dans le
 * JWT et les imports CSV historiques). Le formulaire le passe en
 * lecture seule en mode édition.
 */
export interface Hotel {
  hotelId?: number;
  code: string;
  nom: string;
  adresse?: string;
  ville?: string;
  pays?: string;
  telephone?: string;
  email?: string;
  siteWeb?: string;
  /** Format ISO `YYYY-MM-DDTHH:mm:ss`. */
  dateCreation?: string;
  actif?: boolean;
}

/**
 * Filtres optionnels pour la liste paginée des hôtels.
 *
 * `actif` distingue les hôtels désactivés des hôtels courants ; le
 * backend supporte `actif=true|false` ou rien (= tous).
 */
export interface FiltresHotels {
  search?: string;
  actif?: boolean;
}
