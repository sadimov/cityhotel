package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.audit.AuditFinanceAction;
import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.finance.FactureCreateDto;
import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureCreateDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureRecapDto;
import com.cityprojects.citybackend.dto.finance.LigneServiceCreateRequest;
import com.cityprojects.citybackend.dto.finance.TransfertLignesRequest;
import com.cityprojects.citybackend.entity.finance.AffectationPaiement;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.LigneFacture;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.TypeFacture;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.entity.finance.TypeServiceTva;
import com.cityprojects.citybackend.entity.inventory.ServiceHotelier;
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
import com.cityprojects.citybackend.repository.finance.AffectationPaiementRepository;
import com.cityprojects.citybackend.repository.finance.CompteRepository;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.repository.finance.LigneFactureRepository;
import com.cityprojects.citybackend.repository.hebergement.NuiteeRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.repository.inventory.FournisseurRepository;
import com.cityprojects.citybackend.repository.inventory.ProduitRepository;
import com.cityprojects.citybackend.repository.inventory.ServiceHotelierRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
public class FactureServiceImpl implements FactureService, FactureRecalcInternalService {

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
    private final ServiceHotelierRepository serviceHotelierRepository;
    private final CommandeRepository commandeRepository;
    private final LigneCommandeRepository ligneCommandeRepository;
    private final AffectationPaiementRepository affectationRepository;
    private final FactureMapper mapper;
    private final NumerotationService numerotationService;
    private final CompteService compteService;
    private final OperationCompteService operationCompteService;
    private final ExerciceService exerciceService;
    private final EcritureGenerationService ecritureGenerationService;
    private final EcritureComptableService ecritureComptableService;
    private final TauxTvaConfigService tauxTvaConfigService;

    public FactureServiceImpl(FactureRepository factureRepository,
                              LigneFactureRepository ligneRepository,
                              ReservationRepository reservationRepository,
                              NuiteeRepository nuiteeRepository,
                              ClientRepository clientRepository,
                              SocieteRepository societeRepository,
                              CompteRepository compteRepository,
                              FournisseurRepository fournisseurRepository,
                              ProduitRepository produitRepository,
                              ServiceHotelierRepository serviceHotelierRepository,
                              CommandeRepository commandeRepository,
                              LigneCommandeRepository ligneCommandeRepository,
                              AffectationPaiementRepository affectationRepository,
                              FactureMapper mapper,
                              NumerotationService numerotationService,
                              CompteService compteService,
                              OperationCompteService operationCompteService,
                              ExerciceService exerciceService,
                              EcritureGenerationService ecritureGenerationService,
                              EcritureComptableService ecritureComptableService,
                              TauxTvaConfigService tauxTvaConfigService) {
        this.factureRepository = factureRepository;
        this.ligneRepository = ligneRepository;
        this.reservationRepository = reservationRepository;
        this.nuiteeRepository = nuiteeRepository;
        this.clientRepository = clientRepository;
        this.societeRepository = societeRepository;
        this.compteRepository = compteRepository;
        this.fournisseurRepository = fournisseurRepository;
        this.produitRepository = produitRepository;
        this.serviceHotelierRepository = serviceHotelierRepository;
        this.commandeRepository = commandeRepository;
        this.ligneCommandeRepository = ligneCommandeRepository;
        this.affectationRepository = affectationRepository;
        this.mapper = mapper;
        this.numerotationService = numerotationService;
        this.compteService = compteService;
        this.operationCompteService = operationCompteService;
        this.exerciceService = exerciceService;
        this.ecritureGenerationService = ecritureGenerationService;
        this.ecritureComptableService = ecritureComptableService;
        this.tauxTvaConfigService = tauxTvaConfigService;
    }

    /**
     * Mapping {@link TypeLigneFacture} -&gt; {@link TypeServiceTva} (B4).
     *
     * <p>Source unique pour appliquer le bon taux TVA selon le type de
     * ligne. Les producteurs internes ({@code fromReservation},
     * {@code fromCommande}, etc.) appliquent ce mapping ; les DTOs externes
     * ({@code LigneFactureCreateDto.tauxTva}) peuvent override manuellement
     * pour des cas speciaux.</p>
     */
    private static TypeServiceTva tvaTypeForLigne(TypeLigneFacture type) {
        if (type == null) {
            return TypeServiceTva.AUTRE_SERVICE_HOTELIER;
        }
        return switch (type) {
            case NUITEE -> TypeServiceTva.HEBERGEMENT_NUITEE;
            case PRODUIT -> TypeServiceTva.RESTAURATION;
            case COMMANDE -> TypeServiceTva.RESTAURATION;
            case SERVICE -> TypeServiceTva.AUTRE_SERVICE_HOTELIER;
            case DIVERS -> TypeServiceTva.AUTRE_SERVICE_HOTELIER;
        };
    }

    @Override
    @Transactional
    @AuditFinanceAction(value = "FACTURE_CREATION", entityType = "FACTURE")
    public FactureDto create(FactureCreateDto dto) {
        // Garde anti-modification dans exercice clos (B1) : refuse la
        // creation d'une facture dont la date appartient a un exercice
        // EN_CLOTURE ou CLOTURE. Auto-cree l'exercice courant si necessaire.
        LocalDate dateCible = dto.dateFacture() != null ? dto.dateFacture() : LocalDate.now();
        exerciceService.assertOuvert(dateCible);

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
    @AuditFinanceAction(value = "FACTURE_EMISSION", entityType = "FACTURE")
    public FactureDto emettre(Long factureId) {
        Facture facture = factureRepository.findById(factureId)
                .orElseThrow(() -> new ResourceNotFoundException("error.facture.notFound"));
        if (facture.getStatut() != StatutFacture.BROUILLON) {
            // Idempotence : si deja EMISE / PAYEE / ANNULEE, refus avant
            // generation d'ecriture (evite un 2e jeu d'ecritures).
            throw new BusinessException("error.facture.emission.statutInvalide");
        }
        facture.setStatut(StatutFacture.EMISE);
        factureRepository.save(facture);

        // Audit trail auxiliaire client (Tour 22.1) : DEBIT sur le compte du client
        // si la facture y est rattachee. Pas d'operation pour AVOIR (cf. Tour
        // finance-2 : ecriture inverse a definir) ni pour facture sans client
        // (cash anonyme, facture fournisseur via fournisseurId).
        recordDebitOnAccountIfApplicable(facture);

        // Bloc B3 : generation atomique de l'ecriture VTE (411xxx D / 706xxx C).
        // Si la generation echoue (exercice clos, compte invalide, etc.) la TX
        // rollback et la facture reste BROUILLON. Cas AVOIR : skip - la
        // contre-passation specifique sera traitee plus tard (cf. emettreEcritureFacture
        // qui ne genere QUE pour TypeFacture.FACTURE via le compte client).
        if (facture.getTypeFacture() == TypeFacture.FACTURE) {
            Long ecritureId = ecritureGenerationService.emettreEcritureFacture(facture);
            if (ecritureId != null) {
                facture.setEcritureEmissionId(ecritureId);
                factureRepository.save(facture);
            }
        }

        logger.info("Facture emise : id={}, numero={}, ecritureEmissionId={}",
                facture.getFactureId(), facture.getNumeroFacture(),
                facture.getEcritureEmissionId());
        return toDtoWithLignes(facture);
    }

    @Override
    @Transactional
    @AuditFinanceAction(value = "FACTURE_ANNULATION", entityType = "FACTURE")
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
        boolean avaitEcriture = facture.getEcritureEmissionId() != null;
        facture.setStatut(StatutFacture.ANNULEE);
        factureRepository.save(facture);

        // Bloc B3 : contre-passation de l'ecriture VTE si la facture avait
        // ete emise (donc avait genere une ecriture). La contre-passation
        // se fait sur l'exercice OUVERT courant (cf. EcritureComptableService).
        if (avaitEcriture) {
            ecritureComptableService.contrePasser(
                    facture.getEcritureEmissionId(),
                    "Annulation facture " + facture.getNumeroFacture());
        }

        logger.info("Facture annulee : id={}, numero={}, contrePassation={}",
                facture.getFactureId(), facture.getNumeroFacture(), avaitEcriture);
        return toDtoWithLignes(facture);
    }

    @Override
    @Transactional
    public FactureDto fromReservation(Long reservationId) {
        // Garde anti-modification dans exercice clos (B1).
        exerciceService.assertOuvert(LocalDate.now());

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

        // Cree 1 ligne NUITEE par nuitee. Taux TVA resolu via TauxTvaConfigService
        // (B4) : par defaut HEBERGEMENT_NUITEE = 0% (exoneration de fait
        // dans la pratique hoteliere mauritanienne). Un admin peut surcharger
        // a 16% via /api/finance/tva/config si l'etablissement est concerne.
        BigDecimal tauxNuitee = tauxTvaConfigService.getTaux(TypeServiceTva.HEBERGEMENT_NUITEE);
        for (Nuitee n : nuiteesAFacturer) {
            LigneFacture ligne = new LigneFacture();
            ligne.setFactureId(savedFacture.getFactureId());
            ligne.setTypeLigne(TypeLigneFacture.NUITEE);
            ligne.setNuiteeId(n.getNuiteeId());
            ligne.setLibelle("Nuit du " + n.getDateNuit() + " - chambre " + n.getChambreId());
            ligne.setQuantite(BigDecimal.ONE);
            ligne.setPrixUnitaire(n.getPrixNuit());
            ligne.setTauxTva(tauxNuitee);
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
        // Pas de TVA POS (doctrine prompt_restaurant_pos.txt) : la TVA n'est
        // pas appliquee sur les commandes POS reportees en chambre. Si un hotel
        // doit la facturer, il pourra surcharger via une evolution dediee
        // (B4 ne casse pas la doctrine POS existante).
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

        // Bloc B3 : ecriture VTE associee (411xxx D / 706xxx C).
        if (refreshed.getTypeFacture() == TypeFacture.FACTURE) {
            Long ecritureId = ecritureGenerationService.emettreEcritureFacture(refreshed);
            if (ecritureId != null) {
                refreshed.setEcritureEmissionId(ecritureId);
                factureRepository.save(refreshed);
            }
        }

        logger.info("Facture creee depuis reservation {} : id={}, numero={}, nuitees={}, commandesReportees={}, total={}",
                reservationId, refreshed.getFactureId(), refreshed.getNumeroFacture(),
                nuiteesAFacturer.size(), commandesReportees.size(), refreshed.getMontantTtc());

        return toDtoWithLignes(refreshed);
    }

    @Override
    @Transactional
    public FactureDto previsionFromReservation(Long reservationId) {
        // Garde anti-modification dans exercice clos (B1).
        exerciceService.assertOuvert(LocalDate.now());

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));

        if (reservation.getFactureId() != null) {
            throw new BusinessException("error.facture.reservation.dejaFacturee");
        }

        // Toutes les nuitees de la reservation, quel que soit leur statut
        // (incl. PREVUE - la facture est creee a l'avance).
        List<Nuitee> nuitees = nuiteeRepository.findByReservationIdOrderByDateNuitAsc(reservationId);
        if (nuitees.isEmpty()) {
            throw new BusinessException("error.facture.reservation.aucuneNuitee");
        }

        // Cree la facture en BROUILLON puis EMISE (recalc montants atomique).
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

        // 1 ligne NUITEE par nuitee. Pas de transition de statut (Nuitee
        // reste PREVUE jusqu'au check-in / night audit). On rattache toutefois
        // factureId + ligneFactureId pour la traçabilite. Taux TVA B4 :
        // HEBERGEMENT_NUITEE (defaut 0%, surchargable).
        BigDecimal tauxNuiteePrev = tauxTvaConfigService.getTaux(TypeServiceTva.HEBERGEMENT_NUITEE);
        for (Nuitee n : nuitees) {
            LigneFacture ligne = new LigneFacture();
            ligne.setFactureId(savedFacture.getFactureId());
            ligne.setTypeLigne(TypeLigneFacture.NUITEE);
            ligne.setNuiteeId(n.getNuiteeId());
            ligne.setLibelle("Nuit du " + n.getDateNuit() + " - chambre " + n.getChambreId());
            ligne.setQuantite(BigDecimal.ONE);
            ligne.setPrixUnitaire(n.getPrixNuit());
            ligne.setTauxTva(tauxNuiteePrev);
            ligne.setDatePrestation(n.getDateNuit());
            LigneFacture savedLigne = ligneRepository.save(ligne);

            n.setFactureId(savedFacture.getFactureId());
            n.setLigneFactureId(savedLigne.getLigneFactureId());
            // Volontairement PAS de setStatut(FACTUREE) : la nuitee reste
            // PREVUE et passera CONSOMMEE/FACTUREE selon le flux check-in.
            nuiteeRepository.save(n);
        }

        // Met a jour la reservation : factureId pointe sur la prevision.
        reservation.setFactureId(savedFacture.getFactureId());
        reservationRepository.save(reservation);

        // Recalcule les montants HT/TVA/TTC (somme lignes via @PrePersist).
        recalcMontantsFacture(savedFacture.getFactureId());

        // Emet immediatement (la facture previsionnelle est figee : la
        // reservation peut deja recevoir des paiements).
        Facture refreshed = factureRepository.findById(savedFacture.getFactureId())
                .orElseThrow(() -> new BusinessException("error.facture.notFound"));
        refreshed.setStatut(StatutFacture.EMISE);
        factureRepository.save(refreshed);

        // DEBIT compte auxiliaire client (engage la dette des creation).
        recordDebitOnAccountIfApplicable(refreshed);

        // Bloc B3 : ecriture VTE associee.
        if (refreshed.getTypeFacture() == TypeFacture.FACTURE) {
            Long ecritureId = ecritureGenerationService.emettreEcritureFacture(refreshed);
            if (ecritureId != null) {
                refreshed.setEcritureEmissionId(ecritureId);
                factureRepository.save(refreshed);
            }
        }

        logger.info("Facture previsionnelle creee depuis reservation {} : id={}, numero={}, nuitees={}, total={}",
                reservationId, refreshed.getFactureId(), refreshed.getNumeroFacture(),
                nuitees.size(), refreshed.getMontantTtc());

        return toDtoWithLignes(refreshed);
    }

    @Override
    @Transactional
    public FactureDto fromCommande(Long commandeId) {
        // Garde anti-modification dans exercice clos (B1).
        exerciceService.assertOuvert(LocalDate.now());

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

        // Bloc B3 : ecriture VTE associee (commande comptant -> facture EMISE,
        // l'ecriture est posee maintenant ; l'encaissement separe declenchera
        // sa propre ecriture CAI/BAN via PaiementServiceImpl.create()).
        if (refreshed.getTypeFacture() == TypeFacture.FACTURE) {
            Long ecritureId = ecritureGenerationService.emettreEcritureFacture(refreshed);
            if (ecritureId != null) {
                refreshed.setEcritureEmissionId(ecritureId);
                factureRepository.save(refreshed);
            }
        }

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
     * <p>Cas AVOIR (TypeFacture.AVOIR) : ecriture inverse traitee dans le
     * Bloc B2 de la compta native (creation directe de l'OperationCompte
     * CREDIT lors de l'emission de l'avoir).</p>
     */
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
        // B4 : taux TVA respecte l'override DTO (cas special, y compris 0
        // explicite) ; sinon resolution via TauxTvaConfigService selon le
        // type de ligne. Tous les tests existants passent BigDecimal.ZERO
        // explicite -> compat 100%.
        BigDecimal tauxResolu = (dto.tauxTva() != null)
                ? dto.tauxTva()
                : tauxTvaConfigService.getTaux(tvaTypeForLigne(dto.typeLigne()));
        ligne.setTauxTva(tauxResolu);
        ligne.setDatePrestation(dto.datePrestation());
        ligneRepository.save(ligne);
    }

    /**
     * Recalcule les montants HT/TVA/TTC d'une facture a partir de ses lignes.
     * Source unique de verite : la somme des montants des lignes (la ligne
     * est elle-meme recalculee via @PrePersist/@PreUpdate).
     *
     * <p>Arrondi HALF_UP scale=2.</p>
     *
     * <p>Tour 45 : visibilite passe a {@code public} pour implementer
     * {@link FactureRecalcInternalService} (exposition aux services
     * cross-module sans casser l'API REST).</p>
     */
    @Override
    public void recalcMontantsFacture(Long factureId) {
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
        FactureDto withLignes = mapper.withLignes(base, lignes);
        // Résolution des noms FK pour affichage front (anti-N+1 unitaire).
        String nomClient = (facture.getClientId() != null)
                ? clientRepository.findById(facture.getClientId())
                        .map(c -> c.getNomComplet()).orElse(null)
                : null;
        String nomSociete = (facture.getSocieteId() != null)
                ? societeRepository.findById(facture.getSocieteId())
                        .map(s -> s.getSocieteNom()).orElse(null)
                : null;
        String nomFournisseur = (facture.getFournisseurId() != null)
                ? fournisseurRepository.findById(facture.getFournisseurId())
                        .map(f -> f.getNomFournisseur()).orElse(null)
                : null;
        String numeroRes = (facture.getReservationId() != null)
                ? reservationRepository.findById(facture.getReservationId())
                        .map(Reservation::getNumeroReservation).orElse(null)
                : null;
        return withLignes.withResolvedNames(nomClient, nomSociete, nomFournisseur, numeroRes);
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
    public FactureDto transfererLignes(TransfertLignesRequest request) {
        if (request == null) {
            throw new BusinessException("error.facture.transfert.requestRequired");
        }
        List<Long> lignesIds = request.lignesIds();
        Long factureCibleId = request.factureCibleId();
        if (lignesIds == null || lignesIds.isEmpty()) {
            throw new BusinessException("error.facture.transfert.lignesRequired");
        }
        if (factureCibleId == null) {
            throw new BusinessException("error.facture.transfert.factureCibleRequired");
        }

        // 1) Verifier la facture cible (tenant filter auto)
        Facture cible = factureRepository.findById(factureCibleId)
                .orElseThrow(() -> new ResourceNotFoundException("error.facture.notFound"));
        if (cible.getStatut() == StatutFacture.PAYEE || cible.getStatut() == StatutFacture.ANNULEE) {
            throw new BusinessException("error.facture.transfert.factureCibleTerminated");
        }

        // 2) Recuperer toutes les lignes et identifier les factures sources concernees.
        // Verifier qu'aucune ligne n'a deja un paiement affecte.
        Set<Long> sourcesIds = new HashSet<>();
        List<LigneFacture> lignesAdeplacer = new ArrayList<>();
        for (Long ligneId : lignesIds) {
            LigneFacture ligne = ligneRepository.findById(ligneId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.ligneFacture.notFound"));
            // Refus si la ligne a deja une affectation paiement.
            // Tour 45 : on cherche par ligneFactureId (nouveau champ).
            List<AffectationPaiement> affsByLigne = affectationRepository
                    .findByFactureIdOrderByDateAffectationAsc(ligne.getFactureId())
                    .stream()
                    .filter(a -> ligneId.equals(a.getLigneFactureId()))
                    .collect(Collectors.toList());
            if (!affsByLigne.isEmpty()) {
                throw new BusinessException("error.facture.transfert.lignePayee");
            }
            sourcesIds.add(ligne.getFactureId());
            lignesAdeplacer.add(ligne);
        }

        // 3) Verifier le statut de toutes les factures sources distinctes.
        for (Long sourceId : sourcesIds) {
            Facture source = factureRepository.findById(sourceId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.facture.notFound"));
            if (source.getStatut() == StatutFacture.PAYEE || source.getStatut() == StatutFacture.ANNULEE) {
                throw new BusinessException("error.facture.transfert.factureSourceTerminated");
            }
        }

        // 4) Effectuer le transfert : reaffecter ligne.factureId vers la cible.
        for (LigneFacture ligne : lignesAdeplacer) {
            ligne.setFactureId(factureCibleId);
            ligneRepository.save(ligne);
        }

        // 5) Recalculer les factures sources et la cible.
        for (Long sourceId : sourcesIds) {
            recalcMontantsFacture(sourceId);
        }
        recalcMontantsFacture(factureCibleId);

        logger.info("Transfert lignes Tour 45 : {} lignes deplacees vers facture cible {}",
                lignesAdeplacer.size(), factureCibleId);

        Facture refreshed = factureRepository.findById(factureCibleId)
                .orElseThrow(() -> new BusinessException("error.facture.notFound"));
        return toDtoWithLignes(refreshed);
    }

    @Override
    public List<LigneFactureRecapDto> findLignesRecapByReservation(Long reservationId) {
        // Verifie l'appartenance tenant de la reservation (Hibernate filtre @TenantId).
        reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));

        // Cache local des numeros de facture pour eviter N+1 lookups quand
        // plusieurs lignes appartiennent a la meme facture.
        java.util.Map<Long, String> numeroByFactureId = new java.util.HashMap<>();
        List<LigneFacture> lignes = ligneRepository.findByReservationId(reservationId);
        List<LigneFactureRecapDto> result = new ArrayList<>(lignes.size());
        for (LigneFacture l : lignes) {
            String numero = numeroByFactureId.computeIfAbsent(l.getFactureId(), fid ->
                    factureRepository.findById(fid).map(Facture::getNumeroFacture).orElse(""));

            String description = (l.getLibelle() != null && !l.getLibelle().isBlank())
                    ? l.getLibelle()
                    : ("Ligne #" + l.getLigneFactureId());

            // dateLigne : NUITEE -> nuitee.dateNuit ; sinon datePrestation (peut etre null).
            LocalDate dateLigne = l.getDatePrestation();
            if (l.getTypeLigne() == TypeLigneFacture.NUITEE && l.getNuiteeId() != null && dateLigne == null) {
                dateLigne = nuiteeRepository.findById(l.getNuiteeId())
                        .map(Nuitee::getDateNuit)
                        .orElse(null);
            }

            BigDecimal montantTtc = l.getMontantTtc() != null ? l.getMontantTtc() : BigDecimal.ZERO;
            BigDecimal montantPaye = affectationRepository
                    .sumMontantByLigneFactureId(l.getLigneFactureId());
            if (montantPaye == null) {
                montantPaye = BigDecimal.ZERO;
            }
            BigDecimal reste = montantTtc.subtract(montantPaye).max(BigDecimal.ZERO);

            result.add(new LigneFactureRecapDto(
                    l.getLigneFactureId(),
                    l.getFactureId(),
                    numero,
                    description,
                    l.getTypeLigne(),
                    dateLigne,
                    montantTtc.setScale(2, RoundingMode.HALF_UP),
                    montantPaye.setScale(2, RoundingMode.HALF_UP),
                    reste.setScale(2, RoundingMode.HALF_UP)));
        }
        return result;
    }

    /**
     * Tour 51bis - Bridge ServiceHotelier -&gt; LigneFacture.
     *
     * <p>Cf. {@link FactureService#addLigneService(LigneServiceCreateRequest)}
     * pour le contrat metier.</p>
     */
    @Override
    @Transactional
    public LigneFactureDto addLigneService(LigneServiceCreateRequest request) {
        if (request == null) {
            throw new BusinessException("error.ligneService.requestRequired");
        }

        // Garde anti-modification dans exercice clos (B1).
        exerciceService.assertOuvert(LocalDate.now());

        // 1) Resolution du ServiceHotelier (filtre @TenantId Hibernate auto)
        ServiceHotelier service = serviceHotelierRepository.findById(request.serviceId())
                .orElseThrow(() -> new ResourceNotFoundException("error.serviceHotelier.notFound"));
        if (!Boolean.TRUE.equals(service.getActif())) {
            throw new BusinessException("error.ligneService.serviceInactif");
        }

        // 2) Resolution de la facture cible
        Facture facture = resolveTargetFacture(request);

        // 3) Refus si facture terminale (PAYEE ou ANNULEE)
        if (facture.getStatut() == StatutFacture.PAYEE
                || facture.getStatut() == StatutFacture.ANNULEE) {
            throw new BusinessException("error.facture.statut.cloturee");
        }

        // 4) Construction de la ligne SERVICE
        LigneFacture ligne = new LigneFacture();
        ligne.setFactureId(facture.getFactureId());
        ligne.setTypeLigne(TypeLigneFacture.SERVICE);
        ligne.setServiceId(service.getServiceId());
        String libelle = (request.description() != null && !request.description().isBlank())
                ? request.description()
                : service.getNom();
        ligne.setLibelle(libelle);
        ligne.setQuantite(request.quantite());
        BigDecimal prixUnitaire = (request.prixUnitaireOverride() != null)
                ? request.prixUnitaireOverride()
                : service.getPrixUnitaire();
        ligne.setPrixUnitaire(prixUnitaire != null ? prixUnitaire : BigDecimal.ZERO);
        // B4 : override respecte ; sinon AUTRE_SERVICE_HOTELIER (defaut 16%).
        BigDecimal tauxService = (request.tauxTva() != null)
                ? request.tauxTva()
                : tauxTvaConfigService.getTaux(TypeServiceTva.AUTRE_SERVICE_HOTELIER);
        ligne.setTauxTva(tauxService);
        ligne.setDatePrestation(LocalDate.now());

        // Capture l'ancien montant pour calculer le delta DEBIT si facture deja emise
        BigDecimal montantAvant = facture.getMontantTtc() != null
                ? facture.getMontantTtc() : BigDecimal.ZERO;

        LigneFacture saved = ligneRepository.save(ligne);

        // 5) Recalcul des montants de la facture parente
        recalcMontantsFacture(facture.getFactureId());

        // 6) Si la facture est deja "comptablement engagee" (EMISE ou
        //    PARTIELLEMENT_PAYEE) avec un client rattache, on enregistre un
        //    DEBIT complementaire equivalent au delta (la nouvelle ligne ajoute
        //    de l'engagement). Pour BROUILLON, pas de DEBIT - il sera passe a
        //    l'emission via recordDebitOnAccountIfApplicable.
        Facture refreshed = factureRepository.findById(facture.getFactureId())
                .orElseThrow(() -> new BusinessException("error.facture.notFound"));
        if ((refreshed.getStatut() == StatutFacture.EMISE
                || refreshed.getStatut() == StatutFacture.PARTIELLEMENT_PAYEE)
                && refreshed.getClientId() != null
                && refreshed.getTypeFacture() == TypeFacture.FACTURE) {
            BigDecimal montantApres = refreshed.getMontantTtc() != null
                    ? refreshed.getMontantTtc() : BigDecimal.ZERO;
            BigDecimal delta = montantApres.subtract(montantAvant);
            if (delta.signum() > 0) {
                var compte = compteService.findOrCreateForClient(refreshed.getClientId());
                operationCompteService.recordDebit(
                        compte.getCompteId(),
                        delta,
                        refreshed.getFactureId(),
                        "Service " + service.getCode() + " - Facture " + refreshed.getNumeroFacture());
            }
        }

        logger.info(
                "LigneFacture SERVICE creee : ligneId={}, factureId={}, serviceId={}, montantTtc={}",
                saved.getLigneFactureId(), facture.getFactureId(),
                service.getServiceId(), saved.getMontantTtc());

        // Recharge pour avoir montants HT/TVA/TTC recalcules par @PrePersist
        LigneFacture freshLigne = ligneRepository.findById(saved.getLigneFactureId())
                .orElseThrow(() -> new BusinessException("error.ligneFacture.notFound"));
        return mapper.toLigneDto(freshLigne);
    }

    /**
     * Resolution de la facture cible pour {@link #addLigneService}.
     *
     * <p>Priorite a {@code factureId} ; sinon recherche par
     * {@code reservationId} (selectionne la facture non terminale la plus
     * recente). Refuse si aucun des deux n'est fourni ou si la recherche
     * par reservation ne donne aucun resultat exploitable.</p>
     */
    private Facture resolveTargetFacture(LigneServiceCreateRequest request) {
        if (request.factureId() != null) {
            return factureRepository.findById(request.factureId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.facture.notFound"));
        }
        if (request.reservationId() != null) {
            // Verifie l'appartenance tenant de la reservation (Hibernate @TenantId)
            reservationRepository.findById(request.reservationId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));

            List<Facture> factures = factureRepository.findByReservationId(request.reservationId());
            // Selectionne la plus recente parmi celles non terminales (PAYEE / ANNULEE
            // exclues) ; tri par factureId DESC pour avoir la derniere creee.
            Facture target = null;
            Long maxId = -1L;
            for (Facture f : factures) {
                if (f.getStatut() == StatutFacture.PAYEE
                        || f.getStatut() == StatutFacture.ANNULEE) {
                    continue;
                }
                if (f.getFactureId() != null && f.getFactureId() > maxId) {
                    maxId = f.getFactureId();
                    target = f;
                }
            }
            if (target == null) {
                // Aucune facture exploitable : si la reservation a des factures
                // toutes terminees, c'est une erreur metier explicite ;
                // sinon "aucune facture trouvee" -> meme erreur cible.
                throw new BusinessException("error.ligneService.reservationSansFactureOuverte");
            }
            return target;
        }
        throw new BusinessException("error.ligneService.targetRequired");
    }
}
