package com.cityprojects.citybackend.entity.finance;

import com.cityprojects.citybackend.common.audit.AuditableEntity;
import com.cityprojects.citybackend.common.tenant.TenantAware;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Ecriture comptable (en-tete) au sein d'un journal.
 *
 * <h3>Partie double</h3>
 * <p>Une ecriture est composee d'au moins 2 lignes (cf. {@link LigneEcriture})
 * dont la somme des debits doit egaler la somme des credits. La validation
 * structurelle est portee cote service
 * ({@link com.cityprojects.citybackend.service.finance.EcritureComptableService})
 * et renforcee cote base par un trigger PL/pgSQL en Postgres (defense en
 * profondeur).</p>
 *
 * <h3>Numerotation</h3>
 * <p>{@code numero} est genere par
 * {@link com.cityprojects.citybackend.service.finance.NumerotationService}
 * (type {@link com.cityprojects.citybackend.service.finance.TypeNumerotation#JRN},
 * discriminant = code du journal). Format :
 * {@code JRN-VTE-2026-MR-000123} - sequence par hotel x journal x exercice.
 * Unicite garantie par {@code UNIQUE (hotel_id, numero)}.</p>
 *
 * <h3>Cycle de vie</h3>
 * <pre>
 *   BROUILLON  --validation-->  VALIDEE  --contre-passation-->  CONTRE_PASSEE
 * </pre>
 * Cf. {@link StatutEcriture}.
 *
 * <h3>Contre-passation</h3>
 * <p>Annuler une ecriture VALIDEE = creer une nouvelle ecriture avec lignes
 * inversees (D devient C, C devient D), <b>meme</b> journal, <b>meme</b>
 * exercice, date = aujourd'hui. Les deux ecritures sont liees :</p>
 * <ul>
 *   <li>l'originale passe en {@link StatutEcriture#CONTRE_PASSEE} et porte
 *       {@code contrePasseeParId} = id de la nouvelle ;</li>
 *   <li>la nouvelle reste {@link StatutEcriture#VALIDEE} et porte
 *       {@code ecritureSourceId} = id de l'originale.</li>
 * </ul>
 *
 * <h3>Totaux denormalises</h3>
 * <p>{@code totalDebit} et {@code totalCredit} sont recalcules automatiquement
 * a chaque persist/update via les hooks JPA {@link PrePersist}/{@link PreUpdate}.
 * Stockes pour eviter une agregation a chaque consultation (perf - liste des
 * ecritures, balance, etc.).</p>
 */
@Entity
@Table(
        name = "ecriture_comptable",
        schema = "finance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ecriture_comptable_hotel_numero",
                columnNames = {"hotel_id", "numero"}))
public class EcritureComptable extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;

    @NotBlank
    @Size(max = 30)
    @Column(name = "numero", nullable = false, length = 30)
    private String numero;

    @NotNull
    @Column(name = "date_comptable", nullable = false)
    private LocalDate dateComptable;

    @NotNull
    @Column(name = "date_piece", nullable = false)
    private LocalDate datePiece;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_id", nullable = false)
    private JournalComptable journal;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercice_id", nullable = false)
    private Exercice exercice;

    @NotBlank
    @Size(max = 500)
    @Column(name = "libelle", nullable = false, length = 500)
    private String libelle;

    /** Reference de la piece source (ex. numero de facture / paiement). */
    @Size(max = 50)
    @Column(name = "reference", length = 50)
    private String reference;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutEcriture statut = StatutEcriture.BROUILLON;

    /**
     * Id de l'ecriture qui contre-passe celle-ci. Non null quand le statut
     * vaut {@link StatutEcriture#CONTRE_PASSEE}.
     */
    @Column(name = "contre_passee_par_id")
    private Long contrePasseeParId;

    /**
     * Si cette ecriture est elle-meme une contre-passation, id de l'ecriture
     * source qu'elle annule. Reciproque de {@code contrePasseeParId}.
     */
    @Column(name = "ecriture_source_id")
    private Long ecritureSourceId;

    @OneToMany(mappedBy = "ecriture", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LigneEcriture> lignes = new ArrayList<>();

    @NotNull
    @Column(name = "total_debit", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @NotNull
    @Column(name = "total_credit", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCredit = BigDecimal.ZERO;

    /** Constructeur JPA. */
    public EcritureComptable() {
    }

    /**
     * Recalcule {@code totalDebit} / {@code totalCredit} comme somme des
     * montants des lignes par sens. Pas de validation de l'equilibre ici -
     * la regle "D = C" est portee par le service avant passage en VALIDEE
     * (ce hook tourne aussi pour les BROUILLON ou D peut differer de C
     * temporairement).
     */
    @PrePersist
    @PreUpdate
    void recalc() {
        BigDecimal d = BigDecimal.ZERO;
        BigDecimal c = BigDecimal.ZERO;
        if (lignes != null) {
            for (LigneEcriture l : lignes) {
                if (l == null || l.getMontant() == null || l.getSens() == null) {
                    continue;
                }
                if (l.getSens() == SensLigne.DEBIT) {
                    d = d.add(l.getMontant());
                } else if (l.getSens() == SensLigne.CREDIT) {
                    c = c.add(l.getMontant());
                }
            }
        }
        this.totalDebit = d.setScale(2, RoundingMode.HALF_UP);
        this.totalCredit = c.setScale(2, RoundingMode.HALF_UP);
    }

    /** Helper : ajoute une ligne et synchronise la relation bidirectionnelle. */
    public void addLigne(LigneEcriture ligne) {
        if (ligne == null) {
            return;
        }
        ligne.setEcriture(this);
        this.lignes.add(ligne);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public Long getHotelId() {
        return hotelId;
    }

    @Override
    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public LocalDate getDateComptable() {
        return dateComptable;
    }

    public void setDateComptable(LocalDate dateComptable) {
        this.dateComptable = dateComptable;
    }

    public LocalDate getDatePiece() {
        return datePiece;
    }

    public void setDatePiece(LocalDate datePiece) {
        this.datePiece = datePiece;
    }

    public JournalComptable getJournal() {
        return journal;
    }

    public void setJournal(JournalComptable journal) {
        this.journal = journal;
    }

    public Exercice getExercice() {
        return exercice;
    }

    public void setExercice(Exercice exercice) {
        this.exercice = exercice;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public StatutEcriture getStatut() {
        return statut;
    }

    public void setStatut(StatutEcriture statut) {
        this.statut = statut;
    }

    public Long getContrePasseeParId() {
        return contrePasseeParId;
    }

    public void setContrePasseeParId(Long contrePasseeParId) {
        this.contrePasseeParId = contrePasseeParId;
    }

    public Long getEcritureSourceId() {
        return ecritureSourceId;
    }

    public void setEcritureSourceId(Long ecritureSourceId) {
        this.ecritureSourceId = ecritureSourceId;
    }

    public List<LigneEcriture> getLignes() {
        return lignes;
    }

    public void setLignes(List<LigneEcriture> lignes) {
        this.lignes = (lignes != null) ? lignes : new ArrayList<>();
    }

    public BigDecimal getTotalDebit() {
        return totalDebit;
    }

    public void setTotalDebit(BigDecimal totalDebit) {
        this.totalDebit = totalDebit;
    }

    public BigDecimal getTotalCredit() {
        return totalCredit;
    }

    public void setTotalCredit(BigDecimal totalCredit) {
        this.totalCredit = totalCredit;
    }
}
