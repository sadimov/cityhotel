package com.cityprojects.citybackend.service.restaurant;

import com.cityprojects.citybackend.common.security.SecurityUtils;
import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.restaurant.TicketDto;
import com.cityprojects.citybackend.entity.restaurant.Ticket;
import com.cityprojects.citybackend.entity.restaurant.TypeTicket;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.restaurant.TicketMapper;
import com.cityprojects.citybackend.repository.restaurant.CommandeRepository;
import com.cityprojects.citybackend.repository.restaurant.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Implementation de {@link TicketService} (Tour 24).
 *
 * <p>{@code @RequireTenant} + {@code userId} extrait du SecurityContext (jamais
 * d'un DTO).</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class TicketServiceImpl implements TicketService {

    private static final Logger logger = LoggerFactory.getLogger(TicketServiceImpl.class);

    private final TicketRepository ticketRepository;
    private final CommandeRepository commandeRepository;
    private final TicketMapper mapper;

    public TicketServiceImpl(TicketRepository ticketRepository,
                             CommandeRepository commandeRepository,
                             TicketMapper mapper) {
        this.ticketRepository = ticketRepository;
        this.commandeRepository = commandeRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TicketDto imprimerTicketCaisse(Long commandeId) {
        return imprimer(commandeId, TypeTicket.CAISSE, null);
    }

    @Override
    @Transactional
    public TicketDto imprimerTicketCuisine(Long commandeId) {
        return imprimer(commandeId, TypeTicket.CUISINE, null);
    }

    @Override
    @Transactional
    public TicketDto reimprimer(Long commandeId, TypeTicket typeOrigine, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("error.ticket.motif.required");
        }
        if (typeOrigine == null
                || typeOrigine == TypeTicket.REIMPRESSION) {
            // On exige le type de ticket d'origine (CAISSE ou CUISINE) - on stocke
            // toujours la trace REIMPRESSION mais le motif evoque le type initial
            // dans son texte si besoin.
            throw new BusinessException("error.ticket.typeTicket.invalide");
        }
        return imprimer(commandeId, TypeTicket.REIMPRESSION, motif);
    }

    @Override
    public List<TicketDto> listerParCommande(Long commandeId) {
        // Verifie que la commande existe dans le tenant courant.
        commandeRepository.findById(commandeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.commande.notFound"));
        return ticketRepository.findByCommandeIdOrderByDateImpressionDesc(commandeId)
                .stream().map(mapper::toDto).toList();
    }

    private TicketDto imprimer(Long commandeId, TypeTicket type, String motif) {
        // Verifie que la commande existe et appartient au tenant courant
        // (Hibernate filtre via @TenantId).
        commandeRepository.findById(commandeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.commande.notFound"));

        Ticket ticket = new Ticket();
        ticket.setCommandeId(commandeId);
        ticket.setTypeTicket(type);
        ticket.setDateImpression(Instant.now());
        ticket.setImprimeParUserId(SecurityUtils.currentUserIdOrNull());
        ticket.setMotifReimpression(motif);
        // PAS de setHotelId : Hibernate via @TenantId resolver.

        Ticket saved = ticketRepository.save(ticket);
        logger.info("Ticket emis : id={}, commande={}, type={}, user={}",
                saved.getTicketId(), commandeId, type, saved.getImprimeParUserId());
        return mapper.toDto(saved);
    }

}
