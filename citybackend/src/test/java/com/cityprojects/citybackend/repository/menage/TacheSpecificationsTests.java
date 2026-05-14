package com.cityprojects.citybackend.repository.menage;

import com.cityprojects.citybackend.dto.menage.TacheFiltres;
import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.entity.menage.Tache;
import com.cityprojects.citybackend.entity.menage.TypeNettoyage;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link TacheSpecifications} (sous-tour F5).
 *
 * <p>Verifie :</p>
 * <ul>
 *   <li>Court-circuit quand aucun filtre n'est pose
 *       ({@link TacheFiltres#hasAnyFilter()} == false).</li>
 *   <li>Activation des Predicate pour chaque filtre individuel.</li>
 *   <li>Priorite des raccourcis booleens sur les filtres natifs.</li>
 *   <li>Composition AND finale via {@code cb.and(predicates)}.</li>
 * </ul>
 *
 * <p>Approche : mock {@link CriteriaBuilder}/{@link Root}/{@link CriteriaQuery},
 * applique la Specification, verifie les appels au CriteriaBuilder. Les
 * tests E2E (avec une vraie DB H2) sont laisses aux ITs Spring.</p>
 */
class TacheSpecificationsTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 14);
    private static final LocalTime NOW = LocalTime.of(10, 0);

    private Root<Tache> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;
    private Predicate dummyPredicate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        root = mock(Root.class);
        query = mock(CriteriaQuery.class);
        cb = mock(CriteriaBuilder.class);
        dummyPredicate = mock(Predicate.class);

        // Tous les appels au CriteriaBuilder retournent un Predicate factice
        // (le contenu n'est pas verifie ici — l'objectif est de tracer les
        // appels au builder par le code de la specification).
        lenient().when(cb.equal(any(), any())).thenReturn(dummyPredicate);
        lenient().when(cb.like(any(Expression.class), anyString())).thenReturn(dummyPredicate);
        lenient().when(cb.lower(any())).thenReturn(mock(Expression.class));
        lenient().when(cb.coalesce(any(Expression.class), any(String.class))).thenReturn(mock(Expression.class));
        lenient().when(cb.or(any(Predicate.class), any(Predicate.class))).thenReturn(dummyPredicate);
        lenient().when(cb.or(any(Predicate.class))).thenReturn(dummyPredicate);
        lenient().when(cb.and(any(Predicate[].class))).thenReturn(dummyPredicate);
        lenient().when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(dummyPredicate);
        lenient().when(cb.and(any(Predicate.class), any(Predicate.class), any(Predicate.class))).thenReturn(dummyPredicate);
        lenient().when(cb.isNull(any())).thenReturn(dummyPredicate);
        lenient().when(cb.isNotNull(any())).thenReturn(dummyPredicate);
        lenient().when(cb.lessThan(any(Expression.class), any(LocalDate.class))).thenReturn(dummyPredicate);
        lenient().when(cb.lessThan(any(Expression.class), any(LocalTime.class))).thenReturn(dummyPredicate);

        Path<Object> path = mock(Path.class);
        lenient().when(root.get(anyString())).thenReturn(path);
        lenient().when(path.in(any(Object[].class))).thenReturn(dummyPredicate);
    }

    // ─────────────────────────────────────────────────────────────────
    // Court-circuit
    // ─────────────────────────────────────────────────────────────────

    @Test
    void byFiltres_returnsNullWhenFiltresIsNull() {
        Specification<Tache> spec = TacheSpecifications.byFiltres(null, TODAY, NOW);
        assertThat(spec).isNull();
    }

    @Test
    void byFiltres_returnsNullWhenAllFieldsNull() {
        TacheFiltres empty = new TacheFiltres(null, null, null, null, null, null, null, null, null, null);
        assertThat(empty.hasAnyFilter()).isFalse();
        Specification<Tache> spec = TacheSpecifications.byFiltres(empty, TODAY, NOW);
        assertThat(spec).isNull();
    }

    @Test
    void hasAnyFilter_trueForAnyNonNullField() {
        assertThat(new TacheFiltres("x", null, null, null, null, null, null, null, null, null).hasAnyFilter()).isTrue();
        assertThat(new TacheFiltres(null, TODAY, null, null, null, null, null, null, null, null).hasAnyFilter()).isTrue();
        assertThat(new TacheFiltres(null, null, 1L, null, null, null, null, null, null, null).hasAnyFilter()).isTrue();
        assertThat(new TacheFiltres(null, null, null, 2L, null, null, null, null, null, null).hasAnyFilter()).isTrue();
        assertThat(new TacheFiltres(null, null, null, null, StatutTache.EN_COURS, null, null, null, null, null).hasAnyFilter()).isTrue();
        assertThat(new TacheFiltres(null, null, null, null, null, TypeNettoyage.QUOTIDIEN, null, null, null, null).hasAnyFilter()).isTrue();
        assertThat(new TacheFiltres(null, null, null, null, null, null, 3, null, null, null).hasAnyFilter()).isTrue();
        // Les booleens : false n'active pas le filtre, true oui
        assertThat(new TacheFiltres(null, null, null, null, null, null, null, false, null, null).hasAnyFilter()).isFalse();
        assertThat(new TacheFiltres(null, null, null, null, null, null, null, true, null, null).hasAnyFilter()).isTrue();
        assertThat(new TacheFiltres(null, null, null, null, null, null, null, null, true, null).hasAnyFilter()).isTrue();
        assertThat(new TacheFiltres(null, null, null, null, null, null, null, null, null, true).hasAnyFilter()).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────
    // Filtres individuels
    // ─────────────────────────────────────────────────────────────────

    @Test
    void search_buildsOrLikeOnCommentairesAndProblemes() {
        TacheFiltres f = new TacheFiltres("urgent", null, null, null, null, null, null, null, null, null);
        Specification<Tache> spec = TacheSpecifications.byFiltres(f, TODAY, NOW);
        spec.toPredicate(root, query, cb);
        // 2 cb.like attendus (commentaires + problemes)
        verify(cb, times(2)).like(any(Expression.class), eq("%urgent%"));
        verify(cb, times(1)).or(any(Predicate.class), any(Predicate.class));
    }

    @Test
    void date_buildsEqualOnDatePlanifiee() {
        TacheFiltres f = new TacheFiltres(null, TODAY, null, null, null, null, null, null, null, null);
        TacheSpecifications.byFiltres(f, TODAY, NOW).toPredicate(root, query, cb);
        verify(cb).equal(any(), eq(TODAY));
    }

    @Test
    void personnelId_buildsEqualWhenNonAssigneesFalse() {
        TacheFiltres f = new TacheFiltres(null, null, 42L, null, null, null, null, null, null, null);
        TacheSpecifications.byFiltres(f, TODAY, NOW).toPredicate(root, query, cb);
        verify(cb).equal(any(), eq(42L));
        verify(cb, never()).isNull(any());
    }

    @Test
    void nonAssignees_overridesPersonnelIdFilter() {
        // Les deux filtres sont fournis, mais nonAssignees=true doit prendre
        // priorite : on attend cb.isNull(personnelId) ET PAS cb.equal(42L).
        TacheFiltres f = new TacheFiltres(null, null, 42L, null, null, null, null, null, null, true);
        TacheSpecifications.byFiltres(f, TODAY, NOW).toPredicate(root, query, cb);
        verify(cb).isNull(any());
        verify(cb, never()).equal(any(), eq(42L));
    }

    @Test
    void enCours_overridesStatutFilter() {
        // statut=TERMINEE est fourni mais enCours=true doit forcer EN_COURS.
        TacheFiltres f = new TacheFiltres(null, null, null, null, StatutTache.TERMINEE,
                null, null, true, null, null);
        TacheSpecifications.byFiltres(f, TODAY, NOW).toPredicate(root, query, cb);
        verify(cb).equal(any(), eq(StatutTache.EN_COURS));
        verify(cb, never()).equal(any(), eq(StatutTache.TERMINEE));
    }

    @Test
    void typeNettoyageAndPriorite_buildEqual() {
        TacheFiltres f = new TacheFiltres(null, null, null, null, null,
                TypeNettoyage.GRAND_MENAGE, 2, null, null, null);
        TacheSpecifications.byFiltres(f, TODAY, NOW).toPredicate(root, query, cb);
        verify(cb).equal(any(), eq(TypeNettoyage.GRAND_MENAGE));
        verify(cb).equal(any(), eq(2));
    }

    @Test
    void enRetard_composesNotDoneAndDateConstraints() {
        TacheFiltres f = new TacheFiltres(null, null, null, null, null, null, null, null, true, null);
        TacheSpecifications.byFiltres(f, TODAY, NOW).toPredicate(root, query, cb);
        // 1 cb.lessThan sur la date + 1 cb.lessThan sur l'heure
        verify(cb).lessThan(any(Expression.class), eq(TODAY));
        verify(cb).lessThan(any(Expression.class), eq(NOW));
        verify(cb).isNotNull(any());
    }

    @Test
    void combinedFilters_callCriteriaBuilderAndAtLeastOnce() {
        TacheFiltres f = new TacheFiltres("x", TODAY, 42L, 99L,
                StatutTache.EN_COURS, TypeNettoyage.QUOTIDIEN, 1,
                null, null, null);
        TacheSpecifications.byFiltres(f, TODAY, NOW).toPredicate(root, query, cb);
        // L'appel final compose tous les predicates dans un cb.and(predicates[])
        verify(cb, times(1)).and(any(Predicate[].class));
    }
}
