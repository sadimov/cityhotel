package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.entity.finance.Compte;

import java.util.Optional;

/**
 * Service du compte AUXILIAIRE CLIENT (Tour 22.1).
 *
 * <p><b>⚠️ DÉPRÉCATION SÉMANTIQUE - Tour 20bis (2026-05-07).</b> L'entite
 * {@link Compte} represente un compte <em>auxiliaire client/societe</em> (suivi
 * de la dette client par tenant), <b>PAS</b> un compte du Plan Comptable General
 * SYSCOHADA / mauritanien. La comptabilite generale est externalisee vers
 * Dolibarr (bridge a venir tour ulterieur).</p>
 *
 * <p>Ce service ne fait que de l'audit trail auxiliaire client : DEBIT a
 * l'emission de facture, CREDIT a la validation de paiement, mise a jour
 * {@link Compte#getSoldeActuel()} coherente.</p>
 *
 * <p>Toutes les methodes operent sous {@code @RequireTenant}.</p>
 */
public interface CompteService {

    /**
     * Trouve ou cree le compte auxiliaire pour un client.
     *
     * <p>Idempotent : 2 appels avec le meme {@code clientId} retournent le
     * meme compte (le compte est cree au 1er appel uniquement). Le format du
     * {@code numeroCompte} est {@code CPT-CLI-{clientId}} (ex.
     * {@code CPT-CLI-12345}).</p>
     *
     * @param clientId identifiant client (verifie tenant via Hibernate {@code @TenantId})
     * @return le compte auxiliaire (existant ou nouvellement cree)
     */
    Compte findOrCreateForClient(Long clientId);

    /**
     * Trouve ou cree le compte auxiliaire pour une societe B2B.
     *
     * <p>Idempotent. Format {@code numeroCompte} : {@code CPT-SOC-{societeId}}.</p>
     */
    Compte findOrCreateForSociete(Long societeId);

    /**
     * Recupere un compte par son ID.
     *
     * @throws com.cityprojects.citybackend.exception.ResourceNotFoundException
     *         si le compte n'existe pas pour le tenant courant.
     */
    Compte findById(Long compteId);

    /** Recupere le compte d'un client (ou {@link Optional#empty()} s'il n'existe pas). */
    Optional<Compte> findByClientId(Long clientId);

    /** Recupere le compte d'une societe (ou {@link Optional#empty()} s'il n'existe pas). */
    Optional<Compte> findBySocieteId(Long societeId);
}
