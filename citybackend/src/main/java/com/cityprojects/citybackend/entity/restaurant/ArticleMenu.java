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
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;

/**
 * Article du menu restaurant (plat, boisson, dessert, ...).
 *
 * <p>Catalogue restaurant - Tour 23 (catalogue uniquement, POS reporte au
 * Tour 24+).</p>
 *
 * <p>Isolation tenant via {@code hotelId} annote {@link TenantId}.
 * {@code code_article} unique par hotel (UNIQUE (hotel_id, code_article)).</p>
 *
 * <h3>Pattern projet : pas de @ManyToOne sur categorieId</h3>
 * <p>{@code categorieId} est un {@code Long} (FK simple) plutot qu'une
 * {@code @ManyToOne CategorieMenu}, conformement aux Tours 11/16/19 :
 * <ul>
 *   <li>evite les proxies LAZY (et leurs LazyInitializationException) ;</li>
 *   <li>la coherence cross-tenant est garantie cote Hibernate (filtre
 *       @TenantId sur les deux entites).</li>
 * </ul>
 * </p>
 *
 * <h3>Champs metier</h3>
 * <ul>
 *   <li>{@code prix} : prix de vente, en MRU. CHECK SQL >= 0.</li>
 *   <li>{@code disponible} : indicateur "disponible aujourd'hui" (peut etre
 *       coupe ponctuellement par le service en cuisine).</li>
 *   <li>{@code actif} : present dans le catalogue (false = retire).</li>
 *   <li>{@code statut} : etat fin du cycle de vie ({@link StatutArticle}).</li>
 * </ul>
 */
@Entity
@Table(
        name = "articles_menus",
        schema = "restaurant",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_articles_menus_hotel_code",
                columnNames = {"hotel_id", "code_article"}))
public class ArticleMenu extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "article_id")
    private Long articleId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotBlank
    @Size(max = 30)
    @Column(name = "code_article", nullable = false, length = 30)
    private String codeArticle;

    @NotBlank
    @Size(max = 200)
    @Column(name = "nom", nullable = false, length = 200)
    private String nom;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @NotNull
    @Column(name = "categorie_id", nullable = false)
    private Long categorieId;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @Column(name = "prix", nullable = false, precision = 10, scale = 2)
    private BigDecimal prix = BigDecimal.ZERO;

    @Size(max = 200)
    @Column(name = "image_url", length = 200)
    private String imageUrl;

    @Column(name = "disponible", nullable = false)
    private Boolean disponible = Boolean.TRUE;

    @Column(name = "actif", nullable = false)
    private Boolean actif = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutArticle statut = StatutArticle.ACTIF;

    /** Constructeur JPA. */
    public ArticleMenu() {
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public String getCodeArticle() {
        return codeArticle;
    }

    public void setCodeArticle(String codeArticle) {
        this.codeArticle = codeArticle;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
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

    public BigDecimal getPrix() {
        return prix;
    }

    public void setPrix(BigDecimal prix) {
        this.prix = prix;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Boolean getDisponible() {
        return disponible;
    }

    public void setDisponible(Boolean disponible) {
        this.disponible = disponible;
    }

    public Boolean getActif() {
        return actif;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }

    public StatutArticle getStatut() {
        return statut;
    }

    public void setStatut(StatutArticle statut) {
        this.statut = statut;
    }
}
