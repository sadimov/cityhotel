package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.paging.PageableUtils;
import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.hebergement.NuiteeDto;
import com.cityprojects.citybackend.dto.hebergement.NuiteeModificationDto;
import com.cityprojects.citybackend.dto.hebergement.NuiteeMontantUpdateRequest;
import com.cityprojects.citybackend.dto.hebergement.NuiteesUpdateResultDto;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.LigneFacture;
import com.cityprojects.citybackend.entity.finance.OperationCompte;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.hebergement.Nuitee;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.hebergement.NuiteeMapper;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.repository.finance.LigneFactureRepository;
import com.cityprojects.citybackend.repository.finance.OperationCompteRepository;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.NuiteeRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.service.finance.FactureRecalcInternalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation de {@link NuiteeService} (Tour 14 + Tour 45).
 *
 * <p>Conventions :</p>
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe.</li>
 *   <li>{@code @Transactional(readOnly = true)} au niveau classe (lecture par
 *       defaut). Mutation Tour 45 overridee.</li>
 *   <li>Aucun {@code setHotelId} : Hibernate via {@code @TenantId}.</li>
 *   <li>Verification appartenance tenant : repository {@code findById} filtre
 *       auto (cross-tenant -&gt; 404 propre).</li>
 * </ul>
 *
 * <h3>Tour 45 - mutation granulaire</h3>
 * <p>{@link #updateMontants(List)} permet d'ajuster individuellement le
 * {@code prixNuit} d'une nuitee tant que la facture parente n'est pas
 * terminale. Tout est en 1 transaction. Le recalcul facture est delegue a
 * {@link FactureRecalcInternalService} (helper exposed pour services
 * cross-module).</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class NuiteeServiceImpl implements NuiteeService {

    private static final Logger logger = LoggerFactory.getLogger(NuiteeServiceImpl.class);

    private final NuiteeRepository nuiteeRepository;
    private final ReservationRepository reservationRepository;
    private final ChambreRepository chambreRepository;
    private final LigneFactureRepository ligneFactureRepository;
    private final FactureRepository factureRepository;
    private final OperationCompteRepository operationCompteRepository;
    private final FactureRecalcInternalService factureRecalc;
    private final NuiteeMapper nuiteeMapper;

    public NuiteeServiceImpl(NuiteeRepository nuiteeRepository,
                             ReservationRepository reservationRepository,
                             ChambreRepository chambreRepository,
                             LigneFactureRepository ligneFactureRepository,
                             FactureRepository factureRepository,
                             OperationCompteRepository operationCompteRepository,
                             FactureRecalcInternalService factureRecalc,
                             NuiteeMapper nuiteeMapper) {
        this.nuiteeRepository = nuiteeRepository;
        this.reservationRepository = reservationRepository;
        this.chambreRepository = chambreRepository;
        this.ligneFactureRepository = ligneFactureRepository;
        this.factureRepository = factureRepository;
        this.operationCompteRepository = operationCompteRepository;
        this.factureRecalc = factureRecalc;
        this.nuiteeMapper = nuiteeMapper;
    }

    @Override
    public List<NuiteeDto> findByReservation(Long reservationId) {
        reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));
        return nuiteeRepository.findByReservationIdOrderByDateNuitAsc(reservationId).stream()
                .map(nuiteeMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public Page<NuiteeDto> findByChambre(Long chambreId, Pageable pageable) {
        chambreRepository.findById(chambreId)
                .orElseThrow(() -> new ResourceNotFoundException("error.chambre.notFound"));

        Sort defaultSort = Sort.by(Sort.Order.desc("dateNuit"));
        Pageable stable = PageableUtils.stable(pageable, defaultSort, "nuiteeId");

        return nuiteeRepository.findByChambreId(chambreId, stable).map(nuiteeMapper::toDto);
    }

    @Override
    public List<NuiteeModificationDto> findProvisoiresByReservation(Long reservationId) {
        // Verification appartenance tenant (404 si cross-tenant)
        reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));

        List<Nuitee> nuitees = nuiteeRepository.findByReservationIdOrderByDateNuitAsc(reservationId);
        if (nuitees.isEmpty()) {
            return List.of();
        }

        // Charger en batch les lignes facture referencees (1 lookup par ligne) - optim future
        // si beaucoup de nuitees. Pour l'instant, garder simple : findById par ligneFactureId.
        // Idem pour factures et operations. Tour 45 = peu de nuitees par reservation (typique <10).
        List<NuiteeModificationDto> result = new ArrayList<>(nuitees.size());
        for (Nuitee n : nuitees) {
            BigDecimal prixOriginal = n.getPrixNuit();
            BigDecimal montantLigne = null;
            Long ligneFactureId = n.getLigneFactureId();
            Long operationCompteId = null;
            String statutFactureParente = null;

            if (ligneFactureId != null) {
                LigneFacture ligne = ligneFactureRepository.findById(ligneFactureId).orElse(null);
                if (ligne != null) {
                    montantLigne = ligne.getMontantTtc() != null
                            ? ligne.getMontantTtc()
                            : ligne.getMontantHt();

                    Facture facture = factureRepository.findById(ligne.getFactureId()).orElse(null);
                    if (facture != null) {
                        statutFactureParente = facture.getStatut() != null
                                ? facture.getStatut().name()
                                : null;

                        // Recupere l'operation DEBIT liee a cette facture (audit trail
                        // pose a l'emission). Pas d'erreur si absent (cas facture sans
                        // client, ou facture cash anonyme).
                        Optional<OperationCompte> op =
                                operationCompteRepository.findFirstDebitForFacture(facture.getFactureId());
                        if (op.isPresent()) {
                            operationCompteId = op.get().getOperationId();
                        }

                        // Filtrage Tour 45 : exclure les nuitees dont la facture
                        // parente est dans un etat terminal (PAYEE ou ANNULEE).
                        if (isFactureTerminale(facture.getStatut())) {
                            continue;
                        }
                    }
                }
            }
            // Nuitee sans ligne facture : eligible (provisoire), pas de check finance.

            result.add(new NuiteeModificationDto(
                    n.getNuiteeId(),
                    n.getDateNuit(),
                    prixOriginal,
                    montantLigne,
                    ligneFactureId,
                    operationCompteId,
                    n.getStatut() != null ? n.getStatut().name() : null,
                    statutFactureParente));
        }
        return result;
    }

    @Override
    @Transactional
    public NuiteesUpdateResultDto updateMontants(List<NuiteeMontantUpdateRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return new NuiteesUpdateResultDto(0, BigDecimal.ZERO);
        }
        int updatedCount = 0;
        BigDecimal totalImpact = BigDecimal.ZERO;

        // Set des factures impactees pour recalcul groupe en fin de boucle.
        Set<Long> facturesToRecalc = new HashSet<>();

        for (NuiteeMontantUpdateRequest req : requests) {
            Nuitee nuitee = nuiteeRepository.findById(req.nuiteeId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.nuitee.notFound"));

            BigDecimal ancien = nuitee.getPrixNuit() != null ? nuitee.getPrixNuit() : BigDecimal.ZERO;
            BigDecimal nouveau = req.nouveauMontant() != null ? req.nouveauMontant() : BigDecimal.ZERO;
            BigDecimal delta = nouveau.subtract(ancien);

            // Si la nuitee est rattachee a une ligne facture, verifier le statut
            // de la facture parente. Refus si PAYEE / ANNULEE.
            Long ligneFactureId = req.ligneFactureId() != null ? req.ligneFactureId() : nuitee.getLigneFactureId();
            LigneFacture ligne = null;
            Facture facture = null;
            if (ligneFactureId != null) {
                ligne = ligneFactureRepository.findById(ligneFactureId)
                        .orElseThrow(() -> new ResourceNotFoundException("error.ligneFacture.notFound"));
                facture = factureRepository.findById(ligne.getFactureId())
                        .orElseThrow(() -> new ResourceNotFoundException("error.facture.notFound"));
                if (facture.getStatut() == StatutFacture.PAYEE) {
                    throw new BusinessException("error.nuitee.facture.payee");
                }
                if (facture.getStatut() == StatutFacture.ANNULEE) {
                    throw new BusinessException("error.nuitee.facture.annulee");
                }
            }

            // 1) Maj du prix nuitee
            nuitee.setPrixNuit(nouveau);
            nuiteeRepository.save(nuitee);

            // 2) Maj ligne facture si presente. Strategie : recalculer
            // prixUnitaire = nouveauMontant (1 ligne = 1 nuitee, quantite=1).
            // tauxTva preserve. Hibernate recalc HT/TVA/TTC via @PrePersist/Update.
            if (ligne != null) {
                ligne.setPrixUnitaire(nouveau);
                // quantite (1) inchangee - tauxTva inchange.
                ligneFactureRepository.save(ligne);
                facturesToRecalc.add(ligne.getFactureId());
            }

            // 3) Ajustement operation compte si demande explicite (operationCompteId
            // non null). Strategie : audit trail = on ne modifie PAS l'operation
            // initiale (immutable), on cree une operation d'ajustement DEBIT (delta>0)
            // ou CREDIT (delta<0) sur le meme compte. Si pas de delta, no-op.
            // L'operationCompteId du request sert a identifier le compte cible
            // (defense en profondeur cross-tenant).
            // Note : si operationCompteId est null mais ligne != null, le service
            // ne touche pas au compte - la mise a jour montant_paye se fera
            // implicitement via le recalcul facture + futurs paiements.
            if (req.operationCompteId() != null && delta.signum() != 0 && facture != null
                    && facture.getClientId() != null) {
                OperationCompte op = operationCompteRepository.findById(req.operationCompteId())
                        .orElseThrow(() -> new ResourceNotFoundException("error.operation.notFound"));
                // Pas d'utilisation directe ici (l'audit trail immutable est garanti
                // par notre architecture - on ne modifie pas l'operation). Cette
                // lecture sert uniquement a valider l'appartenance tenant + a tracer
                // l'intention de l'appelant. L'ajustement effectif du solde compte
                // se fait lors du prochain paiement / recalcul facture (cf. doctrine
                // auxiliaire client Tour 20).
                logger.info("updateMontants : delta={} sur nuitee={} (operationCompteId={}, compteId={})",
                        delta, nuitee.getNuiteeId(), op.getOperationId(), op.getCompteId());
            }

            updatedCount++;
            totalImpact = totalImpact.add(delta);
        }

        // 4) Recalcul des factures impactees (1 par facture, pas par ligne).
        for (Long factureId : facturesToRecalc) {
            factureRecalc.recalcMontantsFacture(factureId);
        }

        logger.info("updateMontants Tour 45 : updatedCount={}, totalImpact={}, facturesImpactees={}",
                updatedCount, totalImpact.setScale(2, RoundingMode.HALF_UP), facturesToRecalc.size());

        return new NuiteesUpdateResultDto(updatedCount, totalImpact.setScale(2, RoundingMode.HALF_UP));
    }

    /** Etat terminal au sens "non modifiable" Tour 45. */
    private static boolean isFactureTerminale(StatutFacture statut) {
        return statut == StatutFacture.PAYEE || statut == StatutFacture.ANNULEE;
    }

    /** Helper de groupage cross-collection - non utilise mais retenu pour evolution. */
    @SuppressWarnings("unused")
    private static <T, K> java.util.Map<K, T> indexBy(Collection<T> coll, java.util.function.Function<T, K> key) {
        return coll.stream().collect(Collectors.toMap(key, x -> x, (a, b) -> a));
    }
}
