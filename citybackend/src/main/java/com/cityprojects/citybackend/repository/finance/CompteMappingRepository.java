package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.entity.finance.CompteMapping;
import com.cityprojects.citybackend.entity.finance.TypeEvenementComptable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository du mapping comptable par hotel.
 *
 * <p>Tenant-aware : Hibernate ajoute automatiquement le filtre
 * {@code WHERE hotel_id = ?} a toutes les requetes via {@code @TenantId} sur
 * l'entite {@link CompteMapping}.</p>
 */
@Repository
public interface CompteMappingRepository extends JpaRepository<CompteMapping, Long> {

    /**
     * Recupere le mapping pour un evenement comptable du tenant courant.
     * Renvoie {@link Optional#empty()} si l'hotel n'a pas defini de mapping
     * personnalise (le service applique alors le defaut codé).
     */
    Optional<CompteMapping> findByTypeEvenement(TypeEvenementComptable typeEvenement);

    /** Liste les mappings de l'hotel courant, tri stable. */
    List<CompteMapping> findAllByOrderByTypeEvenementAsc();
}
