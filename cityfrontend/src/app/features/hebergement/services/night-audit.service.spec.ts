import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { NightAuditResultDto } from '../models/night-audit.model';
import { NightAuditService } from './night-audit.service';

describe('NightAuditService', () => {
  let service: NightAuditService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [NightAuditService],
    });
    service = TestBed.inject(NightAuditService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('POST /run retourne le résultat décortiqué de ApiResponse', () => {
    const payload: NightAuditResultDto = {
      hotelId: 1,
      dateExecution: '2026-05-11',
      nbReservationsMarkedNoShow: 2,
      nbNuiteesManquantesGenerees: 5,
      executedAt: '2026-05-11T10:47:00Z',
    };
    let received: NightAuditResultDto | null = null;
    service.run().subscribe((r) => (received = r));

    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/hebergement/night-audit/run`,
    );
    expect(req.request.method).toBe('POST');
    req.flush({
      success: true,
      message: 'OK',
      data: payload,
      timestamp: '2026-05-11T10:47:00Z',
    });

    expect(received).toEqual(payload);
  });
});
