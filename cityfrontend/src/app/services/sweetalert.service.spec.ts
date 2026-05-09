import { TestBed } from '@angular/core/testing';

import { Sweetalert } from './sweetalert';

describe('Sweetalert', () => {
  let service: Sweetalert;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(Sweetalert);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
