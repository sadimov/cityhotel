package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.dto.hebergement.NuiteeDto;
import com.cityprojects.citybackend.dto.hebergement.NuiteeModificationDto;
import com.cityprojects.citybackend.dto.hebergement.NuiteeMontantUpdateRequest;
import com.cityprojects.citybackend.dto.hebergement.NuiteesUpdateResultDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service de lecture des nuitees (Tour 14 - module hebergement) + mutation
 * granulaire montant (Tour 45).
 *
 * <p>Read-only pour le coeur (creation = {@link ReservationService} +
 * {@link NightAuditService}). Tour 45 introduit la mutation
 * <em>individuelle</em> du {@code prixNuit} d'une nuitee tant que la facture
 * parente n'est pas terminale (PAYEE / ANNULEE).</p>
 *
 * <p>Toutes les methodes operent dans le tenant courant (filtre Hibernate via
 * {@link org.hibernate.annotations.TenantId}).</p>
 */
public interface NuiteeService {

    /**
     * Liste les nuitees d'une reservation, par date croissante (toutes statuts).
     */
    List<NuiteeDto> findByReservation(Long reservationId);

    /**
     * Page des nuitees d'une chambre (toutes reservations / statuts).
     * <p>Tri par defaut : {@code dateNuit DESC, nuiteeId ASC} pour stabilite
     * (Tour 14 audit, finding I2).</p>
     */
    Page<NuiteeDto> findByChambre(Long chambreId, Pageable pageable);

    /**
     * Tour 45 : liste les nuitees <b>provisoires</b> d'une reservation -
     * celles dont la {@code LigneFacture} parente n'est PAS dans un etat
     * terminal (PAYEE / ANNULEE), ou qui n'ont pas encore de ligne facture.
     *
     * <p>Retourne un DTO enrichi avec :</p>
     * <ul>
     *   <li>{@code prixOriginal} = {@code Nuitee.prixNuit} actuel ;</li>
     *   <li>{@code montantLigneFacture} = {@code LigneFacture.montantTtc} ;</li>
     *   <li>{@code ligneFactureId} et {@code operationCompteId} pour
     *       reutilisation cote PATCH ;</li>
     *   <li>{@code statutNuitee}, {@code statutFactureParente}.</li>
     * </ul>
     */
    List<NuiteeModificationDto> findProvisoiresByReservation(Long reservationId);

    /**
     * Tour 45 : mise a jour en lot des montants individuels des nuitees.
     *
     * <p>Pour chaque demande :</p>
     * <ol>
     *   <li>Verifier la nuitee existe (tenant filter auto).</li>
     *   <li>Si elle a une ligne facture, verifier le statut facture parente
     *       (refus si PAYEE ou ANNULEE - cle i18n
     *       {@code error.nuitee.facture.payee} / {@code error.nuitee.facture.annulee}).</li>
     *   <li>Recalculer {@code LigneFacture.montantHt/Ttc} en preservant
     *       {@code tauxTva}.</li>
     *   <li>Recalculer la facture parente (montants + restant).</li>
     *   <li>Si {@code operationCompteId} fourni, ajuster l'operation DEBIT
     *       de facon coherente (delta sur {@code Compte.soldeActuel} via
     *       une nouvelle operation d'ajustement DEBIT/CREDIT).</li>
     *   <li>Mettre a jour {@code Nuitee.prixNuit}.</li>
     * </ol>
     *
     * <p>Toutes les operations dans une seule transaction. En cas d'erreur,
     * rollback complet.</p>
     *
     * @return {@link NuiteesUpdateResultDto} avec {@code updatedCount} et
     *         {@code totalImpact} (somme algebrique des deltas).
     */
    NuiteesUpdateResultDto updateMontants(List<NuiteeMontantUpdateRequest> requests);
}
