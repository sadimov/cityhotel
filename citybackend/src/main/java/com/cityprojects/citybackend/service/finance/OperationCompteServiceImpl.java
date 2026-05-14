package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.finance.FolioDto;
import com.cityprojects.citybackend.dto.finance.OperationCompteFolioDto;
import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.finance.Compte;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.OperationCompte;
import com.cityprojects.citybackend.entity.finance.Paiement;
import com.cityprojects.citybackend.entity.finance.TypeOperationCompte;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.finance.CompteRepository;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.repository.finance.OperationCompteRepository;
import com.cityprojects.citybackend.repository.finance.PaiementRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation de {@link OperationCompteService}.
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
@Service
@RequireTenant
@Transactional
public class OperationCompteServiceImpl implements OperationCompteService {

    private static final Logger logger = LoggerFactory.getLogger(OperationCompteServiceImpl.class);

    /** Sentinel "system" pour l'audit user_id quand aucun SecurityContext n'est present. */
    private static final long SYSTEM_USER_ID = 0L;

    private final CompteRepository compteRepository;
    private final OperationCompteRepository operationRepository;
    private final CompteService compteService;
    private final ClientRepository clientRepository;
    private final FactureRepository factureRepository;
    private final PaiementRepository paiementRepository;

    public OperationCompteServiceImpl(CompteRepository compteRepository,
                                      OperationCompteRepository operationRepository,
                                      CompteService compteService,
                                      ClientRepository clientRepository,
                                      FactureRepository factureRepository,
                                      PaiementRepository paiementRepository) {
        this.compteRepository = compteRepository;
        this.operationRepository = operationRepository;
        this.compteService = compteService;
        this.clientRepository = clientRepository;
        this.factureRepository = factureRepository;
        this.paiementRepository = paiementRepository;
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

    /**
     * {@inheritDoc}
     *
     * <p>Convention de signe (cf. {@link FolioDto}) :
     * <ul>
     *   <li>DEBIT (facturation) augmente le solde-dette</li>
     *   <li>CREDIT (encaissement) diminue le solde-dette</li>
     * </ul>
     * Le {@code soldeApres} retourne est <b>recalcule depuis soldeOuverture</b>
     * pour le folio, pas le {@code soldeApres} chronologique stocke en base.
     * Cette projection est plus parlante pour l'extrait filtre par dates.</p>
     *
     * <p>Performance : O(N) sur toutes les operations du compte + O(M) lookups
     * de factures/lignes/paiements. N et M sont typiquement &lt; 100 (folio sur
     * la duree d'une reservation). Acceptable. Si besoin, faire un fetch en
     * lot via {@code findAllById} sur 3 sets distincts dans une iteration
     * ulterieure.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public FolioDto findFolio(Long clientId, LocalDate dateDebut, LocalDate dateFin) {
        if (clientId == null) {
            throw new ResourceNotFoundException("error.client.notFound");
        }
        // Verifier l'existence du client (tenant via @TenantId)
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("error.client.notFound"));

        // Resoudre / creer le compte client. Idempotent : un appel sur un
        // client qui n'a jamais eu de facture cree un compte avec solde 0.
        Compte compte = compteService.findOrCreateForClient(clientId);

        // Toutes les operations du compte triees chronologiquement.
        // findByCompteIdOrderByDateOperationDesc renvoie en DESC : on
        // l'inverse pour calcul d'accumulation chronologique stable.
        List<OperationCompte> all = new ArrayList<>(
                operationRepository.findByCompteIdOrderByDateOperationDesc(compte.getCompteId()));
        all.sort(Comparator
                .comparing(OperationCompte::getDateOperation)
                .thenComparing(OperationCompte::getOperationId));

        ZoneId zone = ZoneId.systemDefault();
        BigDecimal soldeOuverture = BigDecimal.ZERO;
        List<OperationCompte> dansLaPlage = new ArrayList<>();

        for (OperationCompte op : all) {
            LocalDate dateOp = op.getDateOperation().atZone(zone).toLocalDate();
            boolean avant = dateDebut != null && dateOp.isBefore(dateDebut);
            boolean apres = dateFin != null && dateOp.isAfter(dateFin);
            if (avant) {
                soldeOuverture = applyOperation(soldeOuverture, op);
            } else if (!apres) {
                dansLaPlage.add(op);
            }
            // sinon (apres dateFin) : ignore
        }

        // Lookups enrichissement (3 sets distincts pour un fetch en lot)
        Set<Long> factureIds = new HashSet<>();
        Set<Long> ligneIds = new HashSet<>();
        Set<Long> paiementIds = new HashSet<>();
        for (OperationCompte op : dansLaPlage) {
            if (op.getFactureId() != null) factureIds.add(op.getFactureId());
            if (op.getPaiementId() != null) paiementIds.add(op.getPaiementId());
        }
        // Pas de FK directe ligne_facture_id sur OperationCompte (modele actuel) :
        // le libelle ligneFacture reste null sauf cas particuliers (le frontend
        // peut tracer via factureId + parse libelle). Cf. doctrine Tour 22.1.

        Map<Long, String> factureNumeros = new HashMap<>();
        if (!factureIds.isEmpty()) {
            for (Facture f : factureRepository.findAllById(factureIds)) {
                factureNumeros.put(f.getFactureId(), f.getNumeroFacture());
            }
        }
        Map<Long, String> paiementNumeros = new HashMap<>();
        if (!paiementIds.isEmpty()) {
            for (Paiement p : paiementRepository.findAllById(paiementIds)) {
                paiementNumeros.put(p.getPaiementId(), p.getNumeroPaiement());
            }
        }

        // Construction des DTO avec soldeApres recalcule depuis soldeOuverture.
        BigDecimal soldeCourant = soldeOuverture;
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        List<OperationCompteFolioDto> dtos = new ArrayList<>(dansLaPlage.size());
        for (OperationCompte op : dansLaPlage) {
            soldeCourant = applyOperation(soldeCourant, op);
            if (op.getTypeOperation() == TypeOperationCompte.DEBIT) {
                totalDebits = totalDebits.add(op.getMontant());
            } else {
                totalCredits = totalCredits.add(op.getMontant());
            }
            LocalDate dateOp = op.getDateOperation().atZone(zone).toLocalDate();
            // Le libelle stocke = motif principal ; description = null (modele
            // actuel n'a qu'un champ libelle).
            String motif = op.getLibelle();
            // Lookup ligneFactureId : la FK n'existe pas sur OperationCompte
            // dans le modele courant (Tour 22.1). On laisse null pour ce DTO.
            Long ligneFactureId = null;
            String ligneLibelle = null;
            // Cas d'usage Tour 46 : si une operation DEBIT est rattachee a une
            // ligne facture (ce n'est pas le cas standard, le DEBIT initial est
            // pose globalement a l'emission), on pourrait remonter le libelle
            // via LigneFacture. Pour l'instant : libelle de l'operation suffit.

            dtos.add(new OperationCompteFolioDto(
                    op.getOperationId(),
                    dateOp,
                    op.getTypeOperation().name(),
                    motif,
                    null,
                    op.getMontant(),
                    soldeCourant,
                    op.getFactureId(),
                    op.getFactureId() != null ? factureNumeros.get(op.getFactureId()) : null,
                    ligneFactureId,
                    ligneLibelle,
                    op.getPaiementId(),
                    op.getPaiementId() != null ? paiementNumeros.get(op.getPaiementId()) : null));
        }

        BigDecimal soldeCloture = soldeCourant;
        String clientNom = composeClientNom(client);

        return new FolioDto(
                compte.getCompteId(),
                clientId,
                clientNom,
                soldeOuverture,
                soldeCloture,
                totalDebits,
                totalCredits,
                dtos);
    }

    /** Applique l'effet algebrique d'une operation au solde-dette. */
    private static BigDecimal applyOperation(BigDecimal solde, OperationCompte op) {
        if (op.getTypeOperation() == TypeOperationCompte.DEBIT) {
            return solde.add(op.getMontant());
        }
        return solde.subtract(op.getMontant());
    }

    /** "prenom + nom" trim. Fallback chaine vide. */
    private static String composeClientNom(Client client) {
        String prenom = client.getPrenom() != null ? client.getPrenom().trim() : "";
        String nom = client.getNom() != null ? client.getNom().trim() : "";
        String full = (prenom + " " + nom).trim();
        return full.isEmpty() ? null : full;
    }

}
