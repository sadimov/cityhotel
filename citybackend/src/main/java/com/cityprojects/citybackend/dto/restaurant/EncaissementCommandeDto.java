package com.cityprojects.citybackend.dto.restaurant;

import com.cityprojects.citybackend.entity.finance.ModePaiement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO d'encaissement comptant d'une commande POS (Tour 24).
 *
 * <p>Trigge la creation d'une {@code Facture} {@code FACTURE} + d'un
 * {@code Paiement} {@code VALIDE} avec affectation directe. Met a jour
 * {@code Commande.factureId} et {@code Commande.montantPaye}.</p>
 *
 * <p>Le {@code montant} doit etre egal a {@code commande.montantTtc} +
 * somme des {@code services} (l'encaissement comptant n'admet pas de paiement
 * partiel : pour ce cas-la, faire un report sur chambre puis facture sejour).</p>
 *
 * <p><b>Tour 70</b> : le panier POS peut contenir des lignes SERVICE (services
 * hoteliers). Elles sont passees dans {@link #services()} et ajoutees a la
 * facture creee par {@code fromCommande()} avant l'encaissement, afin que le
 * paiement solde la totalite et que la facture imprimee contienne articles +
 * services.</p>
 */
public record EncaissementCommandeDto(
        @NotNull(message = "error.encaissement.modePaiement.required")
        ModePaiement modePaiement,

        @NotNull(message = "error.encaissement.montant.required")
        @DecimalMin(value = "0.01", message = "error.encaissement.montant.positive")
        BigDecimal montant,

        @Size(max = 100)
        String referencePaiement,

        /** Services hoteliers a ajouter a la facture (Tour 70). Nullable / vide accepte. */
        @Valid
        List<EncaissementServiceDto> services) {
}
