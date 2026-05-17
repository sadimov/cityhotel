package com.cityprojects.citybackend.service.inventory;

import com.cityprojects.citybackend.common.audit.AuditFinanceAction;
import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.inventory.BonCommandeCreateDto;
import com.cityprojects.citybackend.dto.inventory.BonCommandeDto;
import com.cityprojects.citybackend.dto.inventory.LigneBonCommandeCreateDto;
import com.cityprojects.citybackend.dto.inventory.LigneBonCommandeDto;
import com.cityprojects.citybackend.dto.inventory.ReceptionBonCommandeDto;
import com.cityprojects.citybackend.dto.inventory.ReceptionLigneDto;
import com.cityprojects.citybackend.entity.inventory.BonCommande;
import com.cityprojects.citybackend.entity.inventory.Fournisseur;
import com.cityprojects.citybackend.entity.inventory.LigneBonCommande;
import com.cityprojects.citybackend.entity.inventory.MouvementStock;
import com.cityprojects.citybackend.entity.inventory.Produit;
import com.cityprojects.citybackend.entity.inventory.StatutBonCommande;
import com.cityprojects.citybackend.entity.inventory.TypeMouvementStock;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.inventory.BonCommandeMapper;
import com.cityprojects.citybackend.repository.inventory.BonCommandeRepository;
import com.cityprojects.citybackend.repository.inventory.FournisseurRepository;
import com.cityprojects.citybackend.repository.inventory.LigneBonCommandeRepository;
import com.cityprojects.citybackend.repository.inventory.MouvementStockRepository;
import com.cityprojects.citybackend.repository.inventory.ProduitRepository;
import com.cityprojects.citybackend.security.UserPrincipal;
import com.cityprojects.citybackend.service.finance.EcritureComptableService;
import com.cityprojects.citybackend.service.finance.EcritureGenerationService;
import com.cityprojects.citybackend.service.finance.NumerotationService;
import com.cityprojects.citybackend.service.finance.TypeNumerotation;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation de {@link BonCommandeService}.
 *
 * <p>Conventions appliquees :
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe.</li>
 *   <li>{@code numeroBc} genere via {@link NumerotationService} (cle BC).</li>
 *   <li>{@code userId} extrait du SecurityContext (jamais d'un DTO entrant).</li>
 *   <li>Reception : double ecriture atomique (LigneBC + Produit + MouvementStock)
 *       dans la meme transaction.</li>
 * </ul>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class BonCommandeServiceImpl implements BonCommandeService {

    private static final Logger logger = LoggerFactory.getLogger(BonCommandeServiceImpl.class);

    private final BonCommandeRepository bcRepository;
    private final LigneBonCommandeRepository ligneRepository;
    private final FournisseurRepository fournisseurRepository;
    private final ProduitRepository produitRepository;
    private final MouvementStockRepository mouvementRepository;
    private final BonCommandeMapper mapper;
    private final NumerotationService numerotationService;
    private final EcritureGenerationService ecritureGenerationService;
    private final EcritureComptableService ecritureComptableService;

    public BonCommandeServiceImpl(BonCommandeRepository bcRepository,
                                  LigneBonCommandeRepository ligneRepository,
                                  FournisseurRepository fournisseurRepository,
                                  ProduitRepository produitRepository,
                                  MouvementStockRepository mouvementRepository,
                                  BonCommandeMapper mapper,
                                  NumerotationService numerotationService,
                                  EcritureGenerationService ecritureGenerationService,
                                  EcritureComptableService ecritureComptableService) {
        this.bcRepository = bcRepository;
        this.ligneRepository = ligneRepository;
        this.fournisseurRepository = fournisseurRepository;
        this.produitRepository = produitRepository;
        this.mouvementRepository = mouvementRepository;
        this.mapper = mapper;
        this.numerotationService = numerotationService;
        this.ecritureGenerationService = ecritureGenerationService;
        this.ecritureComptableService = ecritureComptableService;
    }

    @Override
    @Transactional
    public BonCommandeDto create(BonCommandeCreateDto dto) {
        logger.info("Creation BC : fournisseur={}, lignes={}", dto.fournisseurId(), dto.lignes().size());

        // 1. Validations
        Fournisseur fournisseur = fournisseurRepository.findById(dto.fournisseurId())
                .orElseThrow(() -> new ResourceNotFoundException("error.fournisseur.notFound"));
        if (!Boolean.TRUE.equals(fournisseur.getActif())) {
            throw new BusinessException("error.bonCommande.fournisseur.inactive");
        }
        // Tous les produits doivent exister et appartenir au tenant courant
        for (LigneBonCommandeCreateDto l : dto.lignes()) {
            Produit p = produitRepository.findById(l.produitId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.produit.notFound"));
            if (!Boolean.TRUE.equals(p.getActif())) {
                throw new BusinessException("error.bonCommande.produit.inactive");
            }
        }

        // 2. Construction BC
        BonCommande bc = new BonCommande();
        bc.setFournisseurId(dto.fournisseurId());
        bc.setStatut(StatutBonCommande.BROUILLON);
        bc.setDateLivraisonPrevue(dto.dateLivraisonPrevue());
        bc.setCommentaires(dto.commentaires());
        bc.setMontantTotal(BigDecimal.ZERO);
        bc.setMontantTva(BigDecimal.ZERO);
        bc.setUserId(currentUserId());
        bc.setNumeroBc(numerotationService.next(TypeNumerotation.BC));
        // PAS de setHotelId : Hibernate s'en charge via @TenantId resolver.
        BonCommande savedBc = bcRepository.save(bc);

        // 3. Lignes + calcul total
        BigDecimal total = BigDecimal.ZERO;
        for (LigneBonCommandeCreateDto ld : dto.lignes()) {
            LigneBonCommande ligne = new LigneBonCommande();
            ligne.setBonCommandeId(savedBc.getBonCommandeId());
            ligne.setProduitId(ld.produitId());
            ligne.setQuantiteCommandee(ld.quantiteCommandee());
            ligne.setQuantiteRecue(0);
            ligne.setPrixUnitaire(ld.prixUnitaire());
            ligneRepository.save(ligne);
            total = total.add(ligne.getSousTotal());
        }
        savedBc.setMontantTotal(total);
        bcRepository.save(savedBc);

        logger.info("BC cree : id={}, numero={}, total={}",
                savedBc.getBonCommandeId(), savedBc.getNumeroBc(), total);
        return toDtoWithLignes(savedBc);
    }

    @Override
    public BonCommandeDto findById(Long bonCommandeId) {
        BonCommande bc = bcRepository.findById(bonCommandeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.bonCommande.notFound"));
        return toDtoWithLignes(bc);
    }

    @Override
    public Page<BonCommandeDto> findByStatut(StatutBonCommande statut, Pageable pageable) {
        Page<BonCommande> page = (statut != null)
                ? bcRepository.findByStatutOrderByDateCommandeDesc(statut, pageable)
                : bcRepository.findAll(pageable);
        return page.map(this::toDtoWithLignes);
    }

    @Override
    @Transactional
    public BonCommandeDto changerStatut(Long bonCommandeId, StatutBonCommande nouveauStatut) {
        BonCommande bc = bcRepository.findById(bonCommandeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.bonCommande.notFound"));

        if (!isTransitionValide(bc.getStatut(), nouveauStatut)) {
            throw new BusinessException("error.bonCommande.transitionInvalide");
        }
        bc.setStatut(nouveauStatut);
        bcRepository.save(bc);
        return toDtoWithLignes(bc);
    }

    @Override
    @Transactional
    @AuditFinanceAction(value = "BON_COMMANDE_RECEPTION", entityType = "BON_COMMANDE")
    public BonCommandeDto receptionner(Long bonCommandeId, ReceptionBonCommandeDto reception) {
        logger.info("Reception BC id={}, lignes={}", bonCommandeId, reception.lignes().size());

        BonCommande bc = bcRepository.findById(bonCommandeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.bonCommande.notFound"));

        // Statuts permettant la reception
        if (bc.getStatut() != StatutBonCommande.CONFIRME
                && bc.getStatut() != StatutBonCommande.RECU_PARTIEL
                && bc.getStatut() != StatutBonCommande.ENVOYE) {
            throw new BusinessException("error.bonCommande.reception.statutInvalide");
        }

        // Charger toutes les lignes du BC
        List<LigneBonCommande> lignesBc = ligneRepository.findByBonCommandeIdOrderByLigneIdAsc(bonCommandeId);
        Map<Long, LigneBonCommande> ligneMap = new HashMap<>();
        for (LigneBonCommande l : lignesBc) {
            ligneMap.put(l.getLigneId(), l);
        }

        Long userId = currentUserId();
        LocalDate today = LocalDate.now();

        // Bloc B3 : on cumule la valorisation des lignes effectivement
        // receptionnees sur CETTE reception pour generer une ecriture
        // ACH equivalente (311 D / 401 C).
        BigDecimal montantReception = BigDecimal.ZERO;

        for (ReceptionLigneDto rl : reception.lignes()) {
            if (rl.quantiteRecue() == 0) {
                continue;
            }
            LigneBonCommande ligne = ligneMap.get(rl.ligneId());
            if (ligne == null) {
                throw new BusinessException("error.reception.ligne.notFound");
            }
            int dejaRecu = ligne.getQuantiteRecue() != null ? ligne.getQuantiteRecue() : 0;
            int aRecevoir = ligne.getQuantiteCommandee() - dejaRecu;
            if (rl.quantiteRecue() > aRecevoir) {
                throw new BusinessException("error.reception.quantite.depasse");
            }

            // Met a jour la ligne
            ligne.setQuantiteRecue(dejaRecu + rl.quantiteRecue());
            ligne.setDateReception(today);
            ligneRepository.save(ligne);

            // Cumule la valeur de cette reception (qte * prix unitaire)
            BigDecimal pu = ligne.getPrixUnitaire() != null ? ligne.getPrixUnitaire() : BigDecimal.ZERO;
            montantReception = montantReception.add(pu.multiply(BigDecimal.valueOf(rl.quantiteRecue())));

            // Met a jour le stock du produit + audit trail
            Produit produit = produitRepository.findById(ligne.getProduitId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.produit.notFound"));
            int stockAvant = produit.getStockActuel() != null ? produit.getStockActuel() : 0;
            int stockApres = stockAvant + rl.quantiteRecue();
            produit.setStockActuel(stockApres);
            produitRepository.save(produit);

            MouvementStock mouvement = new MouvementStock();
            mouvement.setProduitId(produit.getProduitId());
            mouvement.setTypeMouvement(TypeMouvementStock.ENTREE);
            mouvement.setQuantite(rl.quantiteRecue());
            mouvement.setPrixUnitaire(ligne.getPrixUnitaire());
            mouvement.setStockAvant(stockAvant);
            mouvement.setStockApres(stockApres);
            mouvement.setReferenceDocument(bc.getNumeroBc());
            mouvement.setUserId(userId);
            mouvementRepository.save(mouvement);
        }

        // Re-evaluer le statut du BC
        boolean toutesLignesCompletes = true;
        boolean auMoinsUneServie = false;
        for (LigneBonCommande l : ligneRepository.findByBonCommandeIdOrderByLigneIdAsc(bonCommandeId)) {
            if (!l.isCompleteReception()) {
                toutesLignesCompletes = false;
            }
            int recu = l.getQuantiteRecue() != null ? l.getQuantiteRecue() : 0;
            if (recu > 0) {
                auMoinsUneServie = true;
            }
        }
        if (toutesLignesCompletes) {
            bc.setStatut(StatutBonCommande.RECU_COMPLET);
            bc.setDateLivraisonReelle(today);
        } else if (auMoinsUneServie) {
            bc.setStatut(StatutBonCommande.RECU_PARTIEL);
        }
        bcRepository.save(bc);

        // Bloc B3 : ecriture ACH si une reception effective a eu lieu.
        // Note : reception multi-passage -> on cree 1 ecriture par passage
        // (la regle est "1 mouvement physique = 1 ecriture"). On stocke
        // l'id de la DERNIERE ecriture sur bc.ecritureReceptionId (la
        // colonne est destinee a indexer rapidement les annulations totales
        // ulterieures ; pour l'historique complet on a audit_finance_log
        // + l'ecriture porte reference = numeroBc).
        if (montantReception.signum() > 0) {
            Long ecritureId = ecritureGenerationService.emettreEcritureReceptionBC(bc, montantReception);
            if (ecritureId != null) {
                bc.setEcritureReceptionId(ecritureId);
                bcRepository.save(bc);
            }
        }

        return toDtoWithLignes(bc);
    }

    @Override
    @Transactional
    @AuditFinanceAction(value = "BON_COMMANDE_ANNULATION", entityType = "BON_COMMANDE")
    public BonCommandeDto annuler(Long bonCommandeId) {
        BonCommande bc = bcRepository.findById(bonCommandeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.bonCommande.notFound"));
        if (!StatutBonCommande.peutEtreAnnule(bc.getStatut())) {
            throw new BusinessException("error.bonCommande.annulation.statutInvalide");
        }
        boolean avaitEcriture = bc.getEcritureReceptionId() != null;
        bc.setStatut(StatutBonCommande.ANNULE);
        bcRepository.save(bc);

        // Bloc B3 : contre-passation si une ecriture de reception avait
        // ete generee (cas RECU_PARTIEL -> ANNULE, rare en pratique mais
        // possible). Le stock physique a deja ete decremente en amont
        // par MouvementStock SORTIE manuel ; la compta suit.
        if (avaitEcriture) {
            ecritureComptableService.contrePasser(
                    bc.getEcritureReceptionId(),
                    "Annulation BC " + bc.getNumeroBc());
        }

        logger.info("BC annule : id={}, numero={}, contrePassation={}",
                bc.getBonCommandeId(), bc.getNumeroBc(), avaitEcriture);
        return toDtoWithLignes(bc);
    }

    /**
     * Valide la transition de statut. La reception est geree par
     * {@link #receptionner(Long, ReceptionBonCommandeDto)} ; cette methode est
     * uniquement pour les transitions sans impact stock (BROUILLON-&gt;ENVOYE,
     * ENVOYE-&gt;CONFIRME).
     */
    private boolean isTransitionValide(StatutBonCommande courant, StatutBonCommande cible) {
        if (cible == StatutBonCommande.ANNULE) {
            return StatutBonCommande.peutEtreAnnule(courant);
        }
        return switch (courant) {
            case BROUILLON -> cible == StatutBonCommande.ENVOYE;
            case ENVOYE -> cible == StatutBonCommande.CONFIRME;
            default -> false;
        };
    }

    private BonCommandeDto toDtoWithLignes(BonCommande bc) {
        BonCommandeDto base = mapper.toDto(bc);
        List<LigneBonCommande> entLignes = ligneRepository
                .findByBonCommandeIdOrderByLigneIdAsc(bc.getBonCommandeId());
        // Batch lookup produits pour résolution noms (anti-N+1)
        Set<Long> produitIds = entLignes.stream()
                .map(LigneBonCommande::getProduitId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Produit> produits = produitIds.isEmpty() ? Map.of()
                : produitRepository.findAllById(produitIds).stream()
                        .collect(Collectors.toMap(Produit::getProduitId, p -> p));
        List<LigneBonCommandeDto> lignes = entLignes.stream()
                .map(l -> {
                    LigneBonCommandeDto baseDto = mapper.toLigneDto(l);
                    Produit p = produits.get(l.getProduitId());
                    return baseDto.withResolvedNames(
                            p != null ? p.getNomProduit() : null,
                            p != null ? p.getCodeProduit() : null,
                            p != null ? p.getUniteMesure() : null);
                })
                .toList();
        BonCommandeDto withLignes = mapper.withLignes(base, lignes);
        String nomFournisseur = (bc.getFournisseurId() != null)
                ? fournisseurRepository.findById(bc.getFournisseurId())
                        .map(f -> f.getNomFournisseur()).orElse(null)
                : null;
        return withLignes.withResolvedNames(nomFournisseur);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId();
        }
        throw new BusinessException("error.user.unknown");
    }
}
