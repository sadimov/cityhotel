package com.cityprojects.citybackend.service.finance.pdf;

import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.client.Societe;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.LigneFacture;
import com.cityprojects.citybackend.entity.finance.StatutFacture;
import com.cityprojects.citybackend.entity.finance.TypeFacture;
import com.cityprojects.citybackend.exception.BusinessException;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Helper OpenPDF dedie au rendu d'une facture client conforme Mauritanie
 * (Bloc B6, 2026-05-06).
 *
 * <p>Choix d'OpenPDF (et non Jasper) : un seul template a maintenir, layout
 * complexe (header double colonne, filigrane diagonal conditionnel,
 * mentions legales pied de page), parametrage exhaustif via Hotel. La
 * complexite d'un .jrxml n'apporte rien sur ce cas.</p>
 *
 * <p>Volontairement separe du {@code EtatsPdfHelper} (B5) : responsabilites
 * differentes (factures clients vs etats comptables), conventions de
 * formatage et de layout differentes. Les deux helpers cohabitent.</p>
 *
 * <p><b>i18n PDF</b> : libelles FR codes en dur pour B6 (tour ulterieur :
 * extraction vers messages_*.properties si besoin client). Pas un payload
 * REST -&gt; le front ne traduit pas.</p>
 */
public final class FacturePdfHelper {

    private static final Logger log = LoggerFactory.getLogger(FacturePdfHelper.class);

    /** Couleur titre encadre (bleu fonce sobre, accent hotel). */
    private static final Color TITLE_BORDER = new Color(40, 60, 110);
    /** Couleur en-tete de tableau. */
    private static final Color HEADER_BG = new Color(40, 60, 110);
    /** Couleur total TTC. */
    private static final Color TTC_BG = new Color(40, 60, 110);

    /** Defaut mentions legales si Hotel.mentionsConditionsPaiement vide. */
    public static final String DEFAULT_CONDITIONS_PAIEMENT =
            "A reception de facture, sauf accord ecrit.";
    /** Defaut mentions legales si Hotel.mentionsPenalitesRetard vide. */
    public static final String DEFAULT_PENALITES_RETARD =
            "Penalites de retard : taux legal applicable en cas de non-paiement a echeance.";

    /** Format dd/MM/yyyy. */
    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Format dd/MM/yyyy HH:mm. */
    public static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private FacturePdfHelper() {
    }

    // ------------------------------------------------------------------
    // Formatage monetaire
    // ------------------------------------------------------------------

    /**
     * Formate un montant selon la devise. MRU (ouguiya moderne) : entier sans
     * decimales. Autres devises : 2 decimales HALF_UP. Separateur de milliers
     * espace fine (typographie francaise).
     */
    public static String money(BigDecimal value, String devise) {
        if (value == null) {
            return formatZero(devise);
        }
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.FRENCH);
        sym.setGroupingSeparator(' ');
        if (isMru(devise)) {
            DecimalFormat fmt = new DecimalFormat("#,##0", sym);
            return fmt.format(value.setScale(0, RoundingMode.HALF_UP)) + " " + safeDevise(devise);
        }
        DecimalFormat fmt = new DecimalFormat("#,##0.00", sym);
        return fmt.format(value.setScale(2, RoundingMode.HALF_UP)) + " " + safeDevise(devise);
    }

    private static String formatZero(String devise) {
        return isMru(devise) ? "0 " + safeDevise(devise) : "0,00 " + safeDevise(devise);
    }

    private static boolean isMru(String devise) {
        return devise == null || "MRU".equalsIgnoreCase(devise);
    }

    private static String safeDevise(String devise) {
        return devise == null ? "MRU" : devise;
    }

    // ------------------------------------------------------------------
    // Generation du PDF complet
    // ------------------------------------------------------------------

    /**
     * Genere le PDF complet d'une facture. Le {@code hotel} et la {@code facture}
     * doivent etre du meme tenant (verification responsabilite du service appelant).
     *
     * @param hotel    hotel emetteur (doit etre non null - resolu par le service)
     * @param facture  facture cible (statut quelconque)
     * @param lignes   lignes de facture triees par ligneFactureId
     * @param client   client (optionnel - peut etre null)
     * @param societe  societe rattachee (optionnelle - peut etre null)
     */
    public static byte[] generate(Hotel hotel, Facture facture, List<LigneFacture> lignes,
                                   Client client, Societe societe) {
        if (hotel == null || facture == null) {
            throw new IllegalArgumentException("hotel and facture must not be null");
        }
        Document doc = new Document(PageSize.A4, 36, 36, 36, 54);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            // Filigrane diagonal + footer page X/Y attaches au writer via page event.
            WatermarkAndFooterEvent event = new WatermarkAndFooterEvent(
                    resolveWatermark(facture.getStatut()), fuseau(hotel));
            writer.setPageEvent(event);

            doc.open();
            addHeader(doc, hotel, facture);
            addClientBlock(doc, facture, client, societe);
            addLignesTable(doc, lignes, facture.getDevise());
            addTotalsBlock(doc, facture, lignes);
            addMentionsLegales(doc, hotel, facture);
            doc.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            log.error("Echec generation PDF facture {} (id={})",
                    facture.getNumeroFacture(), facture.getFactureId(), e);
            throw new BusinessException("error.facture.pdf.failed");
        }
    }

    // ------------------------------------------------------------------
    // Sections du PDF
    // ------------------------------------------------------------------

    private static void addHeader(Document doc, Hotel hotel, Facture facture) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidths(new float[] { 6f, 4f });
        header.setWidthPercentage(100f);
        header.setSpacingAfter(12f);

        // -- Cellule gauche : logo + identite hotel --
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(0f);

        Image logo = loadLogo(hotel.getLogoUrl());
        if (logo != null) {
            // Limite la hauteur du logo a 60pt pour rester proportionne.
            logo.scaleToFit(160f, 60f);
            Paragraph logoP = new Paragraph();
            logoP.add(new com.lowagie.text.Chunk(logo, 0, 0, false));
            left.addElement(logoP);
        } else {
            Font nameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
            Paragraph name = new Paragraph(safe(hotel.getHotelNom()), nameFont);
            left.addElement(name);
        }

        Font infoFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
        if (logo != null) {
            Font nameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);
            Paragraph hotelLine = new Paragraph(safe(hotel.getHotelNom()), nameFont);
            hotelLine.setSpacingBefore(4f);
            left.addElement(hotelLine);
        }
        if (notBlank(hotel.getHotelAdresse())) {
            left.addElement(new Paragraph(hotel.getHotelAdresse(), infoFont));
        }
        String villePays = joinNonBlank(", ",
                hotel.getBoitePostale() == null ? null : "BP " + hotel.getBoitePostale(),
                hotel.getVille(), hotel.getPays());
        if (!villePays.isEmpty()) {
            left.addElement(new Paragraph(villePays, infoFont));
        }
        if (notBlank(hotel.getHotelTel())) {
            left.addElement(new Paragraph("Tel : " + hotel.getHotelTel(), infoFont));
        }
        if (notBlank(hotel.getEmail())) {
            left.addElement(new Paragraph("Email : " + hotel.getEmail(), infoFont));
        }
        if (notBlank(hotel.getSiteWeb())) {
            left.addElement(new Paragraph("Web : " + hotel.getSiteWeb(), infoFont));
        }
        header.addCell(left);

        // -- Cellule droite : encadre titre + statut --
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.BOX);
        right.setBorderColor(TITLE_BORDER);
        right.setBorderWidth(1.5f);
        right.setPadding(8f);

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, TITLE_BORDER);
        Paragraph titreP = new Paragraph(resolveTitre(facture.getTypeFacture()), titleFont);
        titreP.setAlignment(Element.ALIGN_CENTER);
        titreP.setSpacingAfter(6f);
        right.addElement(titreP);

        Font numFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);
        Paragraph numP = new Paragraph("N° " + safe(facture.getNumeroFacture()), numFont);
        numP.setAlignment(Element.ALIGN_CENTER);
        numP.setSpacingAfter(4f);
        right.addElement(numP);

        Font lineFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
        if (facture.getDateFacture() != null) {
            Paragraph dE = new Paragraph(
                    "Date d'emission : " + facture.getDateFacture().format(DATE_FMT), lineFont);
            dE.setAlignment(Element.ALIGN_CENTER);
            right.addElement(dE);
        }
        if (facture.getDateEcheance() != null) {
            Paragraph dEch = new Paragraph(
                    "Echeance : " + facture.getDateEcheance().format(DATE_FMT), lineFont);
            dEch.setAlignment(Element.ALIGN_CENTER);
            right.addElement(dEch);
        }

        // Pastille statut
        Font statutFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        PdfPTable statutTable = new PdfPTable(1);
        statutTable.setWidthPercentage(70f);
        statutTable.setHorizontalAlignment(Element.ALIGN_CENTER);
        statutTable.setSpacingBefore(6f);
        PdfPCell st = new PdfPCell(new Phrase(resolveStatutLibelle(facture.getStatut()), statutFont));
        st.setBackgroundColor(resolveStatutColor(facture.getStatut()));
        st.setHorizontalAlignment(Element.ALIGN_CENTER);
        st.setPadding(4f);
        st.setBorder(Rectangle.NO_BORDER);
        statutTable.addCell(st);
        right.addElement(statutTable);
        header.addCell(right);

        doc.add(header);
    }

    private static void addClientBlock(Document doc, Facture facture, Client client, Societe societe)
            throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
        Font textFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);

        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(60f);
        t.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.setSpacingAfter(10f);

        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.BOX);
        c.setBorderColor(Color.LIGHT_GRAY);
        c.setPadding(8f);

        Paragraph header = new Paragraph("Facture a :", labelFont);
        header.setSpacingAfter(4f);
        c.addElement(header);

        String nom = resolveClientNom(client, societe);
        c.addElement(new Paragraph(nom, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK)));

        if (societe != null && notBlank(societe.getAdresse())) {
            c.addElement(new Paragraph(societe.getAdresse(), textFont));
        } else if (client != null && notBlank(client.getAdresse())) {
            c.addElement(new Paragraph(client.getAdresse(), textFont));
        }
        String villePays = societe != null
                ? joinNonBlank(", ", societe.getVille(), societe.getPays())
                : client != null ? joinNonBlank(", ", client.getVille(), client.getPays()) : "";
        if (!villePays.isEmpty()) {
            c.addElement(new Paragraph(villePays, textFont));
        }
        if (societe != null && notBlank(societe.getTelephone())) {
            c.addElement(new Paragraph("Tel : " + societe.getTelephone(), textFont));
        } else if (client != null && notBlank(client.getTelephone())) {
            c.addElement(new Paragraph("Tel : " + client.getTelephone(), textFont));
        }
        // NIF mauritanien : on utilise siret (champ existant Societe) ou
        // numeroIdentification cote Client si rempli.
        if (societe != null && notBlank(societe.getSiret())) {
            c.addElement(new Paragraph("NIF : " + societe.getSiret(),
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.DARK_GRAY)));
        } else if (client != null && notBlank(client.getNumeroIdentification())) {
            c.addElement(new Paragraph("N° identification : " + client.getNumeroIdentification(),
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.DARK_GRAY)));
        }
        t.addCell(c);
        doc.add(t);
    }

    private static void addLignesTable(Document doc, List<LigneFacture> lignes, String devise)
            throws DocumentException {
        // Colonnes : Designation | Qte | PU HT | TVA % | Mt HT | Mt TTC
        PdfPTable t = new PdfPTable(6);
        t.setWidthPercentage(100f);
        t.setWidths(new float[] { 5.5f, 1.0f, 1.5f, 0.8f, 1.5f, 1.7f });
        t.setSpacingBefore(4f);
        t.setSpacingAfter(0f);

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        addHeaderCell(t, "Designation", headerFont, Element.ALIGN_LEFT);
        addHeaderCell(t, "Qte", headerFont, Element.ALIGN_CENTER);
        addHeaderCell(t, "PU HT", headerFont, Element.ALIGN_RIGHT);
        addHeaderCell(t, "TVA %", headerFont, Element.ALIGN_CENTER);
        addHeaderCell(t, "Montant HT", headerFont, Element.ALIGN_RIGHT);
        addHeaderCell(t, "Montant TTC", headerFont, Element.ALIGN_RIGHT);

        if (lignes == null || lignes.isEmpty()) {
            Font emptyFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.GRAY);
            PdfPCell empty = new PdfPCell(new Phrase("(Aucune ligne)", emptyFont));
            empty.setColspan(6);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            empty.setPadding(8f);
            t.addCell(empty);
        } else {
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
            for (LigneFacture l : lignes) {
                addCell(t, safe(l.getLibelle()), cellFont, Element.ALIGN_LEFT);
                addCell(t, formatQuantite(l.getQuantite()), cellFont, Element.ALIGN_CENTER);
                addCell(t, money(l.getPrixUnitaire(), devise), cellFont, Element.ALIGN_RIGHT);
                addCell(t, formatTaux(l.getTauxTva()), cellFont, Element.ALIGN_CENTER);
                addCell(t, money(l.getMontantHt(), devise), cellFont, Element.ALIGN_RIGHT);
                addCell(t, money(l.getMontantTtc(), devise), cellFont, Element.ALIGN_RIGHT);
            }
        }
        doc.add(t);
    }

    private static void addTotalsBlock(Document doc, Facture facture, List<LigneFacture> lignes)
            throws DocumentException {
        String devise = facture.getDevise();
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(50f);
        t.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.setWidths(new float[] { 3f, 2f });
        t.setSpacingBefore(8f);

        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);

        addTotalRow(t, "Total HT :", money(facture.getMontantHt(), devise), labelFont, valueFont);

        // Detail TVA par taux (si plusieurs taux ou montantTva > 0)
        Map<BigDecimal, BigDecimal> tvaParTaux = aggregateTvaParTaux(lignes);
        if (!tvaParTaux.isEmpty()) {
            for (Map.Entry<BigDecimal, BigDecimal> e : tvaParTaux.entrySet()) {
                String tauxLabel = "TVA " + formatTaux(e.getKey()) + " :";
                addTotalRow(t, tauxLabel, money(e.getValue(), devise), labelFont, valueFont);
            }
        } else {
            addTotalRow(t, "Total TVA :", money(facture.getMontantTva(), devise), labelFont, valueFont);
        }

        // Total TTC : ligne mise en valeur
        Font ttcLabelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE);
        Font ttcValueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE);
        PdfPCell lab = new PdfPCell(new Phrase("Total TTC :", ttcLabelFont));
        lab.setBackgroundColor(TTC_BG);
        lab.setHorizontalAlignment(Element.ALIGN_LEFT);
        lab.setPadding(6f);
        lab.setBorder(Rectangle.BOX);
        lab.setBorderColor(TTC_BG);
        t.addCell(lab);

        PdfPCell val = new PdfPCell(new Phrase(money(facture.getMontantTtc(), devise), ttcValueFont));
        val.setBackgroundColor(TTC_BG);
        val.setHorizontalAlignment(Element.ALIGN_RIGHT);
        val.setPadding(6f);
        val.setBorder(Rectangle.BOX);
        val.setBorderColor(TTC_BG);
        t.addCell(val);

        // Acompte / reste si applicable
        BigDecimal paye = facture.getMontantPaye() == null ? BigDecimal.ZERO : facture.getMontantPaye();
        if (paye.compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(t, "Acompte verse :", money(paye, devise), labelFont, valueFont);
            BigDecimal reste = facture.getMontantRestant();
            Font resteFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
            addTotalRow(t, "Reste a payer :", money(reste, devise), resteFont, resteFont);
        }
        doc.add(t);
    }

    private static void addMentionsLegales(Document doc, Hotel hotel, Facture facture)
            throws DocumentException {
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingBefore(16f);
        doc.add(spacer);

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.DARK_GRAY);
        Font textFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.DARK_GRAY);

        Paragraph title = new Paragraph("Mentions legales", titleFont);
        title.setSpacingAfter(2f);
        doc.add(title);

        String conditions = notBlank(hotel.getMentionsConditionsPaiement())
                ? hotel.getMentionsConditionsPaiement()
                : DEFAULT_CONDITIONS_PAIEMENT;
        doc.add(new Paragraph("Conditions de paiement : " + conditions, textFont));

        String penalites = notBlank(hotel.getMentionsPenalitesRetard())
                ? hotel.getMentionsPenalitesRetard()
                : DEFAULT_PENALITES_RETARD;
        doc.add(new Paragraph(penalites, textFont));

        if ("MRU".equalsIgnoreCase(facture.getDevise())) {
            doc.add(new Paragraph("Facture etablie en MRU (Ouguiya mauritanien).", textFont));
        }
        if (notBlank(hotel.getNif())) {
            Paragraph nif = new Paragraph("NIF de l'hotel : " + hotel.getNif(), textFont);
            nif.setSpacingBefore(2f);
            doc.add(nif);
        }
    }

    // ------------------------------------------------------------------
    // Page event : filigrane diagonal + footer page X/Y et date
    // ------------------------------------------------------------------

    private static class WatermarkAndFooterEvent extends PdfPageEventHelper {
        private final String watermarkText;
        private final ZoneId zone;

        WatermarkAndFooterEvent(String watermarkText, ZoneId zone) {
            this.watermarkText = watermarkText;
            this.zone = zone;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA_BOLD,
                        BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
                PdfContentByte cb = writer.getDirectContentUnder();

                // Filigrane diagonal si statut BROUILLON / ANNULEE
                if (watermarkText != null) {
                    cb.saveState();
                    cb.setColorFill(watermarkText.equals("ANNULEE")
                            ? new Color(220, 60, 60) : new Color(150, 150, 150));
                    // OpenPDF n'expose pas setOpacity simple : on utilise un GState.
                    com.lowagie.text.pdf.PdfGState gs = new com.lowagie.text.pdf.PdfGState();
                    gs.setFillOpacity(0.20f);
                    cb.setGState(gs);
                    cb.beginText();
                    cb.setFontAndSize(bf, 90);
                    Rectangle pageSize = document.getPageSize();
                    float x = (pageSize.getLeft() + pageSize.getRight()) / 2f;
                    float y = (pageSize.getBottom() + pageSize.getTop()) / 2f;
                    cb.showTextAligned(Element.ALIGN_CENTER, watermarkText, x, y, 45f);
                    cb.endText();
                    cb.restoreState();
                }

                // Footer page X/Y + date de generation
                PdfContentByte foot = writer.getDirectContent();
                String now = LocalDateTime.now(zone).format(DATETIME_FMT);
                String txt = "Document genere le " + now
                        + "  -  Page " + writer.getPageNumber();
                foot.saveState();
                foot.beginText();
                foot.setFontAndSize(bf, 8);
                foot.setColorFill(Color.GRAY);
                Rectangle p = document.getPageSize();
                foot.showTextAligned(Element.ALIGN_CENTER, txt,
                        (p.getLeft() + p.getRight()) / 2f, p.getBottom() + 20f, 0f);
                foot.endText();
                foot.restoreState();
            } catch (IOException ioe) {
                log.warn("PDF page event failed", ioe);
                // ne pas casser le PDF si le filigrane echoue
            }
        }
    }

    // ------------------------------------------------------------------
    // Resolveurs et helpers internes
    // ------------------------------------------------------------------

    private static String resolveTitre(TypeFacture type) {
        if (type == null) {
            return "FACTURE";
        }
        switch (type) {
            case AVOIR: return "AVOIR";
            case PROFORMA: return "PROFORMA";
            case FACTURE_FOURNISSEUR: return "FACTURE FOURNISSEUR";
            case FACTURE:
            default: return "FACTURE";
        }
    }

    private static String resolveStatutLibelle(StatutFacture s) {
        if (s == null) {
            return "";
        }
        switch (s) {
            case BROUILLON: return "BROUILLON";
            case EMISE: return "EMISE";
            case PARTIELLEMENT_PAYEE: return "PARTIELLEMENT PAYEE";
            case PAYEE: return "PAYEE";
            case ANNULEE: return "ANNULEE";
            default: return s.name();
        }
    }

    private static Color resolveStatutColor(StatutFacture s) {
        if (s == null) {
            return Color.GRAY;
        }
        switch (s) {
            case BROUILLON: return new Color(120, 120, 120);
            case EMISE: return new Color(40, 90, 180);
            case PARTIELLEMENT_PAYEE: return new Color(220, 140, 40);
            case PAYEE: return new Color(50, 140, 70);
            case ANNULEE: return new Color(200, 60, 60);
            default: return Color.GRAY;
        }
    }

    /** Retourne le texte de filigrane a poser, ou null si pas de filigrane. */
    private static String resolveWatermark(StatutFacture s) {
        if (s == StatutFacture.BROUILLON) {
            return "BROUILLON";
        }
        if (s == StatutFacture.ANNULEE) {
            return "ANNULEE";
        }
        return null;
    }

    private static String resolveClientNom(Client client, Societe societe) {
        if (societe != null && notBlank(societe.getSocieteNom())) {
            return societe.getSocieteNom();
        }
        if (client != null) {
            String n = client.getNomComplet();
            if (notBlank(n)) {
                return n;
            }
        }
        return "Client divers";
    }

    private static ZoneId fuseau(Hotel hotel) {
        if (hotel != null && notBlank(hotel.getFuseauHoraire())) {
            try {
                return ZoneId.of(hotel.getFuseauHoraire());
            } catch (Exception e) {
                log.warn("Fuseau horaire hotel invalide '{}', fallback Africa/Nouakchott",
                        hotel.getFuseauHoraire());
            }
        }
        return ZoneId.of("Africa/Nouakchott");
    }

    /**
     * Charge un logo depuis {@code Hotel.logoUrl}. Trois resolutions essayees :
     * <ol>
     *   <li>URL HTTP/HTTPS absolue (preview/CDN).</li>
     *   <li>Chemin de fichier absolu local.</li>
     *   <li>Chemin relatif a {@code ./uploads} (convention storage avatars/profile).</li>
     * </ol>
     * En cas d'echec quelconque, log WARN et retour null (fallback nom hotel).
     */
    static Image loadLogo(String logoUrl) {
        if (logoUrl == null || logoUrl.isBlank()) {
            return null;
        }
        try {
            String trimmed = logoUrl.trim();
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return Image.getInstance(URI.create(trimmed).toURL());
            }
            Path p = Paths.get(trimmed);
            if (!p.isAbsolute()) {
                p = Paths.get("uploads").resolve(p);
            }
            if (Files.exists(p) && Files.isReadable(p)) {
                return Image.getInstance(p.toAbsolutePath().toString());
            }
            // Derniere chance : classpath (utile pour tests)
            URL cp = FacturePdfHelper.class.getClassLoader().getResource(trimmed);
            if (cp != null) {
                return Image.getInstance(cp);
            }
            log.warn("Logo hotel introuvable : {}", trimmed);
        } catch (RuntimeException | IOException e) {
            // RuntimeException couvre URISyntaxException (decoupage classique
            // pour eviter un catch Exception trop large).
            log.warn("Echec chargement logo hotel '{}': {}", logoUrl, e.getMessage());
        }
        return null;
    }

    private static Map<BigDecimal, BigDecimal> aggregateTvaParTaux(List<LigneFacture> lignes) {
        Map<BigDecimal, BigDecimal> map = new LinkedHashMap<>();
        if (lignes == null) {
            return map;
        }
        for (LigneFacture l : lignes) {
            BigDecimal taux = l.getTauxTva() == null ? BigDecimal.ZERO : l.getTauxTva();
            BigDecimal mtv = l.getMontantTva() == null ? BigDecimal.ZERO : l.getMontantTva();
            map.merge(taux, mtv, BigDecimal::add);
        }
        // Si tous les taux sont a zero on garde un seul total, sinon on retourne le detail.
        if (map.size() == 1 && map.containsKey(BigDecimal.ZERO)
                && map.get(BigDecimal.ZERO).compareTo(BigDecimal.ZERO) == 0) {
            return new LinkedHashMap<>();
        }
        return map;
    }

    private static void addHeaderCell(PdfPTable t, String label, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(safe(label), f));
        c.setBackgroundColor(HEADER_BG);
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(5f);
        c.setBorder(Rectangle.BOX);
        c.setBorderColor(HEADER_BG);
        t.addCell(c);
    }

    private static void addCell(PdfPTable t, String text, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(safe(text), f));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(4f);
        c.setBorderColor(Color.LIGHT_GRAY);
        t.addCell(c);
    }

    private static void addTotalRow(PdfPTable t, String label, String value, Font lf, Font vf) {
        PdfPCell lab = new PdfPCell(new Phrase(label, lf));
        lab.setHorizontalAlignment(Element.ALIGN_LEFT);
        lab.setPadding(4f);
        lab.setBorderColor(Color.LIGHT_GRAY);
        t.addCell(lab);

        PdfPCell val = new PdfPCell(new Phrase(value, vf));
        val.setHorizontalAlignment(Element.ALIGN_RIGHT);
        val.setPadding(4f);
        val.setBorderColor(Color.LIGHT_GRAY);
        t.addCell(val);
    }

    private static String formatQuantite(BigDecimal qte) {
        if (qte == null) {
            return "0";
        }
        // Si la quantite est entiere, on l'affiche sans decimales.
        if (qte.stripTrailingZeros().scale() <= 0) {
            return qte.setScale(0, RoundingMode.HALF_UP).toPlainString();
        }
        return qte.setScale(3, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatTaux(BigDecimal taux) {
        if (taux == null) {
            return "0%";
        }
        if (taux.stripTrailingZeros().scale() <= 0) {
            return taux.setScale(0, RoundingMode.HALF_UP).toPlainString() + "%";
        }
        return taux.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String joinNonBlank(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(sep);
                }
                sb.append(p);
            }
        }
        return sb.toString();
    }

    /** Force la creation d'un ColumnText pour les tests qui necessiteraient. */
    @SuppressWarnings("unused")
    private static ColumnText unused() {
        return null;
    }
}
