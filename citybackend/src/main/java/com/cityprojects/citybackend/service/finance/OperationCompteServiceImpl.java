package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.entity.finance.Compte;
import com.cityprojects.citybackend.entity.finance.OperationCompte;
import com.cityprojects.citybackend.entity.finance.TypeOperationCompte;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.finance.CompteRepository;
import com.cityprojects.citybackend.repository.finance.OperationCompteRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Implementation de {@link OperationCompteService}.
 *
 * <p><b>⚠️ Justification du {@code @SuppressWarnings("deprecation")}.</b>
 * Idem {@link CompteServiceImpl} : les entites {@link Compte} et
 * {@link OperationCompte} sont {@code @Deprecated(forRemoval=false)} pour
 * signaler qu'un renommage semantique est prevu (Tour 20bis), pas pour les
 * supprimer. La fonctionnalite reste valide et ce service en depend
 * volontairement.</p>
 *
 * <h3>Coherence sous concurrence</h3>
 * <p>{@link CompteRepository#findByIdForUpdate(Long)} pose un verrou
 * pessimiste {@code SELECT ... FOR UPDATE} sur la ligne {@code finance.comptes}
 * cible. Les transactions concurrentes qui veulent debiter/crediter le meme
 * compte attendent la liberation du verrou (commit/rollback). C'est l'option
 * la plus simple et la plus sure pour un audit trail de comptabilite
 * auxiliaire (volume modere, pas de hot path POS direct).</p>
 *
 * <h3>userId pour l'audit</h3>
 * <p>Extrait du {@link SecurityContextHolder} (jamais d'un parametre). Si le
 * SecurityContext est vide (cas batch / scheduler / test sans authentification),
 * on retombe sur {@code 0L} (sentinel system) plutot que de lever une
 * exception : un appel batch interne ne doit pas casser l'ecriture de l'audit
 * trail. La traceabilite reste assuree par {@code dateOperation} et le
 * {@code factureId}/{@code paiementId} associe.</p>
 */
@SuppressWarnings("deprecation")
@Service
@RequireTenant
@Transactional
public class OperationCompteServiceImpl implements OperationCompteService {

    private static final Logger logger = LoggerFactory.getLogger(OperationCompteServiceImpl.class);

    /** Sentinel "system" pour l'audit user_id quand aucun SecurityContext n'est present. */
    private static final long SYSTEM_USER_ID = 0L;

    private final CompteRepository compteRepository;
    private final OperationCompteRepository operationRepository;

    public OperationCompteServiceImpl(CompteRepository compteRepository,
                                      OperationCompteRepository operationRepository) {
        this.compteRepository = compteRepository;
        this.operationRepository = operationRepository;
    }

    @Override
    public OperationCompte recordDebit(Long compteId, BigDecimal montant, Long factureId, String libelle) {
        validateCommonArgs(compteId, montant, libelle);
        // Lock pessimiste : aucune autre transaction ne peut modifier le solde
        // tant que cette transaction n'est pas commitee.
        Compte compte = compteRepository.findByIdForUpdate(compteId)
                .orElseThrow(() -> new ResourceNotFoundException("error.compte.notFound"));

        BigDecimal soldeAvant = compte.getSoldeActuel() != null ? compte.getSoldeActuel() : BigDecimal.ZERO;
        BigDecimal soldeApres = soldeAvant.add(montant);
        compte.setSoldeActuel(soldeApres);
        compteRepository.save(compte);

        OperationCompte op = buildOperation(compteId, TypeOperationCompte.DEBIT, montant,
                soldeAvant, soldeApres, libelle);
        op.setFactureId(factureId);
        OperationCompte saved = operationRepository.save(op);
        logger.info("OperationCompte DEBIT enregistree : opId={}, compteId={}, montant={}, "
                        + "soldeAvant={}, soldeApres={}, factureId={}",
                saved.getOperationId(), compteId, montant, soldeAvant, soldeApres, factureId);
        return saved;
    }

    @Override
    public OperationCompte recordCredit(Long compteId, BigDecimal montant, Long paiementId, String libelle) {
        validateCommonArgs(compteId, montant, libelle);
        Compte compte = compteRepository.findByIdForUpdate(compteId)
                .orElseThrow(() -> new ResourceNotFoundException("error.compte.notFound"));

        BigDecimal soldeAvant = compte.getSoldeActuel() != null ? compte.getSoldeActuel() : BigDecimal.ZERO;
        BigDecimal soldeApres = soldeAvant.subtract(montant);
        compte.setSoldeActuel(soldeApres);
        compteRepository.save(compte);

        OperationCompte op = buildOperation(compteId, TypeOperationCompte.CREDIT, montant,
                soldeAvant, soldeApres, libelle);
        op.setPaiementId(paiementId);
        OperationCompte saved = operationRepository.save(op);
        logger.info("OperationCompte CREDIT enregistree : opId={}, compteId={}, montant={}, "
                        + "soldeAvant={}, soldeApres={}, paiementId={}",
                saved.getOperationId(), compteId, montant, soldeAvant, soldeApres, paiementId);
        return saved;
    }

    private static void validateCommonArgs(Long compteId, BigDecimal montant, String libelle) {
        if (compteId == null) {
            throw new IllegalArgumentException("compteId requis");
        }
        if (montant == null || montant.signum() <= 0) {
            throw new IllegalArgumentException("montant doit etre strictement positif");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("libelle requis");
        }
    }

    private OperationCompte buildOperation(Long compteId, TypeOperationCompte type, BigDecimal montant,
                                           BigDecimal soldeAvant, BigDecimal soldeApres, String libelle) {
        OperationCompte op = new OperationCompte();
        op.setCompteId(compteId);
        op.setTypeOperation(type);
        op.setMontant(montant);
        op.setSoldeAvant(soldeAvant);
        op.setSoldeApres(soldeApres);
        op.setLibelle(libelle);
        op.setDateOperation(Instant.now());
        op.setUserId(currentUserIdOrSystem());
        // PAS de setHotelId : Hibernate via @TenantId.
        return op;
    }

    private static long currentUserIdOrSystem() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            Long uid = principal.getUserId();
            return uid != null ? uid : SYSTEM_USER_ID;
        }
        return SYSTEM_USER_ID;
    }
}
