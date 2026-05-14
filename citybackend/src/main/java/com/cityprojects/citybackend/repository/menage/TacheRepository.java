package com.cityprojects.citybackend.repository.menage;

import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.entity.menage.Tache;
import com.cityprojects.citybackend.entity.menage.TypeNettoyage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Repository des taches de menage.
 *
 * <p>Filtre tenant {@code WHERE hotel_id = ?} ajoute automatiquement par
 * Hibernate via {@link org.hibernate.annotations.TenantId}.</p>
 */
@Repository
public interface TacheRepository
        extends JpaRepository<Tache, Long>, JpaSpecificationExecutor<Tache> {

    /** Taches d'une date triees par priorite desc puis heure prevue asc. */
    List<Tache> findByDatePlanifieeOrderByPrioriteDescHeureDebutPrevueAsc(LocalDate date);

    /** Taches assignees a un personnel pour une date. */
    List<Tache> findByPersonnelIdAndDatePlanifieeOrderByHeureDebutPrevueAsc(
            Long personnelId, LocalDate date);

    /** Compteur taches d'un personnel a une date donnee. */
    long countByPersonnelIdAndDatePlanifiee(Long personnelId, LocalDate date);

    /** Toutes les taches actuellement EN_COURS. */
    List<Tache> findByStatutOrderByHeureDebutReelleAsc(StatutTache statut);

    /** Toutes les taches EN_COURS d'un personnel donne. */
    List<Tache> findByPersonnelIdAndStatut(Long personnelId, StatutTache statut);

    /**
     * Taches en retard : non terminees ET (date passee OU date du jour avec
     * heure_fin_prevue depassee).
     *
     * <p><b>Fix Tour 30 etape 1</b> : le filtre n'utilisait que {@code datePlanifiee <= :aujourdhui}
     * et ignorait {@code heureFinPrevue}, ce qui faisait remonter en "retard" a 9h
     * du matin une tache du jour planifiee jusqu'a 23h.</p>
     *
     * <p>Une tache du jour sans {@code heureFinPrevue} (NULL) n'est PAS consideree
     * en retard tant qu'on est dans la journee : on ignore le NULL pour ne pas
     * fausser le tableau de bord. Si une tache anterieure a {@code heureFinPrevue}
     * NULL, elle reste en retard via le premier predicat.</p>
     *
     * @param aujourdhui   date courante (Africa/Nouakchott via Clock)
     * @param heureCourante heure courante (LocalTime sur la meme zone)
     */
    @Query("SELECT t FROM Tache t WHERE t.statut IN (com.cityprojects.citybackend.entity.menage.StatutTache.PLANIFIEE, "
            + "com.cityprojects.citybackend.entity.menage.StatutTache.EN_COURS) "
            + "AND ( t.datePlanifiee < :aujourdhui "
            + "      OR (t.datePlanifiee = :aujourdhui AND t.heureFinPrevue IS NOT NULL "
            + "          AND t.heureFinPrevue < :heureCourante) ) "
            + "ORDER BY t.datePlanifiee ASC, t.heureFinPrevue ASC")
    List<Tache> findEnRetard(@Param("aujourdhui") LocalDate aujourdhui,
                             @Param("heureCourante") LocalTime heureCourante);

    /** Taches non encore assignees pour une date. */
    @Query("SELECT t FROM Tache t WHERE t.personnelId IS NULL "
            + "AND t.datePlanifiee = :date "
            + "ORDER BY t.priorite DESC, t.heureDebutPrevue ASC")
    List<Tache> findNonAssignees(@Param("date") LocalDate date);

    /**
     * Verifie qu'une tache (chambre + date + type) avec un statut autre que
     * celui fourni existe deja - utilise par
     * {@link com.cityprojects.citybackend.service.menage.MenagePlanningService#creerTacheCheckOutSiAbsente}
     * pour garantir l'idempotence du Workflow A (generation post check-out).
     *
     * <p>On exclut typiquement {@code ANNULEE} : si une tache QUOTIDIEN du
     * jour pour la chambre est ANNULEE, on autorise une nouvelle generation
     * (le check-out re-emet un besoin metier).</p>
     */
    boolean existsByChambreIdAndDatePlanifieeAndTypeNettoyageAndStatutNot(
            Long chambreId, LocalDate datePlanifiee, TypeNettoyage typeNettoyage, StatutTache statutExclu);

    /** Recherche textuelle (commentaires, problemes detectes). */
    @Query("SELECT t FROM Tache t WHERE "
            + "LOWER(COALESCE(t.commentaires, '')) LIKE LOWER(CONCAT('%', :terme, '%')) "
            + "OR LOWER(COALESCE(t.problemesDetectes, '')) LIKE LOWER(CONCAT('%', :terme, '%')) "
            + "ORDER BY t.datePlanifiee DESC")
    Page<Tache> rechercher(@Param("terme") String terme, Pageable pageable);

    // ============================================================================
    // Tour 41 — Reporting P2 : R-MEN-001 / R-MEN-002.
    // Filtre tenant ajoute automatiquement par Hibernate via @TenantId.
    // ============================================================================

    /**
     * Toutes les taches dont {@code datePlanifiee} est dans [from, to).
     * Utilise pour groupage cote service.
     */
    @Query("SELECT t FROM Tache t "
            + "WHERE t.datePlanifiee >= :from AND t.datePlanifiee < :to "
            + "ORDER BY t.datePlanifiee ASC")
    List<Tache> findOnRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ─────────────────────────────────────────────────────────────────
    // Sous-tour menage C : agregats pour Dashboard / Statistiques / KPI
    // ─────────────────────────────────────────────────────────────────

    /** Nombre de taches pour une date donnee. */
    long countByDatePlanifiee(LocalDate date);

    /** Nombre de taches dans une periode (bornes incluses). */
    long countByDatePlanifieeBetween(LocalDate from, LocalDate to);

    /** Nombre de taches par statut a une date donnee. */
    long countByDatePlanifieeAndStatut(LocalDate date, StatutTache statut);

    /** Nombre de taches par statut dans une periode. */
    long countByDatePlanifieeBetweenAndStatut(LocalDate from, LocalDate to, StatutTache statut);

    /** Nombre de taches d'une priorite a une date donnee (pour "urgentes"). */
    long countByDatePlanifieeAndPriorite(LocalDate date, Integer priorite);

    /** Compteur taches d'un personnel sur une periode. */
    long countByPersonnelIdAndDatePlanifieeBetween(Long personnelId, LocalDate from, LocalDate to);

    /** Compteur taches d'un personnel sur une periode et un statut precis. */
    long countByPersonnelIdAndDatePlanifieeBetweenAndStatut(
            Long personnelId, LocalDate from, LocalDate to, StatutTache statut);

    /**
     * Distribution count par statut a une date donnee.
     *
     * <p>Retourne {@code List<Object[]>} avec {@code [0] = StatutTache (enum)}
     * et {@code [1] = Long (count)}. Le service appelant transforme en
     * {@code Map<String, Long>} avec {@code statut.name()} comme cle.</p>
     */
    @Query("SELECT t.statut, COUNT(t) FROM Tache t "
            + "WHERE t.datePlanifiee = :date GROUP BY t.statut")
    List<Object[]> countByStatutForDate(@Param("date") LocalDate date);

    /** Distribution count par statut sur une periode. */
    @Query("SELECT t.statut, COUNT(t) FROM Tache t "
            + "WHERE t.datePlanifiee BETWEEN :from AND :to GROUP BY t.statut")
    List<Object[]> countByStatutForPeriode(@Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    /** Distribution count par type de nettoyage a une date donnee. */
    @Query("SELECT t.typeNettoyage, COUNT(t) FROM Tache t "
            + "WHERE t.datePlanifiee = :date GROUP BY t.typeNettoyage")
    List<Object[]> countByTypeForDate(@Param("date") LocalDate date);

    /** Distribution count par type sur une periode. */
    @Query("SELECT t.typeNettoyage, COUNT(t) FROM Tache t "
            + "WHERE t.datePlanifiee BETWEEN :from AND :to GROUP BY t.typeNettoyage")
    List<Object[]> countByTypeForPeriode(@Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    /** Distribution count par priorite a une date donnee. */
    @Query("SELECT t.priorite, COUNT(t) FROM Tache t "
            + "WHERE t.datePlanifiee = :date GROUP BY t.priorite")
    List<Object[]> countByPrioriteForDate(@Param("date") LocalDate date);

    /** Distribution count par priorite sur une periode. */
    @Query("SELECT t.priorite, COUNT(t) FROM Tache t "
            + "WHERE t.datePlanifiee BETWEEN :from AND :to GROUP BY t.priorite")
    List<Object[]> countByPrioriteForPeriode(@Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    /**
     * Taches terminees sur une periode avec les deux instants reels poses.
     * Utilise pour calculer le temps de realisation moyen cote service
     * (Duration.between(heureDebutReelle, heureFinReelle).toMinutes()).
     */
    @Query("SELECT t FROM Tache t "
            + "WHERE t.datePlanifiee BETWEEN :from AND :to "
            + "AND t.statut = com.cityprojects.citybackend.entity.menage.StatutTache.TERMINEE "
            + "AND t.heureDebutReelle IS NOT NULL AND t.heureFinReelle IS NOT NULL")
    List<Tache> findTermineesAvecDureeOnPeriode(@Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);

    /** Idem pour un personnel donne. */
    @Query("SELECT t FROM Tache t "
            + "WHERE t.personnelId = :personnelId "
            + "AND t.datePlanifiee BETWEEN :from AND :to "
            + "AND t.statut = com.cityprojects.citybackend.entity.menage.StatutTache.TERMINEE "
            + "AND t.heureDebutReelle IS NOT NULL AND t.heureFinReelle IS NOT NULL")
    List<Tache> findTermineesAvecDureeForPersonnel(@Param("personnelId") Long personnelId,
                                                    @Param("from") LocalDate from,
                                                    @Param("to") LocalDate to);

    /**
     * Top N taches en retard pour la carte "tachesEnRetard" du dashboard.
     * Reutilise la formule de {@link #findEnRetard} mais limite via
     * {@link Pageable} (top 5 par defaut, le caller controle).
     */
    @Query("SELECT t FROM Tache t WHERE t.statut IN ("
            + " com.cityprojects.citybackend.entity.menage.StatutTache.PLANIFIEE, "
            + " com.cityprojects.citybackend.entity.menage.StatutTache.EN_COURS) "
            + "AND ( t.datePlanifiee < :aujourdhui "
            + "      OR (t.datePlanifiee = :aujourdhui AND t.heureFinPrevue IS NOT NULL "
            + "          AND t.heureFinPrevue < :heureCourante) ) "
            + "ORDER BY t.datePlanifiee ASC, t.heureFinPrevue ASC")
    List<Tache> findTopEnRetard(@Param("aujourdhui") LocalDate aujourdhui,
                                 @Param("heureCourante") LocalTime heureCourante,
                                 Pageable pageable);
}
