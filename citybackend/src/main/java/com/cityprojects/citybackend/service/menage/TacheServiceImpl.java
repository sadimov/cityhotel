package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.common.audit.AuditAction;
import com.cityprojects.citybackend.common.event.TacheCommenceeEvent;
import com.cityprojects.citybackend.common.event.TacheTermineeEvent;
import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.menage.AssignerTacheDto;
import com.cityprojects.citybackend.dto.menage.TacheCreateDto;
import com.cityprojects.citybackend.dto.menage.TacheDto;
import com.cityprojects.citybackend.dto.menage.TerminerTacheDto;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.menage.Personnel;
import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.entity.menage.Tache;
import com.cityprojects.citybackend.entity.menage.TypeNettoyage;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.menage.TacheMapper;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.menage.PersonnelRepository;
import com.cityprojects.citybackend.repository.menage.TacheRepository;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Implementation de {@link TacheService}.
 *
 * <p>Validation cross-module : une tache pointe vers une chambre via Long FK.
 * La verification de l'appartenance de la chambre au tenant courant repose sur
 * Hibernate ({@code @TenantId}) -&gt; un {@code chambreId} d'un autre hotel
 * leve {@link ResourceNotFoundException}.</p>
 *
 * <p>Pas de mutation du statut chambre (Tour 27, decision documentee dans
 * {@link StatutTache}). Si une automatisation est ajoutee plus tard, elle
 * passera par {@code ChambreService}, pas par
 * {@code chambreRepository.updateStatut(...)}.</p>
 *
 * <h3>Audit log (Tour 30 etape 5)</h3>
 * <p>Les insertions dans {@code menage.historique} sont posees par
 * {@link com.cityprojects.citybackend.common.audit.AuditActionAspect} via
 * l'annotation {@link AuditAction} sur chaque methode metier. L'aspect lit le
 * {@code userId} depuis le {@link com.cityprojects.citybackend.security.UserPrincipal}
 * (corrige le bug {@code userId=null} systematique pre-Tour 30).</p>
 *
 * <h3>Concurrence (Tour 30 etape 3)</h3>
 * <p>Les transitions {@code commencer}/{@code terminer}/{@code assigner}/{@code annuler}
 * sont protegees par {@code @Version} sur {@link Tache}. En cas de conflit,
 * Hibernate leve {@link OptimisticLockException}/{@link OptimisticLockingFailureException}
 * que l'on traduit ici en {@code BusinessException("error.tache.concurrent.modification")}
 * (HTTP 409 via {@code GlobalExceptionHandler}).</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class TacheServiceImpl implements TacheService {

    private static final Logger logger = LoggerFactory.getLogger(TacheServiceImpl.class);

    private final TacheRepository tacheRepository;
    private final PersonnelRepository personnelRepository;
    private final ChambreRepository chambreRepository;
    private final TacheMapper mapper;
    private final Clock clock;
    /**
     * Publisher Spring d'evenements applicatifs (Tour 30, couplage event-driven
     * vers {@link com.cityprojects.citybackend.service.menage.ChambreStatutListener}
     * pour les Workflows B/C : transitions automatiques de statut chambre
     * selon le cycle commencer()/terminer() d'une tache).
     */
    private final ApplicationEventPublisher applicationEventPublisher;

    public TacheServiceImpl(TacheRepository tacheRepository,
                            PersonnelRepository personnelRepository,
                            ChambreRepository chambreRepository,
                            TacheMapper mapper,
                            Clock clock,
                            ApplicationEventPublisher applicationEventPublisher) {
        this.tacheRepository = tacheRepository;
        this.personnelRepository = personnelRepository;
        this.chambreRepository = chambreRepository;
        this.mapper = mapper;
        this.clock = clock;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    @Transactional
    @AuditAction("creation")
    public TacheDto create(TacheCreateDto dto) {
        logger.info("Creation tache : chambreId={}, datePlanifiee={}", dto.chambreId(), dto.datePlanifiee());

        // Validation : la chambre existe et appartient au tenant courant
        Chambre chambre = chambreRepository.findById(dto.chambreId())
                .orElseThrow(() -> new ResourceNotFoundException("error.chambre.notFound"));

        // Validation personnel si specifie
        if (dto.personnelId() != null) {
            Personnel p = personnelRepository.findById(dto.personnelId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.personnel.notFound"));
            if (Boolean.FALSE.equals(p.getActif())) {
                throw new BusinessException("error.personnel.notActive");
            }
        }

        // Validation heures coherentes
        if (dto.heureDebutPrevue() != null && dto.heureFinPrevue() != null
                && !dto.heureFinPrevue().isAfter(dto.heureDebutPrevue())) {
            throw new BusinessException("error.tache.heures.invalid");
        }

        Tache entity = mapper.toEntity(dto);
        entity.setStatut(StatutTache.PLANIFIEE);
        if (entity.getTypeNettoyage() == null) {
            entity.setTypeNettoyage(TypeNettoyage.QUOTIDIEN);
        }
        if (entity.getPriorite() == null) {
            entity.setPriorite(1);
        }
        // PAS de setHotelId : Hibernate via @TenantId

        Tache saved = tacheRepository.save(entity);
        // chambre validee : son existence est garantie par findById().orElseThrow().
        // Le chambreId du DTO retourne reflete celui de la chambre.
        // L'aspect AuditActionAspect inserera l'historique apres ce return.
        logger.debug("Tache creee id={} chambre={}", saved.getTacheId(), chambre.getChambreId());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    @AuditAction("modification")
    public TacheDto update(Long tacheId, TacheCreateDto dto) {
        logger.info("Modification tache id={}", tacheId);
        Tache entity = tacheRepository.findById(tacheId)
                .orElseThrow(() -> new ResourceNotFoundException("error.tache.notFound"));

        if (entity.getStatut() == StatutTache.EN_COURS) {
            throw new BusinessException("error.tache.update.enCours");
        }
        if (entity.getStatut() == StatutTache.TERMINEE) {
            throw new BusinessException("error.tache.update.terminee");
        }

        // Validation chambre cible (peut changer)
        chambreRepository.findById(dto.chambreId())
                .orElseThrow(() -> new ResourceNotFoundException("error.chambre.notFound"));

        // Validation personnel si specifie
        if (dto.personnelId() != null) {
            Personnel p = personnelRepository.findById(dto.personnelId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.personnel.notFound"));
            if (Boolean.FALSE.equals(p.getActif())) {
                throw new BusinessException("error.personnel.notActive");
            }
        }

        entity.setChambreId(dto.chambreId());
        entity.setPersonnelId(dto.personnelId());
        if (dto.typeNettoyage() != null) {
            entity.setTypeNettoyage(dto.typeNettoyage());
        }
        if (dto.priorite() != null) {
            entity.setPriorite(dto.priorite());
        }
        entity.setDatePlanifiee(dto.datePlanifiee());
        entity.setHeureDebutPrevue(dto.heureDebutPrevue());
        entity.setHeureFinPrevue(dto.heureFinPrevue());
        entity.setCommentaires(dto.commentaires());
        entity.setMaterielUtilise(dto.materielNecessaire());

        try {
            Tache saved = tacheRepository.saveAndFlush(entity);
            return mapper.toDto(saved);
        } catch (OptimisticLockException | OptimisticLockingFailureException ex) {
            throw new BusinessException("error.tache.concurrent.modification");
        }
    }

    @Override
    public TacheDto findById(Long tacheId) {
        return tacheRepository.findById(tacheId)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("error.tache.notFound"));
    }

    @Override
    public List<TacheDto> findByDate(LocalDate date) {
        return tacheRepository.findByDatePlanifieeOrderByPrioriteDescHeureDebutPrevueAsc(date)
                .stream().map(mapper::toDto).toList();
    }

    @Override
    public List<TacheDto> findByPersonnel(Long personnelId, LocalDate date) {
        // Hibernate filtre via @TenantId : un personnel d'un autre hotel renvoie 404
        personnelRepository.findById(personnelId)
                .orElseThrow(() -> new ResourceNotFoundException("error.personnel.notFound"));
        return tacheRepository.findByPersonnelIdAndDatePlanifieeOrderByHeureDebutPrevueAsc(personnelId, date)
                .stream().map(mapper::toDto).toList();
    }

    @Override
    public List<TacheDto> findEnCours() {
        return tacheRepository.findByStatutOrderByHeureDebutReelleAsc(StatutTache.EN_COURS)
                .stream().map(mapper::toDto).toList();
    }

    @Override
    public List<TacheDto> findEnRetard() {
        // Tour 30 etape 1 : derive date+heure depuis le Clock (timezone
        // Africa/Nouakchott), passe les deux au repo qui filtre correctement
        // sur heureFinPrevue pour les taches du jour.
        LocalDateTime now = LocalDateTime.now(clock);
        return tacheRepository.findEnRetard(now.toLocalDate(), now.toLocalTime())
                .stream().map(mapper::toDto).toList();
    }

    @Override
    public List<TacheDto> findNonAssignees(LocalDate date) {
        return tacheRepository.findNonAssignees(date)
                .stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional
    @AuditAction(value = "assignation", commentaire = "Assignation modifiee")
    public TacheDto assigner(Long tacheId, AssignerTacheDto dto) {
        logger.info("Assignation tache {} -> personnel {}", tacheId, dto.personnelId());
        Tache tache = tacheRepository.findById(tacheId)
                .orElseThrow(() -> new ResourceNotFoundException("error.tache.notFound"));
        Personnel personnel = personnelRepository.findById(dto.personnelId())
                .orElseThrow(() -> new ResourceNotFoundException("error.personnel.notFound"));
        if (Boolean.FALSE.equals(personnel.getActif())) {
            throw new BusinessException("error.personnel.notActive");
        }
        if (tache.getStatut() == StatutTache.TERMINEE || tache.getStatut() == StatutTache.ANNULEE) {
            throw new BusinessException("error.tache.assigner.invalidStatut");
        }
        tache.setPersonnelId(personnel.getPersonnelId());
        try {
            Tache saved = tacheRepository.saveAndFlush(tache);
            return mapper.toDto(saved);
        } catch (OptimisticLockException | OptimisticLockingFailureException ex) {
            throw new BusinessException("error.tache.concurrent.modification");
        }
    }

    @Override
    @Transactional
    @AuditAction(value = "debut", transition = true)
    public TacheDto commencer(Long tacheId) {
        logger.info("Debut tache id={}", tacheId);
        Tache tache = tacheRepository.findById(tacheId)
                .orElseThrow(() -> new ResourceNotFoundException("error.tache.notFound"));
        if (tache.getPersonnelId() == null) {
            throw new BusinessException("error.tache.commencer.notAssigned");
        }
        if (tache.getStatut() != StatutTache.PLANIFIEE) {
            throw new BusinessException("error.tache.commencer.invalidStatut");
        }
        tache.setStatut(StatutTache.EN_COURS);
        tache.setHeureDebutReelle(Instant.now(clock));
        try {
            Tache saved = tacheRepository.saveAndFlush(tache);
            // Tour 30 - Workflow C : publish event AFTER_COMMIT.
            // Le listener ChambreStatutListener bascule la chambre en
            // MAINTENANCE si type=MAINTENANCE (no-op pour QUOTIDIEN/GRAND_MENAGE).
            applicationEventPublisher.publishEvent(new TacheCommenceeEvent(
                    saved.getTacheId(), saved.getChambreId(), TenantContext.get(),
                    saved.getTypeNettoyage()));
            return mapper.toDto(saved);
        } catch (OptimisticLockException | OptimisticLockingFailureException ex) {
            throw new BusinessException("error.tache.concurrent.modification");
        }
    }

    @Override
    @Transactional
    @AuditAction(value = "fin", transition = true)
    public TacheDto terminer(Long tacheId, TerminerTacheDto dto) {
        logger.info("Fin tache id={}", tacheId);
        Tache tache = tacheRepository.findById(tacheId)
                .orElseThrow(() -> new ResourceNotFoundException("error.tache.notFound"));
        if (tache.getStatut() != StatutTache.EN_COURS) {
            throw new BusinessException("error.tache.terminer.invalidStatut");
        }
        tache.setStatut(StatutTache.TERMINEE);
        tache.setHeureFinReelle(Instant.now(clock));
        if (dto != null) {
            if (dto.commentaires() != null) {
                tache.setCommentaires(tache.getCommentaires() != null
                        ? tache.getCommentaires() + "\n" + dto.commentaires()
                        : dto.commentaires());
            }
            if (dto.problemesDetectes() != null) {
                tache.setProblemesDetectes(dto.problemesDetectes());
            }
            if (dto.materielUtilise() != null) {
                tache.setMaterielUtilise(dto.materielUtilise());
            }
            // Sous-tour A3 (fix menage) : stockage de la note de qualite.
            // La validation @Min(1) @Max(5) cote DTO + CHECK SQL borne
            // 1..5 (changeset 038-add-note-qualite-taches). Null = pas de
            // note saisie (le cas le plus frequent ; la note est optionnelle).
            if (dto.noteQualite() != null) {
                tache.setNoteQualite(dto.noteQualite());
            }
        }
        try {
            Tache saved = tacheRepository.saveAndFlush(tache);
            // Tour 30 - Workflow B : publish event AFTER_COMMIT.
            // Le listener ChambreStatutListener bascule la chambre vers
            // DISPONIBLE (NETTOYAGE -> DISPONIBLE pour QUOTIDIEN/GRAND_MENAGE,
            // MAINTENANCE -> DISPONIBLE pour MAINTENANCE).
            applicationEventPublisher.publishEvent(new TacheTermineeEvent(
                    saved.getTacheId(), saved.getChambreId(), TenantContext.get(),
                    saved.getTypeNettoyage()));
            return mapper.toDto(saved);
        } catch (OptimisticLockException | OptimisticLockingFailureException ex) {
            throw new BusinessException("error.tache.concurrent.modification");
        }
    }

    @Override
    public Page<TacheDto> search(String terme, Pageable pageable) {
        if (terme == null || terme.isBlank()) {
            return Page.empty(pageable);
        }
        return tacheRepository.rechercher(terme.trim(), pageable).map(mapper::toDto);
    }

    @Override
    @Transactional
    @AuditAction(value = "annulation", transition = true)
    public TacheDto annuler(Long tacheId, String motif) {
        logger.info("Annulation tache id={} motif={}", tacheId, motif);
        Tache tache = tacheRepository.findById(tacheId)
                .orElseThrow(() -> new ResourceNotFoundException("error.tache.notFound"));
        // Refus si deja TERMINEE ou ANNULEE : une tache realisee doit garder
        // ses heures reelles, et on ne reannule pas une annulation.
        if (tache.getStatut() == StatutTache.TERMINEE || tache.getStatut() == StatutTache.ANNULEE) {
            throw new BusinessException("error.tache.annuler.invalidStatut");
        }
        tache.setStatut(StatutTache.ANNULEE);
        // Trace le motif dans les commentaires de la tache (visible cote front
        // sans devoir requeter l'historique).
        if (motif != null && !motif.isBlank()) {
            String prefix = "[ANNULATION] " + motif;
            tache.setCommentaires(tache.getCommentaires() != null
                    ? tache.getCommentaires() + "\n" + prefix
                    : prefix);
        }
        try {
            Tache saved = tacheRepository.saveAndFlush(tache);
            return mapper.toDto(saved);
        } catch (OptimisticLockException | OptimisticLockingFailureException ex) {
            throw new BusinessException("error.tache.concurrent.modification");
        }
    }

    @Override
    @Transactional
    @AuditAction("suppression")
    public void delete(Long tacheId) {
        logger.info("Suppression tache id={}", tacheId);
        Tache tache = tacheRepository.findById(tacheId)
                .orElseThrow(() -> new ResourceNotFoundException("error.tache.notFound"));
        // Tour 30 etape 7 : refus si EN_COURS (rien de neuf) ou TERMINEE
        // (preservation de l'audit). Les TERMINEE doivent passer par un avoir
        // applicatif au niveau du module qui en depend ; les PLANIFIEE/ANNULEE
        // restent supprimables physiquement.
        if (tache.getStatut() == StatutTache.EN_COURS) {
            throw new BusinessException("error.tache.delete.enCours");
        }
        if (tache.getStatut() == StatutTache.TERMINEE) {
            throw new BusinessException("error.tache.delete.terminee");
        }
        // L'historique de la tache est preserve (FK ON DELETE SET NULL).
        // L'aspect AuditActionAspect a deja capte chambreId/personnelId AVANT
        // l'execution (sinon ils auraient ete null apres delete).
        tacheRepository.delete(tache);
    }
}
