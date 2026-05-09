import { Injectable } from '@angular/core';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import { format } from 'date-fns';
import { fr } from 'date-fns/locale';

export interface TableColumn {
  title: string;
  dataKey: string;
  width?: number;
}

export interface TableData {
  [key: string]: string | number | Date;
}

export interface PdfOptions {
  title?: string;
  subtitle?: string;
  orientation?: 'portrait' | 'landscape';
  format?: 'a4' | 'a3' | 'letter';
  margins?: {
    top: number;
    right: number;
    bottom: number;
    left: number;
  };
  footer?: string;
  logo?: string;
}

@Injectable({
  providedIn: 'root'
})
export class PdfService {
  private readonly defaultMargins = {
    top: 20,
    right: 20,
    bottom: 20,
    left: 20
  };

  constructor() {}

  /**
   * Créer un nouveau document PDF
   */
  createDocument(options: PdfOptions = {}): jsPDF {
    const doc = new jsPDF({
      orientation: options.orientation || 'portrait',
      unit: 'mm',
      format: options.format || 'a4'
    });

    // Configuration des polices
    doc.setFont('helvetica');
    
    return doc;
  }

  /**
   * Ajouter un en-tête au document
   */
  addHeader(
    doc: jsPDF, 
    title: string, 
    subtitle?: string, 
    logo?: string,
    hotelInfo?: { name: string; address?: string; phone?: string; email?: string }
  ): number {
    let yPosition = 20;
    const pageWidth = doc.internal.pageSize.getWidth();

    // Logo (si fourni)
    if (logo) {
      try {
        doc.addImage(logo, 'PNG', 20, yPosition, 30, 20);
        yPosition += 25;
      } catch (error) {
        console.warn('Erreur lors de l\'ajout du logo:', error);
      }
    }

    // Informations de l'hôtel
    if (hotelInfo) {
      doc.setFontSize(12);
      doc.setFont('helvetica', 'bold');
      doc.text(hotelInfo.name, pageWidth - 20, yPosition, { align: 'right' });
      
      doc.setFontSize(9);
      doc.setFont('helvetica', 'normal');
      
      if (hotelInfo.address) {
        yPosition += 5;
        doc.text(hotelInfo.address, pageWidth - 20, yPosition, { align: 'right' });
      }
      if (hotelInfo.phone) {
        yPosition += 4;
        doc.text(`Tél: ${hotelInfo.phone}`, pageWidth - 20, yPosition, { align: 'right' });
      }
      if (hotelInfo.email) {
        yPosition += 4;
        doc.text(`Email: ${hotelInfo.email}`, pageWidth - 20, yPosition, { align: 'right' });
      }
    }

    // Titre principal
    yPosition += 15;
    doc.setFontSize(20);
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(33, 150, 243); // Couleur primaire
    doc.text(title, pageWidth / 2, yPosition, { align: 'center' });

    // Sous-titre
    if (subtitle) {
      yPosition += 10;
      doc.setFontSize(12);
      doc.setFont('helvetica', 'normal');
      doc.setTextColor(108, 117, 125); // Couleur secondaire
      doc.text(subtitle, pageWidth / 2, yPosition, { align: 'center' });
    }

    // Date de génération
    yPosition += 8;
    doc.setFontSize(9);
    doc.setTextColor(108, 117, 125);
    const dateStr = format(new Date(), 'dd/MM/yyyy à HH:mm', { locale: fr });
    doc.text(`Généré le ${dateStr}`, pageWidth / 2, yPosition, { align: 'center' });

    // Ligne de séparation
    yPosition += 5;
    doc.setDrawColor(33, 150, 243);
    doc.setLineWidth(0.5);
    doc.line(20, yPosition, pageWidth - 20, yPosition);

    // Réinitialiser la couleur du texte
    doc.setTextColor(0, 0, 0);

    return yPosition + 10;
  }

  /**
   * Ajouter un pied de page
   */
  addFooter(doc: jsPDF, footerText?: string): void {
    const pageHeight = doc.internal.pageSize.getHeight();
    const pageWidth = doc.internal.pageSize.getWidth();
    
    // Ligne de séparation
    doc.setDrawColor(200, 200, 200);
    doc.setLineWidth(0.3);
    doc.line(20, pageHeight - 15, pageWidth - 20, pageHeight - 15);

    // Texte du pied de page
    doc.setFontSize(8);
    doc.setTextColor(108, 117, 125);
    
    if (footerText) {
      doc.text(footerText, 20, pageHeight - 10);
    }

    // Numéro de page
    const pageCount = doc.getNumberOfPages();
    const currentPage = doc.getCurrentPageInfo().pageNumber;
    doc.text(
      `Page ${currentPage} sur ${pageCount}`,
      pageWidth - 20,
      pageHeight - 10,
      { align: 'right' }
    );
  }

  /**
   * Générer un tableau avec autoTable
   */
  generateTable(
    doc: jsPDF,
    columns: TableColumn[],
    data: TableData[],
    startY: number = 50,
    options: any = {}
  ): number {
    const tableOptions = {
      startY,
      head: [columns.map(col => col.title)],
      body: data.map(row => columns.map(col => {
        const value = row[col.dataKey];
        if (value instanceof Date) {
          return format(value, 'dd/MM/yyyy');
        }
        return String(value || '');
      })),
      styles: {
        fontSize: 10,
        cellPadding: 3,
        lineColor: [220, 220, 220],
        lineWidth: 0.1
      },
      headStyles: {
        fillColor: [33, 150, 243],
        textColor: [255, 255, 255],
        fontStyle: 'bold',
        halign: 'center'
      },
      alternateRowStyles: {
        fillColor: [248, 249, 250]
      },
      columnStyles: {},
      margin: { top: 20, right: 20, bottom: 20, left: 20 },
      ...options
    };

    // Configuration des largeurs de colonnes
    columns.forEach((col, index) => {
      if (col.width) {
        tableOptions.columnStyles[index] = { cellWidth: col.width };
      }
    });

    autoTable(doc, tableOptions);

    // Retourner la position Y après le tableau
    return (doc as any).lastAutoTable.finalY + 10;
  }

  /**
   * Ajouter du texte formaté
   */
  addFormattedText(
    doc: jsPDF,
    text: string,
    x: number,
    y: number,
    options: {
      fontSize?: number;
      fontStyle?: 'normal' | 'bold' | 'italic';
      color?: [number, number, number];
      align?: 'left' | 'center' | 'right';
      maxWidth?: number;
    } = {}
  ): number {
    const {
      fontSize = 10,
      fontStyle = 'normal',
      color = [0, 0, 0],
      align = 'left',
      maxWidth
    } = options;

    doc.setFontSize(fontSize);
    doc.setFont('helvetica', fontStyle);
    doc.setTextColor(color[0], color[1], color[2]);

    if (maxWidth) {
      const lines = doc.splitTextToSize(text, maxWidth);
      doc.text(lines, x, y, { align });
      return y + (lines.length * fontSize * 0.35);
    } else {
      doc.text(text, x, y, { align });
      return y + (fontSize * 0.35);
    }
  }

  /**
   * Générer un rapport de réservations
   */
  generateReservationReport(
    reservations: any[],
    hotelInfo: any,
    period: { start: Date; end: Date }
  ): void {
    const doc = this.createDocument({ orientation: 'landscape' });
    
    const subtitle = `Période du ${format(period.start, 'dd/MM/yyyy')} au ${format(period.end, 'dd/MM/yyyy')}`;
    let yPosition = this.addHeader(doc, 'Rapport des Réservations', subtitle, undefined, hotelInfo);

    // Statistiques générales
    yPosition += 10;
    const totalReservations = reservations.length;
    const totalRevenu = reservations.reduce((sum, res) => sum + (res.montant || 0), 0);

    doc.setFontSize(12);
    doc.setFont('helvetica', 'bold');
    doc.text('Statistiques de la période:', 20, yPosition);
    
    yPosition += 8;
    doc.setFontSize(10);
    doc.setFont('helvetica', 'normal');
    doc.text(`• Nombre total de réservations: ${totalReservations}`, 25, yPosition);
    
    yPosition += 6;
    doc.text(`• Revenu total: ${totalRevenu.toLocaleString('fr-FR')} MRU`, 25, yPosition);

    // Tableau des réservations
    yPosition += 15;
    const columns: TableColumn[] = [
      { title: 'N° Réservation', dataKey: 'numero', width: 30 },
      { title: 'Client', dataKey: 'clientNom', width: 40 },
      { title: 'Chambre', dataKey: 'chambre', width: 25 },
      { title: 'Arrivée', dataKey: 'dateArrivee', width: 30 },
      { title: 'Départ', dataKey: 'dateDepart', width: 30 },
      { title: 'Nuits', dataKey: 'nombreNuits', width: 20 },
      { title: 'Montant', dataKey: 'montant', width: 30 },
      { title: 'Statut', dataKey: 'statut', width: 25 }
    ];

    this.generateTable(doc, columns, reservations, yPosition);

    // Pied de page
    this.addFooter(doc, `© ${new Date().getFullYear()} City Hotel - Rapport confidentiel`);

    // Télécharger le PDF
    const filename = `rapport-reservations-${format(new Date(), 'yyyy-MM-dd')}.pdf`;
    doc.save(filename);
  }

  /**
   * Générer un rapport financier
   */
  generateFinancialReport(
    transactions: any[],
    hotelInfo: any,
    period: { start: Date; end: Date }
  ): void {
    const doc = this.createDocument();
    
    const subtitle = `Période du ${format(period.start, 'dd/MM/yyyy')} au ${format(period.end, 'dd/MM/yyyy')}`;
    let yPosition = this.addHeader(doc, 'Rapport Financier', subtitle, undefined, hotelInfo);

    // Résumé financier
    yPosition += 10;
    const recettes = transactions.filter(t => t.type === 'recette').reduce((sum, t) => sum + t.montant, 0);
    const depenses = transactions.filter(t => t.type === 'depense').reduce((sum, t) => sum + t.montant, 0);
    const benefice = recettes - depenses;

    doc.setFontSize(14);
    doc.setFont('helvetica', 'bold');
    doc.text('Résumé Financier', 20, yPosition);

    yPosition += 15;
    doc.setFontSize(12);
    doc.setTextColor(40, 167, 69); // Vert pour les recettes
    doc.text(`Recettes: ${recettes.toLocaleString('fr-FR')} MRU`, 20, yPosition);

    yPosition += 8;
    doc.setTextColor(220, 53, 69); // Rouge pour les dépenses
    doc.text(`Dépenses: ${depenses.toLocaleString('fr-FR')} MRU`, 20, yPosition);

    yPosition += 8;
    doc.setTextColor(benefice >= 0 ? 40 : 220, benefice >= 0 ? 167 : 53, benefice >= 0 ? 69 : 69);
    doc.text(`Bénéfice: ${benefice.toLocaleString('fr-FR')} MRU`, 20, yPosition);

    // Tableau des transactions
    yPosition += 20;
    doc.setTextColor(0, 0, 0);
    const columns: TableColumn[] = [
      { title: 'Date', dataKey: 'date', width: 30 },
      { title: 'Description', dataKey: 'description', width: 80 },
      { title: 'Type', dataKey: 'type', width: 25 },
      { title: 'Montant', dataKey: 'montant', width: 30 },
      { title: 'Catégorie', dataKey: 'categorie', width: 35 }
    ];

    this.generateTable(doc, columns, transactions, yPosition);

    this.addFooter(doc, `© ${new Date().getFullYear()} City Hotel - Document confidentiel`);

    const filename = `rapport-financier-${format(new Date(), 'yyyy-MM-dd')}.pdf`;
    doc.save(filename);
  }

  /**
   * Générer une facture
   */
  generateInvoice(
    invoiceData: any,
    hotelInfo: any
  ): void {
    const doc = this.createDocument();
    
    let yPosition = this.addHeader(
      doc, 
      `Facture N° ${invoiceData.numero}`, 
      `Date: ${format(invoiceData.date, 'dd/MM/yyyy')}`,
      undefined,
      hotelInfo
    );

    // Informations client
    yPosition += 15;
    doc.setFontSize(12);
    doc.setFont('helvetica', 'bold');
    doc.text('Facturé à:', 20, yPosition);

    yPosition += 8;
    doc.setFontSize(10);
    doc.setFont('helvetica', 'normal');
    doc.text(invoiceData.client.nom, 20, yPosition);
    
    if (invoiceData.client.adresse) {
      yPosition += 5;
      doc.text(invoiceData.client.adresse, 20, yPosition);
    }

    // Tableau des lignes de facture
    yPosition += 20;
    const columns: TableColumn[] = [
      { title: 'Description', dataKey: 'description', width: 80 },
      { title: 'Qté', dataKey: 'quantite', width: 20 },
      { title: 'Prix Unit.', dataKey: 'prixUnitaire', width: 30 },
      { title: 'Total', dataKey: 'total', width: 30 }
    ];

    const finalY = this.generateTable(doc, columns, invoiceData.lignes, yPosition, {
      footStyles: {
        fillColor: [33, 150, 243],
        textColor: [255, 255, 255],
        fontStyle: 'bold'
      }
    });

    // Total
    const pageWidth = doc.internal.pageSize.getWidth();
    let totalY = finalY + 10;
    
    doc.setFontSize(12);
    doc.setFont('helvetica', 'bold');
    doc.text(`Total HT: ${invoiceData.totalHT.toLocaleString('fr-FR')} MRU`, pageWidth - 20, totalY, { align: 'right' });
    
    totalY += 8;
    doc.text(`TVA: ${invoiceData.tva.toLocaleString('fr-FR')} MRU`, pageWidth - 20, totalY, { align: 'right' });
    
    totalY += 8;
    doc.setFontSize(14);
    doc.setTextColor(33, 150, 243);
    doc.text(`Total TTC: ${invoiceData.totalTTC.toLocaleString('fr-FR')} MRU`, pageWidth - 20, totalY, { align: 'right' });

    this.addFooter(doc, 'Merci pour votre confiance - Paiement sous 30 jours');

    const filename = `facture-${invoiceData.numero}-${format(new Date(), 'yyyy-MM-dd')}.pdf`;
    doc.save(filename);
  }

  /**
   * Exporter des données en PDF simple
   */
  exportToPdf(
    title: string,
    columns: TableColumn[],
    data: TableData[],
    options: PdfOptions = {}
  ): void {
    const doc = this.createDocument(options);
    
    let yPosition = this.addHeader(doc, title, options.subtitle);
    
    yPosition += 10;
    this.generateTable(doc, columns, data, yPosition);

    this.addFooter(doc, options.footer);

    const filename = `${title.toLowerCase().replace(/\s+/g, '-')}-${format(new Date(), 'yyyy-MM-dd')}.pdf`;
    doc.save(filename);
  }
}