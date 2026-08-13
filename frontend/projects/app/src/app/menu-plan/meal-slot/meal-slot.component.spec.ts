import { TestBed } from '@angular/core/testing';
import { MealSlotComponent } from './meal-slot.component';
import { MealPlanEntryDto } from '@KitchenVault/api-client';

const entry: MealPlanEntryDto = {
  recipeId: 'recipe-1',
  recipeName: 'Pasta ail & huile d\'olive',
};

function setup() {
  const fixture = TestBed.createComponent(MealSlotComponent);
  const component = fixture.componentInstance;
  component.date = '2026-06-09';
  component.mealType = 'LUNCH';
  component.label = 'Déjeuner';
  return { fixture, component };
}

describe('MealSlotComponent — mode déplacement', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [MealSlotComponent] });
  });

  it('affiche le bouton Déplacer sur une recette planifiée en mode normal', () => {
    const { fixture, component } = setup();
    component.entry = entry;
    component.heldMode = 'none';
    fixture.detectChanges();

    const moveBtn = fixture.nativeElement.querySelector('[aria-label="Déplacer cette recette"]');
    expect(moveBtn).toBeTruthy();
  });

  it('émet moveRequested au clic sur le bouton Déplacer', () => {
    const { fixture, component } = setup();
    component.entry = entry;
    component.heldMode = 'none';
    fixture.detectChanges();

    const emitted: { date: string; mealType: string }[] = [];
    component.moveRequested.subscribe(e => emitted.push(e));

    const moveBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[aria-label="Déplacer cette recette"]');
    moveBtn.click();

    expect(emitted).toEqual([{ date: '2026-06-09', mealType: 'LUNCH' }]);
  });

  it('atténue et désactive la carte source pendant la tenue', () => {
    const { fixture, component } = setup();
    component.entry = entry;
    component.heldMode = 'source';
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('.opacity-30.pointer-events-none');
    expect(card).toBeTruthy();
    // Aucun bouton d'action ne doit être rendu sur la source tenue
    expect(fixture.nativeElement.querySelector('[aria-label="Déplacer cette recette"]')).toBeFalsy();
    expect(fixture.nativeElement.querySelector('[aria-label="Supprimer ce repas"]')).toBeFalsy();
  });

  it('rend un créneau cible pulsant pour un slot vide en mode target-empty', () => {
    const { fixture, component } = setup();
    component.entry = null;
    component.heldMode = 'target-empty';
    fixture.detectChanges();

    const target: HTMLButtonElement = fixture.nativeElement.querySelector('.animate-pulse');
    expect(target).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Déposer ici');
  });

  it('émet placeRequested au clic sur un créneau vide ciblé', () => {
    const { fixture, component } = setup();
    component.entry = null;
    component.heldMode = 'target-empty';
    fixture.detectChanges();

    const emitted: { date: string; mealType: string }[] = [];
    component.placeRequested.subscribe(e => emitted.push(e));

    const target: HTMLButtonElement = fixture.nativeElement.querySelector('.animate-pulse');
    target.click();

    expect(emitted).toEqual([{ date: '2026-06-09', mealType: 'LUNCH' }]);
  });

  it('émet placeRequested au clic sur une carte occupée en mode target-swap', () => {
    const { fixture, component } = setup();
    component.entry = entry;
    component.heldMode = 'target-swap';
    fixture.detectChanges();

    const emitted: { date: string; mealType: string }[] = [];
    component.placeRequested.subscribe(e => emitted.push(e));

    const card: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    card.click();

    expect(emitted).toEqual([{ date: '2026-06-09', mealType: 'LUNCH' }]);
    expect(fixture.nativeElement.textContent).toContain('⇄');
  });
});
