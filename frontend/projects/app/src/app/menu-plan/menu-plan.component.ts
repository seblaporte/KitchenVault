import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, EMPTY, forkJoin, of, switchMap } from 'rxjs';
import { MenuDayComponent } from './menu-day/menu-day.component';
import { RecipePickerDialogComponent } from './recipe-picker-dialog/recipe-picker-dialog.component';
import { MenuPlanService, MenuPlanDto, DayPlanDto, MealType, MealPlanUpsertDto } from '@KitchenVault/api-client';

function getMondayOf(date: Date): Date {
  const d = new Date(date);
  d.setHours(0, 0, 0, 0);
  const day = d.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  d.setDate(d.getDate() + diff);
  return d;
}

function toISODate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function emptyWeekPlan(monday: Date): MenuPlanDto {
  const days: DayPlanDto[] = [];
  for (let i = 0; i < 7; i++) {
    const d = new Date(monday);
    d.setDate(d.getDate() + i);
    days.push({ date: toISODate(d) });
  }
  return { days };
}

@Component({
  selector: 'app-menu-plan',
  standalone: true,
  imports: [CommonModule, MenuDayComponent, RecipePickerDialogComponent],
  template: `
    <div class="space-y-6">
      <h1 class="text-2xl font-semibold tracking-tight text-stone-900 dark:text-stone-100">Menu de la semaine</h1>

      <!-- Navigation semaine -->
      <div class="flex items-center gap-3 rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 px-4 py-3 shadow-sm">
        <button
          (click)="prevWeek()"
          class="inline-flex items-center gap-1.5 rounded-lg border border-stone-200 dark:border-stone-700 px-3 py-1.5 text-sm text-stone-700 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500"
          aria-label="Semaine précédente"
        >
          <svg class="h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
          Précédent
        </button>
        <span class="flex-1 text-center text-sm font-medium text-stone-700 dark:text-stone-300">{{ weekLabel() }}</span>
        <button
          (click)="goToToday()"
          class="rounded-lg border border-stone-200 dark:border-stone-700 px-3 py-1.5 text-sm text-stone-700 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500"
          aria-label="Revenir à la semaine courante"
        >
          Aujourd'hui
        </button>
        <button
          (click)="nextWeek()"
          class="inline-flex items-center gap-1.5 rounded-lg border border-stone-200 dark:border-stone-700 px-3 py-1.5 text-sm text-stone-700 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500"
          aria-label="Semaine suivante"
        >
          Suivant
          <svg class="h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
          </svg>
        </button>
        <button
          (click)="suggestWeek()"
          [disabled]="loading() || suggestingWeek()"
          class="inline-flex items-center gap-1.5 rounded-lg bg-forest-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-forest-700 transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline-2 focus-visible:outline-forest-600"
          aria-label="Suggérer des recettes pour tous les créneaux vides de la semaine"
        >
          @if (suggestingWeek()) {
            <svg class="animate-spin h-3.5 w-3.5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-hidden="true">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
            </svg>
          } @else {
            <svg class="h-3.5 w-3.5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 3l14 9-14 9V3z" />
            </svg>
          }
          Suggérer la semaine
        </button>
      </div>

      <!-- Chargement -->
      @if (loading()) {
        <div class="flex items-center justify-center py-16">
          <svg class="animate-spin h-8 w-8 text-forest-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-label="Chargement">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
          </svg>
        </div>
      }

      <!-- Erreur -->
      @if (error()) {
        <div class="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">{{ error() }}</div>
      }

      <!-- Plan de la semaine -->
      @if (!loading() && weekPlan()) {
        <div class="flex flex-col gap-4">
          @for (day of weekPlan()!.days; track day.date) {
            <app-menu-day
              [dayPlan]="day"
              (addRequested)="openPicker($event)"
              (removeRequested)="handleRemove($event)"
              (addSuggestion)="handleAddRecipe($event)"
            />
          }
        </div>
      }
    </div>

    <!-- Dialog de sélection de recette -->
    @if (pickerOpen()) {
      <app-recipe-picker-dialog
        [date]="pickerDate"
        [mealType]="pickerMealType"
        (recipeSelected)="handlePickerSelect($event)"
        (dismissed)="closePicker()"
      />
    }
  `,
})
export class MenuPlanComponent implements OnInit {
  weekStart = signal<Date>(getMondayOf(new Date()));
  weekPlan = signal<MenuPlanDto | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);
  pickerOpen = signal(false);
  suggestingWeek = signal(false);
  pickerDate = '';
  pickerMealType = '';

  constructor(private menuPlanService: MenuPlanService) {}

  ngOnInit(): void {
    this.loadWeekPlan();
  }

  weekLabel(): string {
    const start = this.weekStart();
    const end = new Date(start);
    end.setDate(end.getDate() + 6);
    const opts: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'long', year: 'numeric' };
    return `Semaine du ${start.toLocaleDateString('fr-FR', opts)} au ${end.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long' })}`;
  }

  prevWeek(): void {
    const d = new Date(this.weekStart());
    d.setDate(d.getDate() - 7);
    this.weekStart.set(d);
    this.loadWeekPlan();
  }

  nextWeek(): void {
    const d = new Date(this.weekStart());
    d.setDate(d.getDate() + 7);
    this.weekStart.set(d);
    this.loadWeekPlan();
  }

  goToToday(): void {
    this.weekStart.set(getMondayOf(new Date()));
    this.loadWeekPlan();
  }

  openPicker(event: { date: string; mealType: string }): void {
    this.pickerDate = event.date;
    this.pickerMealType = event.mealType;
    this.pickerOpen.set(true);
  }

  closePicker(): void {
    this.pickerOpen.set(false);
  }

  handlePickerSelect(recipeId: string): void {
    this.closePicker();
    this.handleAddRecipe({ date: this.pickerDate, mealType: this.pickerMealType, recipeId });
  }

  handleAddRecipe(event: { date: string; mealType: string; recipeId: string }): void {
    const dto: MealPlanUpsertDto = { recipeId: event.recipeId };
    this.menuPlanService.upsertEntry(event.date, event.mealType as MealType, dto)
      .pipe(catchError(() => {
        this.error.set('Impossible de modifier le créneau.');
        return EMPTY;
      }))
      .subscribe(() => this.loadWeekPlan());
  }

  handleRemove(event: { date: string; mealType: string }): void {
    this.menuPlanService.removeEntry(event.date, event.mealType as MealType)
      .pipe(catchError(() => {
        this.error.set('Impossible de modifier le créneau.');
        return EMPTY;
      }))
      .subscribe(() => this.loadWeekPlan());
  }

  suggestWeek(): void {
    const plan = this.weekPlan();
    if (!plan) return;

    const emptySlots: Array<{ date: string; mealType: MealType }> = [];
    for (const day of plan.days) {
      if (!day.lunch)  emptySlots.push({ date: day.date, mealType: 'LUNCH' as MealType });
      if (!day.dinner) emptySlots.push({ date: day.date, mealType: 'DINNER' as MealType });
    }

    if (emptySlots.length === 0) return;

    this.suggestingWeek.set(true);
    this.error.set(null);

    const suggestionRequests = emptySlots.map(slot =>
      this.menuPlanService.getSuggestions(slot.date, slot.mealType, undefined, 1).pipe(
        catchError(() => of([]))
      )
    );

    forkJoin(suggestionRequests).pipe(
      switchMap(results => {
        const upsertRequests = results
          .map((suggestions, i) => ({ suggestion: suggestions[0], slot: emptySlots[i] }))
          .filter(({ suggestion }) => suggestion?.recipeId)
          .map(({ suggestion, slot }) =>
            this.menuPlanService.upsertEntry(
              slot.date,
              slot.mealType,
              { recipeId: suggestion!.recipeId! }
            ).pipe(catchError(() => of(null)))
          );

        return upsertRequests.length > 0 ? forkJoin(upsertRequests) : of(null);
      }),
      catchError(() => {
        this.error.set('Certaines suggestions n\'ont pas pu être appliquées.');
        return of(null);
      })
    ).subscribe(() => {
      this.suggestingWeek.set(false);
      this.loadWeekPlan();
    });
  }

  private loadWeekPlan(): void {
    this.loading.set(true);
    this.error.set(null);
    this.menuPlanService.getWeekPlan(toISODate(this.weekStart()))
      .pipe(catchError((err: HttpErrorResponse) => {
        if (err.status === 404) {
          return of(emptyWeekPlan(this.weekStart()));
        }
        this.error.set('Impossible de charger le plan de la semaine.');
        return of(null);
      }))
      .subscribe(plan => {
        this.weekPlan.set(plan);
        this.loading.set(false);
      });
  }
}
