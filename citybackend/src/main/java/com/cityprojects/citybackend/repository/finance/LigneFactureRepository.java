package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.entity.finance.LigneFacture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository des lignes de facture.
 *
 * <p>Pas de {@code @TenantId} sur LigneFacture (l'isolation est portee par la
 * facture parent). Le service doit toujours valider que {@code facture.hotelId}
 * correspond au tenant courant avant de manipuler les lignes.</p>
 */
@Repository
public interface LigneFactureRepository extends JpaRepository<LigneFacture, Long> {

    List<LigneFacture> findByFactureIdOrderByLigneFactureIdAsc(Long factureId);

    /** Supprime toutes les lignes d'une facture (utilise avant recalcul). */
    void deleteByFactureId(Long factureId);

    /** Existe-t-il une ligne deja liee a cette nuitee ? Pour idempotence. */
    boolean existsByNuiteeId(Long nuiteeId);
}
