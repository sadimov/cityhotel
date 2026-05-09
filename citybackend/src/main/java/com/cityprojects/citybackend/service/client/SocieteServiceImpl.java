package com.cityprojects.citybackend.service.client;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.client.SocieteCreateDto;
import com.cityprojects.citybackend.dto.client.SocieteDto;
import com.cityprojects.citybackend.dto.client.SocieteUpdateDto;
import com.cityprojects.citybackend.entity.client.Societe;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.client.SocieteMapper;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.client.SocieteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implementation de {@link SocieteService}.
 * <p>
 * Patterns appliques :
 * <ul>
 *   <li>{@code @RequireTenant} : refuse tout appel sans TenantContext (cle i18n
 *       {@code error.tenant.missing}).</li>
 *   <li>{@code @Transactional(readOnly = true)} au niveau classe, override en ecriture.</li>
 *   <li>Constructeur manuel (palier 1, pas de Lombok).</li>
 *   <li>Levee d'exceptions metier portant des cles i18n traduites par
 *       {@link com.cityprojects.citybackend.exception.GlobalExceptionHandler}.</li>
 *   <li>Aucun {@code setHotelId} explicite : la valeur est resolue par Hibernate
 *       via le {@link com.cityprojects.citybackend.common.tenant.CityTenantIdentifierResolver}.</li>
 * </ul>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class SocieteServiceImpl implements SocieteService {

    private static final Logger logger = LoggerFactory.getLogger(SocieteServiceImpl.class);

    /**
     * Plafond defensif pour {@link #findAllActive()} (endpoint
     * {@code GET /api/societes/active}, typiquement consomme par un select
     * dropdown front). Au-dela, on log un WARN car la liste devient trop grosse
     * pour un dropdown - le front doit basculer sur la version paginee
     * {@link #findAllActive(Pageable)} avec recherche.
     */
    private static final int FIND_ALL_ACTIVE_HARD_LIMIT = 1000;

    private final SocieteRepository societeRepository;
    private final ClientRepository clientRepository;
    private final SocieteMapper societeMapper;

    public SocieteServiceImpl(SocieteRepository societeRepository,
                              ClientRepository clientRepository,
                              SocieteMapper societeMapper) {
        this.societeRepository = societeRepository;
        this.clientRepository = clientRepository;
        this.societeMapper = societeMapper;
    }

    /**
     * Cree une nouvelle societe.
     *
     * <h2>Semantique des unicites (nom, SIRET, email)</h2>
     * <p>L'unicite de {@code societeNom} (case-insensitive) et {@code siret} est
     * verifiee sur <b>toutes</b> les societes du tenant, y compris les inactives.
     * Une societe desactivee conserve donc son nom et son SIRET reserves : si
     * elle revient, on doit la reactiver (ne pas en creer une nouvelle). Meme
     * principe pour {@code email} (cf. unicite cote service appelant le cas
     * echeant) - coherent avec la regle appliquee aux clients.</p>
     *
     * <p><b>Attention :</b> la contrainte est <b>app-only</b> (pas de UNIQUE SQL
     * sur ces colonnes). Sous concurrence, deux INSERT simultanes peuvent passer
     * la verification. Mitigation acceptee : faible probabilite, ecart detecte
     * en revue manuelle. A durcir avec des partial index Postgres
     * {@code UNIQUE (hotel_id, lower(societe_nom))} et
     * {@code UNIQUE (hotel_id, siret)} si l'ecart devient un probleme.</p>
     */
    @Override
    @Transactional
    public SocieteDto create(SocieteCreateDto dto) {
        logger.info("Creation d'une societe : nom={}", dto.societeNom());

        // Verifications metier d'unicite (dans le tenant courant — Hibernate filtre auto)
        if (societeRepository.existsBySocieteNomIgnoreCase(dto.societeNom())) {
            throw new BusinessException("error.societe.nom.alreadyExists");
        }
        if (dto.siret() != null && !dto.siret().isBlank()
                && societeRepository.existsBySiret(dto.siret())) {
            throw new BusinessException("error.societe.siret.alreadyExists");
        }

        Societe entity = societeMapper.toEntity(dto);
        entity.setActif(Boolean.TRUE);
        // PAS de setHotelId : Hibernate populate via le resolver a l'INSERT.

        Societe saved = societeRepository.save(entity);
        logger.info("Societe creee : id={}", saved.getSocieteId());
        return societeMapper.toDto(saved);
    }

    @Override
    @Transactional
    public SocieteDto update(Long societeId, SocieteUpdateDto dto) {
        logger.info("Modification de la societe id={}", societeId);

        Societe entity = societeRepository.findById(societeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.societe.notFound"));

        // Conflit de nom : interdit si une AUTRE societe porte deja ce nom dans le tenant
        Optional<Societe> existing =
                societeRepository.findBySocieteNomIgnoreCase(dto.societeNom());
        if (existing.isPresent() && !existing.get().getSocieteId().equals(societeId)) {
            throw new BusinessException("error.societe.nom.alreadyExists");
        }

        // Conflit de SIRET (idem)
        if (dto.siret() != null && !dto.siret().isBlank()) {
            Optional<Societe> bySiret = societeRepository.findBySiret(dto.siret());
            if (bySiret.isPresent() && !bySiret.get().getSocieteId().equals(societeId)) {
                throw new BusinessException("error.societe.siret.alreadyExists");
            }
        }

        societeMapper.updateEntity(entity, dto);
        Societe saved = societeRepository.save(entity);
        return societeMapper.toDto(saved);
    }

    @Override
    public SocieteDto findById(Long societeId) {
        Societe entity = societeRepository.findById(societeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.societe.notFound"));
        return societeMapper.toDto(entity);
    }

    @Override
    public List<SocieteDto> findAllActive() {
        // Borne defensive : on appelle la variante paginee avec un plafond,
        // pour eviter un OOM si un tenant a des milliers de societes actives.
        // Endpoint expose : GET /api/societes/active (utilise pour dropdowns front).
        Page<Societe> page = societeRepository.findByActifTrueOrderBySocieteNomAsc(
                PageRequest.of(0, FIND_ALL_ACTIVE_HARD_LIMIT));
        if (page.getTotalElements() > FIND_ALL_ACTIVE_HARD_LIMIT) {
            logger.warn("findAllActive() societes : {} actives mais plafond defensif {} atteint - "
                            + "le front DOIT basculer sur la version paginee avec recherche",
                    page.getTotalElements(), FIND_ALL_ACTIVE_HARD_LIMIT);
        }
        return page.getContent().stream()
                .map(societeMapper::toDto)
                .toList();
    }

    @Override
    public Page<SocieteDto> findAllActive(Pageable pageable) {
        return societeRepository.findByActifTrueOrderBySocieteNomAsc(pageable)
                .map(societeMapper::toDto);
    }

    @Override
    public Page<SocieteDto> search(String recherche, Pageable pageable) {
        if (recherche == null || recherche.isBlank()) {
            return findAllActive(pageable);
        }
        return societeRepository.searchSocietes(recherche.trim(), pageable)
                .map(societeMapper::toDto);
    }

    @Override
    @Transactional
    public void deactivate(Long societeId) {
        logger.info("Desactivation de la societe id={}", societeId);
        Societe entity = societeRepository.findById(societeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.societe.notFound"));

        long activeClients = clientRepository.countBySocieteIdAndActifTrue(societeId);
        if (activeClients > 0) {
            throw new BusinessException("error.societe.deactivate.hasActiveClients");
        }
        entity.setActif(Boolean.FALSE);
        societeRepository.save(entity);
    }

    @Override
    @Transactional
    public void reactivate(Long societeId) {
        logger.info("Reactivation de la societe id={}", societeId);
        Societe entity = societeRepository.findById(societeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.societe.notFound"));
        entity.setActif(Boolean.TRUE);
        societeRepository.save(entity);
    }

    @Override
    @Transactional
    public void delete(Long societeId) {
        logger.info("Suppression definitive de la societe id={}", societeId);
        Societe entity = societeRepository.findById(societeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.societe.notFound"));

        long activeClients = clientRepository.countBySocieteIdAndActifTrue(societeId);
        if (activeClients > 0) {
            throw new BusinessException("error.societe.delete.hasActiveClients");
        }
        societeRepository.delete(entity);
    }
}
