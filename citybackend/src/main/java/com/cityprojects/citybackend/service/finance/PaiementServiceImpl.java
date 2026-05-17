package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.audit.AuditFinanceAction;
import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.finance.AffectationCreateDto;
import com.cityprojects.citybackend.dto.finance.AffectationPaiementDto;
import com.cityprojects.citybackend.dto.finance.PaiementCreateDto;
import com.cityprojects.citybackend.dto.finance.PaiementDto;
import com.cityprojects.citybackend.dto.finance.PaiementGlobalRequest;
import com.cityprojects.citybackend.dto.finance.PaiementLignesRequest;
import com.cityprojects.citybackend.entity.finance.AffectationPaiement;
import com.cityprojects.citybackend.entity.finance.Compte;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.LigneFacture;
import com.cityprojects.citybackend.entity.finance.Paiement;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.StatutPaiement;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.finance.PaiementMapper;
import com.cityprojects.citybackend.repository.finance.AffectationPaiementRepository;
import com.cityprojects.citybackend.repository.finance.CompteRepository;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.repository.finance.LigneFactureRepository;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final LigneFactureRepository ligneFactureRepository;
    private final CompteRepository compteRepository;
    private final PaiementMapper mapper;
    private final NumerotationService numerotationService;
    private final CompteService compteService;
    private final OperationCompteService operationCompteService;
    private final ExerciceService exerciceService;
    private final EcritureGenerationService ecritureGenerationService;
    private final EcritureComptableService ecritureComptableService;

    public PaiementServiceImpl(PaiementRepository paiementRepository,
                               AffectationPaiementRepository affectationRepository,
                               FactureRepository factureRepository,
                               LigneFactureRepository ligneFactureRepository,
                               CompteRepository compteRepository,
                               PaiementMapper mapper,
                               NumerotationService numerotationService,
                               CompteService compteService,
                               OperationCompteService operationCompteService,
                               ExerciceService exerciceService,
                               EcritureGenerationService ecritureGenerationService,
                               EcritureComptableService ecritureComptableService) {
        this.paiementRepository = paiementRepository;
        this.affectationRepository = affectationRepository;
        this.factureRepository = factureRepository;
        this.ligneFactureRepository = ligneFactureRepository;
        this.compteRepository = compteRepository;
        this.mapper = mapper;
        this.numerotationService = numerotationService;
        this.compteService = compteService;
        this.operationCompteService = operationCompteService;
        this.exerciceService = exerciceService;
        this.ecritureGenerationService = ecritureGenerationService;
        this.ecritureComptableService = ecritureComptableService;
    }

    @Override
    @Transactional
    @AuditFinanceAction(value = "PAIEMENT_CREATION", entityType = "PAIEMENT")
    public PaiementDto create(PaiementCreateDto dto) {
        // Garde anti-modification dans exercice clos (B1) : refuse la
        // creation d'un paiement dont la date appartient a un exercice
        // EN_CLOTURE ou CLOTURE. Auto-cree l'exercice courant si necessaire.
        LocalDate dateCible = dto.datePaiement() != null ? dto.datePaiement() : LocalDate.now();
        exerciceService.assertOuvert(dateCible);

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

        // Affectation directe si factureId fourni (legacy : ligneFactureId=null).
        // Resout aussi clientId/societeId pour l'ecriture comptable.
        Long clientIdForEcriture = null;
        Long societeIdForEcriture = null;
        if (dto.factureId() != null) {
            Facture facture = factureRepository.findById(dto.factureId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.facture.notFound"));
            clientIdForEcriture = facture.getClientId();
            societeIdForEcriture = facture.getSocieteId();
            affecterUneAffectation(saved.getPaiementId(), dto.factureId(), null, dto.montantTotal());
        }

        // Bloc B3 : generation atomique de l'ecriture de tresorerie
        // (5xxx D / 411xxx C) via journal CAI ou BAN.
        Long ecritureId = ecritureGenerationService.emettreEcritureEncaissement(
                saved, clientIdForEcriture, societeIdForEcriture);
        if (ecritureId != null) {
            saved.setEcritureEncaissementId(ecritureId);
            paiementRepository.save(saved);
        }

        logger.info("Paiement cree : id={}, numero={}, montant={}, mode={}, ecritureEncaissementId={}",
                saved.getPaiementId(), saved.getNumeroPaiement(),
                saved.getMontantTotal(), saved.getModePaiement(), ecritureId);
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
    @AuditFinanceAction(value = "PAIEMENT_AFFECTATION", entityType = "PAIEMENT")
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
            affecterUneAffectation(paiementId, aff.factureId(), aff.ligneFactureId(), aff.montantAffecte());
        }

        return toDtoWithAffectations(paiement);
    }

    @Override
    @Transactional
    @AuditFinanceAction(value = "PAIEMENT_ANNULATION", entityType = "PAIEMENT")
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
        boolean avaitEcriture = paiement.getEcritureEncaissementId() != null;
        paiement.setStatut(StatutPaiement.ANNULE);
        paiementRepository.save(paiement);

        // Bloc B3 : contre-passation de l'ecriture de tresorerie.
        if (avaitEcriture) {
            ecritureComptableService.contrePasser(
                    paiement.getEcritureEncaissementId(),
                    "Annulation paiement " + paiement.getNumeroPaiement());
        }

        logger.info("Paiement annule : id={}, numero={}, contrePassation={}",
                paiement.getPaiementId(), paiement.getNumeroPaiement(), avaitEcriture);
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
     *
     * <p>Tour 45 : accepte un {@code ligneFactureId} optionnel pour la
     * granularite "paiement de ligne specifique" (sinon null = legacy
     * affectation a la facture entiere).</p>
     */
    private void affecterUneAffectation(Long paiementId, Long factureId, Long ligneFactureId, BigDecimal montant) {
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
        // Coherence ligneFactureId : si non null, doit appartenir a la facture
        if (ligneFactureId != null) {
            LigneFacture ligne = ligneFactureRepository.findById(ligneFactureId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.ligneFacture.notFound"));
            if (!ligne.getFactureId().equals(factureId)) {
                throw new BusinessException("error.paiement.ligne.factureMismatch");
            }
        }

        AffectationPaiement aff = new AffectationPaiement();
        aff.setPaiementId(paiementId);
        aff.setFactureId(factureId);
        aff.setLigneFactureId(ligneFactureId);
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
     * <p>Cas AVOIR : traite dans le Bloc B2 (ecriture inverse explicite a
     * l'emission de l'avoir).</p>
     */
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
        List<com.cityprojects.citybackend.entity.finance.AffectationPaiement> entAffs = affectationRepository
                .findByPaiementIdOrderByDateAffectationAsc(paiement.getPaiementId());
        // Batch lookup factures pour résolution numeroFacture (anti-N+1)
        java.util.Set<Long> factureIds = entAffs.stream()
                .map(com.cityprojects.citybackend.entity.finance.AffectationPaiement::getFactureId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, String> numerosFactures = factureIds.isEmpty() ? java.util.Map.of()
                : factureRepository.findAllById(factureIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Facture::getFactureId, Facture::getNumeroFacture));
        List<AffectationPaiementDto> affs = entAffs.stream()
                .map(a -> mapper.toAffectationDto(a)
                        .withResolvedNames(numerosFactures.get(a.getFactureId())))
                .toList();
        return mapper.withAffectations(base, affs);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId();
        }
        throw new BusinessException("error.user.unknown");
    }

    @Override
    @Transactional
    public PaiementDto paierLignes(PaiementLignesRequest request) {
        // Garde anti-modification dans exercice clos (B1).
        exerciceService.assertOuvert(LocalDate.now());

        // 1) Validations basiques
        if (request == null) {
            throw new BusinessException("error.paiement.lignes.requestRequired");
        }
        List<Long> lignesIds = request.lignesIds();
        if (lignesIds == null || lignesIds.isEmpty()) {
            throw new BusinessException("error.paiement.lignes.required");
        }
        if (request.idClient() == null) {
            throw new BusinessException("error.paiement.lignes.clientRequired");
        }

        // 2) Charger toutes les lignes et identifier la facture parente unique
        List<LigneFacture> lignes = new ArrayList<>();
        Set<Long> factureIds = new HashSet<>();
        for (Long ligneId : lignesIds) {
            LigneFacture ligne = ligneFactureRepository.findById(ligneId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.ligneFacture.notFound"));
            lignes.add(ligne);
            factureIds.add(ligne.getFactureId());
        }
        if (factureIds.size() > 1) {
            throw new BusinessException("error.paiement.lignes.multiFactures");
        }
        Long factureId = factureIds.iterator().next();

        // 3) Si factureId fourni, verifier coherence
        if (request.factureId() != null && !request.factureId().equals(factureId)) {
            throw new BusinessException("error.paiement.lignes.factureMismatch");
        }

        // 4) Charger la facture et verifier le statut
        Facture facture = factureRepository.findById(factureId)
                .orElseThrow(() -> new ResourceNotFoundException("error.facture.notFound"));
        if (facture.getStatut() != StatutFacture.EMISE
                && facture.getStatut() != StatutFacture.PARTIELLEMENT_PAYEE) {
            throw new BusinessException("error.paiement.facture.statutInvalide");
        }

        // 5) Calculer le reste de chaque ligne = montantTtc - somme des affectations
        //    existantes sur cette ligne (filtre ligneFactureId).
        List<AffectationPaiement> affsFacture = affectationRepository
                .findByFactureIdOrderByDateAffectationAsc(factureId);
        // Indexation : montant deja affecte par ligne (ligneFactureId == ligne.id)
        java.util.Map<Long, BigDecimal> dejaParLigne = new java.util.HashMap<>();
        BigDecimal dejaFactureEntiere = BigDecimal.ZERO;
        for (AffectationPaiement aff : affsFacture) {
            if (aff.getLigneFactureId() != null) {
                dejaParLigne.merge(aff.getLigneFactureId(), aff.getMontantAffecte(), BigDecimal::add);
            } else {
                dejaFactureEntiere = dejaFactureEntiere.add(aff.getMontantAffecte());
            }
        }
        // Si des affectations "facture entiere" existent (legacy), on les repartit
        // proportionnellement (defense en profondeur, cas rare dans Tour 45).
        // Simplification : on ne touche pas a dejaFactureEntiere, on l'ajoute au
        // montantPaye de la facture pour le calcul de reste global - mais pour
        // le reste par ligne on suppose 0 (mode legacy + Tour 45 ne se melangent
        // pas en pratique).

        BigDecimal totalResteSelection = BigDecimal.ZERO;
        java.util.Map<Long, BigDecimal> restePoint = new java.util.LinkedHashMap<>();
        for (LigneFacture ligne : lignes) {
            BigDecimal montantLigne = ligne.getMontantTtc() != null
                    ? ligne.getMontantTtc() : BigDecimal.ZERO;
            BigDecimal deja = dejaParLigne.getOrDefault(ligne.getLigneFactureId(), BigDecimal.ZERO);
            BigDecimal reste = montantLigne.subtract(deja).max(BigDecimal.ZERO);
            restePoint.put(ligne.getLigneFactureId(), reste);
            totalResteSelection = totalResteSelection.add(reste);
        }

        BigDecimal montant = request.montant();
        if (montant == null || montant.signum() <= 0) {
            throw new BusinessException("error.paiement.montant.positive");
        }

        // 6) Resoudre / valider le compte client (cree si necessaire via CompteService).
        Long compteClientId = request.idCompteClient();
        if (compteClientId == null || compteClientId == 0L) {
            Compte compte = compteService.findOrCreateForClient(request.idClient());
            compteClientId = compte.getCompteId();
        } else {
            // Validation tenant (Hibernate filter auto)
            compteRepository.findById(compteClientId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.compte.notFound"));
        }

        // 7) Creer le paiement
        Paiement paiement = new Paiement();
        paiement.setCompteId(compteClientId);
        paiement.setMontantTotal(montant);
        paiement.setDevise(facture.getDevise() != null ? facture.getDevise() : "MRU");
        paiement.setModePaiement(request.modePaiement());
        paiement.setReferencePaiement(request.motif());
        paiement.setDatePaiement(LocalDate.now());
        // Commentaires = description + motif
        String commentaires = composeCommentaires(request.motif(), request.description());
        paiement.setCommentaires(commentaires);
        paiement.setStatut(StatutPaiement.VALIDE);
        paiement.setUserId(currentUserId());
        paiement.setNumeroPaiement(numerotationService.next(TypeNumerotation.PAY));
        Paiement savedPaiement = paiementRepository.save(paiement);

        // Bloc B3 : ecriture de tresorerie associee. Client / societe resolus
        // depuis la facture parente (1 seule facture concernee dans paierLignes).
        Long ecritureId = ecritureGenerationService.emettreEcritureEncaissement(
                savedPaiement, facture.getClientId(), facture.getSocieteId());
        if (ecritureId != null) {
            savedPaiement.setEcritureEncaissementId(ecritureId);
            paiementRepository.save(savedPaiement);
        }

        // 8) Ventilation
        BigDecimal montantVentile = montant.min(totalResteSelection);
        BigDecimal excedent = montant.subtract(totalResteSelection).max(BigDecimal.ZERO);

        if (montantVentile.signum() > 0 && totalResteSelection.signum() > 0) {
            // Repartition proportionnelle. Le dernier element recoit le delta d'arrondi.
            BigDecimal totalAttribue = BigDecimal.ZERO;
            int i = 0;
            int n = restePoint.size();
            for (var entry : restePoint.entrySet()) {
                Long ligneId = entry.getKey();
                BigDecimal resteLigne = entry.getValue();
                BigDecimal part;
                if (i == n - 1) {
                    // Dernier : recoit le delta restant pour eviter pertes d'arrondi
                    part = montantVentile.subtract(totalAttribue);
                } else {
                    if (totalResteSelection.signum() == 0) {
                        part = BigDecimal.ZERO;
                    } else {
                        part = montantVentile.multiply(resteLigne)
                                .divide(totalResteSelection, 2, RoundingMode.HALF_UP);
                    }
                    totalAttribue = totalAttribue.add(part);
                }
                if (part.signum() > 0) {
                    affecterUneAffectation(savedPaiement.getPaiementId(), factureId, ligneId, part);
                }
                i++;
            }
        }

        // 9) Excedent -> CREDIT sur compte client
        if (excedent.signum() > 0) {
            operationCompteService.recordCredit(
                    compteClientId,
                    excedent,
                    savedPaiement.getPaiementId(),
                    "Excedent paiement " + savedPaiement.getNumeroPaiement());
        }

        logger.info("paierLignes Tour 45 : paiementId={}, numero={}, lignes={}, montant={}, ventile={}, excedent={}",
                savedPaiement.getPaiementId(), savedPaiement.getNumeroPaiement(),
                lignesIds.size(), montant, montantVentile, excedent);

        return toDtoWithAffectations(savedPaiement);
    }

    @Override
    @Transactional
    public PaiementDto payerGlobal(PaiementGlobalRequest request) {
        // Garde anti-modification dans exercice clos (B1).
        exerciceService.assertOuvert(LocalDate.now());

        // 1) Validations basiques
        if (request == null) {
            throw new BusinessException("error.paiement.global.requestRequired");
        }
        if (request.reservationId() == null) {
            throw new BusinessException("error.paiement.global.reservationRequired");
        }
        if (request.idClient() == null) {
            throw new BusinessException("error.paiement.global.clientRequired");
        }
        BigDecimal montant = request.montant();
        if (montant == null || montant.signum() <= 0) {
            throw new BusinessException("error.paiement.global.montantInvalid");
        }

        // 2) Resoudre / valider le compte client (Hibernate filtre tenant).
        Long compteClientId = request.idCompteClient();
        if (compteClientId == null || compteClientId == 0L) {
            Compte compte = compteService.findOrCreateForClient(request.idClient());
            compteClientId = compte.getCompteId();
        } else {
            compteRepository.findById(compteClientId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.compte.notFound"));
        }

        // 3) Toutes les lignes facture de la reservation, deja triees
        //    (factureId ASC, ligneFactureId ASC) cf. repository.
        //    Pour avoir tri par dateFacture ASC (FIFO chronologique), on
        //    recharge les factures et re-trie en memoire (volume modere).
        List<LigneFacture> toutesLignes = ligneFactureRepository.findByReservationId(request.reservationId());

        // Map factureId -> Facture (et set de factures distinctes).
        java.util.Map<Long, Facture> facturesById = new java.util.HashMap<>();
        for (LigneFacture l : toutesLignes) {
            if (!facturesById.containsKey(l.getFactureId())) {
                Facture f = factureRepository.findById(l.getFactureId())
                        .orElseThrow(() -> new ResourceNotFoundException("error.facture.notFound"));
                facturesById.put(l.getFactureId(), f);
            }
        }

        // 4) Filtrer les lignes facture eligibles : facture EMISE ou
        //    PARTIELLEMENT_PAYEE + reste de la ligne > 0.
        List<LigneFacture> lignesEligibles = new ArrayList<>();
        java.util.Map<Long, BigDecimal> resteParLigne = new java.util.LinkedHashMap<>();
        for (LigneFacture l : toutesLignes) {
            Facture f = facturesById.get(l.getFactureId());
            if (f.getStatut() != StatutFacture.EMISE
                    && f.getStatut() != StatutFacture.PARTIELLEMENT_PAYEE) {
                continue;
            }
            BigDecimal montantTtc = l.getMontantTtc() != null ? l.getMontantTtc() : BigDecimal.ZERO;
            BigDecimal deja = affectationRepository.sumMontantByLigneFactureId(l.getLigneFactureId());
            if (deja == null) deja = BigDecimal.ZERO;
            BigDecimal reste = montantTtc.subtract(deja);
            if (reste.signum() > 0) {
                lignesEligibles.add(l);
                resteParLigne.put(l.getLigneFactureId(), reste);
            }
        }

        // Tri FIFO : par dateFacture ASC puis ligneFactureId ASC.
        lignesEligibles.sort((a, b) -> {
            Facture fa = facturesById.get(a.getFactureId());
            Facture fb = facturesById.get(b.getFactureId());
            int cmp = fa.getDateFacture().compareTo(fb.getDateFacture());
            if (cmp != 0) return cmp;
            return Long.compare(a.getLigneFactureId(), b.getLigneFactureId());
        });

        BigDecimal totalRestes = resteParLigne.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5) Cas degrade : aucune facture du tout pour la reservation.
        //    On refuse plutot que de creer un acompte sans facture (le client
        //    Tour 46 a forcement deja une facture sur la reservation).
        if (facturesById.isEmpty()) {
            throw new BusinessException("error.paiement.global.aucuneLigne");
        }

        // 6) Creer le paiement.
        //    Devise = celle de la 1ere facture eligible (sinon "MRU").
        String devise = "MRU";
        if (!lignesEligibles.isEmpty()) {
            Facture premiereFacture = facturesById.get(lignesEligibles.get(0).getFactureId());
            if (premiereFacture.getDevise() != null) {
                devise = premiereFacture.getDevise();
            }
        } else {
            // toutes lignes deja payees : prendre devise de n'importe quelle facture
            Facture qq = facturesById.values().iterator().next();
            if (qq.getDevise() != null) devise = qq.getDevise();
        }

        Paiement paiement = new Paiement();
        paiement.setCompteId(compteClientId);
        paiement.setMontantTotal(montant);
        paiement.setDevise(devise);
        paiement.setModePaiement(request.modePaiement());
        paiement.setReferencePaiement(request.motif());
        paiement.setDatePaiement(LocalDate.now());
        paiement.setCommentaires(composeCommentaires(request.motif(), request.description()));
        paiement.setStatut(StatutPaiement.VALIDE);
        paiement.setUserId(currentUserId());
        paiement.setNumeroPaiement(numerotationService.next(TypeNumerotation.PAY));
        Paiement savedPaiement = paiementRepository.save(paiement);

        // Bloc B3 : ecriture de tresorerie associee. Client / societe resolus
        // depuis la 1ere facture eligible (sinon depuis une facture quelconque
        // de la reservation - cas excedent pur sans dette).
        Long clientIdEcr = null;
        Long societeIdEcr = null;
        Facture factureRefForEcr = null;
        if (!lignesEligibles.isEmpty()) {
            factureRefForEcr = facturesById.get(lignesEligibles.get(0).getFactureId());
        } else if (!facturesById.isEmpty()) {
            factureRefForEcr = facturesById.values().iterator().next();
        }
        if (factureRefForEcr != null) {
            clientIdEcr = factureRefForEcr.getClientId();
            societeIdEcr = factureRefForEcr.getSocieteId();
        }
        Long ecritureId = ecritureGenerationService.emettreEcritureEncaissement(
                savedPaiement, clientIdEcr, societeIdEcr);
        if (ecritureId != null) {
            savedPaiement.setEcritureEncaissementId(ecritureId);
            paiementRepository.save(savedPaiement);
        }

        // 7) Ventilation FIFO sequentielle.
        //    Chaque ligne consomme son reste complet jusqu'a epuisement du montant.
        //    La derniere ligne touchee recoit le solde residuel (potentiellement partiel).
        BigDecimal restantAVentiler = montant.min(totalRestes);
        if (restantAVentiler.signum() > 0) {
            for (LigneFacture ligne : lignesEligibles) {
                if (restantAVentiler.signum() <= 0) break;
                BigDecimal resteLigne = resteParLigne.get(ligne.getLigneFactureId());
                BigDecimal part = restantAVentiler.min(resteLigne);
                if (part.signum() > 0) {
                    affecterUneAffectation(savedPaiement.getPaiementId(),
                            ligne.getFactureId(), ligne.getLigneFactureId(), part);
                    restantAVentiler = restantAVentiler.subtract(part);
                }
            }
        }

        // 8) Excedent (montant > somme des restes OU lignes toutes payees) -> CREDIT compte client.
        BigDecimal excedent = montant.subtract(totalRestes).max(BigDecimal.ZERO);
        if (excedent.signum() > 0) {
            String libelleAvance = totalRestes.signum() == 0
                    ? "Avance solde " + savedPaiement.getNumeroPaiement()
                    : "Excedent paiement " + savedPaiement.getNumeroPaiement();
            operationCompteService.recordCredit(
                    compteClientId,
                    excedent,
                    savedPaiement.getPaiementId(),
                    libelleAvance);
        }

        logger.info("payerGlobal Tour 46 : paiementId={}, numero={}, reservationId={}, "
                        + "lignesEligibles={}, montant={}, ventile={}, excedent={}",
                savedPaiement.getPaiementId(), savedPaiement.getNumeroPaiement(),
                request.reservationId(), lignesEligibles.size(),
                montant, montant.subtract(excedent), excedent);

        return toDtoWithAffectations(savedPaiement);
    }

    /** Concatene motif + description dans une string commentaires "motif - description". */
    private static String composeCommentaires(String motif, String description) {
        boolean hasMotif = motif != null && !motif.isBlank();
        boolean hasDesc = description != null && !description.isBlank();
        if (hasMotif && hasDesc) {
            return motif.trim() + " - " + description.trim();
        }
        if (hasMotif) {
            return motif.trim();
        }
        if (hasDesc) {
            return description.trim();
        }
        return null;
    }
}
