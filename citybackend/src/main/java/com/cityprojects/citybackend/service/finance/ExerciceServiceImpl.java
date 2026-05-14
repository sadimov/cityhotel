package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.audit.AuditFinanceAction;
import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.finance.ExerciceDto;
import com.cityprojects.citybackend.entity.finance.Exercice;
import com.cityprojects.citybackend.entity.finance.StatutExercice;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.finance.ExerciceMapper;
import com.cityprojects.citybackend.repository.finance.ExerciceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.util.Optional;

/**
 * Implementation de {@link ExerciceService}.
 *
 * <p>Conventions :
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe.</li>
 *   <li>{@code @Transactional(readOnly = true)} au niveau classe, override en
 *       écriture pour {@link #getOrCreateCurrent()} et {@link #cloturer(Long)}.</li>
 *   <li>{@link #assertOuvert(LocalDate)} : garde appelée par {@link FactureServiceImpl}
 *       et {@link PaiementServiceImpl} avant tout INSERT/UPDATE de facture/paiement
 *       métier — empêche d'antidater dans un exercice clos.</li>
 * </ul>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class ExerciceServiceImpl implements ExerciceService {

    private static final Logger logger = LoggerFactory.getLogger(ExerciceServiceImpl.class);

    private final ExerciceRepository exerciceRepository;
    private final ExerciceMapper mapper;

    public ExerciceServiceImpl(ExerciceRepository exerciceRepository,
                               ExerciceMapper mapper) {
        this.exerciceRepository = exerciceRepository;
        this.mapper = mapper;
    }

    /**
     * Auto-création de l'exercice courant.
     *
     * <p>{@code Propagation.REQUIRES_NEW} : un appelant readOnly (FactureService.findById)
     * ne doit pas démarrer une nouvelle transaction d'écriture par effet de bord.
     * On ouvre donc une transaction dédiée, courte, qui commit l'exercice et
     * libère le verrou pessimiste avant le retour. La transaction parente
     * récupère ensuite l'exercice via une lecture standard.</p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExerciceDto getOrCreateCurrent() {
        LocalDate today = LocalDate.now();
        // Verrou pessimiste sur l'exercice contenant aujourd'hui pour serialiser
        // les auto-creations concurrentes.
        Optional<Exercice> existing = exerciceRepository.findContainingDateForUpdate(today);
        if (existing.isPresent()) {
            return mapper.toDto(existing.get());
        }

        // Pas d'exercice : on crée une année calendaire.
        int year = Year.now().getValue();
        String code = String.valueOf(year);

        // Securite : si le code existe deja (cas d'un exercice avec annee
        // calendaire mais dates personnalisees ne couvrant pas today),
        // on echoue avec un message explicite plutot que de creer un doublon.
        if (exerciceRepository.findByCode(code).isPresent()) {
            throw new BusinessException("error.exercice.dateInvalide");
        }

        Exercice exercice = new Exercice();
        exercice.setCode(code);
        exercice.setDateDebut(LocalDate.of(year, 1, 1));
        exercice.setDateFin(LocalDate.of(year, 12, 31));
        exercice.setStatut(StatutExercice.OUVERT);
        // PAS de setHotelId : Hibernate via @TenantId.

        Exercice saved = exerciceRepository.save(exercice);
        logger.info("Exercice auto-cree : code={}, periode={} -> {}",
                saved.getCode(), saved.getDateDebut(), saved.getDateFin());
        return mapper.toDto(saved);
    }

    @Override
    public void assertOuvert(LocalDate date) {
        if (date == null) {
            throw new BusinessException("error.exercice.dateRequired");
        }
        Optional<Exercice> existing = exerciceRepository.findContainingDate(date);
        if (existing.isPresent()) {
            StatutExercice statut = existing.get().getStatut();
            if (statut == StatutExercice.EN_CLOTURE || statut == StatutExercice.CLOTURE) {
                throw new BusinessException("error.exercice.cloture");
            }
            return;
        }
        // Pas d'exercice couvrant cette date.
        // - Si la date est l'annee courante : on auto-cree (REQUIRES_NEW).
        // - Sinon : on refuse - antidater dans le passe sans exercice est
        //   un usage anormal, l'utilisateur doit créer l'exercice manuellement.
        int year = Year.now().getValue();
        if (date.getYear() == year) {
            getOrCreateCurrent();
            // Re-verifier apres creation
            Optional<Exercice> created = exerciceRepository.findContainingDate(date);
            if (created.isPresent()) {
                StatutExercice statut = created.get().getStatut();
                if (statut == StatutExercice.EN_CLOTURE || statut == StatutExercice.CLOTURE) {
                    throw new BusinessException("error.exercice.cloture");
                }
                return;
            }
        }
        throw new BusinessException("error.exercice.dateInvalide");
    }

    @Override
    public ExerciceDto findById(Long id) {
        Exercice exercice = exerciceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.exercice.notFound"));
        return mapper.toDto(exercice);
    }

    @Override
    public Page<ExerciceDto> findAll(Pageable pageable) {
        return exerciceRepository.findAllByOrderByDateDebutDesc(pageable).map(mapper::toDto);
    }

    @Override
    @Transactional
    @AuditFinanceAction(value = "EXERCICE_CLOTURE", entityType = "EXERCICE")
    public ExerciceDto cloturer(Long id) {
        Exercice exercice = exerciceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.exercice.notFound"));
        if (exercice.getStatut() == StatutExercice.CLOTURE) {
            throw new BusinessException("error.exercice.dejaCloture");
        }
        exercice.setStatut(StatutExercice.CLOTURE);
        exercice.setDateCloture(LocalDate.now());
        exercice.setClotureBy(currentUsernameOrSystem());
        Exercice saved = exerciceRepository.save(exercice);
        logger.info("Exercice cloture : id={}, code={}", saved.getId(), saved.getCode());
        return mapper.toDto(saved);
    }

    /**
     * Username Spring Security ou {@code "system"} (cohérent avec
     * {@code AuditableEntity.createdBy/updatedBy}).
     */
    private String currentUsernameOrSystem() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null
                && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "system";
    }
}
