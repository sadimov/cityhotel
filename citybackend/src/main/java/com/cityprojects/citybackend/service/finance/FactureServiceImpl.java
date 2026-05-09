package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureCreateDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureDto;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.LigneFacture;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.TypeFacture;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.entity.hebergement.Nuitee;
import com.cityprojects.citybackend.entity.hebergement.Reservation;
import com.cityprojects.citybackend.entity.hebergement.StatutNuitee;
import com.cityprojects.citybackend.entity.restaurant.Commande;
import com.cityprojects.citybackend.entity.restaurant.LigneCommande;
import com.cityprojects.citybackend.entity.restaurant.ModeReglementCommande;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.finance.FactureMapper;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.client.SocieteRepository;
import com.cityprojects.citybackend.repository.finance.CompteRepository;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.repository.finance.LigneFactureRepository;
import com.cityprojects.citybackend.repository.hebergement.NuiteeRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.repository.inventory.FournisseurRepository;
import com.cityprojects.citybackend.repository.inventory.ProduitRepository;
import com.cityprojects.citybackend.repository.restaurant.CommandeRepository;
import com.cityprojects.citybackend.repository.restaurant.LigneCommandeRepository;
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
import java.util.List;

/**
 * Implementation de {@link FactureService}.
 *
 * <p>Conventions :
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe.</li>
 *   <li>{@code numeroFacture} genere via {@link NumerotationService} (FACT ou AVOIR).</li>
 *   <li>{@code userId} extrait du SecurityContext (jamais d'un DTO).</li>
 *   <li>Recalcul des montants atomique : {@code recalcMontantsFacture()} apres
 *       chaque modification de lignes.</li>
 *   <li>Soft delete uniquement (statut ANNULEE), pas de suppression physique.</li>
 * </ul>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class FactureServiceImpl implements FactureService {

    private static final Logger logger = LoggerFactory.getLogger(FactureServiceImpl.class);

    private final FactureRepository factureRepository;
    private final LigneFactureRepository ligneRepository;
    private final ReservationRepository reservationRepository;
    private final NuiteeRepository nuiteeRepository;
    private final ClientRepository clientRepository;
    private final SocieteRepository societeRepository;
    private final CompteRepository compteRepository;
    private final FournisseurRepository fournisseurRepository;
    private final ProduitRepository produitRepository;
    private final CommandeRepository commandeRepository;
    private final LigneCommandeRepository ligneCommandeRepository;
    private final FactureMapper mapper;
    private final NumerotationService numerotationService;
    private final CompteService compteService;
    private final OperationCompteService operationCompteService;

    public FactureServiceImpl(FactureRepository factureRepository,
                              LigneFactureRepository ligneRepository,
                              ReservationRepository reservationRepository,
                              NuiteeRepository nuiteeRepository,
                              ClientRepository clientRepository,
                              SocieteRepository societeRepository,
                              CompteRepository compteRepository,
                              FournisseurRepository fournisseurRepository,
                              ProduitRepository produitRepository,
                              CommandeRepository commandeRepository,
                              LigneCommandeRepository ligneCommandeRepository,
                              FactureMapper mapper,
                              NumerotationService numerotationService,
                              CompteService compteService,
                              OperationCompteService operationCompteService) {
        this.factureRepository = factureRepository;
        this.ligneRepository = ligneRepository;
        this.reservationRepository = reservationRepository;
        this.nuiteeRepository = nuiteeRepository;
        this.clientRepository = clientRepository;
        this.societeRepository = societeRepository;
        this.compteRepository = compteRepository;
        this.fournisseurRepository = fournisseurRepository;
        this.produitRepository = produitRepository;
        this.commandeRepository = commandeRepository;
        this.ligneCommandeRepository = ligneCommandeRepository;
        this.mapper = mapper;
        this.numerotationService = numerotationService;
        this.compteService = compteService;
        this.operationCompteService = operationCompteService;
    }

    @Override
    @Transactional
    public FactureDto create(FactureCreateDto dto) {
        Facture facture = new Facture();
        facture.setTypeFacture(dto.typeFacture() != null ? dto.typeFacture() : TypeFacture.FACTURE);
        // Validation tenant-aware des FK cross-module : Hibernate filtre la lecture
        // via @TenantId, donc un findById() depuis un autre hotel renvoie Optional.empty()
        // et leve ResourceNotFoundException -> impossible de persister une FK croisee.
        if (dto.compteId() != null) {
            compteRepository.findById(dto.compteId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.compte.notFound"));
        }
        facture.setCompteId(dto.compteId());
        if (dto.clientId() != null) {
            clientRepository.findById(dto.clientId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.client.notFound"));
        }
        facture.setClientId(dto.clientId());
        if (dto.societeId() != null) {
            societeRepository.findById(dto.societeId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.societe.notFound"));
        }
        facture.setSocieteId(dto.societeId());
        if (dto.reservationId() != null) {
            reservationRepository.findById(dto.reservationId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));
        }
        facture.setReservationId(dto.reservationId());
        if (dto.fournisseurId() != null) {
            fournisseurRepository.findById(dto.fournisseurId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.fournisseur.notFound"));
        }
        facture.setFournisseurId(dto.fournisseurId());
        facture.setDateFacture(dto.dateFacture() != null ? dto.dateFacture() : LocalDate.now());
        facture.setDateEcheance(dto.dateEcheance());
        facture.setDevise(dto.devise() != null ? dto.devise() : "MRU");
        facture.setCommentaires(dto.commentaires());
        facture.setStatut(StatutFacture.BROUILLON);
        facture.setUserId(currentUserId());

        TypeNumerotation typeNumero = (facture.getTypeFacture() == TypeFacture.AVOIR)
                ? TypeNumerotation.AVOIR
                : TypeNumerotation.FACT;
        facture.setNumeroFacture(numerotationService.next(typeNumero));
        // PAS de setHotelId : Hibernate s'en charge via @TenantId resolver.
        Facture saved = factureRepository.save(facture);

        // Cree les lignes (si fournies)
        if (dto.lignes() != null) {
            for (LigneFactureCreateDto ld : dto.lignes()) {
                creerLigne(saved.getFactureId(), ld);
            }
        }
        recalcMontantsFacture(saved.getFactureId());

        Facture refreshed = factureRepository.findById(saved.getFactureId())
                .orElseThrow(() -> new BusinessException("error.facture.notFound"));

        logger.info("Facture creee : id={}, numero={}, montantTtc={}",
                refreshed.getFactureId(), refreshed.getNumeroFacture(), refreshed.getMontantTtc());

        return toDtoWithLignes(refreshed);
    }

    @Override
    public FactureDto findById(Long factureId) {
        Facture facture = factureRepository.findById(factureId)
                .orElseThrow(() -> new ResourceNotFoundException("error.facture.notFound"));
        return toDtoWithLignes(facture);
    }

    @Override
    public Page<FactureDto> findAll(Pageable pageable) {
        return factureRepository.findAll(pageable).map(this::toDtoWithLignes);
    }

    @Override
    @Transactional
    public FactureDto emettre(Long factureId) {
        Facture facture = factureRepository.findById(factureId)
                .orElseThrow(() -> new ResourceNotFoundException("error.facture.notFound"));
        if (facture.getStatut() != StatutFacture.BROUILLON) {
            throw new BusinessException("error.facture.emission.statutInvalide");
        }
        facture.setStatut(StatutFacture.EMISE);
        factureRepository.save(facture);

        // Audit trail auxiliaire client (Tour 22.1) : DEBIT sur le compte du client
        // si la facture y est rattachee. Pas d'operation pour AVOIR (cf. Tour
        // finance-2 : ecriture inverse a definir) ni pour facture sans client
        // (cash anonyme, facture fournisseur via fournisseurId).
        recordDebitOnAccountIfApplicable(facture);

        logger.info("Facture emise : id={}, numero={}", facture.getFactureId(), facture.getNumeroFacture());
        return toDtoWithLignes(facture);
    }

    @Override
    @Transactional
    public FactureDto annuler(Long factureId) {
        Facture facture = factureRepository.findById(factureId)
                .orElseThrow(() -> new ResourceNotFoundException("error.facture.notFound"));
        if (facture.getStatut() == StatutFacture.ANNULEE) {
            throw new BusinessException("error.facture.dejaAnnulee");
        }
        if (facture.getStatut() == StatutFacture.PARTIELLEMENT_PAYEE
                || facture.getStatut() == StatutFacture.PAYEE) {
            throw new BusinessException("error.facture.annulation.statutInvalide");
        }
        if (facture.getMontantPaye().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("error.facture.annulation.dejaPayee");
        }
        facture.setStatut(StatutFacture.ANNULEE);
        factureRepository.save(facture);
        logger.info("Facture annulee : id={}, numero={}", facture.getFactureId(), facture.getNumeroFacture());
        return toDtoWithLignes(facture);
    }

    @Override
    @Transactional
    public FactureDto fromReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));

        if (reservation.getFactureId() != null) {
            throw new BusinessException("error.facture.reservation.dejaFacturee");
        }

        // Recupere les nuitees CONSOMMEE non encore facturees
        List<Nuitee> nuitees = nuiteeRepository.findByReservationIdOrderByDateNuitAsc(reservationId);
        List<Nuitee> nuiteesAFacturer = new ArrayList<>();
        for (Nuitee n : nuitees) {
            if (n.getStatut() == StatutNuitee.CONSOMMEE && n.getFactureId() == null) {
                nuiteesAFacturer.add(n);
            }
        }

        // Tour 25 : recupere AUSSI les commandes REPORTE_CHAMBRE non encore facturees
        // pour les rattacher a la meme facture sejour (single source of truth pour
        // le client a la sortie).
        List<Commande> commandesReportees = commandeRepository
                .findByReservationIdAndModeReglementAndFactureIdIsNull(
                        reservationId, ModeReglementCommande.REPORTE_CHAMBRE);

        if (nuiteesAFacturer.isEmpty() && commandesReportees.isEmpty()) {
            throw new BusinessException("error.facture.reservation.aucuneNuiteeAFacturer");
        }

        // Cree la facture (BROUILLON puis EMISE en fin de methode)
        Facture facture = new Facture();
        facture.setTypeFacture(TypeFacture.FACTURE);
        facture.setReservationId(reservationId);
        facture.setClientId(reservation.getClientPrincipalId());
        facture.setSocieteId(reservation.getSocieteId());
        facture.setDateFacture(LocalDate.now());
        facture.setDevise("MRU");
        facture.setStatut(StatutFacture.BROUILLON);
        facture.setUserId(currentUserId());
        facture.setNumeroFacture(numerotationService.next(TypeNumerotation.FACT));
        Facture savedFacture = factureRepository.save(facture);

        // Cree 1 ligne NUITEE par nuitee
        for (Nuitee n : nuiteesAFacturer) {
            LigneFacture ligne = new LigneFacture();
            ligne.setFactureId(savedFacture.getFactureId());
            ligne.setTypeLigne(TypeLigneFacture.NUITEE);
            ligne.setNuiteeId(n.getNuiteeId());
            ligne.setLibelle("Nuit du " + n.getDateNuit() + " - chambre " + n.getChambreId());
            ligne.setQuantite(BigDecimal.ONE);
            ligne.setPrixUnitaire(n.getPrixNuit());
            ligne.setTauxTva(BigDecimal.ZERO);
            ligne.setDatePrestation(n.getDateNuit());
            LigneFacture savedLigne = ligneRepository.save(ligne);

            // Met a jour la nuitee : facture_id + ligne_facture_id + statut FACTUREE
            n.setFactureId(savedFacture.getFactureId());
            n.setLigneFactureId(savedLigne.getLigneFactureId());
            n.setStatut(StatutNuitee.FACTUREE);
            nuiteeRepository.save(n);
        }

        // Tour 25 : cree 1 ligne COMMANDE par ligne de chaque commande REPORTE_CHAMBRE,
        // puis attache la commande a la facture (commande.factureId).
        // Pas de TVA POS (cf. doctrine prompt_restaurant_pos.txt).
        for (Commande cmd : commandesReportees) {
            List<LigneCommande> lignesCmd = ligneCommandeRepository
                    .findByCommandeIdOrderByLigneIdAsc(cmd.getCommandeId());
            for (LigneCommande lc : lignesCmd) {
                LigneFacture lf = new LigneFacture();
                lf.setFactureId(savedFacture.getFactureId());
                lf.setTypeLigne(TypeLigneFacture.COMMANDE);
                lf.setCommandeId(cmd.getCommandeId());
                lf.setLibelle(lc.getLibelle());
                lf.setQuantite(lc.getQuantite());
                lf.setPrixUnitaire(lc.getPrixUnitaire());
                lf.setTauxTva(BigDecimal.ZERO);
                ligneRepository.save(lf);
            }
            cmd.setFactureId(savedFacture.getFactureId());
            commandeRepository.save(cmd);
        }

        // Met a jour la reservation : facture_id
        reservation.setFactureId(savedFacture.getFactureId());
        reservationRepository.save(reservation);

        // Recalcule les montants (inclut nuitees + commandes reportees)
        recalcMontantsFacture(savedFacture.getFactureId());

        // Emet la facture (transition BROUILLON -> EMISE pour cloturer le flow)
        Facture refreshed = factureRepository.findById(savedFacture.getFactureId())
                .orElseThrow(() -> new BusinessException("error.facture.notFound"));
        refreshed.setStatut(StatutFacture.EMISE);
        factureRepository.save(refreshed);

        // Audit trail auxiliaire client (Tour 22.1) : DEBIT sur le compte client
        // a l'emission, identique au flux emettre() manuel. Le montant inclut
        // nuitees + commandes reportees (coherent avec la facture totale).
        recordDebitOnAccountIfApplicable(refreshed);

        logger.info("Facture creee depuis reservation {} : id={}, numero={}, nuitees={}, commandesReportees={}, total={}",
                reservationId, refreshed.getFactureId(), refreshed.getNumeroFacture(),
                nuiteesAFacturer.size(), commandesReportees.size(), refreshed.getMontantTtc());

        return toDtoWithLignes(refreshed);
    }

    @Override
    @Transactional
    public FactureDto fromCommande(Long commandeId) {
        Commande commande = commandeRepository.findById(commandeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.commande.notFound"));

        if (commande.getModeReglement() != ModeReglementCommande.COMPTANT) {
            throw new BusinessException("error.facture.commande.modeReglementInvalide");
        }
        if (commande.getFactureId() != null) {
            throw new BusinessException("error.facture.commande.dejaFacturee");
        }

        List<LigneCommande> lignesCommande = ligneCommandeRepository
                .findByCommandeIdOrderByLigneIdAsc(commandeId);
        if (lignesCommande.isEmpty()) {
            throw new BusinessException("error.facture.commande.aucuneLigne");
        }

        // Cree la facture (BROUILLON puis EMISE en fin de methode pour reutiliser
        // le pattern fromReservation : recalc montants atomique + emission via
        // setter direct, sans repasser par emettre() pour eviter un 2e DEBIT).
        Facture facture = new Facture();
        facture.setTypeFacture(TypeFacture.FACTURE);
        facture.setClientId(commande.getClientId());
        facture.setDateFacture(LocalDate.now());
        facture.setDevise(commande.getDevise() != null ? commande.getDevise() : "MRU");
        facture.setStatut(StatutFacture.BROUILLON);
        facture.setUserId(currentUserId());
        facture.setNumeroFacture(numerotationService.next(TypeNumerotation.FACT));
        Facture saved = factureRepository.save(facture);

        // Cree 1 ligne de facture par ligne de commande, avec snapshot.
        for (LigneCommande lc : lignesCommande) {
            LigneFacture ligne = new LigneFacture();
            ligne.setFactureId(saved.getFactureId());
            ligne.setTypeLigne(TypeLigneFacture.COMMANDE);
            ligne.setCommandeId(commandeId);
            ligne.setLibelle(lc.getLibelle());
            ligne.setQuantite(lc.getQuantite());
            ligne.setPrixUnitaire(lc.getPrixUnitaire());
            ligne.setTauxTva(BigDecimal.ZERO); // Pas de TVA POS - prompt_restaurant_pos.txt
            ligneRepository.save(ligne);
        }

        recalcMontantsFacture(saved.getFactureId());

        // Emission immediate (commande COMPTANT = facturation directe + paiement
        // dans la foulee cote CommandeService.encaisserComptant)
        Facture refreshed = factureRepository.findById(saved.getFactureId())
                .orElseThrow(() -> new BusinessException("error.facture.notFound"));
        refreshed.setStatut(StatutFacture.EMISE);
        factureRepository.save(refreshed);

        // Audit trail auxiliaire client (Tour 22.1) : DEBIT sur compte client si
        // clientId existe (sinon facture cash anonyme : pas de compte auxiliaire).
        recordDebitOnAccountIfApplicable(refreshed);

        // Met a jour la commande : facture_id pointe sur la facture creee.
        commande.setFactureId(refreshed.getFactureId());
        commandeRepository.save(commande);

        logger.info("Facture creee depuis commande {} : id={}, numero={}, lignes={}, total={}",
                commandeId, refreshed.getFactureId(), refreshed.getNumeroFacture(),
                lignesCommande.size(), refreshed.getMontantTtc());

        return toDtoWithLignes(refreshed);
    }

    /**
     * Enregistre un DEBIT sur le compte auxiliaire CLIENT a l'emission d'une
     * facture standard. No-op si :
     * <ul>
     *   <li>la facture est un AVOIR (ecriture inverse differee Tour finance-2),</li>
     *   <li>aucun {@code clientId} n'est rattache (cash anonyme ou facture
     *       fournisseur via {@code fournisseurId}),</li>
     *   <li>{@code montantTtc} est nul ou negatif (facture vide, ne devrait pas
     *       arriver mais on protege).</li>
     * </ul>
     *
     * <p>Le compte est cree a la volee si inexistant (idempotent via
     * {@link CompteService#findOrCreateForClient(Long)}).</p>
     *
     * <p>TODO Tour finance-2 : gerer le cas {@code TypeFacture.AVOIR} avec
     * ecriture CREDIT sur le compte client (annulation comptable d'une dette
     * precedemment debitee).</p>
     */
    @SuppressWarnings("deprecation")
    private void recordDebitOnAccountIfApplicable(Facture facture) {
        if (facture.getTypeFacture() != TypeFacture.FACTURE) {
            return; // AVOIR : differé Tour finance-2
        }
        if (facture.getClientId() == null) {
            return; // cash anonyme ou facture fournisseur
        }
        if (facture.getMontantTtc() == null || facture.getMontantTtc().signum() <= 0) {
            return;
        }
        var compte = compteService.findOrCreateForClient(facture.getClientId());
        operationCompteService.recordDebit(
                compte.getCompteId(),
                facture.getMontantTtc(),
                facture.getFactureId(),
                "Facture " + facture.getNumeroFacture());
    }

    /**
     * Cree une ligne de facture. Verifie que la facture parent existe et est
     * accessible au tenant courant (Hibernate filtre via @TenantId).
     */
    private void creerLigne(Long factureId, LigneFactureCreateDto dto) {
        // Verifie que la facture existe et appartient au tenant courant
        factureRepository.findById(factureId)
                .orElseThrow(() -> new ResourceNotFoundException("error.facture.notFound"));

        // Validation tenant-aware des FK cross-module de la ligne :
        // Hibernate filtre la lecture via @TenantId, donc un findById() depuis un
        // autre hotel renvoie Optional.empty() -> impossible de creer une ligne
        // referencant une nuitee/produit d'un autre tenant.
        if (dto.nuiteeId() != null) {
            nuiteeRepository.findById(dto.nuiteeId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.nuitee.notFound"));
        }
        if (dto.produitId() != null) {
            produitRepository.findById(dto.produitId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.produit.notFound"));
        }
        if (dto.commandeId() != null) {
            commandeRepository.findById(dto.commandeId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.commande.notFound"));
        }
        // TODO Vague 2 : valider serviceId quand l'entite Service metier existera.

        LigneFacture ligne = new LigneFacture();
        ligne.setFactureId(factureId);
        ligne.setTypeLigne(dto.typeLigne());
        ligne.setNuiteeId(dto.nuiteeId());
        ligne.setProduitId(dto.produitId());
        ligne.setCommandeId(dto.commandeId());
        ligne.setServiceId(dto.serviceId());
        ligne.setLibelle(dto.libelle());
        ligne.setQuantite(dto.quantite());
        ligne.setPrixUnitaire(dto.prixUnitaire());
        ligne.setTauxTva(dto.tauxTva() != null ? dto.tauxTva() : BigDecimal.ZERO);
        ligne.setDatePrestation(dto.datePrestation());
        ligneRepository.save(ligne);
    }

    /**
     * Recalcule les montants HT/TVA/TTC d'une facture a partir de ses lignes.
     * Source unique de verite : la somme des montants des lignes (la ligne
     * est elle-meme recalculee via @PrePersist/@PreUpdate).
     *
     * <p>Arrondi HALF_UP scale=2.</p>
     */
    void recalcMontantsFacture(Long factureId) {
        Facture facture = factureRepository.findById(factureId)
                .orElseThrow(() -> new ResourceNotFoundException("error.facture.notFound"));
        List<LigneFacture> lignes = ligneRepository.findByFactureIdOrderByLigneFactureIdAsc(factureId);

        BigDecimal sommeHt = BigDecimal.ZERO;
        BigDecimal sommeTva = BigDecimal.ZERO;
        BigDecimal sommeTtc = BigDecimal.ZERO;
        for (LigneFacture l : lignes) {
            sommeHt = sommeHt.add(l.getMontantHt() != null ? l.getMontantHt() : BigDecimal.ZERO);
            sommeTva = sommeTva.add(l.getMontantTva() != null ? l.getMontantTva() : BigDecimal.ZERO);
            sommeTtc = sommeTtc.add(l.getMontantTtc() != null ? l.getMontantTtc() : BigDecimal.ZERO);
        }
        facture.setMontantHt(sommeHt.setScale(2, RoundingMode.HALF_UP));
        facture.setMontantTva(sommeTva.setScale(2, RoundingMode.HALF_UP));
        facture.setMontantTtc(sommeTtc.setScale(2, RoundingMode.HALF_UP));
        factureRepository.save(facture);
    }

    private FactureDto toDtoWithLignes(Facture facture) {
        FactureDto base = mapper.toDto(facture);
        List<LigneFactureDto> lignes = ligneRepository
                .findByFactureIdOrderByLigneFactureIdAsc(facture.getFactureId())
                .stream().map(mapper::toLigneDto).toList();
        return mapper.withLignes(base, lignes);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId();
        }
        throw new BusinessException("error.user.unknown");
    }
}
