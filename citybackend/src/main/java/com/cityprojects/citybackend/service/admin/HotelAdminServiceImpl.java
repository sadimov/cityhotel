package com.cityprojects.citybackend.service.admin;

import com.cityprojects.citybackend.dto.admin.HotelAdminDto;
import com.cityprojects.citybackend.dto.admin.HotelCreateAdminDto;
import com.cityprojects.citybackend.dto.admin.HotelUpdateAdminDto;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.admin.HotelAdminMapper;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.service.finance.CompteMappingInitializer;
import com.cityprojects.citybackend.service.finance.JournalComptableInitializer;
import com.cityprojects.citybackend.service.finance.TauxTvaConfigInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation de {@link HotelAdminService}.
 *
 * <h2>Pas de @RequireTenant</h2>
 * <p>Service technique cross-tenant : toutes les operations doivent
 * fonctionner en mode ROOT (TenantContext vide). L'invariant
 * {@code @RequireTenant} ne s'applique pas : la securite est garantie par
 * {@code @PreAuthorize("hasRole('SUPERADMIN')")} sur le controller
 * {@link com.cityprojects.citybackend.controller.admin.HotelAdminController}.</p>
 *
 * <p>Les entites {@link Hotel} ne portent pas {@code @TenantId} (cf.
 * {@link com.cityprojects.citybackend.entity.core.Hotel}), donc Hibernate
 * ne filtre rien : {@code findAll}, {@code findById}, etc. retournent
 * naturellement les donnees de tous les hotels.</p>
 */
@Service
@Transactional(readOnly = true)
public class HotelAdminServiceImpl implements HotelAdminService {

    private static final Logger logger = LoggerFactory.getLogger(HotelAdminServiceImpl.class);

    private final HotelRepository hotelRepository;
    private final HotelAdminMapper hotelMapper;
    private final CompteMappingInitializer compteMappingInitializer;
    private final JournalComptableInitializer journalComptableInitializer;
    private final TauxTvaConfigInitializer tauxTvaConfigInitializer;

    public HotelAdminServiceImpl(HotelRepository hotelRepository,
                                 HotelAdminMapper hotelMapper,
                                 CompteMappingInitializer compteMappingInitializer,
                                 JournalComptableInitializer journalComptableInitializer,
                                 TauxTvaConfigInitializer tauxTvaConfigInitializer) {
        this.hotelRepository = hotelRepository;
        this.hotelMapper = hotelMapper;
        this.compteMappingInitializer = compteMappingInitializer;
        this.journalComptableInitializer = journalComptableInitializer;
        this.tauxTvaConfigInitializer = tauxTvaConfigInitializer;
    }

    @Override
    @Transactional
    public HotelAdminDto create(HotelCreateAdminDto dto) {
        logger.info("Admin: creation hotel code={}", dto.hotelCode());
        if (hotelRepository.existsByHotelCode(dto.hotelCode())) {
            throw new BusinessException("error.hotel.code.alreadyExists");
        }
        Hotel entity = hotelMapper.toEntity(dto);
        // Defauts metier (defauts deja portes par l'entite mais reinforces ici
        // pour explicitement documenter les valeurs initiales).
        entity.setActif(Boolean.TRUE);
        if (entity.getDevise() == null || entity.getDevise().isBlank()) {
            entity.setDevise("MRU");
        }
        if (entity.getCodePays() == null || entity.getCodePays().isBlank()) {
            entity.setCodePays("MR");
        }
        if (entity.getFuseauHoraire() == null || entity.getFuseauHoraire().isBlank()) {
            entity.setFuseauHoraire("Africa/Nouakchott");
        }
        Hotel saved = hotelRepository.save(entity);
        logger.info("Admin: hotel cree id={} code={}", saved.getHotelId(), saved.getHotelCode());

        // Seed automatique des mappings comptables par defaut (B1) :
        // l'hotel a immediatement une configuration comptable exploitable
        // (codes par defaut depuis TypeEvenementComptable.defaultCompteCode).
        // Le bean dedie ouvre une transaction REQUIRES_NEW + TenantScope.runAs,
        // donc le seed est indépendant du flow de creation d'hotel et
        // idempotent en cas de rejeu manuel.
        compteMappingInitializer.initDefaultsForHotel(saved.getHotelId());

        // Seed automatique des 6 journaux comptables standards (B2) :
        // VTE, ACH, BAN, CAI, OD, AVO. Idempotent et isole transactionnel
        // (REQUIRES_NEW + TenantScope.runAs).
        journalComptableInitializer.seedDefault(saved.getHotelId());

        // Seed automatique des configurations TVA par defaut (B4) :
        // 7 types de services (HEBERGEMENT_NUITEE 0%, RESTAURATION 16%, etc.).
        // Idempotent et isole transactionnel.
        tauxTvaConfigInitializer.seedDefault(saved.getHotelId());

        return hotelMapper.toDto(saved);
    }

    @Override
    @Transactional
    public HotelAdminDto update(Long hotelId, HotelUpdateAdminDto dto) {
        logger.info("Admin: update hotel id={}", hotelId);
        Hotel entity = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("error.hotel.notFound"));
        hotelMapper.updateEntity(entity, dto);
        return hotelMapper.toDto(hotelRepository.save(entity));
    }

    @Override
    public HotelAdminDto findById(Long hotelId) {
        Hotel entity = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("error.hotel.notFound"));
        return hotelMapper.toDto(entity);
    }

    @Override
    public Page<HotelAdminDto> findAll(Pageable pageable) {
        return hotelRepository.findAll(pageable).map(hotelMapper::toDto);
    }

    @Override
    @Transactional
    public void desactiver(Long hotelId) {
        logger.info("Admin: desactivation hotel id={}", hotelId);
        Hotel entity = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("error.hotel.notFound"));
        if (Boolean.FALSE.equals(entity.getActif())) {
            // Idempotent : pas d'erreur, juste un log debug.
            logger.debug("Hotel {} deja desactive", hotelId);
            return;
        }
        entity.setActif(Boolean.FALSE);
        hotelRepository.save(entity);
    }

    @Override
    @Transactional
    public void reactiver(Long hotelId) {
        logger.info("Admin: reactivation hotel id={}", hotelId);
        Hotel entity = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("error.hotel.notFound"));
        if (Boolean.TRUE.equals(entity.getActif())) {
            logger.debug("Hotel {} deja actif", hotelId);
            return;
        }
        entity.setActif(Boolean.TRUE);
        hotelRepository.save(entity);
    }
}
