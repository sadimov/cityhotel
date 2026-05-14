package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.EcritureComptableCreateDto;
import com.cityprojects.citybackend.dto.finance.EcritureComptableDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

/**
 * Service de gestion des ecritures comptables en partie double (cœur du
 * module compta native).
 *
 * <p>Toutes les operations sont tenant-scopees ({@code @RequireTenant}).</p>
 */
public interface EcritureComptableService {

    /**
     * Cree une ecriture VALIDEE en partie double.
     *
     * <p>Validations effectuees :</p>
     * <ul>
     *   <li>{@code dto.lignes()} contient au moins 2 entrees
     *       (sinon {@code BusinessException("error.ecriture.minLines")}) ;</li>
     *   <li>Σ debits == Σ credits avec tolerance 0.01 MRU
     *       (sinon {@code BusinessException("error.ecriture.unbalanced")}) ;</li>
     *   <li>Chaque {@code compteCode} existe et est {@code utilisable=true}
     *       dans le PCG
     *       (sinon {@code BusinessException("error.ecriture.compteInvalide")}) ;</li>
     *   <li>L'exercice contenant {@code dateComptable} est OUVERT
     *       (delegue a {@code ExerciceService.assertOuvert}) ;</li>
     *   <li>Le journal {@code journalCode} existe et est actif
     *       (sinon {@code BusinessException("error.ecriture.journalInvalide")}).</li>
     * </ul>
     *
     * <p>Le numero est genere via {@link NumerotationService} (type JRN,
     * discriminant = codeJournal). L'ecriture est creee en statut VALIDEE.</p>
     */
    EcritureComptableDto creer(EcritureComptableCreateDto dto);

    /**
     * Contre-passe une ecriture VALIDEE.
     *
     * <p>Cree une nouvelle ecriture sur le meme journal et meme exercice,
     * avec lignes inversees (D devient C, C devient D). Date comptable de
     * la nouvelle ecriture = aujourd'hui (la contre-passation est un fait
     * comptable nouveau). Reference = {@code "CP-{numeroSource}"}, libelle =
     * {@code "Contre-passation : {libelleSource} ({motif})"}.</p>
     *
     * <p>L'ecriture source passe en CONTRE_PASSEE, son champ
     * {@code contrePasseeParId} pointe vers la nouvelle. La nouvelle reste
     * VALIDEE, son champ {@code ecritureSourceId} pointe vers l'originale.</p>
     *
     * <p>Erreurs :</p>
     * <ul>
     *   <li>{@code error.ecriture.notFound} si l'id est inconnu (filtre
     *       tenant Hibernate intervient meme cross-tenant) ;</li>
     *   <li>{@code error.ecriture.notValidated} si statut != VALIDEE ;</li>
     *   <li>{@code error.ecriture.alreadyContrePassed} si statut ==
     *       CONTRE_PASSEE ;</li>
     *   <li>{@code error.motif.required} si motif vide.</li>
     * </ul>
     */
    EcritureComptableDto contrePasser(Long ecritureId, String motif);

    /** Lecture par id. */
    EcritureComptableDto findById(Long id);

    /** Liste paginee de toutes les ecritures du tenant courant. */
    Page<EcritureComptableDto> findAll(Pageable pageable);

    /**
     * Ecritures d'un journal sur une plage de dates comptables (bornes
     * incluses).
     */
    Page<EcritureComptableDto> findByJournal(Long journalId, LocalDate dateDebut,
                                             LocalDate dateFin, Pageable pageable);

    /**
     * Ecritures contenant au moins une ligne sur le compte donne, sur une
     * plage de dates comptables (grand livre).
     */
    Page<EcritureComptableDto> findByCompte(String compteCode, LocalDate dateDebut,
                                            LocalDate dateFin, Pageable pageable);

    /** Ecritures d'un exercice donne, paginees. */
    Page<EcritureComptableDto> findByExercice(Long exerciceId, Pageable pageable);
}
