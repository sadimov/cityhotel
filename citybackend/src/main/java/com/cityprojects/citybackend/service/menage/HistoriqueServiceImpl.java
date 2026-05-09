package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.menage.HistoriqueDto;
import com.cityprojects.citybackend.entity.menage.Historique;
import com.cityprojects.citybackend.mapper.menage.HistoriqueMapper;
import com.cityprojects.citybackend.repository.menage.HistoriqueRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Implementation de {@link HistoriqueService}.
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class HistoriqueServiceImpl implements HistoriqueService {

    private static final Logger logger = LoggerFactory.getLogger(HistoriqueServiceImpl.class);

    /**
     * Tour 30 etape 7 : retention minimale de 30 jours, en dur. Une purge plus
     * agressive risquerait de supprimer des traces necessaires a un controle
     * qualite ou a un litige paie. Si le besoin metier evolue (ex. RGPD),
     * remplacer par un parametre {@code city.menage.historique.retention.min}
     * — pas avant.
     */
    static final int MIN_RETENTION_DAYS = 30;

    private final HistoriqueRepository repository;
    private final HistoriqueMapper mapper;

    public HistoriqueServiceImpl(HistoriqueRepository repository, HistoriqueMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void enregistrer(Long tacheId, Long chambreId, Long personnelId,
                            String action, String ancienStatut, String nouveauStatut,
                            String commentaire, Long userId) {
        Historique h = new Historique();
        // hotelId : Hibernate via @TenantId
        h.setTacheId(tacheId);
        h.setChambreId(chambreId);
        h.setPersonnelId(personnelId);
        h.setAction(action);
        h.setAncienStatut(ancienStatut);
        h.setNouveauStatut(nouveauStatut);
        h.setCommentaire(commentaire);
        h.setUserId(userId);
        h.setTimestampAction(Instant.now());
        repository.save(h);
        logger.debug("Action menage '{}' enregistree pour tache={} chambre={}", action, tacheId, chambreId);
    }

    @Override
    public Page<HistoriqueDto> findAll(Pageable pageable) {
        return repository.findAllByOrderByTimestampActionDesc(pageable).map(mapper::toDto);
    }

    @Override
    public List<HistoriqueDto> findByTache(Long tacheId) {
        return repository.findByTacheIdOrderByTimestampActionDesc(tacheId)
                .stream().map(mapper::toDto).toList();
    }

    @Override
    public List<HistoriqueDto> findByChambre(Long chambreId) {
        return repository.findByChambreIdOrderByTimestampActionDesc(chambreId)
                .stream().map(mapper::toDto).toList();
    }

    @Override
    public List<HistoriqueDto> findByPersonnel(Long personnelId) {
        return repository.findByPersonnelIdOrderByTimestampActionDesc(personnelId)
                .stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional
    public int nettoyer(int joursConservation) {
        // Tour 30 etape 7 : refus si en dessous du plancher de retention.
        // IllegalArgumentException -> 400 (cf. GlobalExceptionHandler) avec
        // cle i18n stable pour le front.
        if (joursConservation < MIN_RETENTION_DAYS) {
            throw new IllegalArgumentException("error.historique.retention.min");
        }
        Instant avant = Instant.now().minus(joursConservation, ChronoUnit.DAYS);
        Long currentUser = currentUserId();

        // Tour 30 etape 7 : trace l'action en log applicatif AVANT la purge.
        // On ne pose pas d'entree d'historique "PURGE_HISTORIQUE" dans la
        // table elle-meme : la colonne chambre_id est NOT NULL avec FK vers
        // hebergement.chambres, donc on ne peut pas y referencer un placeholder
        // d'action systeme sans casser l'integrite. Si une table d'audit
        // systeme dediee est ajoutee plus tard, basculer la trace ici.
        logger.warn("[AUDIT] Purge historique menage demarree : retention={} jours, "
                        + "seuil de coupure={}, declenche par userId={}",
                joursConservation, avant, currentUser);

        int n = repository.deleteOlderThan(avant);
        logger.warn("[AUDIT] Purge historique menage terminee : {} entrees supprimees "
                        + "(anciennete > {} jours, par userId={})",
                n, joursConservation, currentUser);
        return n;
    }

    /**
     * Recupere l'id de l'utilisateur depuis le {@link UserPrincipal} pose par
     * {@link com.cityprojects.citybackend.security.JwtAuthenticationFilter}.
     * Retourne {@code null} si pas d'auth (boot, scheduler, batch).
     */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UserPrincipal up) {
            return up.getUserId();
        }
        return null;
    }
}
