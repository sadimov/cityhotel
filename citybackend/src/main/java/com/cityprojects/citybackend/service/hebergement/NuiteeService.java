package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.dto.hebergement.NuiteeDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service de lecture des nuitees (Tour 14 - module hebergement).
 *
 * <p>Read-only : la creation / mise a jour des nuitees est exclusivement geree
 * par {@link ReservationService} (lors d'une creation de reservation, d'un
 * check-in/out) et par {@link NightAuditService} (generation des nuitees
 * manquantes a midi). Aucune mutation n'est exposee ici.</p>
 *
 * <p>Toutes les methodes operent dans le tenant courant (filtre Hibernate via
 * {@link org.hibernate.annotations.TenantId}).</p>
 */
public interface NuiteeService {

    /**
     * Liste les nuitees d'une reservation, par date croissante (toutes statuts).
     */
    List<NuiteeDto> findByReservation(Long reservationId);

    /**
     * Page des nuitees d'une chambre (toutes reservations / statuts).
     * <p>Tri par defaut : {@code dateNuit DESC, nuiteeId ASC} pour stabilite
     * (Tour 14 audit, finding I2).</p>
     */
    Page<NuiteeDto> findByChambre(Long chambreId, Pageable pageable);
}
