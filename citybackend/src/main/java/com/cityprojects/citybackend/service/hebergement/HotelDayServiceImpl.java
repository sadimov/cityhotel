package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.hebergement.EtatJourneeHoteliere;
import com.cityprojects.citybackend.entity.hebergement.JourneeHoteliere;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.hebergement.JourneeHoteliereRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Implémentation de {@link HotelDayService}.
 *
 * <h3>Multi-tenant</h3>
 * <p>{@code @RequireTenant} : refuse toute opération sans {@link TenantContext}.
 * {@code @TenantId} sur {@link JourneeHoteliere} garantit l'isolation des
 * SELECT ; les INSERT positionnent automatiquement {@code hotel_id} depuis le
 * resolver. Aucun {@code setHotelId(...)} manuel dans ce service.</p>
 *
 * <h3>Concurrence</h3>
 * <p>Les transitions appellent {@code findByIdForUpdate} (PESSIMISTIC_WRITE) +
 * exploitent {@code @Version} pour bloquer toute course entre deux admins
 * tentant de lancer le night audit en parallèle.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class HotelDayServiceImpl implements HotelDayService {

    private static final Logger logger = LoggerFactory.getLogger(HotelDayServiceImpl.class);

    private final JourneeHoteliereRepository repository;
    private final NightAuditLockRegistry lockRegistry;
    private final Clock clock;

    public HotelDayServiceImpl(JourneeHoteliereRepository repository,
                               NightAuditLockRegistry lockRegistry,
                               Clock clock) {
        this.repository = repository;
        this.lockRegistry = lockRegistry;
        this.clock = clock;
    }

    @Override
    @Transactional
    public JourneeHoteliere currentDay() {
        List<JourneeHoteliere> actives = repository.findActiveCurrentTenant();
        if (!actives.isEmpty()) {
            return actives.get(0);
        }
        // Bootstrap : pas encore de journée pour ce tenant → ouvre une journée
        // à la date du jour calendaire.
        JourneeHoteliere day = new JourneeHoteliere();
        day.setDateHotel(LocalDate.now(clock));
        day.setEtat(EtatJourneeHoteliere.OUVERTE);
        day.setOpenedAt(Instant.now(clock));
        JourneeHoteliere saved = repository.save(day);
        logger.info("Bootstrap journée hôtelière : hotelId={}, dateHotel={}",
                TenantContext.get(), saved.getDateHotel());
        return saved;
    }

    @Override
    public LocalDate currentDate() {
        return currentDay().getDateHotel();
    }

    @Override
    @Transactional
    public JourneeHoteliere startClosure(Long closedByUserId) {
        JourneeHoteliere day = currentDay();
        JourneeHoteliere locked = repository.findByIdForUpdate(day.getId())
                .orElseThrow(() -> new IllegalStateException("Journée hôtelière introuvable après lock"));

        if (locked.getEtat() == EtatJourneeHoteliere.CLOTURE_EN_COURS) {
            // Idempotence : si une autre transaction a déjà déclenché la clôture,
            // on refuse plutôt que de double-lancer le NA. Cas réel : 2 admins
            // cliquent en même temps.
            throw new BusinessException("error.nightAudit.alreadyRunning");
        }
        if (locked.getEtat() != EtatJourneeHoteliere.OUVERTE) {
            // CLOTUREE : ne devrait jamais arriver vu que currentDay() ne retourne
            // que OUVERTE / CLOTURE_EN_COURS. Garde-fou.
            throw new IllegalStateException("Transition impossible depuis " + locked.getEtat());
        }

        locked.setEtat(EtatJourneeHoteliere.CLOTURE_EN_COURS);
        locked.setClosedByUserId(closedByUserId);
        JourneeHoteliere saved = repository.save(locked);
        lockRegistry.lock(TenantContext.get());
        logger.info("Journée hôtelière {} → CLOTURE_EN_COURS (closedByUserId={})",
                saved.getDateHotel(), closedByUserId);
        return saved;
    }

    @Override
    @Transactional
    public JourneeHoteliere completeClosure() {
        JourneeHoteliere day = currentDay();
        JourneeHoteliere locked = repository.findByIdForUpdate(day.getId())
                .orElseThrow(() -> new IllegalStateException("Journée hôtelière introuvable après lock"));

        if (locked.getEtat() != EtatJourneeHoteliere.CLOTURE_EN_COURS) {
            throw new IllegalStateException(
                    "completeClosure attendait CLOTURE_EN_COURS, état actuel=" + locked.getEtat());
        }

        locked.setEtat(EtatJourneeHoteliere.CLOTUREE);
        locked.setClosedAt(Instant.now(clock));
        JourneeHoteliere closed = repository.save(locked);

        // Ouvre la journée suivante (J+1 dans la dimension hôtelière).
        JourneeHoteliere next = new JourneeHoteliere();
        next.setDateHotel(closed.getDateHotel().plusDays(1));
        next.setEtat(EtatJourneeHoteliere.OUVERTE);
        next.setOpenedAt(Instant.now(clock));
        repository.save(next);

        lockRegistry.unlock(TenantContext.get());
        logger.info("Journée hôtelière {} → CLOTUREE ; ouverture {} OUVERTE",
                closed.getDateHotel(), next.getDateHotel());
        return closed;
    }

    @Override
    @Transactional
    public JourneeHoteliere abortClosure() {
        JourneeHoteliere day = currentDay();
        JourneeHoteliere locked = repository.findByIdForUpdate(day.getId())
                .orElseThrow(() -> new IllegalStateException("Journée hôtelière introuvable après lock"));

        if (locked.getEtat() != EtatJourneeHoteliere.CLOTURE_EN_COURS) {
            // Pas de transition à faire — la journée est déjà OUVERTE (ou
            // CLOTUREE, cas anormal qu'on ne traite pas ici).
            lockRegistry.unlock(TenantContext.get());
            return locked;
        }

        locked.setEtat(EtatJourneeHoteliere.OUVERTE);
        locked.setClosedByUserId(null);
        JourneeHoteliere reopened = repository.save(locked);
        lockRegistry.unlock(TenantContext.get());
        logger.warn("Journée hôtelière {} → OUVERTE (abort closure)", reopened.getDateHotel());
        return reopened;
    }
}
