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
     * <b>INTERNAL USE ONLY — ne pas invoquer directement.</b>
     *
     * <p>Enregistre une action dans l'historique. Appele <b>exclusivement</b>
     * par {@link com.cityprojects.citybackend.common.audit.AuditActionAspect}
     * (AOP) qui intercepte les methodes annotees
     * {@code @AuditAction} dans les services menage (creation,
     * assignation, debut, fin, modification, suppression, annulation).</p>
     *
     * <p><b>Pourquoi cette restriction</b> (sous-tour menage E2) : la
     * methode n'effectue aucun controle metier propre (cible chambre/personnel,
     * coherence statut, etc.), elle est conçue comme un sink de l'aspect
     * d'audit. Un appel direct depuis un autre module pourrait inserer
     * dans {@code menage.historique} avec des references arbitraires,
     * polluant la piste d'audit. La methode reste exposee dans l'interface
     * publique pour la facilite de l'injection Spring dans l'aspect, mais
     * tout nouvel appelant doit etre justifie en revue de code.</p>
     *
     * <p>Multi-tenant : {@code hotelId} pose par Hibernate via
     * {@link org.hibernate.annotations.TenantId} sur {@code Historique}.
     * Aucun parametre {@code hotelId} dans la signature.</p>
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
