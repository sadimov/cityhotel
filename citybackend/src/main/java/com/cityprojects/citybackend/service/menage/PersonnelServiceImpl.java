package com.cityprojects.citybackend.service.menage;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.menage.PersonnelCreateDto;
import com.cityprojects.citybackend.dto.menage.PersonnelDto;
import com.cityprojects.citybackend.entity.menage.Personnel;
import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.menage.PersonnelMapper;
import com.cityprojects.citybackend.repository.menage.PersonnelRepository;
import com.cityprojects.citybackend.repository.menage.TacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Implementation de {@link PersonnelService}.
 *
 * <p>Conventions :
 * <ul>
 *   <li>{@link RequireTenant} au niveau classe : refus si TenantContext absent.</li>
 *   <li>{@code @Transactional(readOnly=true)} a la classe, override en ecriture.</li>
 *   <li>Aucun {@code setHotelId} : Hibernate populate via le resolver
 *       {@link org.hibernate.annotations.TenantId}.</li>
 * </ul>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class PersonnelServiceImpl implements PersonnelService {

    private static final Logger logger = LoggerFactory.getLogger(PersonnelServiceImpl.class);

    private final PersonnelRepository personnelRepository;
    private final TacheRepository tacheRepository;
    private final PersonnelMapper mapper;
    private final Clock clock;

    public PersonnelServiceImpl(PersonnelRepository personnelRepository,
                                TacheRepository tacheRepository,
                                PersonnelMapper mapper,
                                Clock clock) {
        this.personnelRepository = personnelRepository;
        this.tacheRepository = tacheRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PersonnelDto create(PersonnelCreateDto dto) {
        logger.info("Creation personnel : numero={}, nom={} {}", dto.numeroEmploye(), dto.prenom(), dto.nom());

        // Tour 30 etape 2 IMPORTANT 2 : trim defensif pour eviter les doublons
        // logiques type "MEN001" vs "MEN001 " (idem email "x@y.test " vs "x@y.test").
        String numeroEmploye = trimToNull(dto.numeroEmploye());
        String email = trimToNull(dto.email());

        // Unicite numero employe (par hotel via @TenantId)
        if (numeroEmploye != null && personnelRepository.existsByNumeroEmploye(numeroEmploye)) {
            throw new BusinessException("error.personnel.numeroEmploye.duplicate");
        }
        // Unicite email si renseigne
        if (email != null && personnelRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("error.personnel.email.duplicate");
        }

        Personnel entity = mapper.toEntity(dto);
        // Re-applique les valeurs trimmees (le mapper a copie les valeurs brutes du DTO).
        entity.setNumeroEmploye(numeroEmploye);
        entity.setEmail(email);
        entity.setActif(Boolean.TRUE);
        // PAS de setHotelId : Hibernate s'en charge via TenantContext.
        return mapper.toDto(personnelRepository.save(entity));
    }

    @Override
    @Transactional
    public PersonnelDto update(Long personnelId, PersonnelCreateDto dto) {
        logger.info("Modification personnel id={}", personnelId);
        Personnel entity = personnelRepository.findById(personnelId)
                .orElseThrow(() -> new ResourceNotFoundException("error.personnel.notFound"));

        // Trim defensif (Tour 30 etape 2)
        String numeroEmploye = trimToNull(dto.numeroEmploye());
        String email = trimToNull(dto.email());

        // Unicite numero employe (excluant le personnel courant)
        if (numeroEmploye != null
                && !numeroEmploye.equals(entity.getNumeroEmploye())
                && personnelRepository.existsByNumeroEmployeAndPersonnelIdNot(numeroEmploye, personnelId)) {
            throw new BusinessException("error.personnel.numeroEmploye.duplicate");
        }

        // Tour 30 etape 2 (FIX 🔴) : unicite email a l'update — manquait
        // pre-Tour 30, ce qui permettait a un agent de "voler" l'email d'un
        // autre membre du personnel par simple PUT.
        if (email != null
                && !email.equalsIgnoreCase(entity.getEmail())
                && personnelRepository.existsByEmailIgnoreCaseAndPersonnelIdNot(email, personnelId)) {
            throw new BusinessException("error.personnel.email.duplicate");
        }

        entity.setNumeroEmploye(numeroEmploye);
        entity.setPrenom(dto.prenom());
        entity.setNom(dto.nom());
        entity.setTelephone(dto.telephone());
        entity.setEmail(email);
        entity.setDateEmbauche(dto.dateEmbauche());
        entity.setSpecialites(dto.specialites());
        return mapper.toDto(personnelRepository.save(entity));
    }

    /**
     * Trim + null si chaine resultante vide. Centralise pour les comparaisons
     * d'unicite (numero, email) afin d'eviter les doublons logiques bases sur
     * l'espace blanc.
     */
    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public PersonnelDto findById(Long personnelId) {
        Personnel entity = personnelRepository.findById(personnelId)
                .orElseThrow(() -> new ResourceNotFoundException("error.personnel.notFound"));
        return mapper.toDto(entity);
    }

    @Override
    public PersonnelDto findByNumero(String numeroEmploye) {
        Personnel entity = personnelRepository.findByNumeroEmploye(numeroEmploye)
                .orElseThrow(() -> new ResourceNotFoundException("error.personnel.notFound"));
        return mapper.toDto(entity);
    }

    @Override
    public List<PersonnelDto> findAllActive() {
        return personnelRepository.findByActifTrueOrderByPrenomAscNomAsc()
                .stream().map(mapper::toDto).toList();
    }

    @Override
    public Page<PersonnelDto> findAll(Boolean actif, Pageable pageable) {
        Page<Personnel> page = (actif != null)
                ? personnelRepository.findByActifOrderByPrenomAscNomAsc(actif, pageable)
                : personnelRepository.findAllByOrderByPrenomAscNomAsc(pageable);
        return page.map(mapper::toDto);
    }

    @Override
    public List<PersonnelDto> search(String terme) {
        if (terme == null || terme.isBlank()) {
            return List.of();
        }
        return personnelRepository.rechercher(terme.trim())
                .stream().map(mapper::toDto).toList();
    }

    @Override
    public List<PersonnelDto> findBySpecialite(String specialite) {
        if (specialite == null || specialite.isBlank()) {
            return List.of();
        }
        return personnelRepository.findBySpecialite(specialite.trim())
                .stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional
    public void deactivate(Long personnelId) {
        logger.info("Desactivation personnel id={}", personnelId);
        Personnel entity = personnelRepository.findById(personnelId)
                .orElseThrow(() -> new ResourceNotFoundException("error.personnel.notFound"));

        // Refuser si des taches sont EN_COURS
        long enCours = tacheRepository.findByPersonnelIdAndStatut(personnelId, StatutTache.EN_COURS).size();
        if (enCours > 0) {
            throw new BusinessException("error.personnel.hasTasksInProgress");
        }
        entity.setActif(Boolean.FALSE);
        personnelRepository.save(entity);
    }

    @Override
    @Transactional
    public void reactivate(Long personnelId) {
        logger.info("Reactivation personnel id={}", personnelId);
        Personnel entity = personnelRepository.findById(personnelId)
                .orElseThrow(() -> new ResourceNotFoundException("error.personnel.notFound"));
        entity.setActif(Boolean.TRUE);
        personnelRepository.save(entity);
    }

    @Override
    public List<PersonnelDto> findDisponibles(LocalDate date) {
        LocalDate effective = (date != null) ? date : LocalDate.now(clock);
        logger.debug("Recherche personnel disponible pour date={}", effective);
        return personnelRepository.findDisponiblesAtDate(effective)
                .stream()
                .map(mapper::toDto)
                .toList();
    }
}
