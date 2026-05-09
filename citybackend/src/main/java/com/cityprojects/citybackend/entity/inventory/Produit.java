package com.cityprojects.citybackend.entity.inventory;

import com.cityprojects.citybackend.common.audit.AuditableEntity;
import com.cityprojects.citybackend.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;

/**
 * Produit manipule dans le stock de l'hotel (matiere premiere, fourniture, consommable).
 *
 * <p>Isolation tenant via {@code hotelId} annote {@link TenantId}. {@code code_produit}
 * unique par hotel ({@code UNIQUE (hotel_id, code_produit)}).</p>
 *
 * <h3>Stock courant</h3>
 * <p>{@code stockActuel} est la <b>source de verite</b> du niveau de stock courant.
 * Toute modification est doublee d'une ecriture dans {@code mouvements_stock}
 * (audit trail historique). Le {@code CHECK (stock_actuel &gt;= 0)} cote SQL empeche
 * un decrement excessif.</p>
 *
 * <h3>References cross-module</h3>
 * <ul>
 *   <li>{@code categorieId} : FK obligatoire vers {@code categories_produits}.</li>
 *   <li>{@code fournisseurPrincipalId} : FK optionnelle vers {@code fournisseurs}.</li>
 * </ul>
 *
 * <p><b>Methodes derivees</b> ({@link Transient}) : {@link #getValeurStock()},
 * {@link #isStockEnAlerte()}, {@link #isStockCritique()}, {@link #getStatutStock()}
 * sont calculees cote entite mais NON persistees. Pour des requetes sur ces
 * etats (ex. liste des produits en alerte), filtrer cote repository sur les
 * colonnes brutes {@code stock_actuel}/{@code seuil_alerte}/{@code seuil_critique}.</p>
 */
@Entity
@Table(
        name = "produits",
        schema = "inventory",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_produits_hotel_code",
                columnNames = {"hotel_id", "code_produit"}))
public class Produit extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "produit_id")
    private Long produitId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotBlank
    @Size(max = 20)
    @Column(name = "code_produit", nullable = false, length = 20)
    private String codeProduit;

    @NotBlank
    @Size(max = 255)
    @Column(name = "nom_produit", nullable = false, length = 255)
    private String nomProduit;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @NotNull
    @Column(name = "categorie_id", nullable = false)
    private Long categorieId;

    @NotBlank
    @Size(max = 20)
    @Column(name = "unite_mesure", nullable = false, length = 20)
    private String uniteMesure;

    @DecimalMin(value = "0.0", inclusive = true)
    @Column(name = "prix_unitaire", precision = 10, scale = 2)
    private BigDecimal prixUnitaire = BigDecimal.ZERO;

    @Min(0)
    @Column(name = "seuil_alerte", nullable = false)
    private Integer seuilAlerte = 0;

    @Min(0)
    @Column(name = "seuil_critique", nullable = false)
    private Integer seuilCritique = 0;

    @Min(0)
    @Column(name = "stock_actuel", nullable = false)
    private Integer stockActuel = 0;

    @Column(name = "fournisseur_principal_id")
    private Long fournisseurPrincipalId;

    @Column(name = "est_facturable", nullable = false)
    private Boolean estFacturable = Boolean.FALSE;

    @Column(name = "actif", nullable = false)
    private Boolean actif = Boolean.TRUE;

    /** Constructeur JPA. */
    public Produit() {
    }

    /**
     * Valeur monetaire estimee du stock courant : {@code stockActuel * prixUnitaire}.
     * Calcul cote app, NON persiste.
     */
    @Transient
    public BigDecimal getValeurStock() {
        if (stockActuel == null || prixUnitaire == null) {
            return BigDecimal.ZERO;
        }
        return prixUnitaire.multiply(BigDecimal.valueOf(stockActuel));
    }

    /**
     * Vrai si {@code stockActuel <= seuilCritique}. Le seuil critique est plus
     * agressif que le seuil d'alerte (declenche un reapprovisionnement urgent).
     */
    @Transient
    public boolean isStockCritique() {
        return stockActuel != null && seuilCritique != null && stockActuel <= seuilCritique;
    }

    /**
     * Vrai si {@code stockActuel <= seuilAlerte} (declenche une notification de
     * reapprovisionnement). Implique typiquement aussi {@link #isStockCritique()}.
     */
    @Transient
    public boolean isStockEnAlerte() {
        return stockActuel != null && seuilAlerte != null && stockActuel <= seuilAlerte;
    }

    /** {@code "CRITIQUE"} | {@code "ALERTE"} | {@code "NORMAL"} (calcul derive). */
    @Transient
    public String getStatutStock() {
        if (isStockCritique()) {
            return "CRITIQUE";
        }
        if (isStockEnAlerte()) {
            return "ALERTE";
        }
        return "NORMAL";
    }

    public Long getProduitId() {
        return produitId;
    }

    public void setProduitId(Long produitId) {
        this.produitId = produitId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public String getCodeProduit() {
        return codeProduit;
    }

    public void setCodeProduit(String codeProduit) {
        this.codeProduit = codeProduit;
    }

    public String getNomProduit() {
        return nomProduit;
    }

    public void setNomProduit(String nomProduit) {
        this.nomProduit = nomProduit;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getCategorieId() {
        return categorieId;
    }

    public void setCategorieId(Long categorieId) {
        this.categorieId = categorieId;
    }

    public String getUniteMesure() {
        return uniteMesure;
    }

    public void setUniteMesure(String uniteMesure) {
        this.uniteMesure = uniteMesure;
    }

    public BigDecimal getPrixUnitaire() {
        return prixUnitaire;
    }

    public void setPrixUnitaire(BigDecimal prixUnitaire) {
        this.prixUnitaire = prixUnitaire;
    }

    public Integer getSeuilAlerte() {
        return seuilAlerte;
    }

    public void setSeuilAlerte(Integer seuilAlerte) {
        this.seuilAlerte = seuilAlerte;
    }

    public Integer getSeuilCritique() {
        return seuilCritique;
    }

    public void setSeuilCritique(Integer seuilCritique) {
        this.seuilCritique = seuilCritique;
    }

    public Integer getStockActuel() {
        return stockActuel;
    }

    public void setStockActuel(Integer stockActuel) {
        this.stockActuel = stockActuel;
    }

    public Long getFournisseurPrincipalId() {
        return fournisseurPrincipalId;
    }

    public void setFournisseurPrincipalId(Long fournisseurPrincipalId) {
        this.fournisseurPrincipalId = fournisseurPrincipalId;
    }

    public Boolean getEstFacturable() {
        return estFacturable;
    }

    public void setEstFacturable(Boolean estFacturable) {
        this.estFacturable = estFacturable;
    }

    public Boolean getActif() {
        return actif;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }
}
