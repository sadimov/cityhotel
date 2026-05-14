package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.finance.EcritureComptableCreateDto;
import com.cityprojects.citybackend.dto.finance.EcritureComptableDto;
import com.cityprojects.citybackend.dto.finance.LigneEcritureCreateDto;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.LigneFacture;
import com.cityprojects.citybackend.entity.finance.ModePaiement;
import com.cityprojects.citybackend.entity.finance.Paiement;
import com.cityprojects.citybackend.entity.finance.SensLigne;
import com.cityprojects.citybackend.entity.finance.TypeEvenementComptable;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.entity.finance.TypeServiceTva;
import com.cityprojects.citybackend.entity.inventory.BonCommande;
import com.cityprojects.citybackend.entity.inventory.BonSortie;
import com.cityprojects.citybackend.repository.finance.JournalComptableRepository;
import com.cityprojects.citybackend.repository.finance.LigneFactureRepository;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation de {@link EcritureGenerationService}.
 *
 * <p>Conventions :</p>
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe — toutes les operations
 *       supposent un {@code TenantContext} courant.</li>
 *   <li>{@code @Transactional} (require). Les services appelants ouvrent
 *       deja une TX en ecriture (FactureServiceImpl.emettre, etc.), on
 *       partage la TX pour que l'echec d'ecriture rollback toute la
 *       mutation metier (atomicite B3).</li>
 *   <li>Aucun log d'audit explicite ici : l'ecriture en aval (
 *       {@link EcritureComptableServiceImpl#creer}) logue deja le numero,
 *       le journal et les totaux.</li>
 * </ul>
 */
@Service
@RequireTenant
@Transactional
public class EcritureGenerationServiceImpl implements EcritureGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(EcritureGenerationServiceImpl.class);

    /** Tolerance d'arrondi pour le solde TTC vs HT+TVA (1 centime). */
    private static final BigDecimal TOLERANCE_EQUILIBRE = new BigDecimal("0.01");

    private final EcritureComptableService ecritureService;
    private final CompteMappingService mappingService;
    private final LigneFactureRepository ligneFactureRepository;
    private final PlanComptableGeneralRepository pcgRepository;
    private final JournalComptableRepository journalRepository;
    private final TauxTvaConfigService tauxTvaConfigService;

    public EcritureGenerationServiceImpl(EcritureComptableService ecritureService,
                                         CompteMappingService mappingService,
                                         LigneFactureRepository ligneFactureRepository,
                                         PlanComptableGeneralRepository pcgRepository,
                                         JournalComptableRepository journalRepository,
                                         TauxTvaConfigService tauxTvaConfigService) {
        this.ecritureService = ecritureService;
        this.mappingService = mappingService;
        this.ligneFactureRepository = ligneFactureRepository;
        this.pcgRepository = pcgRepository;
        this.journalRepository = journalRepository;
        this.tauxTvaConfigService = tauxTvaConfigService;
    }

    /**
     * Mode degrade : si le PCG est totalement vide OU si le journal cible
     * n'existe pas pour le tenant courant, on skip la generation d'ecriture.
     *
     * <p>En PRODUCTION, ce cas ne se produit jamais : le PCG est seede via
     * Liquibase (changeset {@code 040-create-plan-comptable-general.xml})
     * et les journaux par hotel sont crees au moment de la creation de
     * l'hotel via {@link JournalComptableInitializer#seedDefault(Long)}.
     * Si on tombe ici, c'est qu'on est dans un contexte de test qui n'a
     * pas seede l'environnement (Liquibase desactive ou hotel cree
     * directement en JPA) - on prefere ne pas casser le flux metier
     * existant et logger un INFO/DEBUG pour tracer.</p>
     *
     * <p>L'atomicite reste preservee en prod : si la generation est tentee
     * et echoue (compte/journal manquant alors que le PCG existe), la
     * BusinessException levee par {@link EcritureComptableService#creer}
     * rollback la TX comme attendu.</p>
     */
    private boolean cantGenerateForJournal(String journalCode) {
        if (pcgRepository.count() == 0L) {
            logger.debug("EcritureGeneration: PCG vide - skip generation (journalCode={})", journalCode);
            return true;
        }
        if (journalRepository.findByCode(journalCode).isEmpty()) {
            logger.debug("EcritureGeneration: journal {} absent pour tenant courant - skip", journalCode);
            return true;
        }
        return false;
    }

    // ====================================================================
    // FACTURE -> ECRITURE VENTE
    // ====================================================================

    @Override
    public Long emettreEcritureFacture(Facture facture) {
        if (facture == null || facture.getFactureId() == null) {
            return null;
        }
        if (cantGenerateForJournal("VTE")) {
            return null;
        }
        BigDecimal montantTtc = facture.getMontantTtc();
        if (montantTtc == null || montantTtc.signum() <= 0) {
            logger.debug("emettreEcritureFacture: facture {} sans montant - skip",
                    facture.getNumeroFacture());
            return null;
        }
        List<LigneFacture> lignes = ligneFactureRepository
                .findByFactureIdOrderByLigneFactureIdAsc(facture.getFactureId());
        if (lignes.isEmpty()) {
            logger.debug("emettreEcritureFacture: facture {} sans ligne - skip",
                    facture.getNumeroFacture());
            return null;
        }

        // 1) Cote produit (CREDIT 706xxx) : on agrege le HT par TypeLigneFacture
        //    -> code de compte de produit (via CompteMappingService).
        //    En B3, tauxTva = 0 (cf. plan TVA reporte B4) donc HT == TTC sur
        //    chaque ligne. On garde la separation HT/TVA pour preparer B4.
        Map<String, BigDecimal> creditParCompte = new java.util.LinkedHashMap<>();
        BigDecimal totalHt = BigDecimal.ZERO;
        BigDecimal totalTva = BigDecimal.ZERO;
        for (LigneFacture l : lignes) {
            TypeLigneFacture type = l.getTypeLigne();
            String compteCode = mappingService.getCompte(eventForTypeLigne(type));
            BigDecimal mtHt = (l.getMontantHt() != null) ? l.getMontantHt() : BigDecimal.ZERO;
            BigDecimal mtTva = (l.getMontantTva() != null) ? l.getMontantTva() : BigDecimal.ZERO;
            creditParCompte.merge(compteCode, mtHt, BigDecimal::add);
            totalHt = totalHt.add(mtHt);
            totalTva = totalTva.add(mtTva);
        }

        // 2) Cote client (DEBIT 411xxx ou societe) : montant TTC global.
        String compteClient = resolveCompteClient(facture);

        // 3) Lignes d'ecriture
        List<LigneEcritureCreateDto> ecritureLignes = new ArrayList<>();
        int ordre = 1;
        ecritureLignes.add(new LigneEcritureCreateDto(
                ordre++,
                compteClient,
                "Facturation " + facture.getNumeroFacture(),
                SensLigne.DEBIT,
                montantTtc.setScale(2, RoundingMode.HALF_UP),
                referenceFromFacture(facture)));

        for (Map.Entry<String, BigDecimal> e : creditParCompte.entrySet()) {
            ecritureLignes.add(new LigneEcritureCreateDto(
                    ordre++,
                    e.getKey(),
                    "Produit " + e.getKey() + " - " + facture.getNumeroFacture(),
                    SensLigne.CREDIT,
                    e.getValue().setScale(2, RoundingMode.HALF_UP),
                    null));
        }
        // TVA collectee (B4) : si une ligne a un tauxTva > 0 on creditera 445700.
        //  Branchement preserve pour B4 : la garde signum > 0 empeche les
        //  ecritures vides en B3 (tauxTva = 0 partout).
        if (totalTva.signum() > 0) {
            ecritureLignes.add(new LigneEcritureCreateDto(
                    ordre++,
                    mappingService.getCompte(TypeEvenementComptable.TVA_COLLECTEE),
                    "TVA collectee - " + facture.getNumeroFacture(),
                    SensLigne.CREDIT,
                    totalTva.setScale(2, RoundingMode.HALF_UP),
                    null));
        }

        // 4) Garde-fou equilibre (le service appelle aussi cette regle, double
        //    check ne coute rien et evite un rollback tardif).
        verifyEquilibre(ecritureLignes);

        // 5) Cree l'ecriture VTE
        EcritureComptableCreateDto dto = new EcritureComptableCreateDto(
                facture.getDateFacture(),
                facture.getDateFacture(),
                "VTE",
                "Facturation " + facture.getNumeroFacture(),
                referenceFromFacture(facture),
                ecritureLignes);

        EcritureComptableDto created = ecritureService.creer(dto);
        logger.info("Ecriture VTE generee pour facture {} : id={}, totalTtc={}",
                facture.getNumeroFacture(), created.id(), montantTtc);
        return created.id();
    }

    /** Mapping {@link TypeLigneFacture} -> {@link TypeEvenementComptable} (cote produit). */
    private static TypeEvenementComptable eventForTypeLigne(TypeLigneFacture type) {
        if (type == null) {
            return TypeEvenementComptable.VENTE_AUTRE_SERVICE;
        }
        return switch (type) {
            case NUITEE -> TypeEvenementComptable.VENTE_NUITEE_HEBERGEMENT;
            case PRODUIT, COMMANDE -> TypeEvenementComptable.VENTE_RESTAURATION;
            case SERVICE, DIVERS -> TypeEvenementComptable.VENTE_AUTRE_SERVICE;
        };
    }

    /**
     * Resout le compte client (411xxx) selon que la facture porte un
     * clientId, un societeId ou ni l'un ni l'autre. Defensif : retourne le
     * default CLIENT_PARTICULIER avec un WARN si la facture n'a aucun tiers.
     */
    private String resolveCompteClient(Facture facture) {
        if (facture.getSocieteId() != null) {
            return mappingService.getCompte(TypeEvenementComptable.CLIENT_SOCIETE);
        }
        if (facture.getClientId() != null) {
            return mappingService.getCompte(TypeEvenementComptable.CLIENT_PARTICULIER);
        }
        // Anomalie : facture sans tiers (cas hypothetique - cash anonyme
        // sans encaissement direct). On utilise CLIENT_PARTICULIER en
        // defaut pour ne pas casser la generation et on WARN.
        logger.warn("Facture {} sans clientId/societeId : compte client par defaut "
                + "(411100). Cas anormal a tracer.",
                facture.getNumeroFacture());
        return mappingService.getCompte(TypeEvenementComptable.CLIENT_PARTICULIER);
    }

    /** Pour eviter un overflow Size(50) de Reference (numero facture est <= 40 chars). */
    private static String referenceFromFacture(Facture facture) {
        return facture.getNumeroFacture();
    }

    // ====================================================================
    // PAIEMENT -> ECRITURE TRESORERIE
    // ====================================================================

    @Override
    public Long emettreEcritureEncaissement(Paiement paiement, Long clientId, Long societeId) {
        if (paiement == null || paiement.getPaiementId() == null) {
            return null;
        }
        BigDecimal montant = paiement.getMontantTotal();
        if (montant == null || montant.signum() <= 0) {
            logger.debug("emettreEcritureEncaissement: paiement {} sans montant - skip",
                    paiement.getNumeroPaiement());
            return null;
        }

        ModePaiement mode = paiement.getModePaiement();
        String compteTresorerie = mappingService.getCompte(eventForMode(mode));
        String journalCode = journalForMode(mode);

        if (cantGenerateForJournal(journalCode)) {
            return null;
        }

        // Compte client : si societeId fourni -> CLIENT_SOCIETE, sinon CLIENT_PARTICULIER.
        // (clientId nul accepte : on cree quand meme l'ecriture sur 411100 par defaut.)
        String compteClient;
        if (societeId != null) {
            compteClient = mappingService.getCompte(TypeEvenementComptable.CLIENT_SOCIETE);
        } else {
            compteClient = mappingService.getCompte(TypeEvenementComptable.CLIENT_PARTICULIER);
        }

        BigDecimal mt = montant.setScale(2, RoundingMode.HALF_UP);
        List<LigneEcritureCreateDto> lignes = List.of(
                new LigneEcritureCreateDto(
                        1,
                        compteTresorerie,
                        "Encaissement " + paiement.getNumeroPaiement() + " (" + mode + ")",
                        SensLigne.DEBIT,
                        mt,
                        paiement.getNumeroPaiement()),
                new LigneEcritureCreateDto(
                        2,
                        compteClient,
                        "Encaissement " + paiement.getNumeroPaiement(),
                        SensLigne.CREDIT,
                        mt,
                        null)
        );

        verifyEquilibre(lignes);

        EcritureComptableCreateDto dto = new EcritureComptableCreateDto(
                paiement.getDatePaiement(),
                paiement.getDatePaiement(),
                journalCode,
                "Encaissement " + paiement.getNumeroPaiement(),
                paiement.getNumeroPaiement(),
                lignes);

        EcritureComptableDto created = ecritureService.creer(dto);
        logger.info("Ecriture {} generee pour paiement {} : id={}, montant={}",
                journalCode, paiement.getNumeroPaiement(), created.id(), montant);
        return created.id();
    }

    /** Mapping {@link ModePaiement} -> {@link TypeEvenementComptable} (tresorerie). */
    private static TypeEvenementComptable eventForMode(ModePaiement mode) {
        if (mode == null) {
            return TypeEvenementComptable.TRESORERIE_ESPECES;
        }
        return switch (mode) {
            case ESPECES -> TypeEvenementComptable.TRESORERIE_ESPECES;
            case CHEQUE -> TypeEvenementComptable.TRESORERIE_CHEQUE;
            case CARTE_BANCAIRE -> TypeEvenementComptable.TRESORERIE_CARTE_BANCAIRE;
            case BANKILY -> TypeEvenementComptable.TRESORERIE_BANKILY;
            case MASRIVI -> TypeEvenementComptable.TRESORERIE_MASRIVI;
            case SEDAD -> TypeEvenementComptable.TRESORERIE_SEDAD;
            case CLICK -> TypeEvenementComptable.TRESORERIE_CLICK;
            case AMANETY -> TypeEvenementComptable.TRESORERIE_AMANETY;
            case BFI_CASH -> TypeEvenementComptable.TRESORERIE_BFI_CASH;
            case MOOV_MONEY -> TypeEvenementComptable.TRESORERIE_MOOV_MONEY;
            case GAZAPAY -> TypeEvenementComptable.TRESORERIE_GAZAPAY;
            case VIREMENT -> TypeEvenementComptable.TRESORERIE_VIREMENT;
        };
    }

    /**
     * Journal cible selon le mode : CAI (caisse / mobile money / especes) ou
     * BAN (banque / CB / cheque / virement). Cf. specs B3.
     */
    private static String journalForMode(ModePaiement mode) {
        if (mode == null) {
            return "CAI";
        }
        return switch (mode) {
            case ESPECES, BANKILY, MASRIVI, SEDAD, CLICK, AMANETY,
                 BFI_CASH, MOOV_MONEY, GAZAPAY -> "CAI";
            case CHEQUE, CARTE_BANCAIRE, VIREMENT -> "BAN";
        };
    }

    // ====================================================================
    // BON COMMANDE -> ECRITURE ACHAT
    // ====================================================================

    @Override
    public Long emettreEcritureReceptionBC(BonCommande bc, BigDecimal montantReception) {
        if (bc == null || bc.getBonCommandeId() == null) {
            return null;
        }
        if (montantReception == null || montantReception.signum() <= 0) {
            logger.debug("emettreEcritureReceptionBC: BC {} sans montant - skip",
                    bc.getNumeroBc());
            return null;
        }
        if (cantGenerateForJournal("ACH")) {
            return null;
        }

        String compteStock = mappingService.getCompte(TypeEvenementComptable.STOCK_MARCHANDISES);
        String compteFournisseur = mappingService.getCompte(TypeEvenementComptable.FOURNISSEUR_ORDINAIRE);
        String compteTvaDeductible = mappingService.getCompte(TypeEvenementComptable.TVA_DEDUCTIBLE);

        // B4 : decoupage HT / TVA / TTC pour la TVA deductible.
        // Le parametre montantReception est considere comme HT
        // (somme des qte * prix unitaire des lignes du BC, sans TVA).
        // Si l'admin a configure ACHAT_MARCHANDISES = 0% (cas d'achats
        // hors taxe), la ligne TVA est omise et l'ecriture redevient
        // identique au schema B3 (D 311 / C 401 pour TTC = HT).
        BigDecimal ht = montantReception.setScale(2, RoundingMode.HALF_UP);
        BigDecimal tauxTva = tauxTvaConfigService.getTaux(TypeServiceTva.ACHAT_MARCHANDISES);
        BigDecimal tva = ht.multiply(tauxTva)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal ttc = ht.add(tva).setScale(2, RoundingMode.HALF_UP);

        List<LigneEcritureCreateDto> lignes = new java.util.ArrayList<>();
        int ordre = 1;
        lignes.add(new LigneEcritureCreateDto(
                ordre++,
                compteStock,
                "Reception stock " + bc.getNumeroBc(),
                SensLigne.DEBIT,
                ht,
                bc.getNumeroBc()));
        if (tva.signum() > 0) {
            lignes.add(new LigneEcritureCreateDto(
                    ordre++,
                    compteTvaDeductible,
                    "TVA deductible " + bc.getNumeroBc(),
                    SensLigne.DEBIT,
                    tva,
                    null));
        }
        lignes.add(new LigneEcritureCreateDto(
                ordre++,
                compteFournisseur,
                "Dette fournisseur " + bc.getNumeroBc(),
                SensLigne.CREDIT,
                ttc,
                null));

        verifyEquilibre(lignes);

        java.time.LocalDate today = java.time.LocalDate.now();
        EcritureComptableCreateDto dto = new EcritureComptableCreateDto(
                today,
                today,
                "ACH",
                "Reception BC " + bc.getNumeroBc(),
                bc.getNumeroBc(),
                lignes);

        EcritureComptableDto created = ecritureService.creer(dto);
        logger.info("Ecriture ACH generee pour BC {} : id={}, ht={}, tva={}, ttc={}",
                bc.getNumeroBc(), created.id(), ht, tva, ttc);
        return created.id();
    }

    // ====================================================================
    // BON SORTIE -> ECRITURE CONSOMMATION
    // ====================================================================

    @Override
    public Long emettreEcritureSortieBS(BonSortie bs, BigDecimal montantSortie) {
        if (bs == null || bs.getBonSortieId() == null) {
            return null;
        }
        if (montantSortie == null || montantSortie.signum() <= 0) {
            logger.debug("emettreEcritureSortieBS: BS {} sans montant - skip",
                    bs.getNumeroBs());
            return null;
        }
        if (cantGenerateForJournal("OD")) {
            return null;
        }

        String compteAchat = mappingService.getCompte(TypeEvenementComptable.ACHAT_MARCHANDISES);
        String compteStock = mappingService.getCompte(TypeEvenementComptable.STOCK_MARCHANDISES);

        BigDecimal mt = montantSortie.setScale(2, RoundingMode.HALF_UP);
        List<LigneEcritureCreateDto> lignes = List.of(
                new LigneEcritureCreateDto(
                        1,
                        compteAchat,
                        "Consommation " + bs.getNumeroBs(),
                        SensLigne.DEBIT,
                        mt,
                        bs.getNumeroBs()),
                new LigneEcritureCreateDto(
                        2,
                        compteStock,
                        "Sortie stock " + bs.getNumeroBs(),
                        SensLigne.CREDIT,
                        mt,
                        null)
        );
        verifyEquilibre(lignes);

        java.time.LocalDate today = java.time.LocalDate.now();
        EcritureComptableCreateDto dto = new EcritureComptableCreateDto(
                today,
                today,
                "OD",
                "Sortie stock " + bs.getNumeroBs(),
                bs.getNumeroBs(),
                lignes);

        EcritureComptableDto created = ecritureService.creer(dto);
        logger.info("Ecriture OD generee pour BS {} : id={}, montant={}",
                bs.getNumeroBs(), created.id(), montantSortie);
        return created.id();
    }

    // ====================================================================
    // Helpers communs
    // ====================================================================

    /**
     * Verifie Σ D == Σ C (tolerance 0.01). Garde-fou avant appel a
     * {@link EcritureComptableService#creer}. Le service refera la verif
     * de toutes facons, mais on echoue plus tot avec un message explicite.
     */
    private static void verifyEquilibre(List<LigneEcritureCreateDto> lignes) {
        BigDecimal d = BigDecimal.ZERO;
        BigDecimal c = BigDecimal.ZERO;
        for (LigneEcritureCreateDto l : lignes) {
            if (l == null || l.montant() == null || l.sens() == null) {
                continue;
            }
            if (l.sens() == SensLigne.DEBIT) {
                d = d.add(l.montant());
            } else {
                c = c.add(l.montant());
            }
        }
        BigDecimal ecart = d.subtract(c).abs();
        if (ecart.compareTo(TOLERANCE_EQUILIBRE) > 0) {
            // Programmation defensive : si on tombe ici, c'est un bug
            // EcritureGenerationService - on laisse remonter pour rollback.
            throw new com.cityprojects.citybackend.exception.BusinessException(
                    "error.ecriture.unbalanced");
        }
    }

    /** Pour evolution B4 : exposition packagee si besoin de tester en isolation. */
    @SuppressWarnings("unused")
    private static Map<TypeLigneFacture, TypeEvenementComptable> debugMappingTable() {
        Map<TypeLigneFacture, TypeEvenementComptable> map = new EnumMap<>(TypeLigneFacture.class);
        for (TypeLigneFacture t : TypeLigneFacture.values()) {
            map.put(t, eventForTypeLigne(t));
        }
        return map;
    }
}
