package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.dto.menage.AssignerTacheDto;
import com.cityprojects.citybackend.dto.menage.TacheCreateDto;
import com.cityprojects.citybackend.dto.menage.TacheDto;
import com.cityprojects.citybackend.dto.menage.TerminerTacheDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/**
 * Service de gestion des taches de menage.
 *
 * <p>Cycle de vie : PLANIFIEE (creation) -&gt; EN_COURS (commencer) -&gt;
 * TERMINEE (terminer). ANNULEE possible avant le debut.</p>
 *
 * <p>Toutes les actions sont auditees via
 * {@link HistoriqueService#enregistrer}.</p>
 */
public interface TacheService {

    /** Cree une nouvelle tache. */
    TacheDto create(TacheCreateDto dto);

    /** Modifie une tache (refuse si EN_COURS ou TERMINEE). */
    TacheDto update(Long tacheId, TacheCreateDto dto);

    /** Recupere une tache par son ID. */
    TacheDto findById(Long tacheId);

    /** Taches d'une date triees par priorite desc puis heure prevue. */
    List<TacheDto> findByDate(LocalDate date);

    /** Taches d'un personnel pour une date. */
    List<TacheDto> findByPersonnel(Long personnelId, LocalDate date);

    /** Taches actuellement EN_COURS pour le tenant. */
    List<TacheDto> findEnCours();

    /** Taches en retard (non terminees et echeance passee). */
    List<TacheDto> findEnRetard();

    /** Taches non encore assignees pour une date. */
    List<TacheDto> findNonAssignees(LocalDate date);

    /** Assigne une tache a un personnel. */
    TacheDto assigner(Long tacheId, AssignerTacheDto dto);

    /** Demarre une tache (transition PLANIFIEE -&gt; EN_COURS). */
    TacheDto commencer(Long tacheId);

    /** Termine une tache (transition EN_COURS -&gt; TERMINEE). */
    TacheDto terminer(Long tacheId, TerminerTacheDto dto);

    /** Recherche textuelle pageable. */
    Page<TacheDto> search(String terme, Pageable pageable);

    /**
     * Annule une tache (transition PLANIFIEE/EN_COURS -&gt; ANNULEE).
     *
     * <p>Tour 30 etape 8 : alternative au DELETE physique pour preserver l'audit.
     * Refusee si la tache est deja TERMINEE ou ANNULEE.</p>
     *
     * @param tacheId identifiant de la tache
     * @param motif   motif libre stocke dans le commentaire d'historique
     */
    TacheDto annuler(Long tacheId, String motif);

    /**
     * Supprime physiquement une tache.
     *
     * <p>Tour 30 etape 7 : refuse si la tache est EN_COURS (rien de neuf) OU
     * TERMINEE (preservation de l'audit financier/qualite). Pour neutraliser une
     * tache TERMINEE, utiliser un avoir applicatif au niveau du module qui en
     * depend ; pour annuler une PLANIFIEE/EN_COURS, preferer
     * {@link #annuler(Long, String)}.</p>
     */
    void delete(Long tacheId);
}
