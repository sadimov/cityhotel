package com.cityprojects.citybackend.repository.finance;

import com.cityprojects.citybackend.entity.finance.DeclarationTva;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Repository des déclarations TVA (B4).
 *
 * <p>Tenant-aware via {@code @TenantId} sur {@link DeclarationTva} :
 * Hibernate ajoute automatiquement {@code WHERE hotel_id = ?}.</p>
 */
@Repository
public interface DeclarationTvaRepository extends JpaRepository<DeclarationTva, Long> {

    /**
     * Récupère une déclaration sur une période exacte. Utilisé pour
     * l'idempotence du POST de création.
     */
    Optional<DeclarationTva> findByDateDebutAndDateFin(LocalDate dateDebut, LocalDate dateFin);

    Page<DeclarationTva> findAllByOrderByDateDebutDesc(Pageable pageable);
}
