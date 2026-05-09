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
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.TenantId;

import java.time.Instant;

/**
 * Trace d'impression d'un ticket POS (Tour 24).
 *
 * <p>Un ticket est emis pour :</p>
 * <ul>
 *   <li>{@link TypeTicket#CAISSE} : preuve d'achat client (avec prix).</li>
 *   <li>{@link TypeTicket#CUISINE} : bon cuisine pour le passe (sans prix).</li>
 *   <li>{@link TypeTicket#REIMPRESSION} : duplicata (motif obligatoire).</li>
 * </ul>
 *
 * <p>L'entite ne stocke pas le PDF/PNG du ticket lui-meme : le rendu est
 * delegue au front (ou a un futur service d'impression). Elle ne garde que la
 * trace : qui a imprime quoi, quand, et pourquoi (cas reimpression).</p>
 */
@Entity
@Table(name = "tickets", schema = "restaurant")
public class Ticket extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Long ticketId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @Column(name = "commande_id", nullable = false)
    private Long commandeId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_ticket", nullable = false, length = 20)
    private TypeTicket typeTicket;

    @NotNull
    @Column(name = "date_impression", nullable = false)
    private Instant dateImpression;

    /** Utilisateur (DBUser) ayant declenche l'impression. */
    @Column(name = "imprime_par_user_id")
    private Long imprimeParUserId;

    /** Motif obligatoire si {@code typeTicket = REIMPRESSION}. */
    @Column(name = "motif_reimpression", columnDefinition = "TEXT")
    private String motifReimpression;

    /** Constructeur JPA. */
    public Ticket() {
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public Long getCommandeId() {
        return commandeId;
    }

    public void setCommandeId(Long commandeId) {
        this.commandeId = commandeId;
    }

    public TypeTicket getTypeTicket() {
        return typeTicket;
    }

    public void setTypeTicket(TypeTicket typeTicket) {
        this.typeTicket = typeTicket;
    }

    public Instant getDateImpression() {
        return dateImpression;
    }

    public void setDateImpression(Instant dateImpression) {
        this.dateImpression = dateImpression;
    }

    public Long getImprimeParUserId() {
        return imprimeParUserId;
    }

    public void setImprimeParUserId(Long imprimeParUserId) {
        this.imprimeParUserId = imprimeParUserId;
    }

    public String getMotifReimpression() {
        return motifReimpression;
    }

    public void setMotifReimpression(String motifReimpression) {
        this.motifReimpression = motifReimpression;
    }
}
