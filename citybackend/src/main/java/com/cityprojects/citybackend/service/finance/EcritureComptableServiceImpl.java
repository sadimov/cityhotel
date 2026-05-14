package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.audit.AuditFinanceAction;
import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.finance.EcritureComptableCreateDto;
import com.cityprojects.citybackend.dto.finance.EcritureComptableDto;
import com.cityprojects.citybackend.dto.finance.LigneEcritureCreateDto;
import com.cityprojects.citybackend.entity.finance.EcritureComptable;
import com.cityprojects.citybackend.entity.finance.Exercice;
import com.cityprojects.citybackend.entity.finance.JournalComptable;
import com.cityprojects.citybackend.entity.finance.LigneEcriture;
import com.cityprojects.citybackend.entity.finance.SensLigne;
import com.cityprojects.citybackend.entity.finance.StatutEcriture;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.finance.EcritureComptableMapper;
import com.cityprojects.citybackend.repository.finance.EcritureComptableRepository;
import com.cityprojects.citybackend.repository.finance.ExerciceRepository;
import com.cityprojects.citybackend.repository.finance.JournalComptableRepository;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation de {@link EcritureComptableService}.
 *
 * <h2>Conventions</h2>
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe.</li>
 *   <li>{@code @Transactional(readOnly = true)} au niveau classe, override en
 *       ecriture pour {@link #creer} et {@link #contrePasser}.</li>
 *   <li>Toutes les exceptions metier portent une cle i18n
 *       ({@code error.ecriture.*}, {@code error.exercice.*}).</li>
 *   <li>Le {@code numero} est genere via {@link NumerotationService} (type JRN,
 *       discriminant = code du journal).</li>
 * </ul>
 *
 * <h2>Garde-fou equilibre</h2>
 * <p>La regle Σ debits == Σ credits est validee ici avec tolerance
 * {@link #TOLERANCE_EQUILIBRE} (0.01 MRU = 1 centime). Le trigger PL/pgSQL
 * (changeset 045) sert de defense en profondeur cote BDD.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class EcritureComptableServiceImpl implements EcritureComptableService {

    private static final Logger logger = LoggerFactory.getLogger(EcritureComptableServiceImpl.class);

    /** Tolerance d'arrondi pour la verification d'equilibre (1 centime MRU). */
    private static final BigDecimal TOLERANCE_EQUILIBRE = new BigDecimal("0.01");

    private final EcritureComptableRepository ecritureRepository;
    private final JournalComptableRepository journalRepository;
    private final ExerciceRepository exerciceRepository;
    private final PlanComptableGeneralRepository pcgRepository;
    private final EcritureComptableMapper mapper;
    private final NumerotationService numerotationService;
    private final ExerciceService exerciceService;

    public EcritureComptableServiceImpl(EcritureComptableRepository ecritureRepository,
                                        JournalComptableRepository journalRepository,
                                        ExerciceRepository exerciceRepository,
                                        PlanComptableGeneralRepository pcgRepository,
                                        EcritureComptableMapper mapper,
                                        NumerotationService numerotationService,
                                        ExerciceService exerciceService) {
        this.ecritureRepository = ecritureRepository;
        this.journalRepository = journalRepository;
        this.exerciceRepository = exerciceRepository;
        this.pcgRepository = pcgRepository;
        this.mapper = mapper;
        this.numerotationService = numerotationService;
        this.exerciceService = exerciceService;
    }

    @Override
    @Transactional
    @AuditFinanceAction(value = "ECRITURE_CREATION", entityType = "ECRITURE")
    public EcritureComptableDto creer(EcritureComptableCreateDto dto) {
        if (dto == null) {
            throw new BusinessException("error.ecriture.dtoRequired");
        }
        LocalDate dateComptable = dto.dateComptable();
        if (dateComptable == null) {
            throw new BusinessException("error.ecriture.dateComptableRequired");
        }
        LocalDate datePiece = (dto.datePiece() != null) ? dto.datePiece() : dateComptable;

        // 1) Validation structurelle des lignes
        List<LigneEcritureCreateDto> lignesDto = dto.lignes();
        if (lignesDto == null || lignesDto.size() < 2) {
            throw new BusinessException("error.ecriture.minLines");
        }

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (LigneEcritureCreateDto l : lignesDto) {
            if (l == null) {
                throw new BusinessException("error.ecriture.ligneNull");
            }
            if (l.sens() == null) {
                throw new BusinessException("error.ecriture.sensRequired");
            }
            if (l.montant() == null || l.montant().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("error.ecriture.montantPositif");
            }
            if (l.compteCode() == null || l.compteCode().isBlank()) {
                throw new BusinessException("error.ecriture.compteCodeRequired");
            }
            // 2) Validation du compte dans le PCG
            if (!pcgRepository.existsUtilisableByCode(l.compteCode())) {
                throw new BusinessException("error.ecriture.compteInvalide");
            }
            BigDecimal montant = l.montant().setScale(2, RoundingMode.HALF_UP);
            if (l.sens() == SensLigne.DEBIT) {
                totalDebit = totalDebit.add(montant);
            } else {
                totalCredit = totalCredit.add(montant);
            }
        }
        if (totalDebit.compareTo(BigDecimal.ZERO) <= 0 || totalCredit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("error.ecriture.unbalanced");
        }
        BigDecimal ecart = totalDebit.subtract(totalCredit).abs();
        if (ecart.compareTo(TOLERANCE_EQUILIBRE) > 0) {
            throw new BusinessException("error.ecriture.unbalanced");
        }

        // 3) Validation exercice OUVERT (delegue - peut auto-creer l'exercice courant)
        exerciceService.assertOuvert(dateComptable);
        Exercice exercice = exerciceRepository.findContainingDate(dateComptable)
                .orElseThrow(() -> new BusinessException("error.exercice.dateInvalide"));

        // 4) Resolution du journal
        if (dto.journalCode() == null || dto.journalCode().isBlank()) {
            throw new BusinessException("error.ecriture.journalCodeRequired");
        }
        JournalComptable journal = journalRepository.findByCode(dto.journalCode())
                .orElseThrow(() -> new BusinessException("error.ecriture.journalInvalide"));
        if (!journal.isActif()) {
            throw new BusinessException("error.ecriture.journalInactif");
        }

        // 5) Construction et persistence
        EcritureComptable ecriture = new EcritureComptable();
        ecriture.setNumero(numerotationService.next(TypeNumerotation.JRN, journal.getCode()));
        ecriture.setDateComptable(dateComptable);
        ecriture.setDatePiece(datePiece);
        ecriture.setJournal(journal);
        ecriture.setExercice(exercice);
        ecriture.setLibelle(dto.libelle());
        ecriture.setReference(dto.reference());
        ecriture.setStatut(StatutEcriture.VALIDEE);
        // PAS de setHotelId : Hibernate via @TenantId.

        int ordreCourant = 1;
        for (LigneEcritureCreateDto l : lignesDto) {
            LigneEcriture ligne = new LigneEcriture();
            ligne.setOrdre((l.ordre() != null && l.ordre() > 0) ? l.ordre() : ordreCourant);
            ligne.setCompteCode(l.compteCode());
            ligne.setLibelle(l.libelle());
            ligne.setSens(l.sens());
            ligne.setMontant(l.montant().setScale(2, RoundingMode.HALF_UP));
            ligne.setCompteAuxiliaireRef(l.compteAuxiliaireRef());
            ecriture.addLigne(ligne);
            ordreCourant++;
        }
        // Pre-calcul des totaux (deterministe, ne depend pas du hook @PrePersist
        // qui pourrait ne pas tourner en contexte de tests avec repo mocke).
        // Le hook reste actif en production pour les UPDATE ulterieurs.
        ecriture.setTotalDebit(totalDebit.setScale(2, RoundingMode.HALF_UP));
        ecriture.setTotalCredit(totalCredit.setScale(2, RoundingMode.HALF_UP));

        EcritureComptable saved = ecritureRepository.save(ecriture);
        logger.info("Ecriture comptable creee : id={}, numero={}, journal={}, totalD={}, totalC={}",
                saved.getId(), saved.getNumero(), journal.getCode(),
                saved.getTotalDebit(), saved.getTotalCredit());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    @AuditFinanceAction(value = "ECRITURE_CONTREPASSATION", entityType = "ECRITURE")
    public EcritureComptableDto contrePasser(Long ecritureId, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("error.motif.required");
        }
        EcritureComptable source = ecritureRepository.findById(ecritureId)
                .orElseThrow(() -> new ResourceNotFoundException("error.ecriture.notFound"));

        if (source.getStatut() == StatutEcriture.CONTRE_PASSEE) {
            throw new BusinessException("error.ecriture.alreadyContrePassed");
        }
        if (source.getStatut() != StatutEcriture.VALIDEE) {
            throw new BusinessException("error.ecriture.notValidated");
        }

        JournalComptable journal = source.getJournal();
        if (journal == null) {
            throw new BusinessException("error.ecriture.journalInvalide");
        }
        Exercice exercice = source.getExercice();
        if (exercice == null) {
            throw new BusinessException("error.exercice.dateInvalide");
        }

        // La date de la contre-passation est aujourd'hui : la garde
        // assertOuvert verifie que l'exercice contenant cette date est OUVERT.
        LocalDate today = LocalDate.now();
        exerciceService.assertOuvert(today);
        Exercice exerciceContrePassation = exerciceRepository.findContainingDate(today)
                .orElseThrow(() -> new BusinessException("error.exercice.dateInvalide"));

        // Construit la nouvelle ecriture sur le MEME journal, exercice courant.
        EcritureComptable cp = new EcritureComptable();
        cp.setNumero(numerotationService.next(TypeNumerotation.JRN, journal.getCode()));
        cp.setDateComptable(today);
        cp.setDatePiece(today);
        cp.setJournal(journal);
        cp.setExercice(exerciceContrePassation);
        cp.setLibelle("Contre-passation : " + source.getLibelle() + " (" + motif.trim() + ")");
        cp.setReference("CP-" + source.getNumero());
        cp.setStatut(StatutEcriture.VALIDEE);
        cp.setEcritureSourceId(source.getId());

        // Lignes inversees : D <-> C
        List<LigneEcriture> sourceLignes = new ArrayList<>(source.getLignes());
        int ordreCourant = 1;
        BigDecimal totalDebitInv = BigDecimal.ZERO;
        BigDecimal totalCreditInv = BigDecimal.ZERO;
        for (LigneEcriture s : sourceLignes) {
            LigneEcriture inv = new LigneEcriture();
            inv.setOrdre(ordreCourant++);
            inv.setCompteCode(s.getCompteCode());
            inv.setLibelle(s.getLibelle());
            inv.setSens(s.getSens() == SensLigne.DEBIT ? SensLigne.CREDIT : SensLigne.DEBIT);
            inv.setMontant(s.getMontant());
            inv.setCompteAuxiliaireRef(s.getCompteAuxiliaireRef());
            cp.addLigne(inv);
            if (inv.getSens() == SensLigne.DEBIT) {
                totalDebitInv = totalDebitInv.add(inv.getMontant());
            } else {
                totalCreditInv = totalCreditInv.add(inv.getMontant());
            }
        }
        // Pre-calcul des totaux pour determinisme cote test + coherence DB
        // (le trigger PG verifie totalD == totalC sur les ecritures VALIDEE).
        cp.setTotalDebit(totalDebitInv.setScale(2, RoundingMode.HALF_UP));
        cp.setTotalCredit(totalCreditInv.setScale(2, RoundingMode.HALF_UP));

        EcritureComptable savedCp = ecritureRepository.save(cp);

        // Mise a jour de la source : statut + lien retour
        source.setStatut(StatutEcriture.CONTRE_PASSEE);
        source.setContrePasseeParId(savedCp.getId());
        ecritureRepository.save(source);

        logger.info("Ecriture contre-passee : source={} ({}), cp={} ({}), motif='{}'",
                source.getId(), source.getNumero(), savedCp.getId(), savedCp.getNumero(), motif);

        return mapper.toDto(savedCp);
    }

    @Override
    public EcritureComptableDto findById(Long id) {
        EcritureComptable e = ecritureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.ecriture.notFound"));
        return mapper.toDto(e);
    }

    @Override
    public Page<EcritureComptableDto> findAll(Pageable pageable) {
        return ecritureRepository.findAll(pageable).map(mapper::toDto);
    }

    @Override
    public Page<EcritureComptableDto> findByJournal(Long journalId, LocalDate dateDebut,
                                                    LocalDate dateFin, Pageable pageable) {
        validateDateRange(dateDebut, dateFin);
        return ecritureRepository.findByJournalIdAndDateBetween(journalId, dateDebut, dateFin, pageable)
                .map(mapper::toDto);
    }

    @Override
    public Page<EcritureComptableDto> findByCompte(String compteCode, LocalDate dateDebut,
                                                    LocalDate dateFin, Pageable pageable) {
        if (compteCode == null || compteCode.isBlank()) {
            throw new BusinessException("error.ecriture.compteCodeRequired");
        }
        validateDateRange(dateDebut, dateFin);
        return ecritureRepository.findByCompteCodeAndDateBetween(compteCode, dateDebut, dateFin, pageable)
                .map(mapper::toDto);
    }

    @Override
    public Page<EcritureComptableDto> findByExercice(Long exerciceId, Pageable pageable) {
        return ecritureRepository.findByExerciceId(exerciceId, pageable).map(mapper::toDto);
    }

    private static void validateDateRange(LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut == null || dateFin == null) {
            throw new BusinessException("error.ecriture.dateRequired");
        }
        if (dateFin.isBefore(dateDebut)) {
            throw new BusinessException("error.ecriture.dateRangeInvalide");
        }
    }
}
