import { Injectable } from '@angular/core';
import {
  Chart,
  ChartConfiguration,
  ChartType,
  registerables,
  ChartData,
  ChartOptions
} from '../../../node_modules/chart.js';

// Enregistrer tous les composants Chart.js
Chart.register(...registerables);

export interface ChartDataset {
  label: string;
  data: number[];
  backgroundColor?: string | string[];
  borderColor?: string | string[];
  borderWidth?: number;
}

export interface ChartConfig {
  type: ChartType;
  data: ChartData;
  options?: ChartOptions;
}

@Injectable({
  providedIn: 'root'
})
export class ChartService {
  private charts: Map<string, Chart> = new Map();

  constructor() {
    // Configuration globale des graphiques
    Chart.defaults.font.family = 'Inter, system-ui, sans-serif';
    Chart.defaults.color = '#6c757d';
    Chart.defaults.scale.grid.color = 'rgba(0, 0, 0, 0.1)';
  }

  /**
   * Créer un graphique en ligne
   */
  createLineChart(
    canvasId: string,
    labels: string[],
    datasets: ChartDataset[],
    options?: Partial<ChartOptions>
  ): Chart | null {
    const canvas = document.getElementById(canvasId) as HTMLCanvasElement;
    if (!canvas) {
      console.error(`Canvas avec l'ID ${canvasId} non trouvé`);
      return null;
    }

    const config: ChartConfiguration = {
      type: 'line',
      data: {
        labels,
        datasets: datasets.map(dataset => ({
          ...dataset,
          borderColor: dataset.borderColor || '#2196f3',
          backgroundColor: dataset.backgroundColor || 'rgba(33, 150, 243, 0.1)',
          borderWidth: dataset.borderWidth || 2,
          fill: true,
          tension: 0.4
        }))
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'top',
          },
          tooltip: {
            mode: 'index',
            intersect: false,
          }
        },
        scales: {
          x: {
            display: true,
            grid: {
              display: false
            }
          },
          y: {
            display: true,
            beginAtZero: true,
            grid: {
              color: 'rgba(0, 0, 0, 0.05)'
            }
          }
        },
        interaction: {
          mode: 'nearest',
          axis: 'x',
          intersect: false
        },
        ...options
      }
    };

    return this.createChart(canvasId, config);
  }

  /**
   * Créer un graphique en barres
   */
  createBarChart(
    canvasId: string,
    labels: string[],
    datasets: ChartDataset[],
    options?: Partial<ChartOptions>
  ): Chart | null {
    const canvas = document.getElementById(canvasId) as HTMLCanvasElement;
    if (!canvas) {
      console.error(`Canvas avec l'ID ${canvasId} non trouvé`);
      return null;
    }

    const config: ChartConfiguration = {
      type: 'bar',
      data: {
        labels,
        datasets: datasets.map(dataset => ({
          ...dataset,
          backgroundColor: dataset.backgroundColor || [
            '#2196f3', '#28a745', '#ffc107', '#dc3545', 
            '#17a2b8', '#6f42c1', '#fd7e14', '#20c997'
          ],
          borderColor: dataset.borderColor || 'transparent',
          borderWidth: dataset.borderWidth || 0,
          borderRadius: 4
        }))
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'top',
          }
        },
        scales: {
          x: {
            grid: {
              display: false
            }
          },
          y: {
            beginAtZero: true,
            grid: {
              color: 'rgba(0, 0, 0, 0.05)'
            }
          }
        },
        ...options
      }
    };

    return this.createChart(canvasId, config);
  }

  /**
   * Créer un graphique en secteurs (donut)
   */
  /*createDoughnutChart(
    canvasId: string,
    labels: string[],
    data: number[],
    options?: Partial<ChartOptions>
  ): Chart | null {
    const canvas = document.getElementById(canvasId) as HTMLCanvasElement;
    if (!canvas) {
      console.error(`Canvas avec l'ID ${canvasId} non trouvé`);
      return null;
    }

    const config: ChartConfiguration = {
      type: 'doughnut',
      data: {
        labels,
        datasets: [{
          data,
          backgroundColor: [
            '#2196f3', '#28a745', '#ffc107', '#dc3545',
            '#17a2b8', '#6f42c1', '#fd7e14', '#20c997'
          ],
          borderWidth: 0,
          hoverBorderWidth: 2,
          hoverBorderColor: '#fff'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'bottom',
            labels: {
              padding: 20,
              usePointStyle: true
            }
          },
          tooltip: {
            callbacks: {
              label: (context) => {
                const total = context.dataset.data.reduce((a: number, b: number) => a + b, 0);
                const percentage = ((context.parsed * 100) / total).toFixed(1);
                return `${context.label}: ${context.parsed} (${percentage}%)`;
              }
            }
          }
        },
        cutout: '60%',
        ...options
      }
    };

    return this.createChart(canvasId, config);
  }*/

  /**
   * Créer un graphique générique
   */
  createChart(canvasId: string, config: ChartConfiguration): Chart | null {
    try {
      // Détruire le graphique existant s'il y en a un
      this.destroyChart(canvasId);

      const canvas = document.getElementById(canvasId) as HTMLCanvasElement;
      if (!canvas) {
        console.error(`Canvas avec l'ID ${canvasId} non trouvé`);
        return null;
      }

      const chart = new Chart(canvas, config);
      this.charts.set(canvasId, chart);
      
      return chart;
    } catch (error) {
      console.error('Erreur lors de la création du graphique:', error);
      return null;
    }
  }

  /**
   * Mettre à jour les données d'un graphique
   */
  updateChart(canvasId: string, newData: ChartData): void {
    const chart = this.charts.get(canvasId);
    if (chart) {
      chart.data = newData;
      chart.update();
    }
  }

  /**
   * Détruire un graphique
   */
  destroyChart(canvasId: string): void {
    const chart = this.charts.get(canvasId);
    if (chart) {
      chart.destroy();
      this.charts.delete(canvasId);
    }
  }

  /**
   * Détruire tous les graphiques
   */
  destroyAllCharts(): void {
    this.charts.forEach(chart => chart.destroy());
    this.charts.clear();
  }

  /**
   * Exporter un graphique en image
   */
  exportChart(canvasId: string, format: 'png' | 'jpeg' = 'png'): string | null {
    const chart = this.charts.get(canvasId);
    if (chart) {
      return chart.toBase64Image(`image/${format}`, 1.0);
    }
    return null;
  }

  /**
   * Redimensionner un graphique
   */
  resizeChart(canvasId: string): void {
    const chart = this.charts.get(canvasId);
    if (chart) {
      chart.resize();
    }
  }

  /**
   * Obtenir les couleurs par défaut pour les graphiques
   */
  getDefaultColors(): string[] {
    return [
      '#2196f3', // Bleu principal
      '#28a745', // Vert
      '#ffc107', // Jaune
      '#dc3545', // Rouge
      '#17a2b8', // Cyan
      '#6f42c1', // Violet
      '#fd7e14', // Orange
      '#20c997', // Teal
      '#6c757d', // Gris
      '#e83e8c'  // Rose
    ];
  }

  /**
   * Générer des couleurs dégradées
   */
  generateGradientColors(baseColor: string, count: number): string[] {
    const colors: string[] = [];
    const opacity = 1;
    
    for (let i = 0; i < count; i++) {
      const factor = (i + 1) / count;
      colors.push(`${baseColor}${Math.round(opacity * factor * 255).toString(16).padStart(2, '0')}`);
    }
    
    return colors;
  }
}