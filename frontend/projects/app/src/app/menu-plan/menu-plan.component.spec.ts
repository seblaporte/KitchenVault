import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { MenuPlanComponent } from './menu-plan.component';
import { MenuPlanDto, MealType } from '@KitchenVault/api-client';
import { ToastService } from '../shared/toast/toast.service';

const weekPlan: MenuPlanDto = {
  days: [
    { date: '2026-06-08', lunch: { recipeId: 'r-1', recipeName: 'Pasta' } },
    { date: '2026-06-09', dinner: { recipeId: 'r-2', recipeName: 'Salade' } },
    { date: '2026-06-10' },
    { date: '2026-06-11' },
    { date: '2026-06-12' },
    { date: '2026-06-13' },
    { date: '2026-06-14' },
  ],
};

function setup() {
  TestBed.configureTestingModule({
    imports: [MenuPlanComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
    ],
  });
  const fixture = TestBed.createComponent(MenuPlanComponent);
  const component = fixture.componentInstance;
  // On seed directement le state pour ne pas dépendre des appels HTTP d'initialisation (ngOnInit).
  component.weekPlan.set(weekPlan);
  const httpMock = TestBed.inject(HttpTestingController);
  const toast = TestBed.inject(ToastService);
  return { fixture, component, httpMock, toast };
}

describe('MenuPlanComponent — mode Attraper (déplacer une recette)', () => {
  afterEach(() => {
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true);
  });

  it('heldModeFor renvoie "none" tant que rien n\'est tenu', () => {
    const { component } = setup();
    expect(component.heldModeFor('2026-06-08', MealType.LUNCH, true)).toBe('none');
  });

  it('handleMoveRequested attrape la recette du créneau et met à jour heldMeal', () => {
    const { component } = setup();
    component.handleMoveRequested({ date: '2026-06-08', mealType: 'LUNCH' });

    expect(component.heldMeal()).toEqual({
      date: '2026-06-08',
      mealType: MealType.LUNCH,
      recipeId: 'r-1',
      recipeName: 'Pasta',
    });
  });

  it('heldModeFor distingue source / cible vide / cible occupée pendant la tenue', () => {
    const { component } = setup();
    component.handleMoveRequested({ date: '2026-06-08', mealType: 'LUNCH' });

    expect(component.heldModeFor('2026-06-08', MealType.LUNCH, true)).toBe('source');
    expect(component.heldModeFor('2026-06-10', MealType.LUNCH, false)).toBe('target-empty');
    expect(component.heldModeFor('2026-06-09', MealType.DINNER, true)).toBe('target-swap');
  });

  it('cancelHold relâche la recette tenue', () => {
    const { component } = setup();
    component.handleMoveRequested({ date: '2026-06-08', mealType: 'LUNCH' });
    component.cancelHold();
    expect(component.heldMeal()).toBeNull();
  });

  it('handlePlaceRequested vers un créneau vide envoie un seul relocateEntry (PATCH atomique) et affiche un toast de succès', () => {
    const { component, httpMock, toast } = setup();
    component.handleMoveRequested({ date: '2026-06-08', mealType: 'LUNCH' });

    component.handlePlaceRequested({ date: '2026-06-10', mealType: 'LUNCH' });

    expect(component.heldMeal()).toBeNull();

    const relocateReq = httpMock.expectOne(r => r.method === 'PATCH' && r.url.includes('/api/v1/menu-plan/entries/2026-06-08/LUNCH'));
    expect(relocateReq.request.body).toEqual({ date: '2026-06-10', mealType: 'LUNCH' });
    relocateReq.flush({ recipeId: 'r-1', recipeName: 'Pasta' });

    // handlePlaceRequested recharge le planning après succès
    httpMock.expectOne(r => r.method === 'GET' && r.url.includes('/api/v1/menu-plan')).flush(weekPlan);

    expect(toast.current()).toEqual(jasmine.objectContaining({ type: 'success', title: 'Recette déplacée' }));
  });

  it('handlePlaceRequested vers un créneau vide affiche un toast d\'échec si relocateEntry échoue, sans doublon', () => {
    const { component, httpMock, toast } = setup();
    component.handleMoveRequested({ date: '2026-06-08', mealType: 'LUNCH' });

    component.handlePlaceRequested({ date: '2026-06-10', mealType: 'LUNCH' });

    const relocateReq = httpMock.expectOne(r => r.method === 'PATCH' && r.url.includes('/api/v1/menu-plan/entries/2026-06-08/LUNCH'));
    relocateReq.flush(null, { status: 500, statusText: 'Server Error' });

    // Un seul appel de mutation a été fait : pas de fenêtre de duplication possible.
    httpMock.expectOne(r => r.method === 'GET' && r.url.includes('/api/v1/menu-plan')).flush(weekPlan);

    expect(toast.current()).toEqual(jasmine.objectContaining({ type: 'error', title: 'Échec du déplacement' }));
  });

  it('handlePlaceRequested vers un créneau occupé envoie un seul upsertBulkEntries (échange) et affiche un toast dédié', () => {
    const { component, httpMock, toast } = setup();
    component.handleMoveRequested({ date: '2026-06-08', mealType: 'LUNCH' });

    component.handlePlaceRequested({ date: '2026-06-09', mealType: 'DINNER' });

    const bulkReq = httpMock.expectOne(r => r.method === 'POST' && r.url.includes('/api/v1/menu-plan/entries/bulk'));
    expect(bulkReq.request.body).toEqual({
      entries: [
        { date: '2026-06-09', mealType: 'DINNER', recipeId: 'r-1' },
        { date: '2026-06-08', mealType: 'LUNCH', recipeId: 'r-2' },
      ],
    });
    bulkReq.flush([]);

    httpMock.expectOne(r => r.method === 'GET' && r.url.includes('/api/v1/menu-plan')).flush(weekPlan);

    expect(toast.current()).toEqual(jasmine.objectContaining({ type: 'success', title: 'Recettes échangées' }));
  });

  it('handlePlaceRequested ne fait rien si aucune recette n\'est tenue', () => {
    const { component, httpMock } = setup();
    component.handlePlaceRequested({ date: '2026-06-10', mealType: 'LUNCH' });
    httpMock.expectNone(r => r.url.includes('/api/v1/menu-plan'));
  });
});
