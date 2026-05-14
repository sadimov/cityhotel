package com.cityprojects.citybackend.service.finance.comptabilite.pdf;

import com.cityprojects.citybackend.exception.BusinessException;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Helpers d'export PDF programmatique (OpenPDF / iText fork) pour les etats de
 * synthese OHADA (B5).
 *
 * <p>Justification du choix d'OpenPDF par rapport a JasperReports : les bilans
 * et comptes de resultat ont des rubriques a structure variable selon les
 * comptes effectivement mouvementes par l'hotel - JasperReports impose une
 * structure de bandes assez rigide qui complique l'expression de tableaux a
 * deux colonnes (ACTIF / PASSIF) dont chaque cellule contient elle-meme un
 * sous-tableau de longueur variable. OpenPDF programmatique reste lisible
 * pour ces cas et evite une multiplication des templates {@code .jrxml}
 * difficile a maintenir. La signature {@code byte[]} est inchangee cote API ;
 * un passage a Jasper ulterieur reste possible.</p>
 *
 * <p>Style commun : marges 36 pt, A4 portrait par defaut (paysage pour la
 * balance), en-tete avec nom hotel, date de generation, mention "Document non
 * opposable - Brouillon". Cellules monetaires aligness a droite, en-tetes de
 * tableaux gris fonce.</p>
 */
public final class EtatsPdfHelper {

    private static final Logger log = LoggerFactory.getLogger(EtatsPdfHelper.class);

    /** Couleur de fond des en-tetes de tableau (gris fonce). */
    public static final Color HEADER_BG = new Color(80, 80, 80);
    /** Couleur des en-tetes de section (gris clair). */
    public static final Color SECTION_BG = new Color(220, 220, 220);
    /** Couleur de fond pour les totaux (jaune pale). */
    public static final Color TOTAL_BG = new Color(255, 248, 220);

    /** Formatter monetaire MRU - sans decimales (ouguiya entier). */
    private static final DecimalFormat MONEY_FORMAT;
    /** Format de date dd/MM/yyyy. */
    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Format date+heure pour la generation. */
    public static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    static {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.FRENCH);
        sym.setGroupingSeparator(' ');
        MONEY_FORMAT = new DecimalFormat("#,##0", sym);
    }

    private EtatsPdfHelper() {
        // utility class
    }

    /** Formate un BigDecimal en chaine monetaire MRU. */
    public static String money(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return MONEY_FORMAT.format(value.setScale(0, RoundingMode.HALF_UP));
    }

    /** Construit un Document A4 portrait. */
    public static Document newPortraitDocument() {
        return new Document(PageSize.A4, 36, 36, 54, 36);
    }

    /** Construit un Document A4 paysage (balance, grand livre). */
    public static Document newLandscapeDocument() {
        return new Document(PageSize.A4.rotate(), 36, 36, 54, 36);
    }

    /** Initialise un writer PDF et ouvre le document. Renvoie la sortie binaire. */
    public static ByteArrayOutputStream open(Document doc) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            return out;
        } catch (DocumentException e) {
            log.error("PDF document open failed", e);
            throw new BusinessException("error.etat.pdf.failed");
        }
    }

    /** Cloture le document et renvoie le binaire. */
    public static byte[] close(Document doc, ByteArrayOutputStream out) {
        try {
            if (doc.isOpen()) {
                doc.close();
            }
            return out.toByteArray();
        } catch (Exception e) {
            log.error("PDF document close failed", e);
            throw new BusinessException("error.etat.pdf.failed");
        }
    }

    /**
     * Ajoute l'en-tete standard de l'etat : titre + nom hotel + periode + date
     * generation + mention.
     */
    public static void addHeader(Document doc, String titre, String hotelName,
                                 String periodeLibelle) {
        try {
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK);
            Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY);

            Paragraph title = new Paragraph(safe(titre), titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(6f);
            doc.add(title);

            Paragraph sub = new Paragraph(safe(hotelName), subFont);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(2f);
            doc.add(sub);

            if (periodeLibelle != null) {
                Paragraph p = new Paragraph(periodeLibelle, subFont);
                p.setAlignment(Element.ALIGN_CENTER);
                p.setSpacingAfter(2f);
                doc.add(p);
            }

            String gen = "Genere le " + LocalDate.now(ZoneId.systemDefault()).format(DATE_FMT)
                    + " - Document non opposable (brouillon)";
            Paragraph genP = new Paragraph(gen, smallFont);
            genP.setAlignment(Element.ALIGN_CENTER);
            genP.setSpacingAfter(12f);
            doc.add(genP);
        } catch (DocumentException e) {
            log.error("PDF header write failed", e);
            throw new BusinessException("error.etat.pdf.failed");
        }
    }

    /** Cellule d'en-tete de tableau (fond gris fonce, texte blanc bold). */
    public static PdfPCell headerCell(String text) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        PdfPCell c = new PdfPCell(new Phrase(safe(text), f));
        c.setBackgroundColor(HEADER_BG);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setPadding(4f);
        c.setBorder(Rectangle.BOX);
        c.setBorderColor(Color.DARK_GRAY);
        return c;
    }

    /** Cellule de donnee texte (alignement gauche). */
    public static PdfPCell textCell(String text) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
        PdfPCell c = new PdfPCell(new Phrase(safe(text), f));
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        c.setPadding(3f);
        c.setBorderColor(Color.LIGHT_GRAY);
        return c;
    }

    /** Cellule de donnee monetaire (alignement droit, format MRU). */
    public static PdfPCell moneyCell(BigDecimal value) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
        PdfPCell c = new PdfPCell(new Phrase(money(value), f));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setPadding(3f);
        c.setBorderColor(Color.LIGHT_GRAY);
        return c;
    }

    /** Cellule de total (bold, fond jaune pale, alignement droit pour montants). */
    public static PdfPCell totalLabelCell(String label) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK);
        PdfPCell c = new PdfPCell(new Phrase(safe(label), f));
        c.setBackgroundColor(TOTAL_BG);
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        c.setPadding(4f);
        c.setBorderColor(Color.GRAY);
        return c;
    }

    /** Cellule de total monetaire (bold, fond jaune pale). */
    public static PdfPCell totalMoneyCell(BigDecimal value) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK);
        PdfPCell c = new PdfPCell(new Phrase(money(value), f));
        c.setBackgroundColor(TOTAL_BG);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setPadding(4f);
        c.setBorderColor(Color.GRAY);
        return c;
    }

    /** Cellule de section (regroupement, sur la pleine largeur via colspan). */
    public static PdfPCell sectionCell(String label, int colspan) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
        PdfPCell c = new PdfPCell(new Phrase(safe(label), f));
        c.setBackgroundColor(SECTION_BG);
        c.setColspan(colspan);
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        c.setPadding(5f);
        c.setBorderColor(Color.GRAY);
        return c;
    }

    /** Construit un tableau avec largeurs relatives donnees, full width. */
    public static PdfPTable newTable(float[] widths) {
        PdfPTable t = new PdfPTable(widths.length);
        try {
            t.setWidths(widths);
            t.setWidthPercentage(100f);
            t.setSpacingBefore(6f);
            t.setSpacingAfter(6f);
        } catch (DocumentException e) {
            throw new BusinessException("error.etat.pdf.failed");
        }
        return t;
    }

    /** Ajoute un tableau au document avec gestion d'exception. */
    public static void addTable(Document doc, PdfPTable table) {
        try {
            doc.add(table);
        } catch (DocumentException e) {
            log.error("PDF table add failed", e);
            throw new BusinessException("error.etat.pdf.failed");
        }
    }

    /** Ajoute un paragraphe au document avec gestion d'exception. */
    public static void addParagraph(Document doc, String text, float fontSize, boolean bold) {
        try {
            Font f = bold
                    ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, fontSize, Color.BLACK)
                    : FontFactory.getFont(FontFactory.HELVETICA, fontSize, Color.BLACK);
            Paragraph p = new Paragraph(safe(text), f);
            p.setSpacingBefore(4f);
            p.setSpacingAfter(4f);
            doc.add(p);
        } catch (DocumentException e) {
            log.error("PDF paragraph add failed", e);
            throw new BusinessException("error.etat.pdf.failed");
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
