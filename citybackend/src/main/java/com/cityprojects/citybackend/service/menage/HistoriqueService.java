package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.dto.menage.HistoriqueDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service de gestion de l'audit log des actions de menage.
 *
 * <p>Le service expose une API d'ecriture interne ({@link #enregistrer})
 * appelee par {@code TacheServiceImpl} a chaque transition metier, et une API
 * de lecture/purge pour les controllers.</p>
 */
public interface HistoriqueService {

    /**
     * Enregistre une action dans l'historique. Appele depuis les autres
     * services menage (TacheService) pour tracer chaque transition.
     *
     * @param tacheId       FK tache (peut etre null si action systeme)
     * @param chambreId     FK chambre (obligatoire)
     * @param personnelId   FK personnel concerne (peut etre null)
     * @param action        libelle de l'action (creation, assignation, debut, fin, modification, suppression)
     * @param ancienStatut  statut avant transition (peut etre null)
     * @param nouveauStatut statut apres transition (peut etre null)
     * @param commentaire   message libre
     * @param userId        utilisateur qui a effectue l'action (peut etre null)
     */
    void enregistrer(Long tacheId, Long chambreId, Long personnelId,
                     String action, String ancienStatut, String nouveauStatut,
                     String commentaire, Long userId);

    /** Historique pagine du tenant courant (plus recent en premier). */
    Page<HistoriqueDto> findAll(Pageable pageable);

    /** Historique d'une tache donnee. */
    List<HistoriqueDto> findByTache(Long tacheId);

    /** Historique d'une chambre donnee. */
    List<HistoriqueDto> findByChambre(Long chambreId);

    /** Historique d'un personnel donne. */
    List<HistoriqueDto> findByPersonnel(Long personnelId);

    /**
     * Purge des entrees plus anciennes que {@code joursConservation} jours.
     *
     * @return nombre de lignes supprimees
     */
    int nettoyer(int joursConservation);
}
