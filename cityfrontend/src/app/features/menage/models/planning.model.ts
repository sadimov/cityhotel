/**
 * Planning — entité du module ménage (planning du personnel).
 *
 * Source de vérité (Tour 28, from-scratch — cf. CARTOGRAPHIE_MODULES.md) :
 * dérivée du backend mono-source `MENAGE/entities_dto_services_backend-menage.java`
 * (Planning.java l. 410-515 + PlanningDto.java l. 968-1045).
 *
 * Multi-tenant : `hotelId` lu via JWT côté serveur (jamais envoyé par le
 * client, cf. CLAUDE.md racine §6.1).
 *
 * Champs dérivés/dénormalisés exposés par le backend en lecture seule :
 *  - `nomPersonnel`, `numeroEmploye`
 *  - `dureeTravailMinutes` (calculé heureFin − heureDebut)
 *  - `nombreTachesPlanifiees` (compteur cross-table)
 *
 * NOTE — divergence brief vs spec backend :
 * Le brief Tour 28 mentionne « date | personnel | etage | secteur | statut »
 * comme colonnes du tableau. Or la spec backend Planning expose
 * `personnelId / dateTravail / heureDebut / heureFin / disponible /
 *  commentaires` — pas d'`etage` ni `secteur` (ces notions appartiennent
 * potentiellement à `Tache` ou à un futur découpage par zone). On
 * matérialise donc le tableau sur les champs réellement disponibles
 * (date / personnel / horaire / disponibilité / commentaires) et on
 * laisse étage/secteur en TODO côté backend (à arbitrer dans une
 * prochaine spec).
 */
export interface Planning {
  planningId?: number;
  hotelId?: number;
  personnelId: number;
  /** Format ISO `YYYY-MM-DD`. */
  dateTravail: string;
  /** Format `HH:mm` ou `HH:mm:ss`. */
  heureDebut: string;
  /** Format `HH:mm` ou `HH:mm:ss`. */
  heureFin: string;
  disponible?: boolean;
  commentaires?: string;
  // Champs dénormalisés (lecture seule)
  nomPersonnel?: string;
  numeroEmploye?: string;
  dureeTravailMinutes?: number;
  nombreTachesPlanifiees?: number;
}

/**
 * Filtres optionnels pour la liste paginée des plannings.
 *
 * `disponible` distingue les créneaux disponibles des indispos (congés,
 * maladie, ...). Le brief mentionne aussi un filtre par étage (non
 * supporté backend pour l'instant) et par statut (assimilé ici à
 * `disponible` côté client tant que le découpage n'est pas formalisé).
 */
export interface FiltresPlanning {
  search?: string;
  /** Format ISO `YYYY-MM-DD`. */
  date?: string;
  personnelId?: number;
  disponible?: boolean;
}

/** DTO de création — `hotelId` jamais envoyé. */
export type PlanningCreate = Omit<Planning, 'planningId' | 'hotelId' | 'nomPersonnel' | 'numeroEmploye' | 'dureeTravailMinutes' | 'nombreTachesPlanifiees'>;

/** DTO de mise à jour — mêmes champs que la création. */
export type PlanningUpdate = PlanningCreate;
