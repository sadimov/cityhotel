package com.cityprojects.citybackend.dto.finance;

import com.cityprojects.citybackend.entity.finance.ModePaiement;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO d'entree (Tour 45) : paiement granulaire de lignes selectionnees d'une
 * facture.
 *
 * <p>Permet d'encaisser un paiement et de le ventiler <b>uniquement</b> sur
 * un sous-ensemble de lignes d'une facture (ex. paiement des nuitees mais
 * pas des extras). La ventilation est <em>proportionnelle</em> au reste
 * de chaque ligne selectionnee. En cas de paiement excedentaire (montant
 * superieur a la somme des restes), le surplus est credite sur le compte
 * client (avec un libelle "Excedent paiement").</p>
 *
 * <p>Si {@code factureId} est fourni, toutes les {@code lignesIds} doivent
 * appartenir a cette facture (verification service). Si {@code factureId}
 * est {@code null}, le service le deduit de la premiere ligne et verifie
 * la coherence.</p>
 *
 * <p>{@code idCompteClient} peut etre 0 ou {@code null} : le service le
 * resout via {@link com.cityprojects.citybackend.service.finance.CompteService#findOrCreateForClient(Long)}
 * a partir de {@code idClient}.</p>
 */
public record PaiementLignesRequest(
        Long factureId,
        @NotEmpty List<Long> lignesIds,
        @NotNull @DecimalMin(value = "0.01") BigDecimal montant,
        @NotNull ModePaiement modePaiement,
        String motif,
        String description,
        @NotNull Long idClient,
        Long idCompteClient) {
}
