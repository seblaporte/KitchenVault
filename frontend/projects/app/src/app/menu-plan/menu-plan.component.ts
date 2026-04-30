import { Component, OnInit, OnDestroy, signal, effect, inject, Renderer2 } from '@angular/core';
import { CommonModule, DOCUMENT } from '@angular/common';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { heroSparkles, heroPlay } from '@ng-icons/heroicons/outline';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, EMPTY, forkJoin, map, of, switchMap } from 'rxjs';
import { MenuDayComponent } from './menu-day/menu-day.component';
import { RecipePickerDialogComponent } from './recipe-picker-dialog/recipe-picker-dialog.component';
import { ChatModalComponent } from './chat-modal/chat-modal.component';
import { WeeklyPlanDrawerComponent } from './weekly-plan-drawer/weekly-plan-drawer.component';
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

interface ChatContext {
  context: 'week' | 'slot';
  weekStart: string;
  date?: string;
  mealType?: string;
  slotLabel?: string;
}

@Component({
  selector: 'app-menu-plan',
  standalone: true,
  imports: [CommonModule, NgIconComponent, MenuDayComponent, RecipePickerDialogComponent, ChatModalComponent, WeeklyPlanDrawerComponent],
  viewProviders: [provideIcons({ heroSparkles, heroPlay })],
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
          (click)="weeklyDrawerOpen.set(true)"
          [class.ring-2]="weeklyDrawerOpen()"
          [class.ring-forest-500]="weeklyDrawerOpen()"
          class="inline-flex items-center gap-1.5 rounded-lg bg-forest-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-forest-700 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-600"
          aria-label="Planifier la semaine avec l'IA"
        >
          <ng-icon name="heroSparkles" class="h-3.5 w-3.5" aria-hidden="true" />
          Planifier avec l'IA
        </button>
        <button
          (click)="suggestWeek()"
          [disabled]="loading()"
          class="inline-flex items-center gap-1.5 rounded-lg border border-stone-200 dark:border-stone-700 px-3 py-1.5 text-sm text-stone-700 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline-2 focus-visible:outline-forest-500"
          aria-label="Suggérer des recettes aléatoires pour la semaine"
        >
          <ng-icon name="heroPlay" class="h-3.5 w-3.5" aria-hidden="true" />
          Suggérer
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
              (chatRequested)="openChatForSlot($event)"
            />
          }
        </div>
      }
    </div>

    <!-- Drawer de planification IA hebdomadaire (fixed, viewport) -->
    <app-weekly-plan-drawer
      [weekStart]="toISODateStr(weekStart())"
      [class.hidden]="!weeklyDrawerOpen()"
      (dismissed)="onWeeklyDrawerDismissed($event)"
      (planChanged)="loadWeekPlan()"
    />

    <!-- Dialog de sélection de recette -->
    @if (pickerOpen()) {
      <app-recipe-picker-dialog
        [date]="pickerDate"
        [mealType]="pickerMealType"
        (recipeSelected)="handlePickerSelect($event)"
        (dismissed)="closePicker()"
      />
    }

    <!-- Modale de chat IA -->
    @if (chatContext()) {
      <app-chat-modal
        [context]="chatContext()!.context"
        [weekStart]="chatContext()!.weekStart"
        [date]="chatContext()!.date"
        [mealType]="chatContext()!.mealType"
        [slotLabel]="chatContext()!.slotLabel"
        (dismissed)="onChatDismissed($event)"
      />
    }
  `,
})
export class MenuPlanComponent implements OnInit, OnDestroy {
  weekStart = signal<Date>(getMondayOf(new Date()));
  weekPlan = signal<MenuPlanDto | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);
  pickerOpen = signal(false);
  chatContext = signal<ChatContext | null>(null);
  weeklyDrawerOpen = signal(false);
  pickerDate = '';
  pickerMealType = '';

  private renderer = inject(Renderer2);
  private document = inject(DOCUMENT);

  constructor(private menuPlanService: MenuPlanService) {
    effect(() => {
      if (this.weeklyDrawerOpen()) {
        this.renderer.addClass(this.document.body, 'drawer-open');
      } else {
        this.renderer.removeClass(this.document.body, 'drawer-open');
      }
    });
  }

  ngOnDestroy(): void {
    this.renderer.removeClass(this.document.body, 'drawer-open');
  }

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
    const monday = this.weekStart();
    const slots: { date: string; mealType: MealType }[] = [];
    for (let i = 0; i < 7; i++) {
      const d = new Date(monday);
      d.setDate(d.getDate() + i);
      const date = toISODate(d);
      slots.push({ date, mealType: MealType.LUNCH });
      slots.push({ date, mealType: MealType.DINNER });
    }

    this.loading.set(true);
    this.error.set(null);

    forkJoin(
      slots.map(s =>
        this.menuPlanService.getSuggestions(s.date, s.mealType, undefined, 1).pipe(
          map(suggestions => ({ slot: s, suggestion: suggestions[0] }))
        )
      )
    ).pipe(
      switchMap(results => {
        const upserts = results
          .filter(r => r.suggestion?.recipeId)
          .map(r =>
            this.menuPlanService.upsertEntry(r.slot.date, r.slot.mealType, { recipeId: r.suggestion.recipeId! })
          );
        return upserts.length > 0 ? forkJoin(upserts) : of([]);
      }),
      catchError(() => {
        this.error.set('Impossible de générer les suggestions.');
        this.loading.set(false);
        return EMPTY;
      })
    ).subscribe(() => this.loadWeekPlan());
  }

  openChatForSlot(event: { date: string; mealType: string; label: string }): void {
    this.chatContext.set({
      context: 'slot',
      weekStart: toISODate(this.weekStart()),
      date: event.date,
      mealType: event.mealType,
      slotLabel: event.label,
    });
  }

  onChatDismissed(planModified: boolean): void {
    this.chatContext.set(null);
    if (planModified) {
      this.loadWeekPlan();
    }
  }

  onWeeklyDrawerDismissed(planModified: boolean): void {
    this.weeklyDrawerOpen.set(false);
    if (planModified) {
      this.loadWeekPlan();
    }
  }

  toISODateStr(d: Date): string {
    return toISODate(d);
  }

  loadWeekPlan(): void {
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
