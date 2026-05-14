package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.hebergement.MontantCalculDetailDto;
import com.cityprojects.citybackend.dto.hebergement.MontantCalculDto;
import com.cityprojects.citybackend.dto.hebergement.TarifChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.TarifChambreDto;
import com.cityprojects.citybackend.entity.hebergement.TarifChambre;
import com.cityprojects.citybackend.entity.hebergement.TypeChambre;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.hebergement.TarifChambreMapper;
import com.cityprojects.citybackend.repository.hebergement.TarifChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.TypeChambreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation de {@link TarifChambreService} - Tour 44 Phase 1.
 *
 * <p>Strategie de selection (cf. interface) : tarif saisonnier actif applicable
 * a la date avec priorite la plus haute, sinon prixBase du TypeChambre.</p>
 *
 * <p>Origine du prix exposee dans {@code MontantCalculDetailDto.origine} pour
 * traçabilite cote front : {@code "TARIF:<nomTarif>"}, {@code "TARIF_WEEKEND:<nomTarif>"}
 * ou {@code "PRIX_BASE"}.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class TarifChambreServiceImpl implements TarifChambreService {

    private static final Logger logger = LoggerFactory.getLogger(TarifChambreServiceImpl.class);

    private final TarifChambreRepository tarifRepository;
    private final TypeChambreRepository typeChambreRepository;
    private final TarifChambreMapper mapper;

    public TarifChambreServiceImpl(TarifChambreRepository tarifRepository,
                                   TypeChambreRepository typeChambreRepository,
                                   TarifChambreMapper mapper) {
        this.tarifRepository = tarifRepository;
        this.typeChambreRepository = typeChambreRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TarifChambreDto create(TarifChambreCreateDto dto) {
        validateDates(dto);
        // Verifie que le typeChambre existe pour le tenant courant.
        typeChambreRepository.findById(dto.typeId())
                .orElseThrow(() -> new ResourceNotFoundException("error.typeChambre.notFound"));
        TarifChambre entity = mapper.toEntity(dto);
        if (entity.getActif() == null) {
            entity.setActif(Boolean.TRUE);
        }
        if (entity.getPriorite() == null) {
            entity.setPriorite(0);
        }
        TarifChambre saved = tarifRepository.save(entity);
        logger.info("Tarif chambre cree : id={}, typeId={}, nom={}, prix={}",
                saved.getTarifId(), saved.getTypeId(), saved.getNomTarif(), saved.getPrixNuit());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public TarifChambreDto update(Long tarifId, TarifChambreCreateDto dto) {
        validateDates(dto);
        TarifChambre tarif = tarifRepository.findById(tarifId)
                .orElseThrow(() -> new ResourceNotFoundException("error.tarifChambre.notFound"));
        // Si le typeId change, verifier qu'il existe pour le tenant
        if (!tarif.getTypeId().equals(dto.typeId())) {
            typeChambreRepository.findById(dto.typeId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.typeChambre.notFound"));
        }
        mapper.updateEntity(tarif, dto);
        return mapper.toDto(tarifRepository.save(tarif));
    }

    @Override
    @Transactional
    public void delete(Long tarifId) {
        TarifChambre tarif = tarifRepository.findById(tarifId)
                .orElseThrow(() -> new ResourceNotFoundException("error.tarifChambre.notFound"));
        // Soft-delete : on desactive (audit trail tarification)
        tarif.setActif(Boolean.FALSE);
        tarifRepository.save(tarif);
    }

    @Override
    public TarifChambreDto findById(Long tarifId) {
        return mapper.toDto(tarifRepository.findById(tarifId)
                .orElseThrow(() -> new ResourceNotFoundException("error.tarifChambre.notFound")));
    }

    @Override
    public List<TarifChambreDto> findByType(Long typeId) {
        return tarifRepository.findByTypeIdAndActifTrueOrderByDateDebutAsc(typeId)
                .stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    public BigDecimal getPrixForDate(Long typeChambreId, LocalDate date) {
        if (typeChambreId == null || date == null) {
            throw new BusinessException("error.tarifChambre.calcul.parametres.required");
        }
        // Verifie que le typeChambre existe pour le tenant courant
        TypeChambre typeChambre = typeChambreRepository.findById(typeChambreId)
                .orElseThrow(() -> new ResourceNotFoundException("error.typeChambre.notFound"));

        List<TarifChambre> applicables = tarifRepository.findApplicableTarifs(typeChambreId, date);
        if (!applicables.isEmpty()) {
            TarifChambre tarif = applicables.get(0);
            BigDecimal prix = pickPrix(tarif, date);
            if (prix != null) {
                return prix;
            }
        }
        // Fallback : prixBase du TypeChambre
        BigDecimal prixBase = typeChambre.getPrixBase();
        return prixBase != null ? prixBase : BigDecimal.ZERO;
    }

    @Override
    public MontantCalculDto calculer(Long typeChambreId, LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut == null || dateFin == null) {
            throw new BusinessException("error.tarifChambre.calcul.dates.required");
        }
        if (!dateFin.isAfter(dateDebut)) {
            throw new BusinessException("error.tarifChambre.calcul.dates.invalid");
        }
        // Verifie que le typeChambre existe pour le tenant courant
        TypeChambre typeChambre = typeChambreRepository.findById(typeChambreId)
                .orElseThrow(() -> new ResourceNotFoundException("error.typeChambre.notFound"));

        List<MontantCalculDetailDto> detail = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int nbNuits = 0;
        LocalDate jour = dateDebut;
        while (jour.isBefore(dateFin)) {
            BigDecimal prix;
            String origine;
            List<TarifChambre> applicables = tarifRepository.findApplicableTarifs(typeChambreId, jour);
            if (!applicables.isEmpty()) {
                TarifChambre tarif = applicables.get(0);
                BigDecimal prixCandidat = pickPrix(tarif, jour);
                if (prixCandidat != null) {
                    prix = prixCandidat;
                    origine = isWeekend(jour) && tarif.getPrixWeekend() != null
                            ? "TARIF_WEEKEND:" + tarif.getNomTarif()
                            : "TARIF:" + tarif.getNomTarif();
                } else {
                    prix = typeChambre.getPrixBase() != null ? typeChambre.getPrixBase() : BigDecimal.ZERO;
                    origine = "PRIX_BASE";
                }
            } else {
                prix = typeChambre.getPrixBase() != null ? typeChambre.getPrixBase() : BigDecimal.ZERO;
                origine = "PRIX_BASE";
            }
            detail.add(new MontantCalculDetailDto(jour, prix, origine));
            total = total.add(prix);
            nbNuits++;
            jour = jour.plusDays(1);
        }
        BigDecimal montantHt = total.setScale(2, RoundingMode.HALF_UP);
        // Pas de TVA (palier 1) : montantTtc == montantHt
        return new MontantCalculDto(typeChambreId, nbNuits, montantHt, montantHt, detail);
    }

    /**
     * Selectionne le prix applicable pour un tarif et une date :
     * {@code prixWeekend} si samedi/dimanche ET non null, sinon {@code prixNuit}.
     */
    private BigDecimal pickPrix(TarifChambre tarif, LocalDate date) {
        if (isWeekend(date) && tarif.getPrixWeekend() != null) {
            return tarif.getPrixWeekend();
        }
        return tarif.getPrixNuit();
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek d = date.getDayOfWeek();
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }

    private void validateDates(TarifChambreCreateDto dto) {
        if (dto.dateFin() != null && dto.dateFin().isBefore(dto.dateDebut())) {
            throw new BusinessException("error.tarifChambre.dateFin.beforeDebut");
        }
    }
}
