package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.finance.FactureRecapDto;
import com.cityprojects.citybackend.dto.finance.PaiementRecapDto;
import com.cityprojects.citybackend.dto.finance.RecapPaiementsReservationDto;
import com.cityprojects.citybackend.entity.finance.AffectationPaiement;
import com.cityprojects.citybackend.entity.finance.Compte;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.Paiement;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.finance.AffectationPaiementRepository;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.repository.finance.PaiementRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation de {@link ReservationFinanceService} (Tour 44 Phase 1).
 *
 * <p>Strategie : 1) lookup factures par {@code reservationId} ; 2) pour chaque
 * facture, lookup affectations de paiements via
 * {@link AffectationPaiementRepository#findByFactureIdOrderByDateAffectationAsc(Long)}
 * (le pivot ne porte pas {@code hotelId} mais l'isolation est garantie par la
 * facture parente filtree par Hibernate {@code @TenantId}). 3) reconcilier le
 * paiement parent (lookup par id pour les meta-donnees mode/numero/statut).</p>
 *
 * <p>Le total {@code payeGlobal} est calcule a partir des affectations (somme
 * des {@code montantAffecte}), pas des paiements eux-memes (un paiement peut
 * couvrir plusieurs factures de plusieurs reservations - on ne retient que la
 * part affectee a CETTE reservation).</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class ReservationFinanceServiceImpl implements ReservationFinanceService {

    private static final Logger logger = LoggerFactory.getLogger(ReservationFinanceServiceImpl.class);

    private final ReservationRepository reservationRepository;
    private final FactureRepository factureRepository;
    private final PaiementRepository paiementRepository;
    private final AffectationPaiementRepository affectationRepository;
    private final CompteService compteService;
    private final OperationCompteService operationCompteService;

    public ReservationFinanceServiceImpl(ReservationRepository reservationRepository,
                                         FactureRepository factureRepository,
                                         PaiementRepository paiementRepository,
                                         AffectationPaiementRepository affectationRepository,
                                         CompteService compteService,
                                         OperationCompteService operationCompteService) {
        this.reservationRepository = reservationRepository;
        this.factureRepository = factureRepository;
        this.paiementRepository = paiementRepository;
        this.affectationRepository = affectationRepository;
        this.compteService = compteService;
        this.operationCompteService = operationCompteService;
    }

    @Override
    public RecapPaiementsReservationDto getRecapForReservation(Long reservationId) {
        // Verifie l'appartenance tenant via Hibernate @TenantId
        reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));

        List<Facture> factures = factureRepository.findByReservationId(reservationId);
        List<FactureRecapDto> facturesDto = new ArrayList<>(factures.size());
        List<PaiementRecapDto> paiementsDto = new ArrayList<>();
        BigDecimal totalGlobal = BigDecimal.ZERO;
        BigDecimal payeGlobal = BigDecimal.ZERO;

        for (Facture f : factures) {
            BigDecimal totalFact = nz(f.getMontantTtc());
            BigDecimal payeFact = nz(f.getMontantPaye());
            BigDecimal resteFact = totalFact.subtract(payeFact).max(BigDecimal.ZERO);
            facturesDto.add(new FactureRecapDto(
                    f.getFactureId(),
                    f.getNumeroFacture(),
                    f.getStatut(),
                    f.getDateFacture(),
                    totalFact,
                    payeFact,
                    resteFact));
            totalGlobal = totalGlobal.add(totalFact);
            payeGlobal = payeGlobal.add(payeFact);

            // Recupere les affectations de paiements liees a cette facture.
            // Cache des paiements parent pour eviter N+1 lookups.
            Map<Long, Paiement> paiementCache = new HashMap<>();
            for (AffectationPaiement aff : affectationRepository
                    .findByFactureIdOrderByDateAffectationAsc(f.getFactureId())) {
                Paiement paiement = paiementCache.computeIfAbsent(aff.getPaiementId(),
                        id -> paiementRepository.findById(id).orElse(null));
                if (paiement == null) {
                    // Defense en profondeur : affectation orpheline (theoriquement
                    // impossible avec FK on delete restrict). On skip silencieusement.
                    continue;
                }
                paiementsDto.add(new PaiementRecapDto(
                        paiement.getPaiementId(),
                        paiement.getNumeroPaiement(),
                        paiement.getDatePaiement(),
                        paiement.getModePaiement(),
                        paiement.getStatut(),
                        nz(aff.getMontantAffecte()),
                        f.getFactureId(),
                        f.getNumeroFacture()));
            }
        }
        BigDecimal resteGlobal = totalGlobal.subtract(payeGlobal).max(BigDecimal.ZERO);
        return new RecapPaiementsReservationDto(
                reservationId,
                facturesDto,
                paiementsDto,
                totalGlobal.setScale(2, RoundingMode.HALF_UP),
                payeGlobal.setScale(2, RoundingMode.HALF_UP),
                resteGlobal.setScale(2, RoundingMode.HALF_UP));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    @Override
    @Transactional
    public BigDecimal applyCheckOutExpressTransfer(Long reservationId, Long clientId, Long societeId) {
        if (reservationId == null) {
            throw new BusinessException("error.checkoutExpress.reservation.required");
        }
        if (societeId == null) {
            throw new BusinessException("error.checkoutExpress.societe.required");
        }

        // Verification appartenance tenant
        reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));

        List<Facture> factures = factureRepository.findByReservationId(reservationId);
        if (factures.isEmpty()) {
            throw new BusinessException("error.checkoutExpress.factureNonTrouvee");
        }

        // Eligibilite : factures non terminales (EMISE / PARTIELLEMENT_PAYEE)
        List<Facture> eligibles = factures.stream()
                .filter(f -> f.getStatut() == StatutFacture.EMISE
                        || f.getStatut() == StatutFacture.PARTIELLEMENT_PAYEE)
                .collect(java.util.stream.Collectors.toList());

        if (eligibles.isEmpty()) {
            logger.info("checkOutExpress : aucune facture eligible (toutes deja terminales) pour reservation {}",
                    reservationId);
            return BigDecimal.ZERO;
        }

        // Resolution / creation des comptes auxiliaires
        Compte compteSociete = compteService.findOrCreateForSociete(societeId);

        BigDecimal totalTransfere = BigDecimal.ZERO;
        for (Facture facture : eligibles) {
            BigDecimal restant = nz(facture.getMontantRestant());
            if (restant.signum() <= 0) {
                continue;
            }

            // DEBIT compte societe (la societe doit ce montant)
            operationCompteService.recordDebit(
                    compteSociete.getCompteId(),
                    restant,
                    facture.getFactureId(),
                    "Check-out express - transfert depuis facture " + facture.getNumeroFacture());

            // CREDIT compte client (le client est solde a hauteur du transfert),
            // uniquement si la facture est rattachee a un client (sinon facture
            // cash anonyme - skip).
            Long factureClientId = facture.getClientId();
            Long resolvedClientId = factureClientId != null ? factureClientId : clientId;
            if (resolvedClientId != null) {
                Compte compteClient = compteService.findOrCreateForClient(resolvedClientId);
                operationCompteService.recordCredit(
                        compteClient.getCompteId(),
                        restant,
                        null,
                        "Check-out express - transfert vers societe pour facture " + facture.getNumeroFacture());
            }

            // Mettre a jour montantPaye + statut PAYEE (transfert assimile a un encaissement)
            facture.setMontantPaye(nz(facture.getMontantPaye()).add(restant));
            facture.setStatut(StatutFacture.PAYEE);
            factureRepository.save(facture);

            totalTransfere = totalTransfere.add(restant);

            logger.info("checkOutExpress transfert : facture={}, montant={}, societeId={}, clientId={}",
                    facture.getNumeroFacture(), restant, societeId, resolvedClientId);
        }
        return totalTransfere;
    }
}
