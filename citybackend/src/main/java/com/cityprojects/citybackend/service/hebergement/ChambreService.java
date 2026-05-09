package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.dto.hebergement.ChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ChambreDto;
import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/**
 * Service de gestion des chambres (catalogue physique de l'hotel).
 *
 * <p>Toutes les methodes operent dans le tenant courant.</p>
 */
public interface ChambreService {

    ChambreDto create(ChambreCreateDto dto);

    ChambreDto update(Long chambreId, ChambreCreateDto dto);

    ChambreDto findById(Long chambreId);

    ChambreDto findByNumero(String numeroChambre);

    Page<ChambreDto> findAll(Pageable pageable);

    List<ChambreDto> findAllActive();

    List<ChambreDto> findByType(Long typeId);

    List<ChambreDto> findByStatut(StatutChambre statut);

    /**
     * Change le statut d'une chambre selon les transitions valides
     * (cf. {@link StatutChambre}).
     *
     * @throws com.cityprojects.citybackend.exception.BusinessException si la
     *         transition est interdite metier.
     */
    ChambreDto changerStatut(Long chambreId, StatutChambre nouveauStatut);

    void deactivate(Long chambreId);

    /**
     * Reactive une chambre desactivee (Tour 14 B2 API). Repasse {@code actif=true}
     * et {@code statut=DISPONIBLE} (sortie de {@code HORS_SERVICE}).
     */
    void reactivate(Long chambreId);

    /**
     * Liste les chambres disponibles sur la periode {@code [dateDebut, dateFin)}
     * (Tour 14 B2 API). Equivalent : actives, sans pivot
     * {@code reservations_chambres} chevauchant la periode.
     *
     * @throws com.cityprojects.citybackend.exception.BusinessException si dates invalides
     */
    List<ChambreDto> findDisponibles(LocalDate dateDebut, LocalDate dateFin);
}
