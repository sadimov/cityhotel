package com.cityprojects.citybackend.mapper.inventory;

import com.cityprojects.citybackend.dto.inventory.ProduitCreateDto;
import com.cityprojects.citybackend.dto.inventory.ProduitDto;
import com.cityprojects.citybackend.entity.inventory.Produit;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link Produit} et ses DTOs.
 *
 * <p>Les champs derives ({@link Produit#getValeurStock()},
 * {@link Produit#getStatutStock()}) sont mappes automatiquement par MapStruct
 * via les getters {@code @Transient} (le nom du champ DTO matche le getter).</p>
 */
@Mapper(componentModel = "spring")
public interface ProduitMapper {

    /**
     * Mappe l'entite vers le DTO. Les champs derives {@code nomCategorie} et
     * {@code nomFournisseurPrincipal} ne sont PAS resolus ici (le mapper n'a
     * pas acces aux repositories) : c'est au service d'appeler
     * {@link #withResolvedNames(ProduitDto, String, String)} apres lookup.
     */
    @Mapping(target = "nomCategorie", ignore = true)
    @Mapping(target = "nomFournisseurPrincipal", ignore = true)
    ProduitDto toDto(Produit entity);

    /**
     * Helper pour reconstruire un {@link ProduitDto} en injectant les noms
     * resolus de la categorie et du fournisseur principal. Comme le DTO est
     * un record (immutable), on rebuilde une nouvelle instance.
     */
    default ProduitDto withResolvedNames(ProduitDto base,
                                          String nomCategorie,
                                          String nomFournisseurPrincipal) {
        if (base == null) {
            return null;
        }
        return new ProduitDto(
                base.produitId(),
                base.codeProduit(),
                base.nomProduit(),
                base.description(),
                base.categorieId(),
                nomCategorie,
                base.uniteMesure(),
                base.prixUnitaire(),
                base.seuilAlerte(),
                base.seuilCritique(),
                base.stockActuel(),
                base.fournisseurPrincipalId(),
                nomFournisseurPrincipal,
                base.estFacturable(),
                base.actif(),
                base.valeurStock(),
                base.statutStock(),
                base.createdAt(),
                base.updatedAt());
    }

    @Mapping(target = "produitId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "stockActuel", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Produit toEntity(ProduitCreateDto dto);
}
