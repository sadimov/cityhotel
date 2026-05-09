package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.paging.PageableUtils;
import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.hebergement.NuiteeDto;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.hebergement.NuiteeMapper;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.NuiteeRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation read-only de {@link NuiteeService} (Tour 14).
 *
 * <p>Conventions :
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe.</li>
 *   <li>{@code @Transactional(readOnly = true)} au niveau classe (pas de
 *       mutation exposee).</li>
 *   <li>Aucun setter / mutation : les nuitees sont gerees par
 *       {@link ReservationService} et {@link NightAuditService}.</li>
 *   <li>Verification d'appartenance tenant via {@link ReservationRepository#findById}
 *       et {@link ChambreRepository#findById} avant toute lecture (404 propre
 *       en cross-tenant : Hibernate filtre via {@code @TenantId}, et le
 *       repository retourne {@code Optional.empty()}).</li>
 * </ul>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class NuiteeServiceImpl implements NuiteeService {

    private final NuiteeRepository nuiteeRepository;
    private final ReservationRepository reservationRepository;
    private final ChambreRepository chambreRepository;
    private final NuiteeMapper nuiteeMapper;

    public NuiteeServiceImpl(NuiteeRepository nuiteeRepository,
                             ReservationRepository reservationRepository,
                             ChambreRepository chambreRepository,
                             NuiteeMapper nuiteeMapper) {
        this.nuiteeRepository = nuiteeRepository;
        this.reservationRepository = reservationRepository;
        this.chambreRepository = chambreRepository;
        this.nuiteeMapper = nuiteeMapper;
    }

    @Override
    public List<NuiteeDto> findByReservation(Long reservationId) {
        // Verification appartenance tenant : 404 si la reservation n'est pas
        // dans le tenant courant (Hibernate filtre via @TenantId).
        reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));
        return nuiteeRepository.findByReservationIdOrderByDateNuitAsc(reservationId).stream()
                .map(nuiteeMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public Page<NuiteeDto> findByChambre(Long chambreId, Pageable pageable) {
        // Verification appartenance tenant : 404 si la chambre n'est pas
        // dans le tenant courant.
        chambreRepository.findById(chambreId)
                .orElseThrow(() -> new ResourceNotFoundException("error.chambre.notFound"));

        // Tri stable : par defaut dateNuit DESC, tie-breaker nuiteeId ASC
        // (Tour 14 audit, finding I2).
        Sort defaultSort = Sort.by(Sort.Order.desc("dateNuit"));
        Pageable stable = PageableUtils.stable(pageable, defaultSort, "nuiteeId");

        return nuiteeRepository.findByChambreId(chambreId, stable).map(nuiteeMapper::toDto);
    }
}
