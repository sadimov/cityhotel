package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.entity.finance.TauxTvaConfig;
import com.cityprojects.citybackend.entity.finance.TypeServiceTva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository du paramétrage TVA par hotel (B4).
 *
 * <p>Tenant-aware via {@code @TenantId} sur {@link TauxTvaConfig} : Hibernate
 * ajoute automatiquement {@code WHERE hotel_id = ?}.</p>
 */
@Repository
public interface TauxTvaConfigRepository extends JpaRepository<TauxTvaConfig, Long> {

    /**
     * Récupère la configuration TVA pour un {@link TypeServiceTva} donné
     * (du tenant courant). Renvoie {@link Optional#empty()} si non
     * configuré : le service applique alors le défaut codé.
     */
    Optional<TauxTvaConfig> findByTypeService(TypeServiceTva typeService);

    /** Liste les configurations du tenant courant, tri stable par type. */
    List<TauxTvaConfig> findAllByOrderByTypeServiceAsc();
}
