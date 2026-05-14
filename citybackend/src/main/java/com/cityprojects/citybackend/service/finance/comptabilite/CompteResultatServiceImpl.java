package com.cityprojects.citybackend.service.finance.comptabilite;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.dto.finance.comptabilite.CompteResultatDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.LigneResultatDto;
import com.cityprojects.citybackend.dto.finance.comptabilite.RubriqueResultatDto;
import com.cityprojects.citybackend.entity.finance.Exercice;
import com.cityprojects.citybackend.entity.finance.PlanComptableGeneral;
import com.cityprojects.citybackend.entity.finance.SensLigne;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.finance.ExerciceRepository;
import com.cityprojects.citybackend.repository.finance.LigneEcritureRepository;
import com.cityprojects.citybackend.repository.finance.PlanComptableGeneralRepository;
import com.cityprojects.citybackend.service.finance.comptabilite.pdf.EtatsPdfHelper;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnSpec;
import com.cityprojects.citybackend.service.reporting.export.XlsxExportService.ColumnType;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation de {@link CompteResultatService} (B5).
 *
 * <p>Compte de resultat SYSCOHADA simplifie : agrege les comptes de classe 6
 * (charges, sens DEBITEUR) et 7 (produits, sens CREDITEUR) par sous-prefixe
 * fonctionnel.</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class CompteResultatServiceImpl implements CompteResultatService {

    private static final Logger log = LoggerFactory.getLogger(CompteResultatServiceImpl.class);

    private final LigneEcritureRepository ligneEcritureRepository;
    private final PlanComptableGeneralRepository pcgRepository;
    private final ExerciceRepository exerciceRepository;
    private final XlsxExportService xlsxExportService;

    public CompteResultatServiceImpl(LigneEcritureRepository ligneEcritureRepository,
                                      PlanComptableGeneralRepository pcgRepository,
                                      ExerciceRepository exerciceRepository,
                                      XlsxExportService xlsxExportService) {
        this.ligneEcritureRepository = ligneEcritureRepository;
        this.pcgRepository = pcgRepository;
        this.exerciceRepository = exerciceRepository;
        this.xlsxExportService = xlsxExportService;
    }

    @Override
    public CompteResultatDto compute(Long exerciceId, LocalDate dateDebut, LocalDate dateFin) {
        if (exerciceId == null) {
            throw new BusinessException("error.etat.exerciceIdRequired");
        }
        Exercice exercice = exerciceRepository.findById(exerciceId)
                .orElseThrow(() -> new ResourceNotFoundException("error.exercice.notFound"));
        LocalDate d1 = dateDebut != null ? dateDebut : exercice.getDateDebut();
        LocalDate d2 = dateFin != null ? dateFin : exercice.getDateFin();
        if (d2.isBefore(d1)) {
            throw new BusinessException("error.etat.dateRangeInvalide");
        }

        // PRODUITS (classe 7)
        List<RubriqueResultatDto> produits = new ArrayList<>();
        produits.add(buildRubrique("V1", "Ventes hebergement", "7061", d1, d2, true));
        produits.add(buildRubrique("V2", "Ventes restauration", "7062", d1, d2, true));
        produits.add(buildRubrique("V3", "Ventes bar", "7063", d1, d2, true));
        produits.add(buildRubrique("V4", "Autres ventes / services", List.of("7064", "7065", "7066", "7067", "7068"),
                d1, d2, true));
        produits.add(buildRubrique("V5", "Reductions accordees", "719", d1, d2, true));
        produits.add(buildRubrique("V6", "Produits exceptionnels", "754", d1, d2, true));
        // Defensif : autres comptes de classe 7 non couverts par les rubriques ci-dessus
        produits.add(buildRubriqueAutresClasse(7, List.of("7061", "7062", "7063", "7064", "7065", "7066", "7067", "7068", "719", "754"),
                "V7", "Autres produits classe 7", d1, d2, true));

        // CHARGES (classe 6)
        List<RubriqueResultatDto> charges = new ArrayList<>();
        charges.add(buildRubrique("C1", "Achats consommes", "601", d1, d2, false));
        charges.add(buildRubriqueRange("C2", "Charges externes", 611, 627, d1, d2, false));
        charges.add(buildRubrique("C3", "Charges de personnel", "641", d1, d2, false));
        charges.add(buildRubrique("C4", "Charges d'amortissement", "656", d1, d2, false));
        charges.add(buildRubrique("C5", "Charges financieres", "671", d1, d2, false));
        charges.add(buildRubriqueAutresClasse(6, prefixesCharges(), "C6", "Autres charges classe 6", d1, d2, false));

        BigDecimal totalProduits = produits.stream().map(RubriqueResultatDto::montant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCharges = charges.stream().map(RubriqueResultatDto::montant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal resultatNet = totalProduits.subtract(totalCharges);

        // Marge brute = ventes (V1+V2+V3+V4) - achats consommes (C1)
        BigDecimal ventes = produits.stream().limit(4)
                .map(RubriqueResultatDto::montant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal achats = charges.get(0).montant();
        BigDecimal margeBrute = ventes.subtract(achats);

        return new CompteResultatDto(
                exercice.getId(),
                exercice.getCode(),
                d1, d2,
                produits,
                totalProduits.setScale(2, RoundingMode.HALF_UP),
                charges,
                totalCharges.setScale(2, RoundingMode.HALF_UP),
                resultatNet.setScale(2, RoundingMode.HALF_UP),
                margeBrute.setScale(2, RoundingMode.HALF_UP),
                Instant.now());
    }

    private static List<String> prefixesCharges() {
        List<String> p = new ArrayList<>();
        p.add("601");
        for (int i = 611; i <= 627; i++) {
            p.add(String.valueOf(i));
        }
        p.add("641");
        p.add("656");
        p.add("671");
        return p;
    }

    private RubriqueResultatDto buildRubrique(String code, String libelle, String prefixe,
                                               LocalDate d1, LocalDate d2, boolean sensCrediteur) {
        return buildRubrique(code, libelle, List.of(prefixe), d1, d2, sensCrediteur);
    }

    private RubriqueResultatDto buildRubrique(String code, String libelle, List<String> prefixes,
                                               LocalDate d1, LocalDate d2, boolean sensCrediteur) {
        List<LigneResultatDto> lignes = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (String px : prefixes) {
            List<String> codes = ligneEcritureRepository
                    .findDistinctCompteCodesByDateBetweenAndPrefixe(d1, d2, px + "%");
            for (String compteCode : codes) {
                BigDecimal montant = computeSolde(compteCode, d1, d2, sensCrediteur);
                if (montant.signum() == 0) {
                    continue;
                }
                String libelleCompte = pcgRepository.findByCompteCode(compteCode)
                        .map(PlanComptableGeneral::getLibelle)
                        .orElse(compteCode);
                lignes.add(new LigneResultatDto(compteCode, libelleCompte,
                        montant.setScale(2, RoundingMode.HALF_UP)));
                total = total.add(montant);
            }
        }
        return new RubriqueResultatDto(code, libelle, lignes,
                total.setScale(2, RoundingMode.HALF_UP));
    }

    /** Rubrique sur une plage de prefixes numeriques contigus. */
    private RubriqueResultatDto buildRubriqueRange(String code, String libelle, int start, int end,
                                                    LocalDate d1, LocalDate d2, boolean sensCrediteur) {
        List<String> prefixes = new ArrayList<>(end - start + 1);
        for (int i = start; i <= end; i++) {
            prefixes.add(String.valueOf(i));
        }
        return buildRubrique(code, libelle, prefixes, d1, d2, sensCrediteur);
    }

    /**
     * Rubrique "autres" : tous les comptes de la classe MOINS ceux deja couverts
     * par les rubriques explicites.
     */
    private RubriqueResultatDto buildRubriqueAutresClasse(int classe, List<String> excludesPrefixes,
                                                           String code, String libelle,
                                                           LocalDate d1, LocalDate d2,
                                                           boolean sensCrediteur) {
        List<String> codes = ligneEcritureRepository
                .findDistinctCompteCodesByDateBetweenAndPrefixe(d1, d2, classe + "%");
        List<LigneResultatDto> lignes = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (String compteCode : codes) {
            boolean exclu = false;
            for (String p : excludesPrefixes) {
                if (compteCode.startsWith(p)) {
                    exclu = true;
                    break;
                }
            }
            if (exclu) {
                continue;
            }
            BigDecimal montant = computeSolde(compteCode, d1, d2, sensCrediteur);
            if (montant.signum() == 0) {
                continue;
            }
            String libelleCompte = pcgRepository.findByCompteCode(compteCode)
                    .map(PlanComptableGeneral::getLibelle)
                    .orElse(compteCode);
            lignes.add(new LigneResultatDto(compteCode, libelleCompte,
                    montant.setScale(2, RoundingMode.HALF_UP)));
            total = total.add(montant);
        }
        return new RubriqueResultatDto(code, libelle, lignes,
                total.setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal computeSolde(String compteCode, LocalDate d1, LocalDate d2, boolean sensCrediteur) {
        BigDecimal d = nz(ligneEcritureRepository.sumByCompteCodeAndDateBetween(
                compteCode, d1, d2, SensLigne.DEBIT));
        BigDecimal c = nz(ligneEcritureRepository.sumByCompteCodeAndDateBetween(
                compteCode, d1, d2, SensLigne.CREDIT));
        BigDecimal solde = sensCrediteur ? c.subtract(d) : d.subtract(c);
        return solde.signum() > 0 ? solde : BigDecimal.ZERO;
    }

    @Override
    public byte[] exportXlsx(Long exerciceId, LocalDate dateDebut, LocalDate dateFin) {
        CompteResultatDto dto = compute(exerciceId, dateDebut, dateFin);

        List<FlatRow> rows = new ArrayList<>();
        rows.add(FlatRow.section("PRODUITS"));
        for (RubriqueResultatDto r : dto.produits()) {
            rows.add(FlatRow.rubrique(r));
            for (LigneResultatDto l : r.lignes()) {
                rows.add(FlatRow.ligne(l));
            }
        }
        rows.add(FlatRow.total("TOTAL PRODUITS", dto.totalProduits()));
        rows.add(FlatRow.blank());
        rows.add(FlatRow.section("CHARGES"));
        for (RubriqueResultatDto r : dto.charges()) {
            rows.add(FlatRow.rubrique(r));
            for (LigneResultatDto l : r.lignes()) {
                rows.add(FlatRow.ligne(l));
            }
        }
        rows.add(FlatRow.total("TOTAL CHARGES", dto.totalCharges()));
        rows.add(FlatRow.blank());
        rows.add(FlatRow.total("MARGE BRUTE", dto.margeBrute()));
        rows.add(FlatRow.total("RESULTAT NET", dto.resultatNet()));

        List<ColumnSpec<FlatRow>> columns = List.of(
                new ColumnSpec<>("Section", ColumnType.TEXT, FlatRow::section),
                new ColumnSpec<>("Compte", ColumnType.TEXT, FlatRow::compteCode),
                new ColumnSpec<>("Libelle", ColumnType.TEXT, FlatRow::libelle),
                new ColumnSpec<>("Montant", ColumnType.MONEY, FlatRow::montant));

        return xlsxExportService.export("Compte resultat " + dto.exerciceCode(), columns, rows);
    }

    @Override
    public byte[] exportPdf(Long exerciceId, LocalDate dateDebut, LocalDate dateFin) {
        CompteResultatDto dto = compute(exerciceId, dateDebut, dateFin);
        Document doc = EtatsPdfHelper.newPortraitDocument();
        ByteArrayOutputStream out = EtatsPdfHelper.open(doc);
        try {
            String periodeLib = "Periode : " + dto.dateDebut().format(EtatsPdfHelper.DATE_FMT)
                    + " - " + dto.dateFin().format(EtatsPdfHelper.DATE_FMT)
                    + " (Exercice " + dto.exerciceCode() + ")";
            EtatsPdfHelper.addHeader(doc, "Compte de resultat SYSCOHADA simplifie", "", periodeLib);

            float[] widths = {1f, 1f};
            PdfPTable two = new PdfPTable(widths);
            try {
                two.setWidths(widths);
                two.setWidthPercentage(100f);
            } catch (Exception e) {
                throw new BusinessException("error.etat.pdf.failed");
            }
            PdfPCell prodCell = new PdfPCell(buildSectionTable("PRODUITS", dto.produits(), dto.totalProduits()));
            prodCell.setBorder(0);
            prodCell.setVerticalAlignment(Element.ALIGN_TOP);
            two.addCell(prodCell);
            PdfPCell chCell = new PdfPCell(buildSectionTable("CHARGES", dto.charges(), dto.totalCharges()));
            chCell.setBorder(0);
            chCell.setVerticalAlignment(Element.ALIGN_TOP);
            two.addCell(chCell);
            EtatsPdfHelper.addTable(doc, two);

            EtatsPdfHelper.addParagraph(doc,
                    "Marge brute : " + EtatsPdfHelper.money(dto.margeBrute()) + " MRU", 10f, false);
            EtatsPdfHelper.addParagraph(doc,
                    "Resultat net : " + EtatsPdfHelper.money(dto.resultatNet()) + " MRU", 12f, true);
            return EtatsPdfHelper.close(doc, out);
        } catch (RuntimeException e) {
            log.error("Compte de resultat PDF export failed", e);
            if (doc.isOpen()) {
                doc.close();
            }
            throw e;
        }
    }

    private PdfPTable buildSectionTable(String titre, List<RubriqueResultatDto> rubriques,
                                         BigDecimal total) {
        float[] widths = {2f, 4.5f, 2.5f};
        PdfPTable table = EtatsPdfHelper.newTable(widths);
        table.setSpacingBefore(0f);
        table.addCell(EtatsPdfHelper.sectionCell(titre, 3));
        table.addCell(EtatsPdfHelper.headerCell("Compte"));
        table.addCell(EtatsPdfHelper.headerCell("Libelle"));
        table.addCell(EtatsPdfHelper.headerCell("Montant"));
        for (RubriqueResultatDto r : rubriques) {
            table.addCell(EtatsPdfHelper.sectionCell(r.libelle(), 2));
            table.addCell(EtatsPdfHelper.totalMoneyCell(r.montant()));
            for (LigneResultatDto l : r.lignes()) {
                table.addCell(EtatsPdfHelper.textCell(l.compteCode()));
                table.addCell(EtatsPdfHelper.textCell(l.compteLibelle()));
                table.addCell(EtatsPdfHelper.moneyCell(l.montant()));
            }
        }
        table.addCell(EtatsPdfHelper.totalLabelCell("TOTAL " + titre));
        table.addCell(EtatsPdfHelper.totalLabelCell(""));
        table.addCell(EtatsPdfHelper.totalMoneyCell(total));
        return table;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private record FlatRow(String section, String compteCode, String libelle, BigDecimal montant) {
        static FlatRow section(String name) {
            return new FlatRow(name, null, null, null);
        }
        static FlatRow rubrique(RubriqueResultatDto r) {
            return new FlatRow(null, null, r.libelle(), r.montant());
        }
        static FlatRow ligne(LigneResultatDto l) {
            return new FlatRow(null, l.compteCode(), l.compteLibelle(), l.montant());
        }
        static FlatRow total(String label, BigDecimal value) {
            return new FlatRow(null, null, label, value);
        }
        static FlatRow blank() {
            return new FlatRow(null, null, null, null);
        }
    }
}
