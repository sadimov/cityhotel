import { Injectable } from '@angular/core';
import {
  format,
  parseISO,
  addDays,
  subDays,
  addMonths,
  subMonths,
  addYears,
  subYears,
  startOfDay,
  endOfDay,
  startOfWeek,
  endOfWeek,
  startOfMonth,
  endOfMonth,
  startOfYear,
  endOfYear,
  differenceInDays,
  differenceInHours,
  differenceInMinutes,
  isWithinInterval,
  isSameDay,
  isSameMonth,
  isSameYear,
  isToday,
  isTomorrow,
  isYesterday,
  isFuture,
  isPast,
  isWeekend,
  getDay,
  getMonth,
  getYear,
  setHours,
  setMinutes,
  isValid,
  compareAsc,
  compareDesc
} from 'date-fns';
import { fr, ar, enUS } from 'date-fns/locale';
import { TranslationService } from './translation.service';

export interface DateRange {
  start: Date;
  end: Date;
}

export interface TimeSlot {
  start: string; // Format HH:mm
  end: string;   // Format HH:mm
  label: string;
}

@Injectable({
  providedIn: 'root'
})
export class DateUtilService {
  private readonly defaultFormat = 'dd/MM/yyyy';
  private readonly timeFormat = 'HH:mm';
  private readonly datetimeFormat = 'dd/MM/yyyy HH:mm';

  constructor(private translationService: TranslationService) {}

  /**
   * Obtenir la locale selon la langue actuelle
   */
  private getLocale() {
    const language = this.translationService.getCurrentLanguage();
    switch (language) {
      case 'ar':
        return ar;
      case 'en':
        return enUS;
      case 'fr':
      default:
        return fr;
    }
  }

  /**
   * Formatter une date selon le format spécifié
   */
  formatDate(date: Date | string | null, formatStr: string = this.defaultFormat): string {
    if (!date) return '';
    
    try {
      const dateObj = typeof date === 'string' ? parseISO(date) : date;
      if (!isValid(dateObj)) return '';
      
      return format(dateObj, formatStr, { locale: this.getLocale() });
    } catch (error) {
      console.error('Erreur lors du formatage de la date:', error);
      return '';
    }
  }

  /**
   * Formatter une date avec l'heure
   */
  formatDateTime(date: Date | string | null): string {
    return this.formatDate(date, this.datetimeFormat);
  }

  /**
   * Formatter seulement l'heure
   */
  formatTime(date: Date | string | null): string {
    return this.formatDate(date, this.timeFormat);
  }

  /**
   * Formatter une date de manière relative (aujourd'hui, hier, demain)
   */
  formatRelativeDate(date: Date | string | null): string {
    if (!date) return '';
    
    try {
      const dateObj = typeof date === 'string' ? parseISO(date) : date;
      if (!isValid(dateObj)) return '';

      if (isToday(dateObj)) {
        return 'Aujourd\'hui';
      } else if (isTomorrow(dateObj)) {
        return 'Demain';
      } else if (isYesterday(dateObj)) {
        return 'Hier';
      } else {
        return this.formatDate(dateObj);
      }
    } catch (error) {
      console.error('Erreur lors du formatage relatif:', error);
      return this.formatDate(date);
    }
  }

  /**
   * Parser une chaîne de date
   */
  parseDate(dateString: string): Date | null {
    if (!dateString) return null;
    
    try {
      const date = parseISO(dateString);
      return isValid(date) ? date : null;
    } catch (error) {
      console.error('Erreur lors du parsing de la date:', error);
      return null;
    }
  }

  /**
   * Obtenir la date actuelle
   */
  getCurrentDate(): Date {
    return new Date();
  }

  /**
   * Obtenir le début de la journée
   */
  getStartOfDay(date: Date = new Date()): Date {
    return startOfDay(date);
  }

  /**
   * Obtenir la fin de la journée
   */
  getEndOfDay(date: Date = new Date()): Date {
    return endOfDay(date);
  }

  /**
   * Obtenir le début de la semaine
   */
  getStartOfWeek(date: Date = new Date()): Date {
    return startOfWeek(date, { locale: this.getLocale() });
  }

  /**
   * Obtenir la fin de la semaine
   */
  getEndOfWeek(date: Date = new Date()): Date {
    return endOfWeek(date, { locale: this.getLocale() });
  }

  /**
   * Obtenir le début du mois
   */
  getStartOfMonth(date: Date = new Date()): Date {
    return startOfMonth(date);
  }

  /**
   * Obtenir la fin du mois
   */
  getEndOfMonth(date: Date = new Date()): Date {
    return endOfMonth(date);
  }

  /**
   * Obtenir le début de l'année
   */
  getStartOfYear(date: Date = new Date()): Date {
    return startOfYear(date);
  }

  /**
   * Obtenir la fin de l'année
   */
  getEndOfYear(date: Date = new Date()): Date {
    return endOfYear(date);
  }

  /**
   * Ajouter des jours à une date
   */
  addDaysToDate(date: Date, days: number): Date {
    return addDays(date, days);
  }

  /**
   * Soustraire des jours à une date
   */
  subtractDaysFromDate(date: Date, days: number): Date {
    return subDays(date, days);
  }

  /**
   * Ajouter des mois à une date
   */
  addMonthsToDate(date: Date, months: number): Date {
    return addMonths(date, months);
  }

  /**
   * Soustraire des mois à une date
   */
  subtractMonthsFromDate(date: Date, months: number): Date {
    return subMonths(date, months);
  }

  /**
   * Calculer la différence en jours entre deux dates
   */
  getDaysDifference(startDate: Date, endDate: Date): number {
    return differenceInDays(endDate, startDate);
  }

  /**
   * Calculer la différence en heures entre deux dates
   */
  getHoursDifference(startDate: Date, endDate: Date): number {
    return differenceInHours(endDate, startDate);
  }

  /**
   * Calculer la différence en minutes entre deux dates
   */
  getMinutesDifference(startDate: Date, endDate: Date): number {
    return differenceInMinutes(endDate, startDate);
  }

  /**
   * Vérifier si une date est dans un intervalle
   */
  isDateInRange(date: Date, startDate: Date, endDate: Date): boolean {
    return isWithinInterval(date, { start: startDate, end: endDate });
  }

  /**
   * Vérifier si deux dates sont le même jour
   */
  isSameDayAs(date1: Date, date2: Date): boolean {
    return isSameDay(date1, date2);
  }

  /**
   * Vérifier si deux dates sont dans le même mois
   */
  isSameMonthAs(date1: Date, date2: Date): boolean {
    return isSameMonth(date1, date2);
  }

  /**
   * Vérifier si une date est aujourd'hui
   */
  isDateToday(date: Date): boolean {
    return isToday(date);
  }

  /**
   * Vérifier si une date est dans le futur
   */
  isDateFuture(date: Date): boolean {
    return isFuture(date);
  }

  /**
   * Vérifier si une date est dans le passé
   */
  isDatePast(date: Date): boolean {
    return isPast(date);
  }

  /**
   * Vérifier si une date est un weekend
   */
  isDateWeekend(date: Date): boolean {
    return isWeekend(date);
  }

  /**
   * Obtenir le nom du jour de la semaine
   */
  getDayName(date: Date): string {
    return this.formatDate(date, 'EEEE');
  }

  /**
   * Obtenir le nom du mois
   */
  getMonthName(date: Date): string {
    return this.formatDate(date, 'MMMM');
  }

  /**
   * Obtenir l'année
   */
  getDateYear(date: Date): number {
    return getYear(date);
  }

  /**
   * Créer une date avec une heure spécifique
   */
  setDateHour(date: Date, hour: number, minute: number = 0): Date {
    return setMinutes(setHours(date, hour), minute);
  }

  /**
   * Obtenir les plages horaires pour un hôtel
   */
  getHotelTimeSlots(): TimeSlot[] {
    return [
      { start: '00:00', end: '06:00', label: 'Nuit (00h-06h)' },
      { start: '06:00', end: '12:00', label: 'Matin (06h-12h)' },
      { start: '12:00', end: '18:00', label: 'Après-midi (12h-18h)' },
      { start: '18:00', end: '24:00', label: 'Soirée (18h-00h)' }
    ];
  }

  /**
   * Obtenir les périodes prédéfinies pour les rapports
   */
  getPredefinedPeriods(): { [key: string]: DateRange } {
    const today = new Date();
    
    return {
      today: {
        start: this.getStartOfDay(today),
        end: this.getEndOfDay(today)
      },
      yesterday: {
        start: this.getStartOfDay(subDays(today, 1)),
        end: this.getEndOfDay(subDays(today, 1))
      },
      thisWeek: {
        start: this.getStartOfWeek(today),
        end: this.getEndOfWeek(today)
      },
      lastWeek: {
        start: this.getStartOfWeek(subDays(today, 7)),
        end: this.getEndOfWeek(subDays(today, 7))
      },
      thisMonth: {
        start: this.getStartOfMonth(today),
        end: this.getEndOfMonth(today)
      },
      lastMonth: {
        start: this.getStartOfMonth(subMonths(today, 1)),
        end: this.getEndOfMonth(subMonths(today, 1))
      },
      thisYear: {
        start: this.getStartOfYear(today),
        end: this.getEndOfYear(today)
      },
      lastYear: {
        start: this.getStartOfYear(subYears(today, 1)),
        end: this.getEndOfYear(subYears(today, 1))
      }
    };
  }

  /**
   * Générer un calendrier pour un mois donné
   */
  generateMonthCalendar(date: Date = new Date()): Date[][] {
    const startDate = this.getStartOfWeek(this.getStartOfMonth(date));
    const endDate = this.getEndOfWeek(this.getEndOfMonth(date));
    
    const calendar: Date[][] = [];
    let currentDate = startDate;
    
    while (currentDate <= endDate) {
      const week: Date[] = [];
      for (let i = 0; i < 7; i++) {
        week.push(new Date(currentDate));
        currentDate = addDays(currentDate, 1);
      }
      calendar.push(week);
    }
    
    return calendar;
  }

  /**
   * Calculer l'âge à partir d'une date de naissance
   */
  calculateAge(birthDate: Date): number {
    const today = new Date();
    const birthYear = getYear(birthDate);
    const currentYear = getYear(today);
    
    let age = currentYear - birthYear;
    
    // Vérifier si l'anniversaire est déjà passé cette année
    if (getMonth(today) < getMonth(birthDate) || 
        (getMonth(today) === getMonth(birthDate) && today.getDate() < birthDate.getDate())) {
      age--;
    }
    
    return age;
  }

  /**
   * Comparer deux dates
   */
  compareDates(date1: Date, date2: Date): number {
    return compareAsc(date1, date2);
  }

  /**
   * Trier un tableau de dates
   */
  sortDates(dates: Date[], ascending: boolean = true): Date[] {
    return dates.sort(ascending ? compareAsc : compareDesc);
  }

  /**
   * Vérifier si une chaîne est une date valide
   */
  isValidDateString(dateString: string): boolean {
    const date = this.parseDate(dateString);
    return date !== null && isValid(date);
  }

  /**
   * Obtenir le format de date selon la locale
   */
  getDateFormat(): string {
    const language = this.translationService.getCurrentLanguage();
    switch (language) {
      case 'ar':
        return 'dd/MM/yyyy'; // Format arabe
      case 'en':
        return 'MM/dd/yyyy'; // Format américain
      case 'fr':
      default:
        return 'dd/MM/yyyy'; // Format français
    }
  }

  /**
   * Convertir une date en format ISO pour l'API
   */
  toISOString(date: Date): string {
    return date.toISOString();
  }

  /**
   * Convertir une date en format de date seulement (YYYY-MM-DD)
   */
  toDateOnlyString(date: Date): string {
    return format(date, 'yyyy-MM-dd');
  }

  /**
   * Obtenir les jours ouvrables entre deux dates
   */
  getWorkingDays(startDate: Date, endDate: Date): Date[] {
    const days: Date[] = [];
    let currentDate = new Date(startDate);
    
    while (currentDate <= endDate) {
      if (!this.isDateWeekend(currentDate)) {
        days.push(new Date(currentDate));
      }
      currentDate = addDays(currentDate, 1);
    }
    
    return days;
  }

  /**
   * Calculer le nombre de nuits entre deux dates (pour les réservations)
   */
  calculateNights(checkIn: Date, checkOut: Date): number {
    if (checkOut <= checkIn) return 0;
    return this.getDaysDifference(checkIn, checkOut);
  }

  /**
   * Vérifier si les dates de réservation sont valides
   */
  validateReservationDates(checkIn: Date, checkOut: Date): {
    valid: boolean;
    errors: string[];
  } {
    const errors: string[] = [];
    
    if (!isValid(checkIn)) {
      errors.push('Date d\'arrivée invalide');
    }
    
    if (!isValid(checkOut)) {
      errors.push('Date de départ invalide');
    }
    
    if (checkIn && checkOut && checkOut <= checkIn) {
      errors.push('La date de départ doit être après la date d\'arrivée');
    }
    
    if (checkIn && this.isDatePast(this.getStartOfDay(checkIn))) {
      errors.push('La date d\'arrivée ne peut pas être dans le passé');
    }
    
    if (checkIn && checkOut && this.getDaysDifference(checkIn, checkOut) > 365) {
      errors.push('La durée du séjour ne peut pas dépasser 365 jours');
    }
    
    return {
      valid: errors.length === 0,
      errors
    };
  }

  /**
   * Obtenir les statistiques d'une période
   */
  getPeriodStats(startDate: Date, endDate: Date): {
    totalDays: number;
    workingDays: number;
    weekends: number;
    weeks: number;
    months: number;
  } {
    const totalDays = this.getDaysDifference(startDate, endDate) + 1;
    const workingDays = this.getWorkingDays(startDate, endDate).length;
    const weekends = totalDays - workingDays;
    const weeks = Math.ceil(totalDays / 7);
    const months = Math.ceil(totalDays / 30);
    
    return {
      totalDays,
      workingDays,
      weekends,
      weeks,
      months
    };
  }
}