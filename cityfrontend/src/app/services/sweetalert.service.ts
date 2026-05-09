import { Injectable } from '@angular/core';
import Swal, { SweetAlertOptions, SweetAlertResult } from 'sweetalert2';
import { TranslationService } from './translation.service';

export interface ToastOptions {
  title?: string;
  text?: string;
  icon?: 'success' | 'error' | 'warning' | 'info' | 'question';
  timer?: number;
  position?: 'top' | 'top-start' | 'top-end' | 'center' | 'center-start' | 'center-end' | 'bottom' | 'bottom-start' | 'bottom-end';
}

export interface ConfirmOptions {
  title?: string;
  text?: string;
  icon?: 'warning' | 'question' | 'info';
  confirmButtonText?: string;
  cancelButtonText?: string;
  reverseButtons?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class SweetAlertService {
  

}