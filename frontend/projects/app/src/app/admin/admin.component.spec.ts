import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AdminComponent } from './admin.component';

const API = 'http://localhost:8080';

const mockStats = { recipeCount: 42, collectionCount: 5, lastSuccessfulSyncAt: '2026-03-01T10:00:00Z' };
const mockSyncRunning = { id: 'uuid-1', startedAt: '2026-03-29T08:00:00Z', status: 'RUNNING' };
const mockSyncSuccess = {
  id: 'uuid-1',
  startedAt: '2026-03-29T08:00:00Z',
  completedAt: '2026-03-29T08:02:30Z',
  status: 'SUCCESS',
  collectionsSynced: 3,
  recipesSynced: 42,
};

function setupTest() {
  TestBed.configureTestingModule({
    imports: [AdminComponent],
    providers: [provideHttpClient(), provideHttpClientTesting()],
  });
  const fixture = TestBed.createComponent(AdminComponent);
  const component = fixture.componentInstance;
  const httpMock = TestBed.inject(HttpTestingController);
  return { fixture, component, httpMock };
}

function flushInitialRequests(httpMock: HttpTestingController) {
  httpMock.expectOne(`${API}/api/v1/admin/stats`).flush(mockStats);
  httpMock.expectOne(`${API}/api/v1/sync/latest`).flush(mockSyncSuccess);
}

describe('AdminComponent', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('should display stats after load', fakeAsync(() => {
    const { fixture, httpMock } = setupTest();
    fixture.detectChanges();
    flushInitialRequests(httpMock);
    fixture.detectChanges();

    const compiled: HTMLElement = fixture.nativeElement;
    expect(compiled.textContent).toContain('42');
    expect(compiled.textContent).toContain('5');
  }));

  it('should show RUNNING status badge when sync is running', fakeAsync(() => {
    const { fixture, httpMock } = setupTest();
    fixture.detectChanges();

    httpMock.expectOne(`${API}/api/v1/admin/stats`).flush(mockStats);
    httpMock.expectOne(`${API}/api/v1/sync/latest`).flush(mockSyncRunning);
    fixture.detectChanges();

    const compiled: HTMLElement = fixture.nativeElement;
    expect(compiled.textContent).toContain('RUNNING');
  }));

  it('should disable sync button when sync is running', fakeAsync(() => {
    const { fixture, httpMock } = setupTest();
    fixture.detectChanges();

    httpMock.expectOne(`${API}/api/v1/admin/stats`).flush(mockStats);
    httpMock.expectOne(`${API}/api/v1/sync/latest`).flush(mockSyncRunning);
    fixture.detectChanges();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="button"]');
    expect(button.disabled).toBeTrue();
  }));

  it('should enable sync button when no sync is running', fakeAsync(() => {
    const { fixture, httpMock } = setupTest();
    fixture.detectChanges();
    flushInitialRequests(httpMock);
    fixture.detectChanges();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="button"]');
    expect(button.disabled).toBeFalse();
  }));

  it('should trigger POST /sync when button clicked', fakeAsync(() => {
    const { fixture, httpMock } = setupTest();
    fixture.detectChanges();
    flushInitialRequests(httpMock);
    fixture.detectChanges();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="button"]');
    button.click();
    fixture.detectChanges();

    const syncReq = httpMock.expectOne(`${API}/api/v1/sync`);
    expect(syncReq.request.method).toBe('POST');
    syncReq.flush(mockSyncRunning);
  }));

  it('should display collection and recipe counts after successful sync', fakeAsync(() => {
    const { fixture, httpMock } = setupTest();
    fixture.detectChanges();
    flushInitialRequests(httpMock);
    fixture.detectChanges();

    const compiled: HTMLElement = fixture.nativeElement;
    expect(compiled.textContent).toContain('3 collections');
    expect(compiled.textContent).toContain('42 recettes');
  }));

  it('formatDate returns — for undefined', () => {
    const { component, httpMock } = setupTest();
    TestBed.runInInjectionContext(() => {});
    httpMock.match(() => true).forEach(r => r.flush({}));
    expect(component.formatDate(undefined)).toBe('—');
  });

  it('duration formats seconds correctly', () => {
    const { component, httpMock } = setupTest();
    httpMock.match(() => true).forEach(r => r.flush({}));
    const start = '2026-03-29T08:00:00Z';
    const end = '2026-03-29T08:02:35Z';
    expect(component.duration(start, end)).toBe('2m 35s');
  });
});
