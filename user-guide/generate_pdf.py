#!/usr/bin/env python3
"""
Génère le PDF du guide utilisateur "Cycle de vie d'une réservation"
à partir du Markdown source.

Dépend de reportlab :
    pip install reportlab

Usage :
    python3 generate_pdf.py
    # → produit 01-cycle-vie-reservation.pdf dans le même dossier
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

try:
    from reportlab.lib import colors
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
    from reportlab.lib.units import cm
    from reportlab.platypus import (
        Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle,
        PageBreak, Preformatted, KeepTogether,
    )
    from reportlab.lib.enums import TA_LEFT, TA_CENTER
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
except ImportError:
    print("ERREUR : reportlab n'est pas installé.")
    print("Installer avec : pip install reportlab")
    sys.exit(1)


HERE = Path(__file__).parent
MD_SRC = HERE / "01-cycle-vie-reservation.md"
PDF_OUT = HERE / "01-cycle-vie-reservation.pdf"


# ─── Styles ────────────────────────────────────────────────────────────
def build_styles():
    base = getSampleStyleSheet()
    styles = {
        "title": ParagraphStyle(
            "Title", parent=base["Title"],
            fontSize=22, leading=28, spaceAfter=12,
            textColor=colors.HexColor("#0d6efd"),
            alignment=TA_LEFT,
        ),
        "subtitle": ParagraphStyle(
            "Subtitle", parent=base["Normal"],
            fontSize=11, leading=14, spaceAfter=20,
            textColor=colors.HexColor("#6c757d"),
            italic=True,
        ),
        "h1": ParagraphStyle(
            "H1", parent=base["Heading1"],
            fontSize=18, leading=22, spaceBefore=18, spaceAfter=10,
            textColor=colors.HexColor("#0d6efd"),
            borderPadding=(0, 0, 4, 0),
        ),
        "h2": ParagraphStyle(
            "H2", parent=base["Heading2"],
            fontSize=14, leading=18, spaceBefore=14, spaceAfter=8,
            textColor=colors.HexColor("#212529"),
        ),
        "h3": ParagraphStyle(
            "H3", parent=base["Heading3"],
            fontSize=12, leading=15, spaceBefore=10, spaceAfter=6,
            textColor=colors.HexColor("#495057"),
        ),
        "h4": ParagraphStyle(
            "H4", parent=base["Heading4"],
            fontSize=11, leading=14, spaceBefore=8, spaceAfter=4,
            textColor=colors.HexColor("#6c757d"),
        ),
        "body": ParagraphStyle(
            "Body", parent=base["BodyText"],
            fontSize=10, leading=14, spaceAfter=6,
            textColor=colors.HexColor("#212529"),
        ),
        "bullet": ParagraphStyle(
            "Bullet", parent=base["BodyText"],
            fontSize=10, leading=14, spaceAfter=3,
            leftIndent=18, bulletIndent=6,
        ),
        "code": ParagraphStyle(
            "Code", parent=base["Code"],
            fontName="Courier", fontSize=8.5, leading=11,
            backColor=colors.HexColor("#f6f8fa"),
            borderColor=colors.HexColor("#e1e4e8"),
            borderPadding=8, borderWidth=0.5,
            spaceAfter=10,
        ),
        "note": ParagraphStyle(
            "Note", parent=base["BodyText"],
            fontSize=9.5, leading=13,
            textColor=colors.HexColor("#6c757d"),
            italic=True,
            backColor=colors.HexColor("#fff3cd"),
            borderColor=colors.HexColor("#ffeaa7"),
            borderPadding=8, borderWidth=0.5,
            spaceAfter=10,
        ),
    }
    return styles


# ─── Helpers Markdown très simple ──────────────────────────────────────
INLINE_BOLD = re.compile(r"\*\*(.+?)\*\*")
INLINE_ITALIC = re.compile(r"(?<![*])\*(?!\*)(.+?)\*(?!\*)")
INLINE_CODE = re.compile(r"`([^`]+)`")
INLINE_LINK = re.compile(r"\[([^\]]+)\]\(([^)]+)\)")


def md_inline_to_html(text: str) -> str:
    """Transforme markdown inline en mini-HTML compatible reportlab Paragraph."""
    # Échapper d'abord les caractères XML
    text = (text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;"))
    # Code inline d'abord (pour éviter conflit avec * dans le code)
    text = INLINE_CODE.sub(
        r'<font name="Courier" size="9" color="#d63384">\1</font>',
        text,
    )
    # Gras
    text = INLINE_BOLD.sub(r"<b>\1</b>", text)
    # Italique (en évitant les ** déjà traités)
    text = INLINE_ITALIC.sub(r"<i>\1</i>", text)
    # Liens (rendu simple : texte uniquement souligné)
    text = INLINE_LINK.sub(r'<u>\1</u>', text)
    return text


# ─── Parser Markdown → flowables reportlab ─────────────────────────────
def parse_md(md_text: str, styles):
    """Parse simple ligne-par-ligne. Couvre les éléments utilisés dans le doc."""
    lines = md_text.splitlines()
    flowables = []
    i = 0
    while i < len(lines):
        line = lines[i]

        # Titres
        if line.startswith("# "):
            flowables.append(Paragraph(md_inline_to_html(line[2:].strip()), styles["title"]))
            i += 1
            continue
        if line.startswith("## "):
            flowables.append(Paragraph(md_inline_to_html(line[3:].strip()), styles["h1"]))
            i += 1
            continue
        if line.startswith("### "):
            flowables.append(Paragraph(md_inline_to_html(line[4:].strip()), styles["h2"]))
            i += 1
            continue
        if line.startswith("#### "):
            flowables.append(Paragraph(md_inline_to_html(line[5:].strip()), styles["h3"]))
            i += 1
            continue

        # Sous-titre italique sur la 2e ligne après le titre
        if line.startswith("> "):
            flowables.append(Paragraph(md_inline_to_html(line[2:].strip()), styles["note"]))
            i += 1
            continue

        # Bloc code ```
        if line.startswith("```"):
            code_lines = []
            i += 1
            while i < len(lines) and not lines[i].startswith("```"):
                code_lines.append(lines[i])
                i += 1
            if i < len(lines):
                i += 1  # skip closing ```
            code_text = "\n".join(code_lines)
            flowables.append(Preformatted(code_text, styles["code"]))
            continue

        # Tableau : détecté par une ligne "| header | header |" suivie de "| --- | --- |"
        if line.startswith("|") and i + 1 < len(lines) and re.match(r"^\|[-:\s|]+\|$", lines[i + 1]):
            header = [c.strip() for c in line.strip("|").split("|")]
            i += 2  # skip header + separator
            rows = [header]
            while i < len(lines) and lines[i].startswith("|"):
                cells = [c.strip() for c in lines[i].strip("|").split("|")]
                # Convertir markdown inline → ParaG dans chaque cellule pour gras/code
                rows.append([Paragraph(md_inline_to_html(c), styles["body"]) for c in cells])
                i += 1
            # Convertir header en Paragraph aussi
            rows[0] = [Paragraph(f"<b>{md_inline_to_html(c)}</b>", styles["body"]) for c in header]
            tbl = Table(rows, repeatRows=1, hAlign="LEFT")
            tbl.setStyle(TableStyle([
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#0d6efd")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                ("ALIGN", (0, 0), (-1, 0), "LEFT"),
                ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#dee2e6")),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 6),
                ("RIGHTPADDING", (0, 0), (-1, -1), 6),
                ("TOPPADDING", (0, 0), (-1, -1), 4),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1),
                 [colors.HexColor("#ffffff"), colors.HexColor("#f8f9fa")]),
            ]))
            flowables.append(KeepTogether(tbl))
            flowables.append(Spacer(1, 8))
            continue

        # Liste à puces
        if re.match(r"^[-*]\s", line):
            while i < len(lines) and re.match(r"^[-*]\s", lines[i]):
                txt = lines[i][2:].strip()
                flowables.append(Paragraph(
                    "• " + md_inline_to_html(txt),
                    styles["bullet"],
                ))
                i += 1
            flowables.append(Spacer(1, 4))
            continue

        # Liste numérotée
        if re.match(r"^\d+\.\s", line):
            while i < len(lines) and re.match(r"^\d+\.\s", lines[i]):
                m = re.match(r"^(\d+)\.\s(.+)$", lines[i])
                if m:
                    flowables.append(Paragraph(
                        f"<b>{m.group(1)}.</b> {md_inline_to_html(m.group(2))}",
                        styles["bullet"],
                    ))
                i += 1
            flowables.append(Spacer(1, 4))
            continue

        # Séparateur horizontal ---
        if line.strip() == "---":
            flowables.append(Spacer(1, 6))
            flowables.append(Table(
                [[""]],
                colWidths=[16 * cm], rowHeights=[0.5],
                style=TableStyle([
                    ("LINEABOVE", (0, 0), (-1, -1), 0.6, colors.HexColor("#dee2e6")),
                ]),
            ))
            flowables.append(Spacer(1, 6))
            i += 1
            continue

        # Ligne vide
        if not line.strip():
            i += 1
            continue

        # Paragraphe normal
        # Accumule les lignes jusqu'à la prochaine ligne vide ou élément spécial
        para_lines = [line]
        i += 1
        while i < len(lines) and lines[i].strip() and not (
            lines[i].startswith(("#", "- ", "* ", "> ", "|", "```"))
            or re.match(r"^\d+\.\s", lines[i])
            or lines[i].strip() == "---"
        ):
            para_lines.append(lines[i])
            i += 1
        text = " ".join(l.strip() for l in para_lines)
        flowables.append(Paragraph(md_inline_to_html(text), styles["body"]))

    return flowables


# ─── Pieds / entêtes de page ───────────────────────────────────────────
def add_page_decoration(canvas, doc):
    canvas.saveState()
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(colors.HexColor("#6c757d"))
    # En-tête
    canvas.drawString(2 * cm, A4[1] - 1.2 * cm,
                      "City Hotel — Guide utilisateur")
    canvas.drawRightString(A4[0] - 2 * cm, A4[1] - 1.2 * cm,
                            "Cycle de vie d'une réservation")
    canvas.setStrokeColor(colors.HexColor("#dee2e6"))
    canvas.setLineWidth(0.4)
    canvas.line(2 * cm, A4[1] - 1.4 * cm, A4[0] - 2 * cm, A4[1] - 1.4 * cm)
    # Pied
    canvas.line(2 * cm, 1.4 * cm, A4[0] - 2 * cm, 1.4 * cm)
    canvas.drawCentredString(A4[0] / 2, 1.0 * cm, f"— Page {doc.page} —")
    canvas.restoreState()


# ─── Main ──────────────────────────────────────────────────────────────
def main():
    if not MD_SRC.exists():
        print(f"ERREUR : source Markdown introuvable : {MD_SRC}")
        sys.exit(1)
    md = MD_SRC.read_text(encoding="utf-8")
    styles = build_styles()
    flowables = parse_md(md, styles)

    doc = SimpleDocTemplate(
        str(PDF_OUT),
        pagesize=A4,
        leftMargin=2 * cm, rightMargin=2 * cm,
        topMargin=2 * cm, bottomMargin=2 * cm,
        title="City Hotel — Cycle de vie d'une réservation",
        author="City Hotel",
        subject="Guide utilisateur",
    )
    doc.build(flowables,
              onFirstPage=add_page_decoration,
              onLaterPages=add_page_decoration)
    print(f"OK — PDF généré : {PDF_OUT}")
    print(f"     Taille : {PDF_OUT.stat().st_size / 1024:.1f} Ko")


if __name__ == "__main__":
    main()
