package com.cityprojects.citybackend.dto.menage;

import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.entity.menage.TypeNettoyage;

import java.time.LocalDate;

/**
 * Filtres dynamiques optionnels pour la liste paginee des taches de menage
 * (sous-tour B2, endpoint {@code GET /api/menage/taches}).
 *
 * <p>Tous les champs sont {@code Optional} (Java null) : un filtre absent
 * signifie « pas de contrainte sur ce critere ». Le service combine les
 * filtres presents via une {@link org.springframework.data.jpa.domain.Specification}
 * (clauses AND).</p>
 *
 * <p>Alignement strict avec le modele frontend
 * {@code cityfrontend/src/app/features/menage/models/tache.model.ts}
 * {@code FiltresTaches}, sauf {@code statutId} qui appartient au mono-source
 * obsolete et n'est pas pris en charge cote backend (le frontend doit
 * passer {@code statut} = nom de l'enum a la place).</p>
 *
 * <h3>Multi-tenant</h3>
 * <p>Aucun champ {@code hotelId} : le filtre tenant est applique
 * automatiquement par Hibernate via {@code @TenantId} sur {@code Tache}.</p>
 *
 * @param search           texte libre matche en case-insensitive sur
 *                         {@code commentaires} et {@code problemesDetectes}
 * @param date             egalite stricte sur {@code datePlanifiee}
 * @param personnelId      egalite stricte sur {@code personnelId}
 *                         (ignore si {@code nonAssignees == true})
 * @param chambreId        egalite stricte sur {@code chambreId}
 * @param statut           egalite stricte sur l'enum
 *                         (ignore si {@code enCours == true})
 * @param typeNettoyage    egalite stricte sur l'enum
 * @param priorite         egalite stricte sur l'integer (1..3)
 * @param enCours          raccourci : statut = EN_COURS
 * @param enRetard         raccourci : non terminee ET datePlanifiee passee
 *                         (ou meme jour avec heureFinPrevue depassee)
 * @param nonAssignees     raccourci : personnelId IS NULL
 */
public record TacheFiltres(
        String search,
        LocalDate date,
        Long personnelId,
        Long chambreId,
        StatutTache statut,
        TypeNettoyage typeNettoyage,
        Integer priorite,
        Boolean enCours,
        Boolean enRetard,
        Boolean nonAssignees) {

    /**
     * Helper d'analyse : {@code true} si au moins un filtre est specifie.
     * Utile pour court-circuiter la construction de Specifications quand
     * aucun filtre n'est pose (retourne {@code findAll(pageable)} brut).
     */
    public boolean hasAnyFilter() {
        return search != null
                || date != null
                || personnelId != null
                || chambreId != null
                || statut != null
                || typeNettoyage != null
                || priorite != null
                || Boolean.TRUE.equals(enCours)
                || Boolean.TRUE.equals(enRetard)
                || Boolean.TRUE.equals(nonAssignees);
    }
}
