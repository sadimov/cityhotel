/**
 * StatistiquesMenageDto — KPI et compteurs du dashboard ménage.
 *
 * Source de vérité (Tour 27, from-scratch) : dérivée de
 * `MENAGE/entities_dto_services_backend-menage.java` (StatistiquesMenageDto.java
 * lignes 1257-1338).
 *
 * Utilisé par le composant `dashboard/` (vue synthèse texte uniquement
 * pour ce tour — graphiques Chart.js différés).
 */
export interface StatistiquesMenage {
  /** Format ISO `YYYY-MM-DD`. */
  dateReference?: string;

  // Statistiques générales
  nombrePersonnelActif?: number;
  nombreTachesAujourdhui?: number;
  nombreTachesEnCours?: number;
  nombreTachesTerminees?: number;
  nombreTachesEnRetard?: number;

  /** Répartition par code statut (PLANIFIEE, EN_COURS, TERMINEE, ...). */
  repartitionParStatut?: Record<string, number>;

  /** Répartition par type (QUOTIDIEN, GRAND_MENAGE, MAINTENANCE). */
  repartitionParType?: Record<string, number>;

  /** Répartition par niveau de priorité (1, 2, 3). */
  repartitionParPriorite?: Record<string, number>;

  /** Performance — temps de réalisation moyen en minutes. */
  tempsRealisationMoyen?: number;

  /** Pourcentage de tâches réalisées sur prévues. */
  tauxRealisationPourcentage?: number;

  // Alertes
  nombreTachesUrgentes?: number;
  nombreConflitsPlanning?: number;
}

/**
 * Réponse de `GET /menage/dashboard` — agrégat complet exposé par le
 * service `MenageDashboardService` côté backend.
 *
 * On garde une interface souple côté client : la spec backend peut
 * embarquer plusieurs blocs (stats du jour, listes courtes, alertes).
 * On consomme ici uniquement les compteurs nécessaires aux KPI.
 */
export interface DashboardMenage {
  statistiques?: StatistiquesMenage;
  /** Liste courte (top 5) des tâches en retard pour mise en avant. */
  tachesEnRetard?: { tacheId: number; numeroChambre?: string; libelleStatut?: string }[];
  /** Personnels disponibles à la date du jour. */
  personnelsDisponibles?: { personnelId: number; nomComplet?: string }[];
}
