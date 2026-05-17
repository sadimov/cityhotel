package com.cityprojects.citybackend.mapper.finance;

import com.cityprojects.citybackend.dto.finance.AffectationPaiementDto;
import com.cityprojects.citybackend.dto.finance.PaiementDto;
import com.cityprojects.citybackend.entity.finance.AffectationPaiement;
import com.cityprojects.citybackend.entity.finance.Paiement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper MapStruct paiements et affectations.
 */
@Mapper(componentModel = "spring")
public interface PaiementMapper {

    @Mapping(target = "affectations", ignore = true)
    PaiementDto toDto(Paiement entity);

    @Mapping(target = "numeroFacture", ignore = true)
    AffectationPaiementDto toAffectationDto(AffectationPaiement entity);

    default PaiementDto withAffectations(PaiementDto dto, List<AffectationPaiementDto> affectations) {
        return new PaiementDto(
                dto.paiementId(),
                dto.numeroPaiement(),
                dto.compteId(),
                dto.montantTotal(),
                dto.devise(),
                dto.modePaiement(),
                dto.referencePaiement(),
                dto.datePaiement(),
                dto.statut(),
                dto.commentaires(),
                dto.userId(),
                affectations,
                dto.createdAt(),
                dto.updatedAt());
    }
}
