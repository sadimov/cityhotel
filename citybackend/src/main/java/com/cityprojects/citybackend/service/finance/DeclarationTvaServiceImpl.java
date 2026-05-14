package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.finance.DeclarationTvaDto;
import com.cityprojects.citybackend.dto.finance.EcritureComptableCreateDto;
import com.cityprojects.citybackend.dto.finance.EcritureComptableDto;
import com.cityprojects.citybackend.dto.finance.LigneEcritureCreateDto;
import com.cityprojects.citybackend.entity.finance.DeclarationTva;
import com.cityprojects.citybackend.entity.finance.Exercice;
import com.cityprojects.citybackend.entity.finance.SensLigne;
import com.cityprojects.citybackend.entity.finance.StatutDeclarationTva;
import com.cityprojects.citybackend.entity.finance.TypeEvenementComptable;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.finance.DeclarationTvaRepository;
import com.cityprojects.citybackend.repository.finance.ExerciceRepository;
import com.cityprojects.citybackend.repository.finance.LigneEcritureRepository;
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
import java.util.Optional;

/**
 * Implémentation de {@link DeclarationTvaService}.
 *
 * <p>Conventions :</p>
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe.</li>
 *   <li>{@code @Transactional(readOnly = true)} au niveau classe, override
 *       en écriture sur {@link #calculer} (upsert) et {@link #valider}
 *       (écriture de liquidation atomique).</li>
 *   <li>Idempotence stricte sur {@link #calculer} : pas de doublon par
 *       période, retour de la déclaration existante (BROUILLON ou VALIDEE).</li>
 *   <li>Atomicité écriture liquidation : si la création de l'écriture OD
 *       plante, la TX rollback et la déclaration reste BROUILLON.</li>
 * </ul>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class DeclarationTvaServiceImpl implements DeclarationTvaService {

    private static final Logger logger = LoggerFactory.getLogger(DeclarationTvaServiceImpl.class);

    private final DeclarationTvaRepository repository;
    private final LigneEcritureRepository ligneEcritureRepository;
    private final ExerciceRepository exerciceRepository;
    private final CompteMappingService compteMappingService;
    private final EcritureComptableService ecritureComptableService;

    public DeclarationTvaServiceImpl(DeclarationTvaRepository repository,
                                     LigneEcritureRepository ligneEcritureRepository,
                                     ExerciceRepository exerciceRepository,
                                     CompteMappingService compteMappingService,
                                     EcritureComptableService ecritureComptableService) {
        this.repository = repository;
        this.ligneEcritureRepository = ligneEcritureRepository;
        this.exerciceRepository = exerciceRepository;
        this.compteMappingService = compteMappingService;
        this.ecritureComptableService = ecritureComptableService;
    }

    @Override
    @Transactional
    public DeclarationTvaDto calculer(LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut == null || dateFin == null) {
            throw new BusinessException("error.declaration.periodeRequired");
        }
        if (dateFin.isBefore(dateDebut)) {
            throw new BusinessException("error.declaration.periodeInvalide");
        }

        // Idempotence stricte : si une déclaration existe pour cette période,
        // on la retourne telle quelle (BROUILLON ou VALIDEE) sans recalculer.
        Optional<DeclarationTva> existing = repository.findByDateDebutAndDateFin(dateDebut, dateFin);
        if (existing.isPresent()) {
            logger.debug("Declaration TVA existante reutilisee : id={}, statut={}",
                    existing.get().getId(), existing.get().getStatut());
            return toDto(existing.get());
        }

        String compteCollectee = compteMappingService.getCompte(TypeEvenementComptable.TVA_COLLECTEE);
        String compteDeductible = compteMappingService.getCompte(TypeEvenementComptable.TVA_DEDUCTIBLE);

        // Agrégation depuis les écritures comptables.
        // Note : LigneEcritureRepository.sumByCompteCodeAndDateBetween filtre
        // via @TenantId, donc retourne uniquement les lignes du tenant courant.
        // Les écritures contre-passées sont prises en compte sous forme de
        // lignes inversées (la contre-passation génère une nouvelle écriture
        // avec D/C swappés) - ce qui est exactement ce qu'on veut pour le
        // calcul fiscal mensuel.
        BigDecimal collectee = nullToZero(ligneEcritureRepository
                .sumByCompteCodeAndDateBetween(compteCollectee, dateDebut, dateFin, SensLigne.CREDIT))
                .subtract(nullToZero(ligneEcritureRepository
                        .sumByCompteCodeAndDateBetween(compteCollectee, dateDebut, dateFin, SensLigne.DEBIT)));
        BigDecimal deductible = nullToZero(ligneEcritureRepository
                .sumByCompteCodeAndDateBetween(compteDeductible, dateDebut, dateFin, SensLigne.DEBIT))
                .subtract(nullToZero(ligneEcritureRepository
                        .sumByCompteCodeAndDateBetween(compteDeductible, dateDebut, dateFin, SensLigne.CREDIT)));

        collectee = collectee.setScale(2, RoundingMode.HALF_UP);
        deductible = deductible.setScale(2, RoundingMode.HALF_UP);
        BigDecimal aDecaisser = collectee.subtract(deductible).setScale(2, RoundingMode.HALF_UP);

        DeclarationTva entity = new DeclarationTva();
        entity.setDateDebut(dateDebut);
        entity.setDateFin(dateFin);
        entity.setTotalTvaCollectee(collectee);
        entity.setTotalTvaDeductible(deductible);
        entity.setTotalTvaADecaisser(aDecaisser);
        entity.setStatut(StatutDeclarationTva.BROUILLON);

        // Rattachement exercice via date_debut (peut être null si aucun
        // exercice ne couvre cette date : déclaration multi-exercice possible).
        Optional<Exercice> exercice = exerciceRepository.findContainingDate(dateDebut);
        exercice.ifPresent(entity::setExercice);

        DeclarationTva saved = repository.save(entity);
        logger.info("Declaration TVA calculee : id={}, periode={}->{}, collectee={}, deductible={}, aDecaisser={}",
                saved.getId(), dateDebut, dateFin, collectee, deductible, aDecaisser);
        return toDto(saved);
    }

    @Override
    @Transactional
    public DeclarationTvaDto valider(Long declarationId) {
        DeclarationTva decl = repository.findById(declarationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.declaration.notFound"));

        if (decl.getStatut() == StatutDeclarationTva.VALIDEE) {
            throw new BusinessException("error.declaration.dejaValidee");
        }

        BigDecimal collectee = nullToZero(decl.getTotalTvaCollectee());
        BigDecimal deductible = nullToZero(decl.getTotalTvaDeductible());

        // Si les deux côtés sont à zéro, pas d'écriture de liquidation à
        // générer (et même si on l'essayait, EcritureComptableService refuserait
        // l'écriture vide). On refuse pour ne pas avoir une déclaration
        // VALIDEE sans pièce comptable associée.
        if (collectee.signum() == 0 && deductible.signum() == 0) {
            throw new BusinessException("error.declaration.aucunMontant");
        }

        // Génération de l'écriture de liquidation (journal OD).
        String compteCollectee = compteMappingService.getCompte(TypeEvenementComptable.TVA_COLLECTEE);
        String compteDeductible = compteMappingService.getCompte(TypeEvenementComptable.TVA_DEDUCTIBLE);
        String compteADecaisser = compteMappingService.getCompte(TypeEvenementComptable.TVA_A_DECAISSER);

        List<LigneEcritureCreateDto> lignes = new ArrayList<>();
        int ordre = 1;
        // Solde 445700 (collectée a un solde créditeur, on le débite pour le ramener à 0)
        if (collectee.signum() > 0) {
            lignes.add(new LigneEcritureCreateDto(
                    ordre++,
                    compteCollectee,
                    "Solde TVA collectee periode " + decl.getDateDebut() + " a " + decl.getDateFin(),
                    SensLigne.DEBIT,
                    collectee,
                    null));
        }
        // Solde 445600 (déductible a un solde débiteur, on le crédite pour le ramener à 0)
        if (deductible.signum() > 0) {
            lignes.add(new LigneEcritureCreateDto(
                    ordre++,
                    compteDeductible,
                    "Solde TVA deductible periode " + decl.getDateDebut() + " a " + decl.getDateFin(),
                    SensLigne.CREDIT,
                    deductible,
                    null));
        }
        // Solde de liquidation sur 445800 (à décaisser ou crédit reportable)
        BigDecimal aDecaisser = collectee.subtract(deductible);
        if (aDecaisser.signum() > 0) {
            // À payer à l'administration fiscale : CRÉDIT 445800.
            lignes.add(new LigneEcritureCreateDto(
                    ordre++,
                    compteADecaisser,
                    "TVA a decaisser",
                    SensLigne.CREDIT,
                    aDecaisser,
                    null));
        } else if (aDecaisser.signum() < 0) {
            // Crédit reportable : DÉBIT 445800 (le compte porte un solde
            // débiteur en faveur de l'entreprise).
            lignes.add(new LigneEcritureCreateDto(
                    ordre++,
                    compteADecaisser,
                    "Credit TVA reportable",
                    SensLigne.DEBIT,
                    aDecaisser.abs(),
                    null));
        }
        // Si aDecaisser == 0, pas de ligne 445800 (déclaration équilibrée
        // collectée == déductible). Les deux lignes 445700/445600 suffisent
        // pour l'équilibre Σ D == Σ C.

        if (lignes.size() < 2) {
            // Cas marginal : un seul des deux côtés non-nul ET aDecaisser
            // a la même valeur. Théoriquement impossible mais on protège.
            throw new BusinessException("error.declaration.aucunMontant");
        }

        LocalDate today = LocalDate.now();
        EcritureComptableCreateDto ecritureDto = new EcritureComptableCreateDto(
                today,
                today,
                "OD",
                "Liquidation TVA " + decl.getDateDebut() + " - " + decl.getDateFin(),
                "TVA-" + decl.getDateDebut() + "_" + decl.getDateFin(),
                lignes);

        EcritureComptableDto created = ecritureComptableService.creer(ecritureDto);

        decl.setStatut(StatutDeclarationTva.VALIDEE);
        decl.setEcritureLiquidationId(created.id());
        decl.setDateValidation(today);
        decl.setValideePar(currentUsernameOrSystem());
        DeclarationTva saved = repository.save(decl);

        logger.info("Declaration TVA validee : id={}, ecritureLiquidationId={}",
                saved.getId(), created.id());
        return toDto(saved);
    }

    @Override
    public Page<DeclarationTvaDto> findAll(Pageable pageable) {
        return repository.findAllByOrderByDateDebutDesc(pageable).map(this::toDto);
    }

    @Override
    public DeclarationTvaDto findById(Long id) {
        DeclarationTva entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.declaration.notFound"));
        return toDto(entity);
    }

    @Override
    public Optional<DeclarationTvaDto> findByPeriode(LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut == null || dateFin == null) {
            return Optional.empty();
        }
        return repository.findByDateDebutAndDateFin(dateDebut, dateFin).map(this::toDto);
    }

    private DeclarationTvaDto toDto(DeclarationTva entity) {
        return new DeclarationTvaDto(
                entity.getId(),
                entity.getDateDebut(),
                entity.getDateFin(),
                entity.getTotalTvaCollectee(),
                entity.getTotalTvaDeductible(),
                entity.getTotalTvaADecaisser(),
                entity.getStatut(),
                entity.getExercice() != null ? entity.getExercice().getId() : null,
                entity.getEcritureLiquidationId(),
                entity.getDateValidation(),
                entity.getValideePar());
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return (v != null) ? v : BigDecimal.ZERO;
    }

    private String currentUsernameOrSystem() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null
                && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "system";
    }
}
