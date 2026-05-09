package com.cityprojects.citybackend.entity.restaurant;

import com.cityprojects.citybackend.common.audit.AuditableEntity;
import com.cityprojects.citybackend.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Commande POS restaurant (Tour 24).
 *
 * <h3>Numerotation</h3>
 * <p>{@code numero_commande} est genere par hotel via
 * {@link com.cityprojects.citybackend.service.finance.NumerotationService}
 * (type {@link com.cityprojects.citybackend.service.finance.TypeNumerotation#COMM}).
 * Format : {@code COMM-{exercice}-{codePays}-{6 chiffres}}, ex.
 * {@code COMM-2026-MR-000001}. Unicite garantie par hotel via
 * {@code UNIQUE (hotel_id, numero_commande)}.</p>
 *
 * <h3>FK cross-module (toutes en {@code Long}, pattern projet)</h3>
 * <ul>
 *   <li>{@code clientId} : FK optionnelle vers {@code client.clients} (POS walk-in :
 *       client anonyme accepte, donc nullable).</li>
 *   <li>{@code reservationId} : FK optionnelle vers {@code hebergement.reservations}.
 *       Renseignee uniquement si {@code modeReglement = REPORTE_CHAMBRE}.</li>
 *   <li>{@code factureId} : FK optionnelle vers {@code finance.factures}.
 *       Renseignee a l'encaissement comptant (cf.
 *       {@code CommandeService.encaisserComptant()}).</li>
 * </ul>
 *
 * <h3>Mode de reglement vs statut</h3>
 * <p>{@link ModeReglementCommande} decrit la <i>nature</i> du reglement
 * (COMPTANT / REPORTE_CHAMBRE) et est positionne a la creation. Il ne change pas
 * pendant le cycle de vie. Le {@link StatutCommande} decrit l'etat de
 * preparation/livraison (BROUILLON, VALIDEE, EN_PREPARATION, PRETE, SERVIE,
 * ANNULEE).</p>
 *
 * <h3>Pas de TVA dans la version POS</h3>
 * <p>Conforme a la doctrine {@code prompt_restaurant_pos.txt} :
 * {@code montant_ht == montant_ttc}, taux_tva = 0 sur les lignes de facture
 * generees a l'encaissement.</p>
 */
@Entity
@Table(
        name = "commandes",
        schema = "restaurant",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_commandes_hotel_numero",
                columnNames = {"hotel_id", "numero_commande"}))
public class Commande extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "commande_id")
    private Long commandeId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    /**
     * Numero metier genere par NumerotationService (COMM-...). @NotNull et non
     * @NotBlank : valeur posee apres save() initial - cf. pattern Reservation.
     */
    @NotNull
    @Size(max = 40)
    @Column(name = "numero_commande", nullable = false, length = 40, updatable = false)
    private String numeroCommande;

    /** FK optionnelle vers client.clients (POS walk-in possible : client anonyme). */
    @Column(name = "client_id")
    private Long clientId;

    /**
     * FK optionnelle vers hebergement.reservations.
     * Non null UNIQUEMENT si {@link #modeReglement} = {@link ModeReglementCommande#REPORTE_CHAMBRE}.
     */
    @Column(name = "reservation_id")
    private Long reservationId;

    /**
     * FK optionnelle vers finance.factures.
     * Renseignee a l'encaissement comptant. Reste null pour les commandes
     * REPORTE_CHAMBRE jusqu'au check-out de la reservation.
     */
    @Column(name = "facture_id")
    private Long factureId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "mode_reglement", nullable = false, length = 30)
    private ModeReglementCommande modeReglement;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_commande", nullable = false, length = 30)
    private StatutCommande statut = StatutCommande.BROUILLON;

    @NotNull
    @Column(name = "montant_ht", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantHt = BigDecimal.ZERO;

    @NotNull
    @Column(name = "montant_ttc", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantTtc = BigDecimal.ZERO;

    @NotNull
    @Column(name = "montant_paye", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantPaye = BigDecimal.ZERO;

    @NotNull
    @Size(max = 3)
    @Column(name = "devise", nullable = false, length = 3)
    private String devise = "MRU";

    @NotNull
    @Column(name = "date_commande", nullable = false)
    private Instant dateCommande;

    /** Motif d'annulation - obligatoire si statut = ANNULEE. */
    @Column(name = "motif_annulation", columnDefinition = "TEXT")
    private String motifAnnulation;

    /**
     * Numero de table physique sur laquelle est prise la commande (Tour 26.1).
     * Optionnel - null pour commandes a emporter / livraison / sans table.
     * Decision arbitree (1=A) : simple chaine, pas d'entite Table dediee.
     * Ex. "T12", "Salon-3", "Terrasse-B".
     */
    @Size(max = 20)
    @Column(name = "numero_table", length = 20)
    private String numeroTable;

    /** Constructeur JPA. */
    public Commande() {
    }

    public Long getCommandeId() {
        return commandeId;
    }

    public void setCommandeId(Long commandeId) {
        this.commandeId = commandeId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public String getNumeroCommande() {
        return numeroCommande;
    }

    public void setNumeroCommande(String numeroCommande) {
        this.numeroCommande = numeroCommande;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public Long getFactureId() {
        return factureId;
    }

    public void setFactureId(Long factureId) {
        this.factureId = factureId;
    }

    public ModeReglementCommande getModeReglement() {
        return modeReglement;
    }

    public void setModeReglement(ModeReglementCommande modeReglement) {
        this.modeReglement = modeReglement;
    }

    public StatutCommande getStatut() {
        return statut;
    }

    public void setStatut(StatutCommande statut) {
        this.statut = statut;
    }

    public BigDecimal getMontantHt() {
        return montantHt;
    }

    public void setMontantHt(BigDecimal montantHt) {
        this.montantHt = montantHt;
    }

    public BigDecimal getMontantTtc() {
        return montantTtc;
    }

    public void setMontantTtc(BigDecimal montantTtc) {
        this.montantTtc = montantTtc;
    }

    public BigDecimal getMontantPaye() {
        return montantPaye;
    }

    public void setMontantPaye(BigDecimal montantPaye) {
        this.montantPaye = montantPaye;
    }

    public String getDevise() {
        return devise;
    }

    public void setDevise(String devise) {
        this.devise = devise;
    }

    public Instant getDateCommande() {
        return dateCommande;
    }

    public void setDateCommande(Instant dateCommande) {
        this.dateCommande = dateCommande;
    }

    public String getMotifAnnulation() {
        return motifAnnulation;
    }

    public void setMotifAnnulation(String motifAnnulation) {
        this.motifAnnulation = motifAnnulation;
    }

    public String getNumeroTable() {
        return numeroTable;
    }

    public void setNumeroTable(String numeroTable) {
        this.numeroTable = numeroTable;
    }
}
