package com.cityprojects.citybackend.entity.client;

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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.TenantId;

import java.time.LocalDate;

/**
 * Client (personne physique) d'un hotel.
 * <p>
 * Peut etre rattache optionnellement a une {@link Societe} (B2B). Isolation
 * tenant via {@code hotelId} annote {@link TenantId}.
 *
 * <h3>Numero client</h3>
 * <p>{@code numero_client} est genere par hotel via
 * {@link com.cityprojects.citybackend.service.finance.NumerotationService}
 * (type {@link com.cityprojects.citybackend.service.finance.TypeNumerotation#CLI}).
 * Format : {@code CLI-{exercice}-{codePays}-{6 chiffres}}, ex. {@code CLI-2026-MR-000123}.
 * Unicite garantie par hotel via {@code UNIQUE (hotel_id, numero_client)}
 * (contrainte {@code uk_clients_hotel_numero}).</p>
 *
 * <h3>References referentielles</h3>
 * <p>{@code nationaliteId} et {@code typeIdentificationId} sont des cles
 * vers {@code core.donnees_referentielles} (table non encore creee : la FK
 * sera ajoutee au tour de creation de cette table). Pour l'instant ces
 * champs sont des {@link Long} sans relation JPA.</p>
 *
 * <p><b>Anti-patterns importants</b> (cf. CLAUDE.md racine §10) :</p>
 * <ul>
 *   <li>NE JAMAIS appeler {@code setHotelId(...)} dans un service metier.</li>
 *   <li>Pas de {@code @ManyToOne} eager vers {@link Societe} : on stocke
 *       uniquement {@code societeId}, les jointures sont faites a la demande
 *       via le repository.</li>
 * </ul>
 */
@Entity
@Table(
        name = "clients",
        schema = "client",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_clients_hotel_numero",
                columnNames = {"hotel_id", "numero_client"}))
public class Client extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "client_id")
    private Long clientId;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    // @NotNull et non @NotBlank : numeroClient est genere par NumerotationService
    // dans ClientServiceImpl.create() apres mapper.toEntity() et avant save() ;
    // @NotBlank refuserait l'instance pendant la fenetre transitoire ou la
    // valeur n'est pas encore positionnee.
    @NotNull
    @Column(name = "numero_client", nullable = false, length = 40)
    private String numeroClient;

    @NotBlank
    @Column(name = "prenom", nullable = false, length = 100)
    private String prenom;

    @NotBlank
    @Column(name = "nom", nullable = false, length = 100)
    private String nom;

    @Column(name = "nationalite_id")
    private Long nationaliteId;

    @Column(name = "telephone", length = 20)
    private String telephone;

    @Email
    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "adresse", columnDefinition = "TEXT")
    private String adresse;

    @Column(name = "ville", length = 100)
    private String ville;

    @Column(name = "pays", length = 100)
    private String pays;

    @Column(name = "type_identification_id")
    private Long typeIdentificationId;

    @Column(name = "numero_identification", length = 50)
    private String numeroIdentification;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(name = "societe_id")
    private Long societeId;

    @Column(name = "actif", nullable = false)
    private Boolean actif = Boolean.TRUE;

    /** Constructeur JPA. */
    public Client() {
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public String getNumeroClient() {
        return numeroClient;
    }

    public void setNumeroClient(String numeroClient) {
        this.numeroClient = numeroClient;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public Long getNationaliteId() {
        return nationaliteId;
    }

    public void setNationaliteId(Long nationaliteId) {
        this.nationaliteId = nationaliteId;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAdresse() {
        return adresse;
    }

    public void setAdresse(String adresse) {
        this.adresse = adresse;
    }

    public String getVille() {
        return ville;
    }

    public void setVille(String ville) {
        this.ville = ville;
    }

    public String getPays() {
        return pays;
    }

    public void setPays(String pays) {
        this.pays = pays;
    }

    public Long getTypeIdentificationId() {
        return typeIdentificationId;
    }

    public void setTypeIdentificationId(Long typeIdentificationId) {
        this.typeIdentificationId = typeIdentificationId;
    }

    public String getNumeroIdentification() {
        return numeroIdentification;
    }

    public void setNumeroIdentification(String numeroIdentification) {
        this.numeroIdentification = numeroIdentification;
    }

    public LocalDate getDateNaissance() {
        return dateNaissance;
    }

    public void setDateNaissance(LocalDate dateNaissance) {
        this.dateNaissance = dateNaissance;
    }

    public Long getSocieteId() {
        return societeId;
    }

    public void setSocieteId(Long societeId) {
        this.societeId = societeId;
    }

    public Boolean getActif() {
        return actif;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }

    /**
     * Retourne le nom complet "Prenom Nom" (derive, non persiste).
     * <p>
     * Annote {@link Transient} pour documenter explicitement l'intention
     * defensive : Hibernate ignore deja les getters sans champ correspondant
     * en mode {@code AccessType.FIELD}, mais l'annotation explicite previent
     * les surprises avec les outils (introspection, JPA metamodel) et un
     * eventuel passage en {@code AccessType.PROPERTY}.
     */
    @Transient
    public String getNomComplet() {
        if (prenom == null && nom == null) {
            return "";
        }
        if (prenom == null) {
            return nom;
        }
        if (nom == null) {
            return prenom;
        }
        return prenom + " " + nom;
    }
}
