package com.cityprojects.citybackend.entity.inventory;

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
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;

/**
 * Audit trail d'un mouvement de stock (entree, sortie, ajustement, perte).
 *
 * <p>Une ligne est creee pour chaque modification de {@code Produit.stockActuel} :
 * la double ecriture (Produit + MouvementStock) doit toujours etre faite dans la
 * meme transaction par le service appelant (cf. {@link com.cityprojects.citybackend.service.inventory.MouvementStockService}).</p>
 *
 * <h3>Reference document</h3>
 * <p>{@code referenceDocument} pointe optionnellement vers le numero du document
 * source (BC-2026-MR-000123, BS-2026-MR-000456, etc.) pour permettre la tracabilite
 * cross-table (sans FK SQL pour rester portable et permettre les ajustements manuels).</p>
 */
@Entity
@Table(name = "mouvements_stock", schema = "inventory")
public class MouvementStock extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mouvement_id")
    private Long mouvementId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @Column(name = "produit_id", nullable = false)
    private Long produitId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_mouvement", nullable = false, length = 20)
    private TypeMouvementStock typeMouvement;

    @NotNull
    @Column(name = "quantite", nullable = false)
    private Integer quantite;

    @Column(name = "prix_unitaire", precision = 10, scale = 2)
    private BigDecimal prixUnitaire;

    @NotNull
    @Column(name = "stock_avant", nullable = false)
    private Integer stockAvant;

    @NotNull
    @Column(name = "stock_apres", nullable = false)
    private Integer stockApres;

    @Size(max = 50)
    @Column(name = "reference_document", length = 50)
    private String referenceDocument;

    @Column(name = "commentaire", columnDefinition = "TEXT")
    private String commentaire;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Constructeur JPA. */
    public MouvementStock() {
    }

    public Long getMouvementId() {
        return mouvementId;
    }

    public void setMouvementId(Long mouvementId) {
        this.mouvementId = mouvementId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public Long getProduitId() {
        return produitId;
    }

    public void setProduitId(Long produitId) {
        this.produitId = produitId;
    }

    public TypeMouvementStock getTypeMouvement() {
        return typeMouvement;
    }

    public void setTypeMouvement(TypeMouvementStock typeMouvement) {
        this.typeMouvement = typeMouvement;
    }

    public Integer getQuantite() {
        return quantite;
    }

    public void setQuantite(Integer quantite) {
        this.quantite = quantite;
    }

    public BigDecimal getPrixUnitaire() {
        return prixUnitaire;
    }

    public void setPrixUnitaire(BigDecimal prixUnitaire) {
        this.prixUnitaire = prixUnitaire;
    }

    public Integer getStockAvant() {
        return stockAvant;
    }

    public void setStockAvant(Integer stockAvant) {
        this.stockAvant = stockAvant;
    }

    public Integer getStockApres() {
        return stockApres;
    }

    public void setStockApres(Integer stockApres) {
        this.stockApres = stockApres;
    }

    public String getReferenceDocument() {
        return referenceDocument;
    }

    public void setReferenceDocument(String referenceDocument) {
        this.referenceDocument = referenceDocument;
    }

    public String getCommentaire() {
        return commentaire;
    }

    public void setCommentaire(String commentaire) {
        this.commentaire = commentaire;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
