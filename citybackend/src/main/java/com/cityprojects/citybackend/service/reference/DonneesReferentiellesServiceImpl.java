package com.cityprojects.citybackend.service.reference;

import com.cityprojects.citybackend.dto.reference.DonneesReferentiellesDto;
import com.cityprojects.citybackend.entity.reference.DonneesReferentielles;
import com.cityprojects.citybackend.repository.reference.DonneesReferentiellesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class DonneesReferentiellesServiceImpl implements DonneesReferentiellesService {

    private final DonneesReferentiellesRepository repository;

    public DonneesReferentiellesServiceImpl(DonneesReferentiellesRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<DonneesReferentiellesDto> findByCategorie(String categorie) {
        return repository
                .findByCategorieAndActifTrueOrderByOrdreAffichageAscLibelleAsc(categorie)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private DonneesReferentiellesDto toDto(DonneesReferentielles e) {
        return new DonneesReferentiellesDto(
                e.getRefId(),
                e.getCategorie(),
                e.getCode(),
                e.getLibelle(),
                e.getLibelleEn(),
                e.getLibelleAr(),
                e.getOrdreAffichage(),
                e.getActif());
    }
}
