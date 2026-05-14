package com.cityprojects.citybackend.repository.menage;

import com.cityprojects.citybackend.dto.menage.TacheFiltres;
import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.entity.menage.Tache;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder de {@link Specification} pour le repository des taches
 * (sous-tour menage B2).
 *
 * <p>Compose les filtres dynamiques de {@link TacheFiltres} en clauses AND.
 * Les filtres absents (null) sont ignores. Les raccourcis booleens
 * ({@code enCours}, {@code enRetard}, {@code nonAssignees}) ont priorite
 * sur les filtres natifs :</p>
 * <ul>
 *   <li>{@code enCours=true} force {@code statut = EN_COURS} (override).</li>
 *   <li>{@code nonAssignees=true} force {@code personnelId IS NULL} (override
 *       du filtre {@code personnelId}).</li>
 *   <li>{@code enRetard=true} ajoute le predicat compose : statut IN
 *       (PLANIFIEE, EN_COURS) AND (date &lt; today OR (date = today AND
 *       heureFinPrevue &lt; heureCourante)).</li>
 * </ul>
 *
 * <p>Le filtre {@code enRetard} necessite la date / heure courante du
 * serveur ({@link LocalDate}/{@link LocalTime}) — passees en parametres
 * par le service appelant (qui possede l'injection {@link java.time.Clock}).</p>
 *
 * <p>Multi-tenant : la clause {@code WHERE hotel_id = ?} est ajoutee
 * automatiquement par Hibernate via {@code @TenantId}.</p>
 */
public final class TacheSpecifications {

    private TacheSpecifications() {
        // Classe utility — pas d'instanciation.
    }

    /**
     * Construit une {@link Specification} combinant tous les filtres presents
     * dans {@code filtres}. Retourne {@code null} si aucun filtre n'est pose
     * (le caller doit alors appeler {@code findAll(pageable)} brut).
     */
    public static Specification<Tache> byFiltres(TacheFiltres filtres,
                                                  LocalDate aujourdhui,
                                                  LocalTime heureCourante) {
        if (filtres == null || !filtres.hasAnyFilter()) {
            return null;
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Recherche textuelle (case-insensitive) sur commentaires +
            // problemes detectes. COALESCE pour gerer les NULL JPA proprement.
            if (filtres.search() != null && !filtres.search().isBlank()) {
                String pattern = "%" + filtres.search().toLowerCase() + "%";
                Predicate inCommentaires = cb.like(
                        cb.lower(cb.coalesce(root.<String>get("commentaires"), "")), pattern);
                Predicate inProblemes = cb.like(
                        cb.lower(cb.coalesce(root.<String>get("problemesDetectes"), "")), pattern);
                predicates.add(cb.or(inCommentaires, inProblemes));
            }

            if (filtres.date() != null) {
                predicates.add(cb.equal(root.get("datePlanifiee"), filtres.date()));
            }

            // Raccourci nonAssignees : prend la priorite sur personnelId.
            if (Boolean.TRUE.equals(filtres.nonAssignees())) {
                predicates.add(cb.isNull(root.get("personnelId")));
            } else if (filtres.personnelId() != null) {
                predicates.add(cb.equal(root.get("personnelId"), filtres.personnelId()));
            }

            if (filtres.chambreId() != null) {
                predicates.add(cb.equal(root.get("chambreId"), filtres.chambreId()));
            }

            // Raccourci enCours : prend la priorite sur statut.
            if (Boolean.TRUE.equals(filtres.enCours())) {
                predicates.add(cb.equal(root.get("statut"), StatutTache.EN_COURS));
            } else if (filtres.statut() != null) {
                predicates.add(cb.equal(root.get("statut"), filtres.statut()));
            }

            if (filtres.typeNettoyage() != null) {
                predicates.add(cb.equal(root.get("typeNettoyage"), filtres.typeNettoyage()));
            }

            if (filtres.priorite() != null) {
                predicates.add(cb.equal(root.get("priorite"), filtres.priorite()));
            }

            // enRetard composite : tache non terminee/non annulee ET
            // (datePlanifiee < today OR (datePlanifiee = today AND heureFinPrevue < heureCourante)).
            // Aligne sur la query JPQL findEnRetard (TacheRepository ligne 60-67).
            if (Boolean.TRUE.equals(filtres.enRetard())) {
                Predicate notDone = root.get("statut").in(StatutTache.PLANIFIEE, StatutTache.EN_COURS);
                Predicate datePassed = cb.lessThan(root.get("datePlanifiee"), aujourdhui);
                Predicate todayWithHourPassed = cb.and(
                        cb.equal(root.get("datePlanifiee"), aujourdhui),
                        cb.isNotNull(root.get("heureFinPrevue")),
                        cb.lessThan(root.get("heureFinPrevue"), heureCourante));
                predicates.add(cb.and(notDone, cb.or(datePassed, todayWithHourPassed)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
