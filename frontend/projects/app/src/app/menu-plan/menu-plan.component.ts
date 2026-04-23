import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { catchError, of } from 'rxjs';
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

@Component({
  selector: 'app-menu-plan',
  standalone: true,
  imports: [CommonModule, MenuDayComponent, RecipePickerDialogComponent],
  template: `
    <div class="space-y-6">
      <h1 class="text-2xl font-semibold tracking-tight text-zinc-900">Menu de la semaine</h1>

      <!-- Navigation semaine -->
      <div class="flex items-center gap-3 rounded-xl border border-zinc-200 bg-white px-4 py-3 shadow-sm">
        <button
          (click)="prevWeek()"
          class="inline-flex items-center gap-1.5 rounded-lg border border-zinc-200 px-3 py-1.5 text-sm text-zinc-700 hover:bg-zinc-50 transition-colors focus-visible:outline-2 focus-visible:outline-indigo-500"
          aria-label="Semaine précédente"
        >
          <svg class="h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
          Précédent
        </button>
        <span class="flex-1 text-center text-sm font-medium text-zinc-700">{{ weekLabel() }}</span>
        <button
          (click)="goToToday()"
          class="rounded-lg border border-zinc-200 px-3 py-1.5 text-sm text-zinc-700 hover:bg-zinc-50 transition-colors focus-visible:outline-2 focus-visible:outline-indigo-500"
          aria-label="Revenir à la semaine courante"
        >
          Aujourd'hui
        </button>
        <button
          (click)="nextWeek()"
          class="inline-flex items-center gap-1.5 rounded-lg border border-zinc-200 px-3 py-1.5 text-sm text-zinc-700 hover:bg-zinc-50 transition-colors focus-visible:outline-2 focus-visible:outline-indigo-500"
          aria-label="Semaine suivante"
        >
          Suivant
          <svg class="h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </div>

      <!-- Chargement -->
      @if (loading()) {
        <div class="flex items-center justify-center py-16">
          <svg class="animate-spin h-8 w-8 text-indigo-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-label="Chargement">
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
        <div class="space-y-4">
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
      .pipe(catchError(() => of(null)))
      .subscribe(() => this.loadWeekPlan());
  }

  handleRemove(event: { date: string; mealType: string }): void {
    this.menuPlanService.removeEntry(event.date, event.mealType as MealType)
      .pipe(catchError(() => of(null)))
      .subscribe(() => this.loadWeekPlan());
  }

  private loadWeekPlan(): void {
    this.loading.set(true);
    this.error.set(null);
    this.menuPlanService.getWeekPlan(toISODate(this.weekStart()))
      .pipe(catchError(() => {
        this.error.set('Impossible de charger le plan de la semaine.');
        this.loading.set(false);
        return of(null);
      }))
      .subscribe(plan => {
        this.weekPlan.set(plan);
        this.loading.set(false);
      });
  }
}
