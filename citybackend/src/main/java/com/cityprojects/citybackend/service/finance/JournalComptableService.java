package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.dto.finance.JournalComptableCreateDto;
import com.cityprojects.citybackend.dto.finance.JournalComptableDto;
import com.cityprojects.citybackend.dto.finance.JournalComptableUpdateDto;
import com.cityprojects.citybackend.entity.finance.TypeJournal;

import java.util.List;

/**
 * Service de gestion des journaux comptables (par hotel).
 *
 * <p>Toutes les operations sont tenant-scopees ({@code @RequireTenant}).</p>
 */
public interface JournalComptableService {

    /**
     * Cree un journal si aucun journal avec ce code n'existe pour l'hotel
     * courant, sinon renvoie l'existant. Idempotent.
     *
     * <p>Utilise par {@code JournalComptableInitializer} a la creation d'un
     * hotel pour seeder les 6 journaux standards (VTE/ACH/BAN/CAI/OD/AVO).</p>
     */
    JournalComptableDto getOrCreate(String code, String libelle, TypeJournal type);

    /**
     * Cree un nouveau journal. Refuse si un journal avec ce code existe deja
     * pour le tenant courant ({@code BusinessException("error.journal.codeAlreadyExists")}).
     */
    JournalComptableDto create(JournalComptableCreateDto dto);

    /**
     * Met a jour libelle et type d'un journal. Le code n'est PAS modifiable
     * (sert de discriminant pour la numerotation des ecritures).
     */
    JournalComptableDto update(Long id, JournalComptableUpdateDto dto);

    /** Recupere un journal par son code. */
    JournalComptableDto findByCode(String code);

    /** Recupere un journal par son id. */
    JournalComptableDto findById(Long id);

    /** Liste tous les journaux du tenant (actifs + inactifs), tries par code. */
    List<JournalComptableDto> findAll();

    /** Liste les journaux actifs du tenant, tries par code. */
    List<JournalComptableDto> findActifs();

    /** Desactive un journal (les ecritures historiques restent intactes). */
    JournalComptableDto desactiver(Long id);

    /** Reactive un journal desactive. */
    JournalComptableDto reactiver(Long id);
}
