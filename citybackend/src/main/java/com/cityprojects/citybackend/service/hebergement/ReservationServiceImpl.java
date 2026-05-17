package com.cityprojects.citybackend.service.hebergement;

import com.cityprojects.citybackend.common.event.ReservationCalendarMutationEvent;
import com.cityprojects.citybackend.common.event.ReservationCheckedOutEvent;
import com.cityprojects.citybackend.common.paging.PageableUtils;
import com.cityprojects.citybackend.common.security.SecurityUtils;
import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.hebergement.ChambreDto;
import com.cityprojects.citybackend.dto.hebergement.ChangerChambreRequest;
import com.cityprojects.citybackend.dto.hebergement.CheckOutExpressRequest;
import com.cityprojects.citybackend.dto.hebergement.NuiteeDto;
import com.cityprojects.citybackend.dto.hebergement.RechercheDisponibiliteRequest;
import com.cityprojects.citybackend.dto.hebergement.ReservationChambreCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationChambreDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationClientCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationCreateDto;
import com.cityprojects.citybackend.dto.hebergement.ReservationDto;
import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.client.Societe;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.hebergement.Nuitee;
import com.cityprojects.citybackend.entity.hebergement.Reservation;
import com.cityprojects.citybackend.entity.hebergement.ReservationChambre;
import com.cityprojects.citybackend.entity.hebergement.ReservationClient;
import com.cityprojects.citybackend.entity.hebergement.StatutChambre;
import com.cityprojects.citybackend.entity.hebergement.StatutNuitee;
import com.cityprojects.citybackend.entity.hebergement.StatutReservation;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.mapper.hebergement.ChambreMapper;
import com.cityprojects.citybackend.mapper.hebergement.ReservationMapper;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.client.SocieteRepository;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.NuiteeRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationChambreRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationClientRepository;
import com.cityprojects.citybackend.repository.hebergement.ReservationRepository;
import com.cityprojects.citybackend.service.finance.FactureService;
import com.cityprojects.citybackend.service.finance.NumerotationService;
import com.cityprojects.citybackend.service.finance.ReservationFinanceService;
import com.cityprojects.citybackend.service.finance.TypeNumerotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation de {@link ReservationService}.
 *
 * <p>Conventions appliquees (cf. citybackend/CLAUDE.md §3.3) :
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe.</li>
 *   <li>{@code @Transactional(readOnly = true)} a la classe, override en ecriture.</li>
 *   <li>Constructeur explicite (pas de Lombok en palier 1).</li>
 *   <li>Aucun {@code setHotelId} : Hibernate populate via le resolver.</li>
 *   <li>{@code numeroReservation} genere via {@link NumerotationService} (cle RES).</li>
 *   <li>{@code userId} extrait du {@code SecurityContext} (jamais d'un DTO entrant).</li>
 * </ul>
 *
 * <h3>Nuitees</h3>
 * <p>Generees automatiquement a la creation : 1 nuitee par couple (chambre,
 * nuit) entre {@code dateDebut} et {@code dateFin} (exclu) de chaque
 * {@code ReservationChambre}. Le {@code montantTotal} est ensuite recalcule
 * comme somme des prix de nuitees, moins {@code reductionPourcentage}.</p>
 *
 * <h3>Cross-module</h3>
 * <p>Pas de dependance directe vers le module finance dans ce service. La
 * facturation est geree par {@code FactureService.fromReservation(reservationId)}
 * (Tour 19) qui lit cette reservation, cree une facture EMISE avec 1 ligne par
 * nuitee CONSOMMEE et met a jour {@code Reservation.factureId}.</p>
 *
 * <h3>Tour 40bis (refactor C1, H5, H8)</h3>
 * <p>{@link #create(ReservationCreateDto)} decoupe en helpers prives :
 * {@link #validate}, {@link #buildReservationEntity}, {@link #createPivotsAndNuitees},
 * {@link #attachAdditionalClients}, {@link #applyDiscount}. {@link #mapToDto}
 * factorise la conversion {@code List<Reservation>} -&gt; {@code List<ReservationDto>}.
 * {@link #markNuiteesConsommees} factorise la transition PREVUE -&gt; CONSOMMEE
 * appelee par checkIn / checkOut / cancel.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class ReservationServiceImpl implements ReservationService {

    private static final Logger logger = LoggerFactory.getLogger(ReservationServiceImpl.class);

    /**
     * Fuseau metier - utilise pour {@code arrivees-today}/{@code departs-today}
     * (Tour 14 B2 API). Le serveur tourne deja en {@code Africa/Nouakchott}
     * (cf. application.yml jackson timezone), mais on ne s'appuie pas dessus
     * pour rester deterministe.
     */
    private static final ZoneId NOUAKCHOTT = ZoneId.of("Africa/Nouakchott");

    private final ReservationRepository reservationRepository;
    private final ReservationChambreRepository reservationChambreRepository;
    private final ReservationClientRepository reservationClientRepository;
    private final NuiteeRepository nuiteeRepository;
    private final ChambreRepository chambreRepository;
    private final ChambreService chambreService;
    private final ClientRepository clientRepository;
    private final SocieteRepository societeRepository;
    private final ReservationMapper reservationMapper;
    private final ChambreMapper chambreMapper;
    private final NumerotationService numerotationService;
    /**
     * Service finance utilise pour la chaine create complete (Tour 44 Phase 1) :
     * Reservation + Nuitees + Facture previsionnelle + Lignes + DEBIT compte
     * client en une seule transaction.
     */
    private final FactureService factureService;
    /**
     * Service cross-module hebergement-finance (Tour 44 Phase 1, etendu Tour 45)
     * - sert pour le check-out express qui doit ajuster les comptes auxiliaires.
     */
    private final ReservationFinanceService reservationFinanceService;
    /**
     * Publisher Spring d'evenements applicatifs (Tour 30, couplage event-driven
     * vers le module ménage). Voir
     * {@link com.cityprojects.citybackend.common.event.ReservationCheckedOutEvent}
     * et {@link com.cityprojects.citybackend.service.menage.MenagePlanningEventListener}.
     *
     * <p>Tour 44 Phase 1 : sert aussi a publier les evenements
     * {@code ReservationCreatedEvent} / {@code ReservationUpdatedEvent} /
     * {@code ReservationDeletedEvent} consommes par
     * {@code CalendarEventListener} (refresh WebSocket du calendrier).</p>
     */
    private final ApplicationEventPublisher applicationEventPublisher;

    public ReservationServiceImpl(ReservationRepository reservationRepository,
                                  ReservationChambreRepository reservationChambreRepository,
                                  ReservationClientRepository reservationClientRepository,
                                  NuiteeRepository nuiteeRepository,
                                  ChambreRepository chambreRepository,
                                  ChambreService chambreService,
                                  ClientRepository clientRepository,
                                  SocieteRepository societeRepository,
                                  ReservationMapper reservationMapper,
                                  ChambreMapper chambreMapper,
                                  NumerotationService numerotationService,
                                  FactureService factureService,
                                  ReservationFinanceService reservationFinanceService,
                                  ApplicationEventPublisher applicationEventPublisher) {
        this.reservationRepository = reservationRepository;
        this.reservationChambreRepository = reservationChambreRepository;
        this.reservationClientRepository = reservationClientRepository;
        this.nuiteeRepository = nuiteeRepository;
        this.chambreRepository = chambreRepository;
        this.chambreService = chambreService;
        this.clientRepository = clientRepository;
        this.societeRepository = societeRepository;
        this.reservationMapper = reservationMapper;
        this.chambreMapper = chambreMapper;
        this.numerotationService = numerotationService;
        this.factureService = factureService;
        this.reservationFinanceService = reservationFinanceService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    @Transactional
    public ReservationDto create(ReservationCreateDto dto) {
        logger.info("Creation reservation : client={}, du {} au {}",
                dto.clientPrincipalId(), dto.dateArrivee(), dto.dateDepart());

        validate(dto);

        Reservation saved = reservationRepository.save(buildReservationEntity(dto, currentUserId()));

        BigDecimal montantBrut = createPivotsAndNuitees(saved, dto);
        attachAdditionalClients(saved, dto);

        BigDecimal montantNet = applyDiscount(montantBrut, saved.getReductionPourcentage());
        saved.setMontantTotal(montantNet);
        reservationRepository.save(saved);

        // Tour 44 Phase 1 : chaine atomique - cree immediatement la facture
        // previsionnelle EMISE + 1 ligne par nuitee + DEBIT compte client.
        // En cas d'echec, la TX rollback toute la creation reservation.
        // flush() garantit que les nuitees sont visibles par previsionFromReservation
        // (FactureServiceImpl execute findByReservationIdOrderByDateNuitAsc).
        nuiteeRepository.flush();
        factureService.previsionFromReservation(saved.getReservationId());

        logger.info("Reservation creee : id={}, numero={}, montant={}",
                saved.getReservationId(), saved.getNumeroReservation(), montantNet);

        // Tour 44 Phase 1 : notification calendrier temps reel via WebSocket.
        // Snapshot TenantContext (le filtre JWT clear le ThreadLocal en finally,
        // le listener AFTER_COMMIT peut donc s'executer apres clear).
        publishCalendarEvent(ReservationCalendarMutationEvent.Type.CREATED, saved.getReservationId());

        return enrichDto(saved);
    }

    /**
     * Helper : publie un evenement de mutation calendrier vers le topic WebSocket
     * de l'hotel courant. Snapshot du TenantContext au moment du publish car le
     * listener tourne apres COMMIT (potentiellement apres le clear du filtre).
     */
    private void publishCalendarEvent(ReservationCalendarMutationEvent.Type type, Long reservationId) {
        Long hotelId = TenantContext.getOrNull();
        if (hotelId == null) {
            logger.warn("publishCalendarEvent: pas de TenantContext, event {} ignore (reservationId={})",
                    type, reservationId);
            return;
        }
        applicationEventPublisher.publishEvent(
                ReservationCalendarMutationEvent.of(type, reservationId, hotelId));
    }

    /**
     * Validations metier prealables a la creation : coherence des dates,
     * existence et activite du client principal, de la societe et de chaque
     * chambre, absence de conflit de reservation sur les chambres demandees.
     *
     * <p>Levee de {@link BusinessException} ou {@link ResourceNotFoundException}
     * - aucune ecriture ne doit avoir eu lieu en amont.</p>
     */
    private void validate(ReservationCreateDto dto) {
        if (!dto.dateDepart().isAfter(dto.dateArrivee())) {
            throw new BusinessException("error.reservation.dates.invalid");
        }
        Client client = clientRepository.findById(dto.clientPrincipalId())
                .orElseThrow(() -> new ResourceNotFoundException("error.client.notFound"));
        if (!Boolean.TRUE.equals(client.getActif())) {
            throw new BusinessException("error.reservation.client.inactive");
        }
        if (dto.societeId() != null) {
            Societe societe = societeRepository.findById(dto.societeId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.societe.notFound"));
            if (!Boolean.TRUE.equals(societe.getActif())) {
                throw new BusinessException("error.reservation.societe.inactive");
            }
        }
        // Verifier que toutes les chambres existent (tenant), et conflits
        for (ReservationChambreCreateDto rc : dto.chambres()) {
            Chambre chambre = chambreRepository.findById(rc.chambreId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.chambre.notFound"));
            if (!Boolean.TRUE.equals(chambre.getActif())) {
                throw new BusinessException("error.reservation.chambre.inactive");
            }
            LocalDate debut = (rc.dateDebut() != null) ? rc.dateDebut() : dto.dateArrivee();
            LocalDate fin = (rc.dateFin() != null) ? rc.dateFin() : dto.dateDepart();
            if (!fin.isAfter(debut)) {
                throw new BusinessException("error.reservation.chambre.dates.invalid");
            }
            // Verrou pessimiste exclusif sur les pivots de la chambre concernee :
            // serialise les requetes concurrentes pour empecher le double-booking
            // (Tour 12bis finding C1). Postgres complete via EXCLUDE USING gist (changeset 004).
            List<ReservationChambre> conflits =
                    reservationChambreRepository.findConflictsForUpdate(rc.chambreId(), debut, fin);
            if (!conflits.isEmpty()) {
                throw new BusinessException("error.reservation.chambre.conflict");
            }
        }
    }

    /**
     * Construit l'entite {@link Reservation} a partir du DTO + de l'identifiant
     * de l'utilisateur courant. {@code montantTotal} est initialise a 0 - il
     * sera recalcule a partir des nuitees apres {@link #createPivotsAndNuitees}.
     * {@code numeroReservation} genere via {@link NumerotationService} (RES).
     *
     * <p><b>Pas de {@code setHotelId}</b> : Hibernate populate via le
     * {@code @TenantId} resolver a l'INSERT.</p>
     */
    private Reservation buildReservationEntity(ReservationCreateDto dto, Long userId) {
        Reservation reservation = new Reservation();
        reservation.setClientPrincipalId(dto.clientPrincipalId());
        reservation.setSocieteId(dto.societeId());
        reservation.setDateArrivee(dto.dateArrivee());
        reservation.setDateDepart(dto.dateDepart());
        // nbNuits : recalcule par l'entite via @PrePersist/@PreUpdate
        // (cf. Reservation#recalcNbNuits, Tour 12bis finding codeC-1).
        reservation.setNbAdultes(dto.nbAdultes() != null ? dto.nbAdultes() : 1);
        reservation.setNbEnfants(dto.nbEnfants() != null ? dto.nbEnfants() : 0);
        // Statut initial = CONFIRMEE = "créée, non encore arrivée".
        // Côté grille calendar ce statut est affiché en rouge (le mapping
        // couleur est porté côté front, cf. STATUT_RESERVATION_CHIP_MAP).
        reservation.setStatut(StatutReservation.CONFIRMEE);
        reservation.setMotifSejour(dto.motifSejour());
        reservation.setCommentaires(dto.commentaires());
        reservation.setReductionPourcentage(
                dto.reductionPourcentage() != null ? dto.reductionPourcentage() : BigDecimal.ZERO);
        reservation.setMontantTotal(BigDecimal.ZERO);
        reservation.setUserId(userId);
        // Tour 41 R-HEB-004 : canal de distribution (optionnel, retro-compat = null).
        reservation.setSourceCanal(dto.sourceCanal());
        // Numero genere par hotel/exercice (RES-2026-MR-000123)
        reservation.setNumeroReservation(numerotationService.next(TypeNumerotation.RES));
        return reservation;
    }

    /**
     * Cree les pivots {@link ReservationChambre} et genere les nuitees
     * correspondantes (1 par couple chambre/nuit dans [debut, fin)). Idempotent
     * sur les nuitees deja presentes (Tour 12bis, finding C2).
     *
     * @return montant brut accumule = somme des {@code prixNuit} des nuitees
     *         nouvellement creees (les nuitees deja presentes contribuent deja
     *         via leur propre prix - on ne double-compte pas).
     */
    private BigDecimal createPivotsAndNuitees(Reservation saved, ReservationCreateDto dto) {
        BigDecimal montantBrut = BigDecimal.ZERO;
        for (ReservationChambreCreateDto rc : dto.chambres()) {
            ReservationChambre pivot = new ReservationChambre();
            pivot.setReservationId(saved.getReservationId());
            pivot.setChambreId(rc.chambreId());
            LocalDate debut = (rc.dateDebut() != null) ? rc.dateDebut() : dto.dateArrivee();
            LocalDate fin = (rc.dateFin() != null) ? rc.dateFin() : dto.dateDepart();
            pivot.setDateDebut(debut);
            pivot.setDateFin(fin);
            pivot.setPrixNuit(rc.prixNuit());
            reservationChambreRepository.save(pivot);

            // Genere une nuitee par jour [debut, fin) - idempotent (Tour 12bis, finding C2)
            LocalDate jour = debut;
            while (jour.isBefore(fin)) {
                if (nuiteeRepository.existsByReservationIdAndChambreIdAndDateNuit(
                        saved.getReservationId(), rc.chambreId(), jour)) {
                    // Nuitee deja presente : pas de doublon, on n'ajoute pas non plus
                    // au montantBrut (la nuitee existante contribue deja via son prix).
                    jour = jour.plusDays(1);
                    continue;
                }
                Nuitee nuitee = new Nuitee();
                nuitee.setReservationId(saved.getReservationId());
                nuitee.setChambreId(rc.chambreId());
                nuitee.setDateNuit(jour);
                nuitee.setPrixNuit(rc.prixNuit());
                nuitee.setTaxeSejour(BigDecimal.ZERO);
                nuitee.setStatut(StatutNuitee.PREVUE);
                nuiteeRepository.save(nuitee);

                montantBrut = montantBrut.add(rc.prixNuit());
                jour = jour.plusDays(1);
            }
        }
        return montantBrut;
    }

    /**
     * Persiste les eventuels {@link ReservationClient} additionnels du DTO
     * (clients secondaires d'une chambre, ex. enfants, accompagnants).
     * No-op si {@code dto.clientsAdditionnels()} est {@code null}.
     */
    private void attachAdditionalClients(Reservation saved, ReservationCreateDto dto) {
        if (dto.clientsAdditionnels() == null) {
            return;
        }
        for (ReservationClientCreateDto rcc : dto.clientsAdditionnels()) {
            ReservationClient pivot = new ReservationClient();
            pivot.setReservationId(saved.getReservationId());
            pivot.setClientId(rcc.clientId());
            pivot.setChambreId(rcc.chambreId());
            pivot.setEstPayant(rcc.estPayant() != null ? rcc.estPayant() : Boolean.TRUE);
            pivot.setPourcentageCharge(
                    rcc.pourcentageCharge() != null
                            ? rcc.pourcentageCharge() : BigDecimal.valueOf(100.00));
            reservationClientRepository.save(pivot);
        }
    }

    /**
     * Applique une reduction en pourcentage au montant brut. Retourne le
     * {@code montantBrut} tel quel si {@code reduction} est nul ou nul-positif.
     *
     * <p>Calcul : {@code montantBrut - (montantBrut * reduction / 100)}, arrondi
     * HALF_UP a 2 decimales.</p>
     */
    private BigDecimal applyDiscount(BigDecimal montantBrut, BigDecimal reduction) {
        if (reduction == null || reduction.compareTo(BigDecimal.ZERO) <= 0) {
            return montantBrut;
        }
        BigDecimal abattement = montantBrut.multiply(reduction)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return montantBrut.subtract(abattement);
    }

    @Override
    public ReservationDto findById(Long reservationId) {
        Reservation entity = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));
        return enrichDto(entity);
    }

    @Override
    public ReservationDto findByNumero(String numeroReservation) {
        Reservation entity = reservationRepository.findByNumeroReservation(numeroReservation)
                .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));
        return enrichDto(entity);
    }

    @Override
    public Page<ReservationDto> findAll(StatutReservation statut,
                                         Long clientPrincipalId,
                                         Pageable pageable) {
        // Tour 14 audit, finding I2 : tri stable.
        // Tour 14 audit, finding I4 : alias front "dateCreation" -> "createdAt".
        Pageable remapped = PageableUtils.remapSort(pageable, Map.of("dateCreation", "createdAt"));
        Sort defaultSort = Sort.by(Sort.Order.desc("dateArrivee"));
        Pageable stable = PageableUtils.stable(remapped, defaultSort, "reservationId");

        // Specification dynamique : combine statut et clientPrincipalId selon
        // les filtres fournis. Hibernate ajoute automatiquement le predicat
        // tenant (WHERE hotel_id = ?) via @TenantId, on n'a donc pas a le
        // rajouter ici.
        Specification<Reservation> spec = Specification.where(null);
        if (statut != null) {
            final StatutReservation s = statut;
            spec = spec.and((root, q, cb) -> cb.equal(root.get("statut"), s));
        }
        if (clientPrincipalId != null) {
            final Long cid = clientPrincipalId;
            spec = spec.and((root, q, cb) -> cb.equal(root.get("clientPrincipalId"), cid));
        }

        return enrichPage(reservationRepository.findAll(spec, stable));
    }

    @Override
    public Page<ReservationDto> findByClient(Long clientId, Pageable pageable) {
        // Inclut les reservations ou le client est principal OU client
        // secondaire (pivot reservations_clients). Le POS doit pouvoir
        // facturer la chambre meme pour un accompagnant.
        //
        // Tri stable identique a findAll : remap alias front "dateCreation"
        // -> "createdAt" (sinon Hibernate plante UnknownPathException) puis
        // tri stable par defaut sur dateArrivee desc + reservationId.
        Pageable remapped = PageableUtils.remapSort(pageable, Map.of("dateCreation", "createdAt"));
        Sort defaultSort = Sort.by(Sort.Order.desc("dateArrivee"));
        Pageable stable = PageableUtils.stable(remapped, defaultSort, "reservationId");
        return enrichPage(reservationRepository
                .findByClientPrincipalOrSecondary(clientId, stable));
    }

    @Override
    public List<ReservationDto> findArriveesToday() {
        LocalDate today = LocalDate.now(NOUAKCHOTT);
        // Check-in du jour : statut encore CONFIRMEE (non encore traite).
        return mapToDto(reservationRepository
                .findByDateArriveeAndStatutOrderByDateArriveeAsc(today, StatutReservation.CONFIRMEE));
    }

    @Override
    public List<ReservationDto> findDepartsToday() {
        LocalDate today = LocalDate.now(NOUAKCHOTT);
        // Check-out du jour : statut ARRIVEE (sejour en cours).
        return mapToDto(reservationRepository
                .findByDateDepartAndStatutOrderByDateDepartAsc(today, StatutReservation.ARRIVEE));
    }

    @Override
    public List<ReservationDto> findEnCours() {
        // Sejours en cours : check-in fait (ARRIVEE), check-out non fait.
        return mapToDto(reservationRepository.findByStatut(StatutReservation.ARRIVEE));
    }

    @Override
    public List<ReservationDto> findCheckInsRetard() {
        LocalDate today = LocalDate.now(NOUAKCHOTT);
        // CONFIRMEE et dateArrivee < today : no-show pas encore traite par le night audit.
        return mapToDto(reservationRepository
                .findByStatutAndDateArriveeBeforeOrderByDateArriveeAsc(StatutReservation.CONFIRMEE, today));
    }

    @Override
    public Page<ReservationDto> rechercher(String terme, Pageable pageable) {
        if (terme == null || terme.isBlank()) {
            throw new BusinessException("error.recherche.terme.required");
        }
        Pageable remapped = PageableUtils.remapSort(pageable, Map.of("dateCreation", "createdAt"));
        Sort defaultSort = Sort.by(Sort.Order.desc("dateArrivee"));
        Pageable stable = PageableUtils.stable(remapped, defaultSort, "reservationId");
        return enrichPage(reservationRepository.rechercher(terme.trim(), stable));
    }

    @Override
    public List<ChambreDto> rechercherDisponibilite(RechercheDisponibiliteRequest request) {
        if (request == null || request.dateDebut() == null || request.dateFin() == null) {
            throw new BusinessException("error.disponibilite.dates.required");
        }
        if (!request.dateFin().isAfter(request.dateDebut())) {
            throw new BusinessException("error.disponibilite.dates.invalid");
        }
        List<Chambre> chambres = (request.nbPersonnes() != null && request.nbPersonnes() > 0)
                ? chambreRepository.findDisponiblesAvecCapacite(
                        request.dateDebut(), request.dateFin(), request.nbPersonnes())
                : chambreRepository.findDisponibles(request.dateDebut(), request.dateFin());
        return chambres.stream()
                .map(chambreMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<NuiteeDto> findNuitees(Long reservationId) {
        // Verifie d'abord l'appartenance tenant via le repository (Hibernate filtre)
        Reservation res = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));
        List<Nuitee> nuitees = nuiteeRepository.findByReservationIdOrderByDateNuitAsc(reservationId);
        // Résolution noms chambres en batch (1 SELECT IN) — toutes les nuitées d'une même
        // résa pointent vers les chambres réservées (généralement 1, parfois 2-3).
        java.util.Set<Long> chambreIds = nuitees.stream()
                .map(Nuitee::getChambreId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> numerosChambres = chambreIds.isEmpty() ? Map.of()
                : chambreRepository.findAllById(chambreIds).stream()
                        .collect(Collectors.toMap(Chambre::getChambreId, Chambre::getNumeroChambre));
        String numRes = res.getNumeroReservation();
        return nuitees.stream()
                .map(n -> reservationMapper.toDto(n)
                        .withResolvedNames(numerosChambres.get(n.getChambreId()), numRes))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ReservationDto checkIn(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));
        if (reservation.getStatut() != StatutReservation.CONFIRMEE) {
            throw new BusinessException("error.reservation.checkin.invalidStatus");
        }
        if (reservation.getDateArrivee().isAfter(LocalDate.now())) {
            throw new BusinessException("error.reservation.checkin.tooEarly");
        }
        reservation.setStatut(StatutReservation.ARRIVEE);
        reservationRepository.save(reservation);

        // Marquer les chambres OCCUPEEs (via ChambreService -> validation transition).
        // Si la chambre n'est pas DISPONIBLE (ex. MAINTENANCE), une BusinessException
        // est propagee : le check-in n'est pas autorise tant que la chambre n'est pas libre.
        for (ReservationChambre rc :
                reservationChambreRepository.findByReservationIdOrderByDateDebutAsc(reservationId)) {
            chambreService.changerStatut(rc.getChambreId(), StatutChambre.OCCUPEE);
        }
        // Marquer les nuitees du jour CONSOMMEES (maxDate = today : pas plus loin)
        markNuiteesConsommees(reservationId, LocalDate.now());
        // Tour 44 Phase 1 : notification calendrier temps reel.
        publishCalendarEvent(ReservationCalendarMutationEvent.Type.UPDATED, reservationId);
        return enrichDto(reservation);
    }

    @Override
    @Transactional
    public ReservationDto checkOut(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));
        if (reservation.getStatut() != StatutReservation.ARRIVEE) {
            throw new BusinessException("error.reservation.checkout.invalidStatus");
        }
        reservation.setStatut(StatutReservation.PARTIE);
        reservationRepository.save(reservation);

        // Liberer les chambres : passage en NETTOYAGE (via ChambreService -> validation transition).
        // On collecte les chambreIds en parallele pour publier ensuite un event
        // ReservationCheckedOut consomme par le module ménage (Tour 30 - Workflow A).
        List<Long> chambreIds = new ArrayList<>();
        for (ReservationChambre rc :
                reservationChambreRepository.findByReservationIdOrderByDateDebutAsc(reservationId)) {
            chambreService.changerStatut(rc.getChambreId(), StatutChambre.NETTOYAGE);
            chambreIds.add(rc.getChambreId());
        }
        // Toutes les nuitees PREVUEs encore non consommees deviennent CONSOMMEES (maxDate = null)
        markNuiteesConsommees(reservationId, null);

        // Tour 30 - Workflow A : publish event AFTER toutes les mutations d'etat.
        // Le listener {@code MenagePlanningEventListener} (AFTER_COMMIT, REQUIRES_NEW)
        // genere une tache QUOTIDIEN PLANIFIEE par chambre liberee. Aucun
        // hotelId dans le DTO d'origine : on snapshote TenantContext.get()
        // car le ThreadLocal peut etre cleared d'ici l'execution du listener.
        applicationEventPublisher.publishEvent(new ReservationCheckedOutEvent(
                reservationId, TenantContext.get(), LocalDate.now(NOUAKCHOTT), chambreIds));

        // Tour 44 Phase 1 : notification calendrier temps reel.
        publishCalendarEvent(ReservationCalendarMutationEvent.Type.UPDATED, reservationId);

        return enrichDto(reservation);
    }

    @Override
    @Transactional
    public ReservationDto update(Long reservationId, ReservationCreateDto dto) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));

        // Refuser modifications si reservation deja terminee
        if (reservation.getStatut() == StatutReservation.PARTIE
                || reservation.getStatut() == StatutReservation.ANNULEE
                || reservation.getStatut() == StatutReservation.NO_SHOW) {
            throw new BusinessException("error.reservation.update.terminated");
        }
        if (!dto.dateDepart().isAfter(dto.dateArrivee())) {
            throw new BusinessException("error.reservation.dates.invalid");
        }

        // Tour 49 : autoriser changement du client principal et de la societe
        // (les ReservationClient additionnels NE sont PAS impactes ici - on
        // modifie uniquement le payeur principal de la reservation).
        if (dto.clientPrincipalId() != null
                && !dto.clientPrincipalId().equals(reservation.getClientPrincipalId())) {
            Client newClient = clientRepository.findById(dto.clientPrincipalId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.client.notFound"));
            if (!Boolean.TRUE.equals(newClient.getActif())) {
                throw new BusinessException("error.client.inactif");
            }
            reservation.setClientPrincipalId(dto.clientPrincipalId());
        }
        // societeId : un dto.societeId == null signifie ici "ne pas toucher".
        // Pour DETACHER la societe, le front envoie explicitement la valeur
        // -1L (sentinelle conventionnelle) - sinon on validerait l'existence.
        if (dto.societeId() != null
                && !dto.societeId().equals(reservation.getSocieteId())) {
            if (dto.societeId() == -1L) {
                reservation.setSocieteId(null);
            } else {
                Societe newSociete = societeRepository.findById(dto.societeId())
                        .orElseThrow(() -> new ResourceNotFoundException("error.societe.notFound"));
                if (!Boolean.TRUE.equals(newSociete.getActif())) {
                    throw new BusinessException("error.societe.inactive");
                }
                reservation.setSocieteId(dto.societeId());
            }
        }

        // Champs editables (les chambres / nuitees / pivots ne sont PAS modifiables ici).
        reservation.setDateArrivee(dto.dateArrivee());
        reservation.setDateDepart(dto.dateDepart());
        reservation.setNbAdultes(dto.nbAdultes() != null ? dto.nbAdultes() : reservation.getNbAdultes());
        reservation.setNbEnfants(dto.nbEnfants() != null ? dto.nbEnfants() : reservation.getNbEnfants());
        reservation.setMotifSejour(dto.motifSejour());
        reservation.setCommentaires(dto.commentaires());
        if (dto.reductionPourcentage() != null) {
            reservation.setReductionPourcentage(dto.reductionPourcentage());
        }
        // Tour 41 R-HEB-004 : canal de distribution editable (null permis = effacer).
        reservation.setSourceCanal(dto.sourceCanal());
        // nbNuits sera recalcule par @PreUpdate (cf. Reservation.recalcNbNuits).
        Reservation persisted = reservationRepository.save(reservation);
        // Tour 44 Phase 1 : notification calendrier temps reel.
        publishCalendarEvent(ReservationCalendarMutationEvent.Type.UPDATED, reservationId);
        return enrichDto(persisted);
    }

    @Override
    @Transactional
    public ReservationDto delete(Long reservationId) {
        // Soft-delete : on annule (motif technique). Conserve la tracabilite
        // (cf. CLAUDE.md racine §6.2 : la facturation reference des nuitees,
        // un DELETE physique casserait l'historique).
        return cancel(reservationId, "Suppression via API DELETE");
    }

    @Override
    @Transactional
    public ReservationDto cancel(Long reservationId, String motif) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));
        if (reservation.getStatut() == StatutReservation.PARTIE
                || reservation.getStatut() == StatutReservation.ANNULEE) {
            throw new BusinessException("error.reservation.cancel.alreadyTerminated");
        }
        // Si ARRIVEE -> liberer les chambres (passage en NETTOYAGE via ChambreService).
        if (reservation.getStatut() == StatutReservation.ARRIVEE) {
            for (ReservationChambre rc :
                    reservationChambreRepository.findByReservationIdOrderByDateDebutAsc(reservationId)) {
                chambreService.changerStatut(rc.getChambreId(), StatutChambre.NETTOYAGE);
            }
        }
        reservation.setStatut(StatutReservation.ANNULEE);
        String motifTrim = (motif != null) ? motif.trim() : "(non specifie)";
        String previous = (reservation.getCommentaires() != null) ? reservation.getCommentaires() : "";
        reservation.setCommentaires(previous + "\nANNULEE le "
                + LocalDate.now() + " - Motif: " + motifTrim);
        Reservation persisted = reservationRepository.save(reservation);
        // Tour 44 Phase 1 : notification calendrier temps reel.
        // DELETED car cote calendrier la reservation disparait (filtre statut !=ANNULEE).
        publishCalendarEvent(ReservationCalendarMutationEvent.Type.DELETED, reservationId);
        return enrichDto(persisted);
    }

    /**
     * Helper i/o : convertit une liste d'entites Reservation en liste de DTO
     * enrichis avec leurs pivots {@code reservation_chambres}.
     * Tour 40bis (refactor H5) + Tour 44 (enrichissement chambres).
     */
    private List<ReservationDto> mapToDto(List<Reservation> entities) {
        return enrichDtos(entities);
    }

    /**
     * Enrichit un DTO unitaire avec ses pivots {@code reservation_chambres}
     * et les noms client/société (résolus en 2 SELECTs supplémentaires).
     * Pour les listes / pages, préférer {@link #enrichDtos(List)} ou
     * {@link #enrichPage(Page)} (batch anti-N+1).
     */
    private ReservationDto enrichDto(Reservation entity) {
        List<ReservationChambre> pivots = reservationChambreRepository
                .findByReservationIdOrderByDateDebutAsc(entity.getReservationId());
        List<ReservationChambreDto> chambres = pivots.stream()
                .map(reservationMapper::toDto)
                .collect(Collectors.toList());
        String nomClient = (entity.getClientPrincipalId() != null)
                ? clientRepository.findById(entity.getClientPrincipalId())
                        .map(Client::getNomComplet).orElse(null)
                : null;
        String nomSociete = (entity.getSocieteId() != null)
                ? societeRepository.findById(entity.getSocieteId())
                        .map(Societe::getSocieteNom).orElse(null)
                : null;
        return withChambres(reservationMapper.toDto(entity), chambres)
                .withResolvedNames(nomClient, nomSociete);
    }

    /**
     * Enrichit une liste de DTOs en batch (1 SELECT groupé via WHERE IN
     * pour les pivots, puis 2 SELECTs batch pour les noms client/société).
     * Indispensable pour le calendrier (jusqu'a 500 reservations / page).
     */
    private List<ReservationDto> enrichDtos(List<Reservation> entities) {
        if (entities.isEmpty()) return List.of();
        List<Long> ids = entities.stream()
                .map(Reservation::getReservationId)
                .collect(Collectors.toList());
        Map<Long, List<ReservationChambreDto>> byReservation = reservationChambreRepository
                .findByReservationIdInOrderByDateDebutAsc(ids)
                .stream()
                .collect(Collectors.groupingBy(
                        ReservationChambre::getReservationId,
                        Collectors.mapping(reservationMapper::toDto, Collectors.toList())));
        // Batch anti-N+1 : noms client + société en 1 SELECT IN chacun
        java.util.Set<Long> clientIds = entities.stream()
                .map(Reservation::getClientPrincipalId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        java.util.Set<Long> societeIds = entities.stream()
                .map(Reservation::getSocieteId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> nomsClients = clientIds.isEmpty() ? Map.of()
                : clientRepository.findAllById(clientIds).stream()
                        .collect(Collectors.toMap(Client::getClientId, Client::getNomComplet));
        Map<Long, String> nomsSocietes = societeIds.isEmpty() ? Map.of()
                : societeRepository.findAllById(societeIds).stream()
                        .collect(Collectors.toMap(Societe::getSocieteId, Societe::getSocieteNom));
        return entities.stream()
                .map(e -> withChambres(
                        reservationMapper.toDto(e),
                        byReservation.getOrDefault(e.getReservationId(), List.of()))
                        .withResolvedNames(
                                nomsClients.get(e.getClientPrincipalId()),
                                nomsSocietes.get(e.getSocieteId())))
                .collect(Collectors.toList());
    }

    /** Variante {@code Page<>} de {@link #enrichDtos(List)}. */
    private Page<ReservationDto> enrichPage(Page<Reservation> page) {
        List<ReservationDto> enriched = enrichDtos(page.getContent());
        return new PageImpl<>(enriched, page.getPageable(), page.getTotalElements());
    }

    /**
     * Recopie immutable d'un {@link ReservationDto} en injectant la liste de
     * chambres. Necessaire car le record est immutable. Les noms client/société
     * sont injectés ensuite via {@link ReservationDto#withResolvedNames}.
     */
    private static ReservationDto withChambres(ReservationDto base, List<ReservationChambreDto> chambres) {
        return new ReservationDto(
                base.reservationId(),
                base.numeroReservation(),
                base.clientPrincipalId(),
                base.societeId(),
                base.dateArrivee(),
                base.dateDepart(),
                base.nbNuits(),
                base.nbAdultes(),
                base.nbEnfants(),
                base.statut(),
                base.motifSejour(),
                base.commentaires(),
                base.reductionPourcentage(),
                base.montantTotal(),
                base.userId(),
                base.createdAt(),
                base.updatedAt(),
                chambres,
                base.sourceCanal(),
                base.nomClientPrincipal(),
                base.nomSociete());
    }

    @Override
    @Transactional
    public ReservationDto changerChambre(Long reservationId, ChangerChambreRequest request) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));

        // Refus de changer la chambre d'une reservation terminee.
        if (reservation.getStatut() == StatutReservation.PARTIE
                || reservation.getStatut() == StatutReservation.ANNULEE
                || reservation.getStatut() == StatutReservation.NO_SHOW) {
            throw new BusinessException("error.reservation.changerChambre.terminated");
        }

        // Identifier le pivot a modifier.
        List<ReservationChambre> pivots =
                reservationChambreRepository.findByReservationIdOrderByDateDebutAsc(reservationId);
        if (pivots.isEmpty()) {
            throw new BusinessException("error.reservation.changerChambre.aucuneChambre");
        }
        ReservationChambre pivot;
        if (request.ancienneChambreId() != null) {
            pivot = pivots.stream()
                    .filter(p -> request.ancienneChambreId().equals(p.getChambreId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            "error.reservation.changerChambre.ancienneChambre.notFound"));
        } else {
            if (pivots.size() > 1) {
                throw new BusinessException(
                        "error.reservation.changerChambre.ancienneChambre.required");
            }
            pivot = pivots.get(0);
        }

        Long ancienneChambreId = pivot.getChambreId();
        Long nouvelleChambreId = request.nouvelleChambreId();

        if (ancienneChambreId.equals(nouvelleChambreId)) {
            throw new BusinessException("error.reservation.changerChambre.identique");
        }

        // Verifier que la nouvelle chambre existe et est active dans le tenant.
        Chambre nouvelle = chambreRepository.findById(nouvelleChambreId)
                .orElseThrow(() -> new ResourceNotFoundException("error.chambre.notFound"));
        if (!Boolean.TRUE.equals(nouvelle.getActif())) {
            throw new BusinessException("error.reservation.chambre.inactive");
        }

        // Verifier qu'il n'y a pas de conflit (lock pessimiste, exclut le pivot
        // courant via filtrage en memoire pour eviter de se compter soi-meme
        // sur l'ancienne chambre - sans interet ici puisque la nouvelle chambre
        // est differente, mais defense en profondeur).
        List<ReservationChambre> conflits = reservationChambreRepository
                .findConflictsForUpdate(nouvelleChambreId, pivot.getDateDebut(), pivot.getDateFin());
        boolean conflitReel = conflits.stream()
                .anyMatch(c -> !c.getReservationChambreId().equals(pivot.getReservationChambreId()));
        if (conflitReel) {
            throw new BusinessException("error.reservation.chambre.conflict");
        }

        // Mise a jour du pivot.
        pivot.setChambreId(nouvelleChambreId);
        reservationChambreRepository.save(pivot);

        // Mise a jour des nuitees PREVUES pour cette chambre.
        // Tour 44 Phase 1 : depuis la chaine atomique create(), les nuitees
        // ont deja un factureId (facture previsionnelle EMISE) mais restent
        // en PREVUE jusqu'au check-in. On rebascule donc TOUTES les PREVUES
        // sur la nouvelle chambre - la facture previsionnelle est juste mise
        // a jour cote presentation (libelle ligne contient l'ancienne chambre,
        // mais l'audit reste tracable via Historique).
        // Les nuitees CONSOMMEES/FACTUREES (effectivement vecues) restent
        // rattachees a l'ancienne chambre (audit comptable - on ne reecrit pas
        // le passe).
        for (Nuitee n : nuiteeRepository.findByReservationIdOrderByDateNuitAsc(reservationId)) {
            if (n.getChambreId().equals(ancienneChambreId)
                    && n.getStatut() == StatutNuitee.PREVUE) {
                n.setChambreId(nouvelleChambreId);
                nuiteeRepository.save(n);
            }
        }

        // Liberation/occupation des chambres si la reservation est en cours.
        if (reservation.getStatut() == StatutReservation.ARRIVEE) {
            // Ancienne chambre liberee -> NETTOYAGE (workflow menage standard).
            chambreService.changerStatut(ancienneChambreId, StatutChambre.NETTOYAGE);
            // Nouvelle chambre OCCUPEE.
            chambreService.changerStatut(nouvelleChambreId, StatutChambre.OCCUPEE);
        }

        // Trace dans le commentaire de la reservation.
        String previous = (reservation.getCommentaires() != null) ? reservation.getCommentaires() : "";
        String raison = (request.raison() != null && !request.raison().isBlank())
                ? request.raison().trim() : "(non specifiee)";
        reservation.setCommentaires(previous + "\nCHANGEMENT CHAMBRE le "
                + LocalDate.now() + " : " + ancienneChambreId + " -> " + nouvelleChambreId
                + " - Raison: " + raison);
        Reservation persisted = reservationRepository.save(reservation);

        logger.info("Changement chambre reservation {} : {} -> {} (par user {})",
                reservationId, ancienneChambreId, nouvelleChambreId, currentUserIdSafe());

        // Tour 44 Phase 1 : notification calendrier temps reel.
        publishCalendarEvent(ReservationCalendarMutationEvent.Type.UPDATED, reservationId);
        return enrichDto(persisted);
    }

    /**
     * Variante "safe" de {@link #currentUserId()} qui retourne null en
     * l'absence d'authentification (utilise pour les logs uniquement).
     */
    private Long currentUserIdSafe() {
        return SecurityUtils.currentUserIdOrNull();
    }

    /**
     * Marque les nuitees PREVUEs d'une reservation comme CONSOMMEEs.
     *
     * <p>Si {@code maxDate} est non null, seules les nuitees dont
     * {@code dateNuit <= maxDate} sont marquees (cas check-in : on ne consomme
     * que jusqu'a aujourd'hui inclus). Si {@code maxDate} est null, toutes
     * les PREVUEs sont consommees (cas check-out / cancel).</p>
     *
     * <p>Tour 40bis (refactor H8) : factorise la boucle dupliquee dans
     * checkIn / checkOut / cancel.</p>
     *
     * @return nombre de nuitees marquees CONSOMMEEs.
     */
    private int markNuiteesConsommees(Long reservationId, LocalDate maxDate) {
        int count = 0;
        for (Nuitee n : nuiteeRepository.findByReservationIdAndStatutOrderByDateNuitAsc(
                reservationId, StatutNuitee.PREVUE)) {
            if (maxDate != null && n.getDateNuit().isAfter(maxDate)) {
                continue;
            }
            n.setStatut(StatutNuitee.CONSOMMEE);
            nuiteeRepository.save(n);
            count++;
        }
        return count;
    }

    /**
     * Recupere l'identifiant utilisateur courant depuis le SecurityContext.
     * <p>Si l'authentification n'est pas un {@code UserPrincipal} (cas d'un
     * test sans setup security), leve une {@link BusinessException} (cle
     * i18n {@code error.reservation.user.unknown}) plutot que de masquer
     * l'incident avec une valeur par defaut (la regle metier exige le createur).
     * Le {@code GlobalExceptionHandler} traduit en HTTP 4xx propre.</p>
     *
     * <p>Tour 40bis : utilise {@link SecurityUtils#currentUserIdOrNull()} pour
     * la lecture brute, mais conserve la cle i18n metier specifique reservation.</p>
     */
    private Long currentUserId() {
        Long userId = SecurityUtils.currentUserIdOrNull();
        if (userId == null) {
            throw new BusinessException("error.reservation.user.unknown");
        }
        return userId;
    }

    @Override
    @Transactional
    public ReservationDto checkOutExpress(Long reservationId, CheckOutExpressRequest request) {
        if (request == null || request.societeId() == null) {
            throw new BusinessException("error.checkoutExpress.societe.required");
        }
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reservation.notFound"));

        if (reservation.getStatut() != StatutReservation.ARRIVEE) {
            throw new BusinessException("error.checkoutExpress.statut.invalid");
        }

        // 1) Transfert comptable : DEBIT compte societe + CREDIT compte client.
        //    Le service finance verifie aussi l'existence d'une facture.
        Long clientResolved = request.clientId() != null
                ? request.clientId() : reservation.getClientPrincipalId();
        reservationFinanceService.applyCheckOutExpressTransfer(
                reservationId, clientResolved, request.societeId());

        // 2) Workflow check-out standard : reservation -> PARTIE, chambres -> NETTOYAGE.
        reservation.setStatut(StatutReservation.PARTIE);
        // Trace
        String previous = reservation.getCommentaires() != null ? reservation.getCommentaires() : "";
        reservation.setCommentaires(previous + "\nCHECK-OUT EXPRESS le " + LocalDate.now()
                + " - Transfert vers societe " + request.societeId());
        reservationRepository.save(reservation);

        List<Long> chambreIds = new ArrayList<>();
        for (ReservationChambre rc :
                reservationChambreRepository.findByReservationIdOrderByDateDebutAsc(reservationId)) {
            chambreService.changerStatut(rc.getChambreId(), StatutChambre.NETTOYAGE);
            chambreIds.add(rc.getChambreId());
        }

        // 3) Marquer toutes les nuitees PREVUEs en CONSOMMEES (idem checkOut standard).
        markNuiteesConsommees(reservationId, null);

        // 4) Publier l'event menage (workflow A - generation taches QUOTIDIEN).
        applicationEventPublisher.publishEvent(new ReservationCheckedOutEvent(
                reservationId, TenantContext.get(), LocalDate.now(NOUAKCHOTT), chambreIds));

        // 5) Notification calendrier
        publishCalendarEvent(ReservationCalendarMutationEvent.Type.UPDATED, reservationId);

        logger.info("checkOutExpress Tour 45 : reservation={} -> PARTIE, societe={}, client={}",
                reservationId, request.societeId(), clientResolved);
        return enrichDto(reservation);
    }
}
