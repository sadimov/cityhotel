package com.cityprojects.citybackend.mapper.restaurant;

import com.cityprojects.citybackend.dto.restaurant.CommandeDto;
import com.cityprojects.citybackend.dto.restaurant.LigneCommandeDto;
import com.cityprojects.citybackend.entity.restaurant.Commande;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper MapStruct entre {@link Commande} et ses DTOs (Tour 24).
 *
 * <p><b>NE MAPPE JAMAIS</b> {@code hotelId} dans les DTO de sortie (resolver
 * Hibernate). La construction du DTO complet (avec les lignes) passe par
 * {@link #withLignes(CommandeDto, List)} appele par le service.</p>
 */
@Mapper(componentModel = "spring")
public interface CommandeMapper {

    @Mapping(target = "lignes", ignore = true)
    CommandeDto toDto(Commande entity);

    /**
     * Reconstruit un CommandeDto en y injectant la liste des lignes (resolues
     * cote service depuis {@code LigneCommandeRepository}).
     */
    default CommandeDto withLignes(CommandeDto base, List<LigneCommandeDto> lignes) {
        if (base == null) {
            return null;
        }
        return new CommandeDto(
                base.commandeId(), base.numeroCommande(), base.clientId(),
                base.reservationId(), base.factureId(), base.modeReglement(),
                base.statut(), base.montantHt(), base.montantTtc(), base.montantPaye(),
                base.devise(), base.dateCommande(), base.motifAnnulation(),
                base.numeroTable(),
                lignes, base.createdAt(), base.updatedAt());
    }
}
