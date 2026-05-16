package com.cityprojects.citybackend.mapper.finance;

import com.cityprojects.citybackend.dto.finance.FactureDto;
import com.cityprojects.citybackend.dto.finance.LigneFactureDto;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.LigneFacture;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper MapStruct pour les factures et leurs lignes.
 *
 * <p>Le toDto de Facture ne mappe pas les lignes : le service les charge via
 * {@code LigneFactureRepository} et complete le DTO via {@link #withLignes}.
 * {@code montantRestant} est calcule via {@code Facture.getMontantRestant()}.</p>
 */
@Mapper(componentModel = "spring")
public interface FactureMapper {

    @Mapping(target = "lignes", ignore = true)
    @Mapping(target = "montantRestant", expression = "java(entity.getMontantRestant())")
    @Mapping(target = "nomClient", ignore = true)
    @Mapping(target = "nomSociete", ignore = true)
    @Mapping(target = "nomFournisseur", ignore = true)
    @Mapping(target = "numeroReservation", ignore = true)
    FactureDto toDto(Facture entity);

    LigneFactureDto toLigneDto(LigneFacture entity);

    /**
     * Recompose un DTO Facture avec ses lignes mappees.
     */
    default FactureDto withLignes(FactureDto dto, List<LigneFactureDto> lignes) {
        return new FactureDto(
                dto.factureId(),
                dto.numeroFacture(),
                dto.typeFacture(),
                dto.compteId(),
                dto.clientId(),
                dto.societeId(),
                dto.reservationId(),
                dto.fournisseurId(),
                dto.factureReferenceId(),
                dto.dateFacture(),
                dto.dateEcheance(),
                dto.montantHt(),
                dto.montantTva(),
                dto.montantTtc(),
                dto.montantPaye(),
                dto.montantRestant(),
                dto.statut(),
                dto.devise(),
                dto.commentaires(),
                dto.userId(),
                lignes,
                dto.createdAt(),
                dto.updatedAt(),
                dto.nomClient(),
                dto.nomSociete(),
                dto.nomFournisseur(),
                dto.numeroReservation());
    }
}
