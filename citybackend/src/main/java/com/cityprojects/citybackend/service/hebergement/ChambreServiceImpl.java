package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.paging.PageableUtils;
import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.hebergement.ChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ChambreDto;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import com.cityprojects.citybackend.entity.hebergement.TypeChambre;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.hebergement.ChambreMapper;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.TypeChambreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation de {@link ChambreService}.
 *
 * <h3>Transitions de statut</h3>
 * <p>Validees explicitement par {@link #checkTransition(StatutChambre, StatutChambre)}.
 * Cf. {@link StatutChambre} pour la matrice complete.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class ChambreServiceImpl implements ChambreService {

    private static final Logger logger = LoggerFactory.getLogger(ChambreServiceImpl.class);

    /**
     * Matrice des transitions autorisees pour {@link #checkTransition} (Tour
     * 40bis - refactor H6). Issue d'une analyse exhaustive du switch precedent
     * pour preserver strictement le comportement (5 enum values * 5 cibles).
     *
     * <p>Source de verite : le switch precedent. Voir {@link StatutChambre}
     * pour la documentation produit (qui peut differer legerement, en
     * particulier la transition OCCUPEE -&gt; DISPONIBLE n'est PAS bloquee
     * par le code actuel meme si la doctrine encourage le passage par
     * NETTOYAGE).</p>
     */
    private static final Map<StatutChambre, Set<StatutChambre>> TRANSITIONS_AUTORISEES = Map.of(
            StatutChambre.DISPONIBLE,
                    EnumSet.of(StatutChambre.OCCUPEE, StatutChambre.NETTOYAGE, StatutChambre.MAINTENANCE),
            StatutChambre.OCCUPEE,
                    EnumSet.of(StatutChambre.DISPONIBLE, StatutChambre.NETTOYAGE),
            StatutChambre.NETTOYAGE,
                    EnumSet.of(StatutChambre.DISPONIBLE, StatutChambre.MAINTENANCE),
            StatutChambre.MAINTENANCE,
                    EnumSet.of(StatutChambre.DISPONIBLE, StatutChambre.HORS_SERVICE),
            StatutChambre.HORS_SERVICE,
                    EnumSet.of(StatutChambre.MAINTENANCE));

    /**
     * Cles d'erreur i18n par cible. Permet de preserver les memes cles que
     * l'ancien switch pour ne pas casser les tests qui assertent sur le
     * message (cf. {@code ReservationServiceTests#T4}).
     */
    private static final Map<StatutChambre, String> CLES_ERREUR_PAR_CIBLE = Map.of(
            StatutChambre.OCCUPEE,      "error.chambre.transition.toOccupied",
            StatutChambre.DISPONIBLE,   "error.chambre.transition.fromOutOfService",
            StatutChambre.MAINTENANCE,  "error.chambre.transition.maintenanceFromOccupied",
            StatutChambre.NETTOYAGE,    "error.chambre.transition.invalidToCleaning",
            StatutChambre.HORS_SERVICE, "error.chambre.transition.outOfServiceRequiresMaintenance");

    private final ChambreRepository chambreRepository;
    private final TypeChambreRepository typeChambreRepository;
    private final ChambreMapper chambreMapper;

    public ChambreServiceImpl(ChambreRepository chambreRepository,
                              TypeChambreRepository typeChambreRepository,
                              ChambreMapper chambreMapper) {
        this.chambreRepository = chambreRepository;
        this.typeChambreRepository = typeChambreRepository;
        this.chambreMapper = chambreMapper;
    }

    @Override
    @Transactional
    public ChambreDto create(ChambreCreateDto dto) {
        logger.info("Creation chambre : numero={}, typeId={}", dto.numeroChambre(), dto.typeId());

        // Le type doit exister dans le meme tenant (Hibernate filtre auto)
        TypeChambre type = typeChambreRepository.findById(dto.typeId())
                .orElseThrow(() -> new ResourceNotFoundException("error.typeChambre.notFound"));
        if (!Boolean.TRUE.equals(type.getActif())) {
            throw new BusinessException("error.chambre.type.inactive");
        }

        if (chambreRepository.existsByNumeroChambre(dto.numeroChambre())) {
            throw new BusinessException("error.chambre.numero.alreadyExists");
        }

        Chambre entity = chambreMapper.toEntity(dto);
        entity.setActif(Boolean.TRUE);
        if (entity.getStatut() == null) {
            entity.setStatut(StatutChambre.DISPONIBLE);
        }

        return enrichDto(chambreRepository.save(entity));
    }

    @Override
    @Transactional
    public ChambreDto update(Long chambreId, ChambreCreateDto dto) {
        Chambre entity = chambreRepository.findById(chambreId)
                .orElseThrow(() -> new ResourceNotFoundException("error.chambre.notFound"));

        // Type modifie ? validation tenant
        if (!entity.getTypeId().equals(dto.typeId())) {
            TypeChambre type = typeChambreRepository.findById(dto.typeId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.typeChambre.notFound"));
            if (!Boolean.TRUE.equals(type.getActif())) {
                throw new BusinessException("error.chambre.type.inactive");
            }
        }

        // Numero modifie ? unicite
        if (!entity.getNumeroChambre().equals(dto.numeroChambre())
                && chambreRepository.existsByNumeroChambre(dto.numeroChambre())) {
            throw new BusinessException("error.chambre.numero.alreadyExists");
        }

        entity.setNumeroChambre(dto.numeroChambre());
        entity.setTypeId(dto.typeId());
        entity.setEtage(dto.etage());
        entity.setNbLits(dto.nbLits());
        entity.setNbPersonnesMax(dto.nbPersonnesMax());
        entity.setEquipements(dto.equipements());
        entity.setDescription(dto.description());
        // statut non modifie ici : passer par changerStatut()

        return enrichDto(chambreRepository.save(entity));
    }

    @Override
    public ChambreDto findById(Long chambreId) {
        Chambre entity = chambreRepository.findById(chambreId)
                .orElseThrow(() -> new ResourceNotFoundException("error.chambre.notFound"));
        return enrichDto(entity);
    }

    @Override
    public ChambreDto findByNumero(String numeroChambre) {
        Chambre entity = chambreRepository.findByNumeroChambre(numeroChambre)
                .orElseThrow(() -> new ResourceNotFoundException("error.chambre.notFound"));
        return enrichDto(entity);
    }

    @Override
    public Page<ChambreDto> findAll(Pageable pageable) {
        // Tri stable (Tour 14 audit, finding I2) + alias front "dateCreation" -> "createdAt"
        // (finding I4 : alias controller-level pour garder le DTO inchange).
        Pageable remapped = PageableUtils.remapSort(pageable, Map.of("dateCreation", "createdAt"));
        Sort defaultSort = Sort.by(Sort.Order.asc("numeroChambre"));
        Pageable stable = PageableUtils.stable(remapped, defaultSort, "chambreId");
        Page<Chambre> page = chambreRepository.findAll(stable);
        Map<Long, String> typeNoms = batchTypeNoms(page.getContent());
        return page.map(c -> chambreMapper.toDto(c).withResolvedNames(typeNoms.get(c.getTypeId())));
    }

    @Override
    public List<ChambreDto> findAllActive() {
        return enrichList(chambreRepository.findByActifTrueOrderByNumeroChambreAsc());
    }

    @Override
    public List<ChambreDto> findByType(Long typeId) {
        return enrichList(chambreRepository.findByTypeIdAndActifTrueOrderByNumeroChambreAsc(typeId));
    }

    @Override
    public List<ChambreDto> findByStatut(StatutChambre statut) {
        return enrichList(chambreRepository.findByStatutAndActifTrueOrderByNumeroChambreAsc(statut));
    }

    /** Enrichit un DTO unitaire avec le nom du type de chambre (1 SELECT). */
    private ChambreDto enrichDto(Chambre entity) {
        String nomType = (entity.getTypeId() != null)
                ? typeChambreRepository.findById(entity.getTypeId())
                        .map(TypeChambre::getTypeNom).orElse(null)
                : null;
        return chambreMapper.toDto(entity).withResolvedNames(nomType);
    }

    /** Enrichit une liste de chambres en batch (1 SELECT IN sur les types). */
    private List<ChambreDto> enrichList(List<Chambre> entities) {
        Map<Long, String> typeNoms = batchTypeNoms(entities);
        return entities.stream()
                .map(c -> chambreMapper.toDto(c).withResolvedNames(typeNoms.get(c.getTypeId())))
                .collect(Collectors.toList());
    }

    /** Batch lookup type chambre → nom (1 SELECT IN, anti-N+1). */
    private Map<Long, String> batchTypeNoms(List<Chambre> entities) {
        Set<Long> typeIds = entities.stream()
                .map(Chambre::getTypeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (typeIds.isEmpty()) return Map.of();
        return typeChambreRepository.findAllById(typeIds).stream()
                .collect(Collectors.toMap(TypeChambre::getTypeId, TypeChambre::getTypeNom));
    }

    @Override
    @Transactional
    public ChambreDto changerStatut(Long chambreId, StatutChambre nouveauStatut) {
        Chambre entity = chambreRepository.findById(chambreId)
                .orElseThrow(() -> new ResourceNotFoundException("error.chambre.notFound"));

        checkTransition(entity.getStatut(), nouveauStatut);
        entity.setStatut(nouveauStatut);
        return enrichDto(chambreRepository.save(entity));
    }

    @Override
    @Transactional
    public void deactivate(Long chambreId) {
        Chambre entity = chambreRepository.findById(chambreId)
                .orElseThrow(() -> new ResourceNotFoundException("error.chambre.notFound"));
        if (entity.getStatut() == StatutChambre.OCCUPEE) {
            throw new BusinessException("error.chambre.cannotDeactivateOccupied");
        }
        entity.setActif(Boolean.FALSE);
        entity.setStatut(StatutChambre.HORS_SERVICE);
        chambreRepository.save(entity);
    }

    @Override
    @Transactional
    public void reactivate(Long chambreId) {
        Chambre entity = chambreRepository.findById(chambreId)
                .orElseThrow(() -> new ResourceNotFoundException("error.chambre.notFound"));
        if (Boolean.TRUE.equals(entity.getActif())) {
            // Idempotent : pas d'erreur si deja active.
            return;
        }
        entity.setActif(Boolean.TRUE);
        // Sortie obligatoire de HORS_SERVICE -> DISPONIBLE (la matrice
        // checkTransition() refuse le passage HORS_SERVICE -> DISPONIBLE
        // mais on l'autorise ici dans le cadre de la reactivation explicite,
        // qui est elle-meme un acte d'administration).
        entity.setStatut(StatutChambre.DISPONIBLE);
        chambreRepository.save(entity);
    }

    @Override
    public List<ChambreDto> findDisponibles(LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut == null || dateFin == null) {
            throw new BusinessException("error.disponibilite.dates.required");
        }
        if (!dateFin.isAfter(dateDebut)) {
            throw new BusinessException("error.disponibilite.dates.invalid");
        }
        return enrichList(chambreRepository.findDisponibles(dateDebut, dateFin));
    }

    /**
     * Valide les transitions de statut. Voir {@link StatutChambre} pour la matrice
     * documentaire et {@link #TRANSITIONS_AUTORISEES} pour la matrice effective.
     *
     * <p>Tour 40bis (refactor H6) : remplace le switch verbeux par un lookup
     * dans {@link #TRANSITIONS_AUTORISEES}. La cle d'erreur i18n levee est
     * resolue via {@link #CLES_ERREUR_PAR_CIBLE} pour preserver strictement
     * les messages historiques (assertion par les tests).</p>
     */
    private void checkTransition(StatutChambre actuel, StatutChambre nouveau) {
        if (actuel == nouveau) {
            return;
        }
        Set<StatutChambre> autorisees = TRANSITIONS_AUTORISEES.get(actuel);
        if (autorisees == null || !autorisees.contains(nouveau)) {
            String cle = CLES_ERREUR_PAR_CIBLE.getOrDefault(
                    nouveau, "error.chambre.transition.unknown");
            throw new BusinessException(cle);
        }
    }
}
