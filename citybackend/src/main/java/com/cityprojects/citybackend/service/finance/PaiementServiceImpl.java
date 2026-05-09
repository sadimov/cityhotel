package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.finance.AffectationCreateDto;
import com.cityprojects.citybackend.dto.finance.AffectationPaiementDto;
import com.cityprojects.citybackend.dto.finance.PaiementCreateDto;
import com.cityprojects.citybackend.dto.finance.PaiementDto;
import com.cityprojects.citybackend.entity.finance.AffectationPaiement;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.Paiement;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.StatutPaiement;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.finance.PaiementMapper;
import com.cityprojects.citybackend.repository.finance.AffectationPaiementRepository;
import com.cityprojects.citybackend.repository.finance.CompteRepository;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.repository.finance.PaiementRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Implementation de {@link PaiementService}.
 *
 * <p>Vigilance comptable :
 * <ul>
 *   <li>Validation stricte du montant : {@code paiement.montant &lt;= facture.montantRestant}
 *       a chaque affectation.</li>
 *   <li>Recalcul atomique de {@code Facture.montantPaye} et transition de statut
 *       (EMISE -&gt; PARTIELLEMENT_PAYEE -&gt; PAYEE).</li>
 *   <li>Soft delete uniquement (statut ANNULE).</li>
 * </ul>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class PaiementServiceImpl implements PaiementService {

    private static final Logger logger = LoggerFactory.getLogger(PaiementServiceImpl.class);

    private final PaiementRepository paiementRepository;
    private final AffectationPaiementRepository affectationRepository;
    private final FactureRepository factureRepository;
    private final CompteRepository compteRepository;
    private final PaiementMapper mapper;
    private final NumerotationService numerotationService;
    private final CompteService compteService;
    private final OperationCompteService operationCompteService;

    public PaiementServiceImpl(PaiementRepository paiementRepository,
                               AffectationPaiementRepository affectationRepository,
                               FactureRepository factureRepository,
                               CompteRepository compteRepository,
                               PaiementMapper mapper,
                               NumerotationService numerotationService,
                               CompteService compteService,
                               OperationCompteService operationCompteService) {
        this.paiementRepository = paiementRepository;
        this.affectationRepository = affectationRepository;
        this.factureRepository = factureRepository;
        this.compteRepository = compteRepository;
        this.mapper = mapper;
        this.numerotationService = numerotationService;
        this.compteService = compteService;
        this.operationCompteService = operationCompteService;
    }

    @Override
    @Transactional
    public PaiementDto create(PaiementCreateDto dto) {
        Paiement paiement = new Paiement();
        // Validation tenant-aware de la FK compteId : Hibernate filtre la lecture
        // via @TenantId, donc un findById() depuis un autre hotel renvoie
        // Optional.empty() -> impossible de creer un paiement sur un compte croise.
        if (dto.compteId() != null) {
            compteRepository.findById(dto.compteId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.compte.notFound"));
        }
        paiement.setCompteId(dto.compteId());
        paiement.setMontantTotal(dto.montantTotal());
        paiement.setDevise(dto.devise() != null ? dto.devise() : "MRU");
        paiement.setModePaiement(dto.modePaiement());
        paiement.setReferencePaiement(dto.referencePaiement());
        paiement.setDatePaiement(dto.datePaiement() != null ? dto.datePaiement() : LocalDate.now());
        paiement.setCommentaires(dto.commentaires());
        paiement.setStatut(StatutPaiement.VALIDE);
        paiement.setUserId(currentUserId());
        paiement.setNumeroPaiement(numerotationService.next(TypeNumerotation.PAY));
        // PAS de setHotelId : Hibernate via @TenantId.

        Paiement saved = paiementRepository.save(paiement);

        // Affectation directe si factureId fourni
        if (dto.factureId() != null) {
            affecterUneAffectation(saved.getPaiementId(), dto.factureId(), dto.montantTotal());
        }

        logger.info("Paiement cree : id={}, numero={}, montant={}, mode={}",
                saved.getPaiementId(), saved.getNumeroPaiement(),
                saved.getMontantTotal(), saved.getModePaiement());
        return toDtoWithAffectations(saved);
    }

    @Override
    public PaiementDto findById(Long paiementId) {
        Paiement paiement = paiementRepository.findById(paiementId)
                .orElseThrow(() -> new ResourceNotFoundException("error.paiement.notFound"));
        return toDtoWithAffectations(paiement);
    }

    @Override
    public Page<PaiementDto> findAll(Pageable pageable) {
        return paiementRepository.findAll(pageable).map(this::toDtoWithAffectations);
    }

    @Override
    @Transactional
    public PaiementDto affecter(Long paiementId, List<AffectationCreateDto> affectations) {
        Paiement paiement = paiementRepository.findById(paiementId)
                .orElseThrow(() -> new ResourceNotFoundException("error.paiement.notFound"));
        if (paiement.getStatut() != StatutPaiement.VALIDE) {
            throw new BusinessException("error.paiement.affectation.statutInvalide");
        }

        // Calcul du total deja affecte
        BigDecimal dejaAffecte = affectationRepository
                .findByPaiementIdOrderByDateAffectationAsc(paiementId)
                .stream().map(AffectationPaiement::getMontantAffecte)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal nouveauTotal = dejaAffecte;
        for (AffectationCreateDto aff : affectations) {
            nouveauTotal = nouveauTotal.add(aff.montantAffecte());
        }
        // Tolerance arrondi 0.01
        if (nouveauTotal.compareTo(paiement.getMontantTotal().add(BigDecimal.valueOf(0.01))) > 0) {
            throw new BusinessException("error.paiement.affectation.depasseMontant");
        }

        for (AffectationCreateDto aff : affectations) {
            affecterUneAffectation(paiementId, aff.factureId(), aff.montantAffecte());
        }

        return toDtoWithAffectations(paiement);
    }

    @Override
    @Transactional
    public PaiementDto annuler(Long paiementId) {
        Paiement paiement = paiementRepository.findById(paiementId)
                .orElseThrow(() -> new ResourceNotFoundException("error.paiement.notFound"));
        if (paiement.getStatut() == StatutPaiement.ANNULE) {
            throw new BusinessException("error.paiement.dejaAnnule");
        }
        List<AffectationPaiement> aff = affectationRepository
                .findByPaiementIdOrderByDateAffectationAsc(paiementId);
        if (!aff.isEmpty()) {
            throw new BusinessException("error.paiement.annulation.affectationsExistantes");
        }
        paiement.setStatut(StatutPaiement.ANNULE);
        paiementRepository.save(paiement);
        logger.info("Paiement annule : id={}, numero={}", paiement.getPaiementId(), paiement.getNumeroPaiement());
        return toDtoWithAffectations(paiement);
    }

    /**
     * Cree une affectation et met a jour le montantPaye + statut de la facture.
     * Atomique dans la transaction parente.
     *
     * <p>Tour 22.1 : enregistre egalement un CREDIT sur le compte auxiliaire
     * client (audit trail). Coherent avec le DEBIT pose a l'emission de la
     * facture par {@link FactureServiceImpl#emettre(Long)}. Comme le service
     * Tour 19 cree les paiements directement {@code VALIDE}, l'affectation est
     * le seul moment ou l'on a la garantie d'un paiement valide associe a une
     * facture donnee : c'est donc ici qu'on inscrit le CREDIT.</p>
     */
    private void affecterUneAffectation(Long paiementId, Long factureId, BigDecimal montant) {
        Facture facture = factureRepository.findById(factureId)
                .orElseThrow(() -> new ResourceNotFoundException("error.facture.notFound"));
        if (facture.getStatut() != StatutFacture.EMISE
                && facture.getStatut() != StatutFacture.PARTIELLEMENT_PAYEE) {
            throw new BusinessException("error.paiement.facture.statutInvalide");
        }
        BigDecimal restant = facture.getMontantRestant();
        if (montant.compareTo(restant.add(BigDecimal.valueOf(0.01))) > 0) {
            throw new BusinessException("error.paiement.depasseMontantRestant");
        }

        AffectationPaiement aff = new AffectationPaiement();
        aff.setPaiementId(paiementId);
        aff.setFactureId(factureId);
        aff.setMontantAffecte(montant);
        affectationRepository.save(aff);

        // Met a jour montantPaye + statut
        BigDecimal nouveauPaye = facture.getMontantPaye().add(montant);
        facture.setMontantPaye(nouveauPaye);
        if (nouveauPaye.compareTo(facture.getMontantTtc()) >= 0) {
            facture.setStatut(StatutFacture.PAYEE);
        } else if (nouveauPaye.compareTo(BigDecimal.ZERO) > 0) {
            facture.setStatut(StatutFacture.PARTIELLEMENT_PAYEE);
        }
        factureRepository.save(facture);

        // Audit trail auxiliaire client (Tour 22.1) : CREDIT sur le compte client
        // proportionnel au montant affecte a CETTE facture (chaque affectation
        // peut viser une facture distincte avec un client distinct).
        recordCreditOnAccountIfApplicable(facture, paiementId, montant);
    }

    /**
     * Enregistre un CREDIT sur le compte auxiliaire CLIENT a l'affectation d'un
     * paiement a une facture standard. No-op si :
     * <ul>
     *   <li>la facture est un AVOIR (cas non gere Tour 22.1, differé Tour finance-2),</li>
     *   <li>la facture n'a pas de {@code clientId} (cash anonyme / fournisseur),</li>
     *   <li>{@code montant} est nul ou negatif.</li>
     * </ul>
     *
     * <p>TODO Tour finance-2 : gerer cas AVOIR (ecriture inverse).</p>
     */
    @SuppressWarnings("deprecation")
    private void recordCreditOnAccountIfApplicable(Facture facture, Long paiementId, BigDecimal montant) {
        if (facture.getTypeFacture() != com.cityprojects.citybackend.entity.finance.TypeFacture.FACTURE) {
            return;
        }
        if (facture.getClientId() == null) {
            return;
        }
        if (montant == null || montant.signum() <= 0) {
            return;
        }
        var compte = compteService.findOrCreateForClient(facture.getClientId());
        operationCompteService.recordCredit(
                compte.getCompteId(),
                montant,
                paiementId,
                "Paiement sur " + facture.getNumeroFacture());
    }

    private PaiementDto toDtoWithAffectations(Paiement paiement) {
        PaiementDto base = mapper.toDto(paiement);
        List<AffectationPaiementDto> affs = affectationRepository
                .findByPaiementIdOrderByDateAffectationAsc(paiement.getPaiementId())
                .stream().map(mapper::toAffectationDto).toList();
        return mapper.withAffectations(base, affs);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId();
        }
        throw new BusinessException("error.user.unknown");
    }
}
