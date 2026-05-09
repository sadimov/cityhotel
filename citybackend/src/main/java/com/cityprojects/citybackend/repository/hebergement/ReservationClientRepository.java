package com.cityprojects.citybackend.repository.hebergement;

import com.cityprojects.citybackend.entity.hebergement.ReservationClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository des clients additionnels d'une reservation.
 *
 * <p>Hibernate ajoute automatiquement {@code WHERE hotel_id = ?} via
 * {@link org.hibernate.annotations.TenantId}.</p>
 */
@Repository
public interface ReservationClientRepository
        extends JpaRepository<ReservationClient, Long>, JpaSpecificationExecutor<ReservationClient> {

    List<ReservationClient> findByReservationIdOrderByReservationClientIdAsc(Long reservationId);

    @Modifying
    @Query("DELETE FROM ReservationClient rc WHERE rc.reservationId = :reservationId")
    int deleteByReservationId(@Param("reservationId") Long reservationId);
}
