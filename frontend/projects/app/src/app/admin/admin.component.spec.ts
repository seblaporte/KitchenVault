import { TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
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
  // SUCCESS triggers loadStats() — drain the follow-up stats request
  httpMock.match(`${API}/api/v1/admin/stats`);
}

describe('AdminComponent', () => {
  afterEach(() => {
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true); // drain any remaining side-effect requests
    httpMock.verify();
  });

  it('should display stats after load', fakeAsync(() => {
    const { fixture, httpMock } = setupTest();
    fixture.detectChanges();
    flushInitialRequests(httpMock);
    fixture.detectChanges();

    const compiled: HTMLElement = fixture.nativeElement;
    expect(compiled.textContent).toContain('42');
    expect(compiled.textContent).toContain('5');
    discardPeriodicTasks();
  }));

  it('should show RUNNING status badge when sync is running', fakeAsync(() => {
    const { fixture, httpMock } = setupTest();
    fixture.detectChanges();

    httpMock.expectOne(`${API}/api/v1/admin/stats`).flush(mockStats);
    httpMock.expectOne(`${API}/api/v1/sync/latest`).flush(mockSyncRunning);
    fixture.detectChanges();

    const compiled: HTMLElement = fixture.nativeElement;
    expect(compiled.textContent).toContain('RUNNING');
    discardPeriodicTasks();
  }));

  it('should disable sync button when sync is running', fakeAsync(() => {
    const { fixture, httpMock } = setupTest();
    fixture.detectChanges();

    httpMock.expectOne(`${API}/api/v1/admin/stats`).flush(mockStats);
    httpMock.expectOne(`${API}/api/v1/sync/latest`).flush(mockSyncRunning);
    fixture.detectChanges();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="button"]');
    expect(button.disabled).toBeTrue();
    discardPeriodicTasks();
  }));

  it('should enable sync button when no sync is running', fakeAsync(() => {
    const { fixture, httpMock } = setupTest();
    fixture.detectChanges();
    flushInitialRequests(httpMock);
    fixture.detectChanges();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="button"]');
    expect(button.disabled).toBeFalse();
    discardPeriodicTasks();
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
    discardPeriodicTasks();
  }));

  it('should display collection and recipe counts after successful sync', fakeAsync(() => {
    const { fixture, httpMock } = setupTest();
    fixture.detectChanges();
    flushInitialRequests(httpMock);
    fixture.detectChanges();

    const compiled: HTMLElement = fixture.nativeElement;
    expect(compiled.textContent).toContain('3 collections');
    expect(compiled.textContent).toContain('42 recettes');
    discardPeriodicTasks();
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

  it('should keep polling when sync/latest returns 404', fakeAsync(() => {
    const { fixture, httpMock } = setupTest();
    fixture.detectChanges();

    httpMock.expectOne(`${API}/api/v1/admin/stats`).flush(mockStats);
    httpMock.expectOne(`${API}/api/v1/sync/latest`).flush('', { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(fixture.componentInstance.latestSync()).toBeNull();
    expect(fixture.componentInstance.isSyncing()).toBeFalse();

    tick(5000);
    httpMock.expectOne(`${API}/api/v1/sync/latest`).flush(mockSyncSuccess);
    fixture.detectChanges();

    expect(fixture.componentInstance.latestSync()?.status).toBe('SUCCESS');
    // SUCCESS triggers loadStats() — drain before discarding
    httpMock.match(`${API}/api/v1/admin/stats`);
    discardPeriodicTasks();
  }));
});
