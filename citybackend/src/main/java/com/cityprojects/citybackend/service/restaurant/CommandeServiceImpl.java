package com.cityprojects.citybackend.service.restaurant;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.finance.PaiementCreateDto;
import com.cityprojects.citybackend.dto.finance.PaiementDto;
import com.cityprojects.citybackend.dto.inventory.BonSortieCreateDto;
import com.cityprojects.citybackend.dto.inventory.BonSortieDto;
import com.cityprojects.citybackend.dto.inventory.LigneBonSortieCreateDto;
import com.cityprojects.citybackend.dto.restaurant.CommandeCreateDto;
import com.cityprojects.citybackend.dto.restaurant.CommandeDto;
import com.cityprojects.citybackend.dto.restaurant.EncaissementCommandeDto;
import com.cityprojects.citybackend.dto.restaurant.LigneCommandeCreateDto;
import com.cityprojects.citybackend.dto.restaurant.LigneCommandeDto;
import com.cityprojects.citybackend.entity.hebergement.Reservation;
import com.cityprojects.citybackend.entity.hebergement.StatutReservation;
import com.cityprojects.citybackend.entity.restaurant.ArticleMenu;
import com.cityprojects.citybackend.entity.restaurant.Commande;
import com.cityprojects.citybackend.entity.restaurant.LigneCommande;
import com.cityprojects.citybackend.entity.restaurant.ModeReglementCommande;
import com.cityprojects.citybackend.entity.restaurant.RecetteArticle;
import com.cityprojects.citybackend.entity.restaurant.StatutArticle;
import com.cityprojects.citybackend.entity.restaurant.StatutCommande;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.restaurant.CommandeMapper;
import com.cityprojects.citybackend.mapper.restaurant.LigneCommandeMapper;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.repository.restaurant.ArticleMenuRepository;
import com.cityprojects.citybackend.repository.restaurant.CommandeRepository;
import com.cityprojects.citybackend.repository.restaurant.LigneCommandeRepository;
import com.cityprojects.citybackend.repository.restaurant.RecetteArticleRepository;
import com.cityprojects.citybackend.service.finance.FactureService;
import com.cityprojects.citybackend.service.finance.NumerotationService;
import com.cityprojects.citybackend.service.finance.PaiementService;
import com.cityprojects.citybackend.service.finance.TypeNumerotation;
import com.cityprojects.citybackend.service.inventory.BonSortieService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation de {@link CommandeService} (Tour 24).
 *
 * <h3>Conventions</h3>
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe.</li>
 *   <li>{@code @Transactional(readOnly = true)} a la classe, override en ecriture.</li>
 *   <li>Aucun {@code setHotelId} : Hibernate populate via le resolver.</li>
 *   <li>Snapshot {@code libelle}/{@code prixUnitaire} depuis l'article au
 *       moment de la prise de commande (cf. doctrine LigneFacture).</li>
 *   <li>Recalcul {@code montantHt} = {@code montantTtc} = SUM(lignes.montant)
 *       a chaque modification (pas de TVA POS).</li>
 * </ul>
 *
 * <h3>Transitions de statut autorisees</h3>
 * <pre>
 *   BROUILLON       -&gt; VALIDEE / ANNULEE
 *   VALIDEE         -&gt; EN_PREPARATION / ANNULEE
 *   EN_PREPARATION  -&gt; PRETE / ANNULEE
 *   PRETE           -&gt; SERVIE / ANNULEE
 *   SERVIE          -&gt; (terminal)
 *   ANNULEE         -&gt; (terminal)
 * </pre>
 *
 * <h3>Tour 25</h3>
 * <ul>
 *   <li>Transition PRETE -&gt; SERVIE : genere automatiquement un BS inventory
 *       a partir des recettes des articles ({@link RecetteArticle}). Si stock
 *       insuffisant, le BS leve une {@link BusinessException} qui rollback la
 *       transition (la commande reste PRETE et le serveur sait qu'il y a un
 *       probleme stock a regler).</li>
 *   <li>Si aucune recette n'est definie pour les articles de la commande,
 *       skip silencieux (log INFO) - cas d'un article sans recette saisie
 *       par l'admin.</li>
 * </ul>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class CommandeServiceImpl implements CommandeService {

    private static final Logger logger = LoggerFactory.getLogger(CommandeServiceImpl.class);

    /** Transitions valides de statut. */
    private static final Map<StatutCommande, Set<StatutCommande>> TRANSITIONS = Map.of(
            StatutCommande.BROUILLON,      EnumSet.of(StatutCommande.VALIDEE, StatutCommande.ANNULEE),
            StatutCommande.VALIDEE,        EnumSet.of(StatutCommande.EN_PREPARATION, StatutCommande.ANNULEE),
            StatutCommande.EN_PREPARATION, EnumSet.of(StatutCommande.PRETE, StatutCommande.ANNULEE),
            StatutCommande.PRETE,          EnumSet.of(StatutCommande.SERVIE, StatutCommande.ANNULEE),
            StatutCommande.SERVIE,         EnumSet.noneOf(StatutCommande.class),
            StatutCommande.ANNULEE,        EnumSet.noneOf(StatutCommande.class));

    private final CommandeRepository commandeRepository;
    private final LigneCommandeRepository ligneCommandeRepository;
    private final ArticleMenuRepository articleMenuRepository;
    private final RecetteArticleRepository recetteArticleRepository;
    private final ClientRepository clientRepository;
    private final ReservationRepository reservationRepository;
    private final CommandeMapper commandeMapper;
    private final LigneCommandeMapper ligneMapper;
    private final NumerotationService numerotationService;
    private final FactureService factureService;
    private final PaiementService paiementService;
    private final BonSortieService bonSortieService;

    public CommandeServiceImpl(CommandeRepository commandeRepository,
                               LigneCommandeRepository ligneCommandeRepository,
                               ArticleMenuRepository articleMenuRepository,
                               RecetteArticleRepository recetteArticleRepository,
                               ClientRepository clientRepository,
                               ReservationRepository reservationRepository,
                               CommandeMapper commandeMapper,
                               LigneCommandeMapper ligneMapper,
                               NumerotationService numerotationService,
                               FactureService factureService,
                               PaiementService paiementService,
                               BonSortieService bonSortieService) {
        this.commandeRepository = commandeRepository;
        this.ligneCommandeRepository = ligneCommandeRepository;
        this.articleMenuRepository = articleMenuRepository;
        this.recetteArticleRepository = recetteArticleRepository;
        this.clientRepository = clientRepository;
        this.reservationRepository = reservationRepository;
        this.commandeMapper = commandeMapper;
        this.ligneMapper = ligneMapper;
        this.numerotationService = numerotationService;
        this.factureService = factureService;
        this.paiementService = paiementService;
        this.bonSortieService = bonSortieService;
    }

    @Override
    @Transactional
    public CommandeDto create(CommandeCreateDto dto) {
        // Coherence mode reglement / reservationId.
        if (dto.modeReglement() == ModeReglementCommande.REPORTE_CHAMBRE) {
            if (dto.reservationId() == null) {
                throw new BusinessException("error.commande.reservation.required");
            }
            Reservation reservation = reservationRepository.findById(dto.reservationId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));
            if (reservation.getStatut() != StatutReservation.ARRIVEE) {
                throw new BusinessException("error.commande.reservation.statutInvalide");
            }
        } else { // COMPTANT
            if (dto.reservationId() != null) {
                throw new BusinessException("error.commande.reservation.interditComptant");
            }
        }

        // Validation FK clientId (optionnelle - walk-in possible).
        if (dto.clientId() != null) {
            clientRepository.findById(dto.clientId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.client.notFound"));
        }

        Commande commande = new Commande();
        commande.setModeReglement(dto.modeReglement());
        commande.setClientId(dto.clientId());
        commande.setReservationId(dto.reservationId());
        commande.setStatut(StatutCommande.BROUILLON);
        commande.setDevise(dto.devise() != null ? dto.devise() : "MRU");
        commande.setDateCommande(Instant.now());
        commande.setNumeroCommande(numerotationService.next(TypeNumerotation.COMM));
        // Tour 26.1 : numero de table physique (optionnel). Persist tel quel
        // (validation @Size(20) cote DTO).
        commande.setNumeroTable(dto.numeroTable());
        // PAS de setHotelId : Hibernate via @TenantId resolver.

        Commande saved = commandeRepository.save(commande);

        // Cree les lignes (snapshot depuis l'article).
        for (LigneCommandeCreateDto ld : dto.lignes()) {
            creerLigne(saved.getCommandeId(), ld);
        }
        recalcMontants(saved.getCommandeId());

        Commande refreshed = commandeRepository.findById(saved.getCommandeId())
                .orElseThrow(() -> new BusinessException("error.commande.notFound"));

        logger.info("Commande creee : id={}, numero={}, mode={}, lignes={}, total={}",
                refreshed.getCommandeId(), refreshed.getNumeroCommande(),
                refreshed.getModeReglement(), dto.lignes().size(), refreshed.getMontantTtc());

        return toDtoWithLignes(refreshed);
    }

    @Override
    public CommandeDto findById(Long commandeId) {
        Commande commande = commandeRepository.findById(commandeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.commande.notFound"));
        return toDtoWithLignes(commande);
    }

    @Override
    public Page<CommandeDto> findByStatut(StatutCommande statut, Pageable pageable) {
        return commandeRepository.findByStatutOrderByDateCommandeDesc(statut, pageable)
                .map(this::toDtoWithLignes);
    }

    @Override
    @Transactional
    public CommandeDto changeStatut(Long commandeId, StatutCommande nouveauStatut) {
        if (nouveauStatut == null) {
            throw new BusinessException("error.commande.statut.required");
        }
        Commande commande = commandeRepository.findById(commandeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.commande.notFound"));

        Set<StatutCommande> autorisees = TRANSITIONS.getOrDefault(
                commande.getStatut(), EnumSet.noneOf(StatutCommande.class));
        if (!autorisees.contains(nouveauStatut)) {
            throw new BusinessException("error.commande.statut.invalide");
        }

        if (nouveauStatut == StatutCommande.ANNULEE) {
            throw new BusinessException("error.commande.annulation.utiliserAnnuler");
        }

        StatutCommande precedent = commande.getStatut();
        commande.setStatut(nouveauStatut);
        commandeRepository.save(commande);

        // Tour 25 : transition PRETE -> SERVIE genere un BS inventory auto a
        // partir des recettes des articles. Si stock insuffisant, BS leve une
        // BusinessException qui rollback toute la transaction (la commande
        // reste PRETE).
        if (precedent == StatutCommande.PRETE && nouveauStatut == StatutCommande.SERVIE) {
            onTransitionToServie(commande);
        }

        logger.info("Commande id={} : statut {} -> {}", commandeId, precedent, nouveauStatut);
        return toDtoWithLignes(commande);
    }

    /**
     * Transition PRETE -&gt; SERVIE : genere un BS inventory regroupant les
     * sorties de stock pour TOUS les ingredients consommes par les articles
     * de la commande (Tour 25).
     *
     * <p><b>Algo</b> : pour chaque ligne de commande, lookup recette active de
     * l'article -&gt; agrege les quantites par produit dans une Map
     * (LinkedHashMap pour deterministique). Cree 1 BS, valide, livre.</p>
     *
     * <p><b>Quantite</b> : la recette est en {@link BigDecimal} (precision 4
     * decimales) mais le BS attend un {@link Integer} (sortie unitaire stock).
     * On arrondit AU SUPERIEUR (CEILING) pour garantir qu'on retire au moins
     * ce qu'il faut. Ex. 0.450 kg de riz -&gt; 1 unite (kg). C'est volontairement
     * conservateur : ameliorations possibles ulterieures (BS en BigDecimal,
     * unite secondaire, etc.).</p>
     *
     * <p><b>Skip silencieux</b> : si aucune recette n'est definie pour aucun
     * des articles de la commande, on log INFO et on sort. C'est le cas d'un
     * article cuisine sans recette saisie par l'admin.</p>
     *
     * <p><b>Stock insuffisant</b> : {@link BonSortieService#valider(Long)}
     * leve {@link BusinessException} -&gt; rollback de la transaction et de
     * la transition. Documente.</p>
     */
    private void onTransitionToServie(Commande commande) {
        Map<Long, BigDecimal> sortiesParProduit = aggregateProductSorties(commande);

        if (sortiesParProduit.isEmpty()) {
            logger.info("Commande {} servie sans BS auto (aucune recette definie pour les articles)",
                    commande.getNumeroCommande());
            return;
        }

        List<LigneBonSortieCreateDto> lignesBs = convertToBsLines(sortiesParProduit);

        BonSortieCreateDto bsDto = new BonSortieCreateDto(
                "Restaurant - Commande " + commande.getNumeroCommande(),
                "Consommation auto suite a commande POS " + commande.getNumeroCommande(),
                lignesBs);

        BonSortieDto bs = bonSortieService.create(bsDto);
        // valider() leve BusinessException si stock insuffisant -> rollback transition.
        bonSortieService.valider(bs.bonSortieId());
        bonSortieService.livrer(bs.bonSortieId());

        logger.info("Commande {} servie : BS {} cree avec {} lignes",
                commande.getNumeroCommande(), bs.numeroBs(), lignesBs.size());
    }

    /**
     * Agregation : pour chaque ligne de commande, lookup de la recette active
     * de l'article et accumulation des quantites par produit dans une
     * {@link LinkedHashMap} (ordre deterministe pour le BS resultant).
     *
     * <p>Tour 40bis (refactor C3). Lignes sans recette : log WARN puis skip
     * (cf. F6).</p>
     *
     * @return Map produitId -&gt; quantite totale (BigDecimal). Vide si aucune
     *         recette n'est definie pour aucun des articles.
     */
    private Map<Long, BigDecimal> aggregateProductSorties(Commande commande) {
        List<LigneCommande> lignes = ligneCommandeRepository
                .findByCommandeIdOrderByLigneIdAsc(commande.getCommandeId());

        Map<Long, BigDecimal> sortiesParProduit = new LinkedHashMap<>();
        for (LigneCommande lc : lignes) {
            List<RecetteArticle> recettes = recetteArticleRepository
                    .findByArticleIdAndActifTrue(lc.getArticleId());
            if (recettes.isEmpty()) {
                // F6 : trace explicite par ligne sans recette - le serveur sait que
                // ce plat ne consomme pas de stock auto. Le log INFO global cote
                // appelant gere le cas ou TOUTES les lignes sont sans recette
                // (skip silencieux).
                logger.warn("Commande {} - article {} (ligne {}) sans recette : "
                        + "pas de BS auto pour cette ligne",
                        commande.getNumeroCommande(), lc.getArticleId(), lc.getLigneId());
                continue;
            }
            for (RecetteArticle r : recettes) {
                BigDecimal qte = r.getQuantiteParUnite().multiply(lc.getQuantite());
                sortiesParProduit.merge(r.getProduitId(), qte, BigDecimal::add);
            }
        }
        return sortiesParProduit;
    }

    /**
     * Conversion {@link BigDecimal} -&gt; {@link Integer} (RoundingMode.CEILING)
     * pour produire la liste de lignes BS attendue par {@link BonSortieService}.
     *
     * <p>Garde defensive : si une quantite ressort a 0 apres arrondi (ne devrait
     * pas arriver vu {@code DecimalMin(0.0001)} cote recette), force a 1.</p>
     *
     * <p>Tour 40bis (refactor C3).</p>
     */
    private List<LigneBonSortieCreateDto> convertToBsLines(Map<Long, BigDecimal> sortiesParProduit) {
        List<LigneBonSortieCreateDto> lignesBs = new ArrayList<>(sortiesParProduit.size());
        for (Map.Entry<Long, BigDecimal> e : sortiesParProduit.entrySet()) {
            int qte = e.getValue().setScale(0, RoundingMode.CEILING).intValueExact();
            if (qte < 1) {
                qte = 1; // garde defensive : DecimalMin(0.0001) cote recette
            }
            lignesBs.add(new LigneBonSortieCreateDto(e.getKey(), qte, null));
        }
        return lignesBs;
    }

    @Override
    @Transactional
    public CommandeDto annuler(Long commandeId, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("error.commande.motif.required");
        }
        Commande commande = commandeRepository.findById(commandeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.commande.notFound"));

        if (commande.getStatut() == StatutCommande.SERVIE) {
            throw new BusinessException("error.commande.annulation.servieInterdite");
        }
        if (commande.getStatut() == StatutCommande.ANNULEE) {
            throw new BusinessException("error.commande.dejaAnnulee");
        }
        if (commande.getFactureId() != null) {
            // Si la commande est deja facturee, l'annulation passe par un avoir
            // cote finance (Tour finance-2). Refus ici.
            throw new BusinessException("error.commande.annulation.dejaFacturee");
        }

        commande.setStatut(StatutCommande.ANNULEE);
        commande.setMotifAnnulation(motif);
        commandeRepository.save(commande);

        logger.info("Commande id={} annulee, motif: {}", commandeId, motif);
        return toDtoWithLignes(commande);
    }

    @Override
    @Transactional
    public CommandeDto encaisserComptant(Long commandeId, EncaissementCommandeDto dto) {
        Commande commande = commandeRepository.findById(commandeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.commande.notFound"));

        if (commande.getModeReglement() != ModeReglementCommande.COMPTANT) {
            throw new BusinessException("error.commande.encaissement.modeInvalide");
        }
        if (commande.getStatut() == StatutCommande.ANNULEE) {
            throw new BusinessException("error.commande.encaissement.annulee");
        }
        if (commande.getFactureId() != null) {
            throw new BusinessException("error.commande.encaissement.dejaFacturee");
        }
        if (dto.montant().compareTo(commande.getMontantTtc()) != 0) {
            throw new BusinessException("error.commande.encaissement.montantIncorrect");
        }

        // 1) Cree la Facture (lignes COMMANDE, statut EMISE) via FactureService.
        var facture = factureService.fromCommande(commandeId);

        // 2) Cree le Paiement (statut VALIDE) avec affectation directe a la facture.
        PaiementCreateDto paiementDto = new PaiementCreateDto(
                /* compteId */ null,
                /* factureId */ facture.factureId(),
                dto.montant(),
                commande.getDevise(),
                dto.modePaiement(),
                dto.referencePaiement(),
                /* datePaiement */ null,
                /* commentaires */ "Encaissement commande " + commande.getNumeroCommande());
        PaiementDto paiement = paiementService.create(paiementDto);

        // 3) Met a jour la commande : factureId est deja pose par FactureService.fromCommande(),
        //    on rafraichit puis on positionne montantPaye.
        Commande refreshed = commandeRepository.findById(commandeId)
                .orElseThrow(() -> new BusinessException("error.commande.notFound"));
        refreshed.setMontantPaye(dto.montant());
        commandeRepository.save(refreshed);

        logger.info("Commande id={} encaissee comptant : facture={}, paiement={}, mode={}, montant={}",
                commandeId, facture.factureId(), paiement.paiementId(),
                dto.modePaiement(), dto.montant());

        return toDtoWithLignes(refreshed);
    }

    // ====================================================================
    // Helpers prives
    // ====================================================================

    /**
     * Cree une ligne de commande, en snapshotant {@code libelle} et
     * {@code prixUnitaire} depuis l'article sauf si le DTO les override
     * explicitement.
     *
     * <p>Validation : article existe dans le tenant courant (Hibernate filtre
     * via @TenantId), actif, statut ACTIF, disponible.</p>
     */
    private void creerLigne(Long commandeId, LigneCommandeCreateDto dto) {
        ArticleMenu article = articleMenuRepository.findById(dto.articleId())
                .orElseThrow(() -> new ResourceNotFoundException("error.articleMenu.notFound"));
        if (!Boolean.TRUE.equals(article.getActif())) {
            throw new BusinessException("error.articleMenu.inactive");
        }
        if (article.getStatut() != StatutArticle.ACTIF) {
            throw new BusinessException("error.articleMenu.statutInvalide");
        }
        if (!Boolean.TRUE.equals(article.getDisponible())) {
            throw new BusinessException("error.articleMenu.indisponible");
        }

        LigneCommande ligne = new LigneCommande();
        ligne.setCommandeId(commandeId);
        ligne.setArticleId(article.getArticleId());
        ligne.setLibelle(article.getNom()); // snapshot
        ligne.setQuantite(dto.quantite());
        // Override prix possible (cas reduction operateur) - sinon prix catalogue.
        BigDecimal prix = (dto.prixUnitaire() != null) ? dto.prixUnitaire() : article.getPrix();
        ligne.setPrixUnitaire(prix);
        ligne.setNotesCuisine(dto.notesCuisine());
        // montant recalcule par @PrePersist
        ligneCommandeRepository.save(ligne);
    }

    /**
     * Recalcule {@code montantHt} et {@code montantTtc} de la commande comme
     * la somme des montants des lignes. Pas de TVA dans le POS (ttc == ht).
     * Source unique de verite : Hibernate (chaque ligne se recalcule via
     * @PrePersist/@PreUpdate).
     */
    private void recalcMontants(Long commandeId) {
        Commande commande = commandeRepository.findById(commandeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.commande.notFound"));
        List<LigneCommande> lignes = ligneCommandeRepository
                .findByCommandeIdOrderByLigneIdAsc(commandeId);

        BigDecimal somme = BigDecimal.ZERO;
        for (LigneCommande l : lignes) {
            somme = somme.add(l.getMontant() != null ? l.getMontant() : BigDecimal.ZERO);
        }
        somme = somme.setScale(2, RoundingMode.HALF_UP);
        commande.setMontantHt(somme);
        commande.setMontantTtc(somme); // pas de TVA POS
        commandeRepository.save(commande);
    }

    private CommandeDto toDtoWithLignes(Commande commande) {
        CommandeDto base = commandeMapper.toDto(commande);
        List<LigneCommandeDto> lignes = new ArrayList<>(ligneCommandeRepository
                .findByCommandeIdOrderByLigneIdAsc(commande.getCommandeId())
                .stream().map(ligneMapper::toDto).toList());
        return commandeMapper.withLignes(base, lignes);
    }
}
