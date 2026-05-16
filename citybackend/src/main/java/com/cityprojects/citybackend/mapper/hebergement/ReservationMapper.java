package com.cityprojects.citybackend.mapper.hebergement;

import com.cityprojects.citybackend.dto.hebergement.NuiteeDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationChambreDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationClientDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationDto;
import com.cityprojects.citybackend.entity.hebergement.Nuitee;
import com.cityprojects.citybackend.entity.hebergement.Reservation;
import com.cityprojects.citybackend.entity.hebergement.ReservationChambre;
import com.cityprojects.citybackend.entity.hebergement.ReservationClient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct des entites de reservation et de leurs satellites.
 *
 * <p><b>NE MAPPE JAMAIS</b> {@code hotelId} ni {@code numeroReservation}
 * depuis un DTO entrant : le tenant est resolu par Hibernate via
 * {@link org.hibernate.annotations.TenantId}, et le numero est genere par
 * {@link com.cityprojects.citybackend.service.finance.NumerotationService}.</p>
 *
 * <p>Pas de {@code toEntity(ReservationCreateDto)} : la creation est trop
 * complexe (chambres, clients additionnels, generation de nuitees) pour
 * un mapping direct. Le service construit l'entite a la main, en respectant
 * les invariants metier.</p>
 *
 * <h3>Alias DTO Nuitee (Tour 14 B1)</h3>
 * <p>Le DTO {@link NuiteeDto} expose {@code id} et {@code dateNuitee} alors
 * que l'entite stocke {@code nuiteeId} et {@code dateNuit}. Mapping explicite
 * pour respecter la spec API.</p>
 */
@Mapper(componentModel = "spring")
public interface ReservationMapper {

    /**
     * Le champ {@code chambres} (liste de pivots) est ignoré : l'entité
     * {@link Reservation} ne possède pas de relation {@code @OneToMany} vers
     * {@link ReservationChambre} (pattern projet — FK stockée en {@code Long}).
     * Le service enrichit le DTO après mapping via une requête batch
     * {@code findByReservationIdIn(...)} pour éviter le N+1.
     */
    @Mapping(target = "chambres", ignore = true)
    @Mapping(target = "nomClientPrincipal", ignore = true)
    @Mapping(target = "nomSociete", ignore = true)
    ReservationDto toDto(Reservation entity);

    ReservationChambreDto toDto(ReservationChambre entity);

    ReservationClientDto toDto(ReservationClient entity);

    @Mapping(target = "id", source = "nuiteeId")
    @Mapping(target = "dateNuitee", source = "dateNuit")
    NuiteeDto toDto(Nuitee entity);
}
