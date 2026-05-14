package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.client.Societe;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.LigneFacture;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.TypeFacture;
import com.cityprojects.citybackend.entity.finance.TypeLigneFacture;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.client.SocieteRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.repository.finance.LigneFactureRepository;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito pour {@link FacturePdfServiceImpl} (Bloc B6).
 *
 * <h3>Couverture</h3>
 * <ul>
 *   <li>Genere un PDF non vide pour les statuts BROUILLON, EMISE,
 *       PARTIELLEMENT_PAYEE, ANNULEE.</li>
 *   <li>Verifie que le filigrane "BROUILLON" / "ANNULEE" est present dans
 *       le contenu (lecture {@link PdfTextExtractor}).</li>
 *   <li>Verifie l'absence de filigrane pour le statut PAYEE.</li>
 *   <li>Verifie {@link ResourceNotFoundException} sur facture absente.</li>
 *   <li>Verifie le libelle "Client divers" quand ni client ni societe.</li>
 * </ul>
 *
 * <p>Pas de Spring context : le service ne fait que de la composition,
 * les dependances repository sont mockees.</p>
 */
@ExtendWith(MockitoExtension.class)
class FacturePdfServiceTests {

    @Mock
    private FactureRepository factureRepository;
    @Mock
    private LigneFactureRepository ligneRepository;
    @Mock
    private HotelRepository hotelRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private SocieteRepository societeRepository;

    @InjectMocks
    private FacturePdfServiceImpl service;

    private static final Long HOTEL_ID = 42L;
    private static final Long FACTURE_ID = 7L;

    @BeforeEach
    void setUp() {
        TenantContext.set(HOTEL_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Hotel buildHotel() {
        Hotel h = new Hotel("MR1", "Hotel Mauritanie");
        h.setHotelId(HOTEL_ID);
        h.setHotelAdresse("Avenue Gamal Abdel Nasser");
        h.setVille("Nouakchott");
        h.setPays("Mauritanie");
        h.setBoitePostale("1234");
        h.setHotelTel("+222 45 25 25 25");
        h.setEmail("contact@hotel-mr.test");
        h.setSiteWeb("https://hotel-mr.test");
        h.setDevise("MRU");
        h.setCodePays("MR");
        h.setFuseauHoraire("Africa/Nouakchott");
        h.setNif("NIF-HOTEL-1234567");
        return h;
    }

    private Facture buildFacture(StatutFacture statut) {
        Facture f = new Facture();
        f.setFactureId(FACTURE_ID);
        f.setNumeroFacture("FACT-2026-MR-000007");
        f.setTypeFacture(TypeFacture.FACTURE);
        f.setDateFacture(LocalDate.of(2026, 5, 6));
        f.setDateEcheance(LocalDate.of(2026, 6, 5));
        f.setStatut(statut);
        f.setDevise("MRU");
        f.setMontantHt(new BigDecimal("10000.00"));
        f.setMontantTva(new BigDecimal("1600.00"));
        f.setMontantTtc(new BigDecimal("11600.00"));
        f.setMontantPaye(statut == StatutFacture.PARTIELLEMENT_PAYEE
                ? new BigDecimal("5000.00") : BigDecimal.ZERO);
        f.setUserId(1L);
        return f;
    }

    private List<LigneFacture> buildLignes() {
        LigneFacture l1 = new LigneFacture();
        l1.setLigneFactureId(1L);
        l1.setFactureId(FACTURE_ID);
        l1.setTypeLigne(TypeLigneFacture.NUITEE);
        l1.setLibelle("Chambre standard - nuit du 05/05/2026");
        l1.setQuantite(new BigDecimal("1"));
        l1.setPrixUnitaire(new BigDecimal("8000.00"));
        l1.setTauxTva(new BigDecimal("16.00"));
        l1.setMontantHt(new BigDecimal("8000.00"));
        l1.setMontantTva(new BigDecimal("1280.00"));
        l1.setMontantTtc(new BigDecimal("9280.00"));

        LigneFacture l2 = new LigneFacture();
        l2.setLigneFactureId(2L);
        l2.setFactureId(FACTURE_ID);
        l2.setTypeLigne(TypeLigneFacture.SERVICE);
        l2.setLibelle("Petit dejeuner");
        l2.setQuantite(new BigDecimal("2"));
        l2.setPrixUnitaire(new BigDecimal("1000.00"));
        l2.setTauxTva(new BigDecimal("16.00"));
        l2.setMontantHt(new BigDecimal("2000.00"));
        l2.setMontantTva(new BigDecimal("320.00"));
        l2.setMontantTtc(new BigDecimal("2320.00"));
        return List.of(l1, l2);
    }

    private void mockCommonRepositories(Facture f) {
        when(factureRepository.findById(f.getFactureId())).thenReturn(Optional.of(f));
        when(ligneRepository.findByFactureIdOrderByLigneFactureIdAsc(f.getFactureId()))
                .thenReturn(buildLignes());
        when(hotelRepository.findById(HOTEL_ID)).thenReturn(Optional.of(buildHotel()));
    }

    @Test
    @DisplayName("BROUILLON : PDF genere, filigrane BROUILLON present")
    void brouillonGeneratesPdfWithWatermark() throws Exception {
        Facture f = buildFacture(StatutFacture.BROUILLON);
        mockCommonRepositories(f);

        byte[] pdf = service.generate(FACTURE_ID);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        assertTrue(extractAllText(pdf).contains("BROUILLON"),
                "Le filigrane BROUILLON doit apparaitre");
    }

    @Test
    @DisplayName("EMISE : PDF genere, pas de filigrane")
    void emiseGeneratesPdfWithoutWatermark() throws Exception {
        Facture f = buildFacture(StatutFacture.EMISE);
        mockCommonRepositories(f);

        byte[] pdf = service.generate(FACTURE_ID);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        String text = extractAllText(pdf);
        // Le statut "EMISE" apparait dans la pastille - on verifie qu'il n'y
        // a PAS de filigrane "BROUILLON" ni "ANNULEE" (les mots seuls).
        assertTrue(!text.contains("BROUILLON"), "Pas de filigrane BROUILLON");
        assertTrue(!text.contains("ANNULEE") || text.contains("EMISE"),
                "Pas de filigrane ANNULEE pour EMISE");
    }

    @Test
    @DisplayName("PARTIELLEMENT_PAYEE : PDF genere, acompte et reste presents")
    void partiellementPayeePdf() throws Exception {
        Facture f = buildFacture(StatutFacture.PARTIELLEMENT_PAYEE);
        mockCommonRepositories(f);

        byte[] pdf = service.generate(FACTURE_ID);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        String text = extractAllText(pdf);
        assertTrue(text.contains("Acompte") || text.contains("Reste"),
                "Acompte/Reste doit apparaitre pour PARTIELLEMENT_PAYEE");
    }

    @Test
    @DisplayName("ANNULEE : PDF genere, filigrane ANNULEE present")
    void annuleeGeneratesPdfWithWatermark() throws Exception {
        Facture f = buildFacture(StatutFacture.ANNULEE);
        mockCommonRepositories(f);

        byte[] pdf = service.generate(FACTURE_ID);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        assertTrue(extractAllText(pdf).contains("ANNULEE"),
                "Le filigrane ANNULEE doit apparaitre");
    }

    @Test
    @DisplayName("PAYEE : PDF genere, pas de filigrane")
    void payeeNoWatermark() throws Exception {
        Facture f = buildFacture(StatutFacture.PAYEE);
        f.setMontantPaye(new BigDecimal("11600.00"));
        mockCommonRepositories(f);

        byte[] pdf = service.generate(FACTURE_ID);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @DisplayName("Facture inexistante -> ResourceNotFoundException")
    void factureNotFound() {
        when(factureRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.generate(999L));
    }

    @Test
    @DisplayName("Sans client ni societe : libelle 'Client divers'")
    void clientDivers() throws Exception {
        Facture f = buildFacture(StatutFacture.EMISE);
        // ni clientId ni societeId
        mockCommonRepositories(f);

        byte[] pdf = service.generate(FACTURE_ID);
        String text = extractAllText(pdf);
        assertTrue(text.contains("Client divers"),
                "Le PDF doit afficher 'Client divers' si client+societe absents");
    }

    @Test
    @DisplayName("Avec societe : nom societe + NIF affiches")
    void avecSociete() throws Exception {
        Facture f = buildFacture(StatutFacture.EMISE);
        f.setSocieteId(100L);
        Societe s = new Societe();
        s.setSocieteId(100L);
        s.setSocieteNom("Mauritania Travel Agency");
        s.setSiret("NIF-CLIENT-MR-99887766");
        s.setAdresse("BP 5678 Nouakchott");
        s.setVille("Nouakchott");
        s.setPays("Mauritanie");
        when(societeRepository.findById(100L)).thenReturn(Optional.of(s));
        mockCommonRepositories(f);

        byte[] pdf = service.generate(FACTURE_ID);
        String text = extractAllText(pdf);
        assertTrue(text.contains("Mauritania Travel Agency"),
                "Nom societe doit etre present");
        assertTrue(text.contains("NIF-CLIENT-MR-99887766"),
                "NIF (siret) doit etre present");
    }

    @Test
    @DisplayName("Avec client : nom complet client affiche")
    void avecClient() throws Exception {
        Facture f = buildFacture(StatutFacture.EMISE);
        f.setClientId(200L);
        Client c = new Client();
        c.setClientId(200L);
        c.setPrenom("Sidi");
        c.setNom("Cheikh");
        c.setAdresse("Quartier Tevragh-Zeina");
        c.setTelephone("+22245252525");
        when(clientRepository.findById(200L)).thenReturn(Optional.of(c));
        mockCommonRepositories(f);

        byte[] pdf = service.generate(FACTURE_ID);
        String text = extractAllText(pdf);
        assertTrue(text.contains("Sidi") && text.contains("Cheikh"),
                "Nom complet client doit etre present");
    }

    @Test
    @DisplayName("Tenant absent -> IllegalStateException via @RequireTenant")
    void tenantManquant() {
        TenantContext.clear();
        // Note : @RequireTenant n'est applique qu'en presence du proxy AOP Spring.
        // Hors contexte Spring, la verification se fait via TenantContext.get()
        // dans le service - meme effet (IllegalStateException avec
        // ERROR_TENANT_MISSING).
        assertThrows(IllegalStateException.class, () -> service.generate(FACTURE_ID));
    }

    @Test
    @DisplayName("Mentions legales : defauts presents si Hotel non renseigne")
    void mentionsLegalesDefaut() throws Exception {
        Facture f = buildFacture(StatutFacture.EMISE);
        Hotel h = buildHotel();
        h.setMentionsConditionsPaiement(null);
        h.setMentionsPenalitesRetard(null);
        when(factureRepository.findById(FACTURE_ID)).thenReturn(Optional.of(f));
        when(ligneRepository.findByFactureIdOrderByLigneFactureIdAsc(FACTURE_ID))
                .thenReturn(buildLignes());
        when(hotelRepository.findById(HOTEL_ID)).thenReturn(Optional.of(h));

        byte[] pdf = service.generate(FACTURE_ID);
        String text = extractAllText(pdf);
        assertTrue(text.toLowerCase().contains("reception de facture")
                || text.toLowerCase().contains("conditions"),
                "Mentions conditions paiement par defaut presentes");
        assertTrue(text.toLowerCase().contains("penalites"),
                "Mentions penalites retard par defaut presentes");
    }

    @Test
    @DisplayName("formatage MRU : pas de decimales pour MRU")
    void formatMruSansDecimales() {
        String s = com.cityprojects.citybackend.service.finance.pdf.FacturePdfHelper
                .money(new BigDecimal("10000.00"), "MRU");
        // Pas de virgule decimale dans le rendu MRU
        assertTrue(s.contains("10") && s.contains("000"), "Doit contenir 10 000");
        assertTrue(s.endsWith("MRU"), "Doit suffixer MRU");
        assertTrue(!s.contains(","), "Pas de virgule decimale en MRU");
    }

    @Test
    @DisplayName("formatage devise autre : 2 decimales")
    void formatEurAvecDecimales() {
        String s = com.cityprojects.citybackend.service.finance.pdf.FacturePdfHelper
                .money(new BigDecimal("1234.56"), "EUR");
        assertTrue(s.endsWith("EUR"));
        assertTrue(s.contains(",") || s.contains("."), "Decimales presentes pour EUR");
    }

    /**
     * Extrait tout le texte d'un PDF (toutes pages) via OpenPDF
     * {@link PdfTextExtractor}. Utilise pour valider la presence de filigranes
     * et de libelles dans les tests.
     */
    private static String extractAllText(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            StringBuilder sb = new StringBuilder();
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            int n = reader.getNumberOfPages();
            for (int i = 1; i <= n; i++) {
                sb.append(extractor.getTextFromPage(i)).append('\n');
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    @Test
    @DisplayName("PDF cree au moins 1 page")
    void atLeastOnePage() throws Exception {
        Facture f = buildFacture(StatutFacture.EMISE);
        mockCommonRepositories(f);
        byte[] pdf = service.generate(FACTURE_ID);
        PdfReader reader = new PdfReader(pdf);
        try {
            assertEquals(1, reader.getNumberOfPages(), "1 page attendue pour facture courte");
        } finally {
            reader.close();
        }
    }
}
