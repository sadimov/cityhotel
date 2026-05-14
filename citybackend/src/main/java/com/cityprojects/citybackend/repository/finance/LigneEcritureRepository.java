package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.entity.finance.LigneEcriture;
import com.cityprojects.citybackend.entity.finance.SensLigne;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Repository des lignes d'ecriture.
 *
 * <p>Tenant-aware via {@code @TenantId} sur {@link LigneEcriture}. Les
 * requetes ci-dessous sont filtrees automatiquement par
 * {@code WHERE hotel_id = ?}.</p>
 */
@Repository
public interface LigneEcritureRepository extends JpaRepository<LigneEcriture, Long> {

    /**
     * Lignes d'un compte sur une plage de dates comptables (bornes incluses).
     * La date est portee par l'ecriture parente, d'ou la jointure.
     * Utilise pour le grand livre detaille.
     */
    @Query("SELECT l FROM LigneEcriture l "
            + "WHERE l.compteCode = :compteCode "
            + "AND l.ecriture.dateComptable BETWEEN :dateDebut AND :dateFin "
            + "ORDER BY l.ecriture.dateComptable ASC, l.ecriture.id ASC, l.ordre ASC")
    List<LigneEcriture> findByCompteCodeAndDateBetween(
            @Param("compteCode") String compteCode,
            @Param("dateDebut") LocalDate dateDebut,
            @Param("dateFin") LocalDate dateFin);

    /**
     * Somme des montants d'un compte sur une plage de dates, par sens
     * (DEBIT ou CREDIT). Utilise pour le calcul du solde et de la balance.
     * Renvoie {@code BigDecimal.ZERO} si aucune ligne ne matche (via
     * {@code COALESCE}).
     */
    @Query("SELECT COALESCE(SUM(l.montant), 0) FROM LigneEcriture l "
            + "WHERE l.compteCode = :compteCode "
            + "AND l.ecriture.dateComptable BETWEEN :dateDebut AND :dateFin "
            + "AND l.sens = :sens")
    BigDecimal sumByCompteCodeAndDateBetween(
            @Param("compteCode") String compteCode,
            @Param("dateDebut") LocalDate dateDebut,
            @Param("dateFin") LocalDate dateFin,
            @Param("sens") SensLigne sens);

    /**
     * Codes de compte distincts ayant au moins une ligne d'ecriture sur la
     * plage de dates donnee. Utilise par la balance, le grand livre et le
     * bilan / compte de resultat (B5) pour ne lister que les comptes
     * effectivement mouvementes.
     *
     * <p>Filtre tenant applique automatiquement via {@code @TenantId}.</p>
     */
    @Query("SELECT DISTINCT l.compteCode FROM LigneEcriture l "
            + "WHERE l.ecriture.dateComptable BETWEEN :dateDebut AND :dateFin "
            + "ORDER BY l.compteCode ASC")
    List<String> findDistinctCompteCodesByDateBetween(
            @Param("dateDebut") LocalDate dateDebut,
            @Param("dateFin") LocalDate dateFin);

    /**
     * Variante par prefixe de compte (classe SYSCOHADA). Le prefixe est
     * applique en LIKE (ex. "6%" pour la classe 6).
     */
    @Query("SELECT DISTINCT l.compteCode FROM LigneEcriture l "
            + "WHERE l.ecriture.dateComptable BETWEEN :dateDebut AND :dateFin "
            + "AND l.compteCode LIKE :prefixe "
            + "ORDER BY l.compteCode ASC")
    List<String> findDistinctCompteCodesByDateBetweenAndPrefixe(
            @Param("dateDebut") LocalDate dateDebut,
            @Param("dateFin") LocalDate dateFin,
            @Param("prefixe") String prefixe);
}
