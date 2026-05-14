import { Injectable, NgZone, OnDestroy } from '@angular/core';
import { Observable, Subject } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../services/auth.service';
import {
  CALENDAR_EVENT_NAMES,
  CalendarEventDto,
  CalendarEventType,
} from '../models/calendar-event.model';

/**
 * Service SSE — Refresh temps réel du calendrier de réservations.
 *
 * Spec API (Tour 44 Phase 1) :
 *   - `GET /api/hebergement/reservations/events` (text/event-stream)
 *   - Auth : header `Authorization: Bearer <JWT>` standard
 *   - Events nommés : `hello` (handshake), `reservation.created`,
 *     `reservation.updated`, `reservation.deleted`
 *   - Filtrage tenant côté serveur (TenantContext) — le client ne reçoit que
 *     les events de son propre hotelId.
 *
 * Tour 44 Phase 2 (2026-05-11) :
 *  - `EventSource` natif NE supporte PAS les headers HTTP custom (impossible
 *    de joindre le `Authorization: Bearer <JWT>`). Solutions envisagées :
 *      a) Lib externe `@microsoft/fetch-event-source` (préférée — code mature,
 *         retry intégré, support `Last-Event-ID`). Installation refusée par
 *         les permissions de l'environnement.
 *      b) Implémentation manuelle `fetch()` + `ReadableStream` + décodeur de
 *         frames SSE (RFC `text/event-stream`). Retenue.
 *  - Reconnexion automatique avec backoff exponentiel (2s → 5s → 10s, plafonné)
 *  - Cleanup obligatoire via `disconnect()` au `ngOnDestroy` du composant
 *    consommateur.
 *
 * Format du flux SSE (rappel) :
 *   `event: <name>\n`
 *   `data: <json>\n`
 *   `\n`   ← séparateur de frames
 *
 * Comportement :
 *  - Un seul canal SSE actif à la fois par instance de service.
 *  - L'event `hello` (handshake) est ignoré.
 *  - En cas de 401 / 403, on stoppe la reconnexion (JWT invalide ou expiré).
 *  - Pas de `console.log` (cf. cityfrontend/CLAUDE.md §8).
 */
@Injectable({ providedIn: 'root' })
export class CalendarLiveService implements OnDestroy {
  private readonly url = `${environment.apiUrl}/api/hebergement/reservations/events`;
  private readonly events$ = new Subject<CalendarEventDto>();

  /** AbortController courant — permet d'interrompre le `fetch()` en cours. */
  private abortController: AbortController | null = null;
  /** Vrai tant qu'une connexion (ou tentative) est armée. */
  private active = false;
  /** Compteur de retry pour le backoff exponentiel. */
  private retryCount = 0;
  /** Timer de reconnexion en attente — annulable via `disconnect()`. */
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly auth: AuthService,
    private readonly zone: NgZone,
  ) {}

  /**
   * Ouvre le canal SSE et retourne un flux d'events de mutation. Si un canal
   * est déjà ouvert, on retourne simplement le flux existant (idempotent).
   */
  connect(): Observable<CalendarEventDto> {
    if (!this.active) {
      this.active = true;
      this.retryCount = 0;
      this.openStream();
    }
    return this.events$.asObservable();
  }

  /**
   * Ferme le canal SSE en cours et stoppe la boucle de reconnexion.
   */
  disconnect(): void {
    this.active = false;
    if (this.reconnectTimer != null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.abortController?.abort();
    this.abortController = null;
  }

  ngOnDestroy(): void {
    // Garde-fou — si le service est détruit par le container Angular.
    this.disconnect();
    this.events$.complete();
  }

  /* ────────────────────────────────────────────────────────────────────── */
  /*  Implémentation                                                          */
  /* ────────────────────────────────────────────────────────────────────── */

  private openStream(): void {
    const token = this.auth.getToken();
    if (!token) {
      // Pas de token → on n'ouvre pas. L'utilisateur est de toute façon
      // intercepté par le guard d'authent qui redirige vers `/login`.
      this.active = false;
      return;
    }
    this.abortController = new AbortController();
    // On tourne hors zone Angular pour éviter de déclencher un change
    // detection à chaque chunk reçu (les keep-alive sont fréquents).
    this.zone.runOutsideAngular(() => {
      this.runFetchLoop(token).catch(() => {
        // `runFetchLoop` gère ses propres erreurs (retry / abort). Le `.catch`
        // ici est un garde-fou silencieux pour les rejets imprévus.
      });
    });
  }

  /**
   * Boucle principale : ouvre `fetch()`, lit le `ReadableStream`, dispatche
   * les frames SSE. Reconnecte si la connexion tombe (sauf si désactivé ou
   * 401/403).
   */
  private async runFetchLoop(token: string): Promise<void> {
    const ctrl = this.abortController;
    if (!ctrl) return;
    try {
      const response = await fetch(this.url, {
        method: 'GET',
        signal: ctrl.signal,
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'text/event-stream',
          // Pas de `Cache-Control: no-cache` → ce header n'est pas CORS-safe et
          // déclenche un preflight OPTIONS que le backend ne whitelist pas
          // (`Access-Control-Allow-Headers` ne contient pas `Cache-Control`).
          // Le navigateur ne met de toute façon pas en cache un text/event-stream.
        },
      });

      if (response.status === 401 || response.status === 403) {
        // JWT invalide ou révoqué — on arrête définitivement.
        this.active = false;
        return;
      }
      if (!response.ok || !response.body) {
        // Erreur HTTP autre que auth — retry avec backoff.
        this.scheduleReconnect();
        return;
      }

      await this.consumeStream(response.body);
      // Le serveur a fermé proprement (timeout 30 min côté backend) → retry.
      if (this.active) {
        this.scheduleReconnect();
      }
    } catch (err) {
      // AbortError = disconnect() volontaire → on ne retry pas.
      if (!this.active || (err as DOMException)?.name === 'AbortError') {
        return;
      }
      this.scheduleReconnect();
    }
  }

  /**
   * Lit le `ReadableStream` chunk par chunk, accumule un buffer texte,
   * découpe en frames SSE (séparateur `\n\n`) puis appelle `parseFrame`.
   */
  private async consumeStream(body: ReadableStream<Uint8Array>): Promise<void> {
    const reader = body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    try {
      while (true) {
        const { value, done } = await reader.read();
        if (done) return;
        buffer += decoder.decode(value, { stream: true });
        // Découpage des frames SSE (séparateur `\n\n` après normalisation CRLF).
        buffer = buffer.replace(/\r\n/g, '\n');
        let sep: number;
        while ((sep = buffer.indexOf('\n\n')) !== -1) {
          const frame = buffer.slice(0, sep);
          buffer = buffer.slice(sep + 2);
          this.parseFrame(frame);
        }
        // Reset retry count dès qu'on reçoit du data — la connexion est saine.
        this.retryCount = 0;
      }
    } finally {
      // Libère le lock du reader pour permettre la fermeture du stream.
      try {
        reader.releaseLock();
      } catch {
        // ignore
      }
    }
  }

  /**
   * Parse une frame SSE (lignes `event:`, `data:`, `id:`, etc.) et dispatche
   * le payload si c'est un event City Hotel.
   */
  private parseFrame(frame: string): void {
    let eventName = 'message';
    const dataLines: string[] = [];
    for (const line of frame.split('\n')) {
      if (line === '' || line.startsWith(':')) continue; // commentaire / keep-alive
      const colon = line.indexOf(':');
      if (colon === -1) continue;
      const field = line.slice(0, colon).trim();
      // RFC : une espace optionnelle suit le `:`.
      const value =
        line.length > colon + 1 && line[colon + 1] === ' '
          ? line.slice(colon + 2)
          : line.slice(colon + 1);
      if (field === 'event') {
        eventName = value;
      } else if (field === 'data') {
        dataLines.push(value);
      }
    }
    if (eventName === CALENDAR_EVENT_NAMES.HELLO) {
      return; // handshake — on l'ignore.
    }
    const type = this.toCalendarEventType(eventName);
    if (!type || dataLines.length === 0) {
      return;
    }
    const dataStr = dataLines.join('\n');
    try {
      const payload = JSON.parse(dataStr) as CalendarEventDto;
      // Re-rentre dans la zone Angular pour les subscribers.
      this.zone.run(() => this.events$.next(payload));
    } catch {
      // Payload malformé — on l'ignore silencieusement.
    }
  }

  private toCalendarEventType(eventName: string): CalendarEventType | null {
    switch (eventName) {
      case CALENDAR_EVENT_NAMES.CREATED:
        return 'CREATED';
      case CALENDAR_EVENT_NAMES.UPDATED:
        return 'UPDATED';
      case CALENDAR_EVENT_NAMES.DELETED:
        return 'DELETED';
      default:
        return null;
    }
  }

  /**
   * Planifie une reconnexion après un délai backoff (2s / 5s / 10s, plafonné).
   */
  private scheduleReconnect(): void {
    if (!this.active) return;
    const delays = [2000, 5000, 10000];
    this.retryCount = Math.min(this.retryCount + 1, delays.length);
    const delay = delays[this.retryCount - 1];
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      if (this.active) {
        this.openStream();
      }
    }, delay);
  }
}
