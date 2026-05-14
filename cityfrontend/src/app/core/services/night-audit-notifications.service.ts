import { Injectable, NgZone, OnDestroy } from '@angular/core';
import { Observable, Subject } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from '../../services/auth.service';
import {
  NightAuditEvent,
  NightAuditEventName,
} from '../../features/hebergement/models/night-audit.model';

/**
 * Service SSE — Notifications night audit (T-3min + ouverture modale).
 *
 * Spec API (Tour 47) :
 *   - `GET /api/hebergement/night-audit/notifications` (text/event-stream)
 *   - Auth : header `Authorization: Bearer <JWT>` standard.
 *   - Events nommés :
 *       - `night-audit-alert` : push à T-3min, broadcast à tous les emitters
 *         du tenant (data `{type: "ALERT_3MIN", message: "..."}`).
 *       - `night-audit-modal` : push à H, filtré côté serveur sur les rôles
 *         SUPERADMIN / ADMIN / NIGHTAUDIT (data
 *         `{type: "OPEN_LAUNCH_MODAL", message: "..."}`).
 *   - Filtrage tenant côté backend (TenantContext) — le client ne reçoit
 *     que les events de son propre hôtel.
 *
 * Implémentation calquée sur `features/hebergement/services/calendar-live.service.ts`
 * (Tour 44 Phase 2) — `EventSource` natif ne supportant pas les headers HTTP
 * custom, on passe par `fetch()` + `ReadableStream` + décodeur de frames SSE.
 *
 * Comportement :
 *  - Un seul canal SSE actif à la fois (idempotent via `connect()`).
 *  - L'event `hello` (handshake, non émis actuellement par ce canal mais
 *    réservé) est ignoré.
 *  - 401 / 403 → on stoppe la reconnexion (JWT invalide ou révoqué).
 *  - Autres erreurs HTTP / déconnexion → backoff 2s / 5s / 10s.
 *  - Pas de `console.log` (cf. cityfrontend/CLAUDE.md §8).
 */
@Injectable({ providedIn: 'root' })
export class NightAuditNotificationsService implements OnDestroy {
  private readonly url = `${environment.apiUrl}/api/hebergement/night-audit/notifications`;
  private readonly eventsSubject = new Subject<NightAuditEvent>();

  /** Flux public des notifications night audit. */
  public readonly events$: Observable<NightAuditEvent> =
    this.eventsSubject.asObservable();

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
   * Ouvre le canal SSE (idempotent — appels multiples sans effet si déjà
   * connecté). Ne retourne rien — souscrire via `events$`.
   */
  connect(): void {
    if (this.active) {
      return;
    }
    this.active = true;
    this.retryCount = 0;
    this.openStream();
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
    this.disconnect();
    this.eventsSubject.complete();
  }

  /* ────────────────────────────────────────────────────────────────────── */
  /*  Implémentation                                                          */
  /* ────────────────────────────────────────────────────────────────────── */

  private openStream(): void {
    const token = this.auth.getToken();
    if (!token) {
      // Pas de token → on n'ouvre pas. L'utilisateur sera redirigé par le
      // guard d'authent.
      this.active = false;
      return;
    }
    this.abortController = new AbortController();
    // Hors zone Angular : les keep-alive SSE ne doivent pas déclencher de
    // change detection. On rentre dans la zone uniquement au moment du
    // `next()` sur le Subject.
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
        },
      });

      if (response.status === 401 || response.status === 403) {
        // JWT invalide / révoqué ou rôle insuffisant — on arrête définitivement.
        this.active = false;
        return;
      }
      if (!response.ok || !response.body) {
        // Erreur HTTP autre que auth — retry avec backoff.
        this.scheduleReconnect();
        return;
      }

      await this.consumeStream(response.body);
      // Le serveur a fermé proprement (timeout) → retry.
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
        // Normalisation CRLF → LF puis découpage des frames SSE.
        buffer = buffer.replace(/\r\n/g, '\n');
        let sep: number;
        while ((sep = buffer.indexOf('\n\n')) !== -1) {
          const frame = buffer.slice(0, sep);
          buffer = buffer.slice(sep + 2);
          this.parseFrame(frame);
        }
        // Reset du retry count dès qu'on reçoit des données — la connexion
        // est saine.
        this.retryCount = 0;
      }
    } finally {
      try {
        reader.releaseLock();
      } catch {
        // ignore
      }
    }
  }

  /**
   * Parse une frame SSE (lignes `event:`, `data:`, `id:`, etc.) et dispatche
   * le payload si c'est un event night audit reconnu.
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
    const knownName = this.toEventName(eventName);
    if (!knownName) {
      return;
    }
    if (knownName === 'hello') {
      return; // handshake — on l'ignore.
    }
    if (dataLines.length === 0) {
      return;
    }
    const dataStr = dataLines.join('\n');
    try {
      const payload = JSON.parse(dataStr) as { type?: string; message?: string };
      const evt: NightAuditEvent = {
        eventName: knownName,
        type: payload.type ?? '',
        message: payload.message ?? '',
      };
      this.zone.run(() => this.eventsSubject.next(evt));
    } catch {
      // Payload malformé — on l'ignore silencieusement.
    }
  }

  private toEventName(eventName: string): NightAuditEventName | null {
    switch (eventName) {
      case 'night-audit-alert':
      case 'night-audit-modal':
      case 'hello':
        return eventName;
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
