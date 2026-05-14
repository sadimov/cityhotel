import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { HotelUsersService } from './hotel-users.service';

describe('HotelUsersService', () => {
  let service: HotelUsersService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(HotelUsersService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
