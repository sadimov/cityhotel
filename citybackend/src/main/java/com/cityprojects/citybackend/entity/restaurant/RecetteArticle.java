package com.cityprojects.citybackend.entity.restaurant;

import com.cityprojects.citybackend.common.audit.AuditableEntity;
import com.cityprojects.citybackend.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;

/**
 * Recette d'un {@link ArticleMenu} : association 1-N article -&gt; produits
 * d'inventaire avec quantite par unite vendue (Tour 25).
 *
 * <p>Sert a generer automatiquement un BS (Bon de Sortie) inventory a la
 * transition {@code PRETE -&gt; SERVIE} d'une commande pour decrementer le
 * stock des ingredients consommes.</p>
 *
 * <h3>Exemple</h3>
 * <pre>
 *   Article "Riz au poisson" (article_id=42) :
 *     - 0.150 kg de riz (produit_id=10)
 *     - 0.200 kg de poisson (produit_id=11)
 *     - 0.050 L d'huile (produit_id=12)
 *   Pour 3 plats commandes : 3 * 0.150 = 0.450 kg de riz consommes.
 * </pre>
 *
 * <h3>Pas de @ManyToOne</h3>
 * <p>{@code articleId} et {@code produitId} restent en {@code Long} (FK simple)
 * conformement au pattern projet (Tours 11/16/19/23) : evite les proxies LAZY,
 * coherence cross-tenant garantie cote Hibernate via {@code @TenantId}.</p>
 *
 * <h3>Unicite</h3>
 * <p>{@code UNIQUE (article_id, produit_id)} : un meme produit ne peut figurer
 * qu'une fois dans la recette d'un article. Pour modifier la quantite, on met
 * a jour la ligne existante (pas d'insert d'un doublon).</p>
 */
@Entity
@Table(
        name = "recettes_articles",
        schema = "restaurant",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_recettes_articles_article_produit",
                columnNames = {"article_id", "produit_id"}))
public class RecetteArticle extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recette_id")
    private Long recetteId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotNull
    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @NotNull
    @Column(name = "produit_id", nullable = false)
    private Long produitId;

    /**
     * Quantite consommee pour 1 unite vendue de l'article.
     * Ex. 0.1500 (kg) pour 0.150 kg de riz par plat.
     * Precision 10,4 : permet des recettes fines (ml, mg) sans imposer Integer.
     */
    @NotNull
    @DecimalMin(value = "0.0001", message = "error.recetteArticle.quantite.tooSmall")
    @Column(name = "quantite_par_unite", nullable = false, precision = 10, scale = 4)
    private BigDecimal quantiteParUnite;

    /**
     * Unite informative (kg, L, piece, ...). L'unite reelle vient du
     * {@code Produit.unite} en base ; ce champ est uniquement pour clarifier
     * la recette en lecture (UI).
     */
    @Size(max = 20)
    @Column(name = "unite", length = 20)
    private String unite;

    /** Note libre (ex. "reduction si surcuisson", "facultatif si rupture"). */
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @NotNull
    @Column(name = "actif", nullable = false)
    private Boolean actif = Boolean.TRUE;

    /** Constructeur JPA. */
    public RecetteArticle() {
    }

    public Long getRecetteId() {
        return recetteId;
    }

    public void setRecetteId(Long recetteId) {
        this.recetteId = recetteId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public Long getProduitId() {
        return produitId;
    }

    public void setProduitId(Long produitId) {
        this.produitId = produitId;
    }

    public BigDecimal getQuantiteParUnite() {
        return quantiteParUnite;
    }

    public void setQuantiteParUnite(BigDecimal quantiteParUnite) {
        this.quantiteParUnite = quantiteParUnite;
    }

    public String getUnite() {
        return unite;
    }

    public void setUnite(String unite) {
        this.unite = unite;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Boolean getActif() {
        return actif;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }
}
