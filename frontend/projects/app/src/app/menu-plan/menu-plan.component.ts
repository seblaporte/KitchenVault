import { Component, OnInit, OnDestroy, HostListener, signal, effect, inject, Renderer2, computed } from '@angular/core';
import { CommonModule, DOCUMENT } from '@angular/common';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { heroSparkles, heroPlay, heroArrowUpOnSquare, heroChevronDown, heroArrowsRightLeft, heroXMark, heroCalendarDays } from '@ng-icons/heroicons/outline';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, EMPTY, forkJoin, map, of, switchMap } from 'rxjs';
import { MealSlotComponent, HeldMode } from './meal-slot/meal-slot.component';
import { RecipePickerDialogComponent } from './recipe-picker-dialog/recipe-picker-dialog.component';
import { ChatModalComponent } from './chat-modal/chat-modal.component';
import { WeeklyPlanDrawerComponent } from './weekly-plan-drawer/weekly-plan-drawer.component';
import { WeeklyReviewModalComponent } from './weekly-review-modal/weekly-review-modal.component';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MenuPlanService, ShoppingListService, MenuPlanDto, DayPlanDto, MealType, MealPlanUpsertDto, MealPlanEntryDto } from '@KitchenVault/api-client';
import { ToastService } from '../shared/toast/toast.service';

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
    days.push({ date: toISODate(d), undefinedMeals: [] });
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

interface WeekDay extends DayPlanDto {
  isToday: boolean;
  abbr: string;
  num: string;
  month: string;
}

interface HeldMeal {
  date: string;
  mealType: MealType;
  recipeId: string;
  recipeName: string;
  /** Set only when the held source is a "Non défini" entry — identifies it since date+mealType isn't unique for that category. */
  sourceId?: number;
}

@Component({
  selector: 'app-menu-plan',
  standalone: true,
  imports: [CommonModule, RouterLink, NgIconComponent, MealSlotComponent, RecipePickerDialogComponent, ChatModalComponent, WeeklyPlanDrawerComponent, WeeklyReviewModalComponent],
  viewProviders: [provideIcons({ heroSparkles, heroPlay, heroArrowUpOnSquare, heroChevronDown, heroArrowsRightLeft, heroXMark, heroCalendarDays })],
  template: `
    <!-- En-tête semaine -->
    <div class="sticky top-[calc(3.5rem+env(safe-area-inset-top)+0.5rem)] sm:top-[calc(4rem+env(safe-area-inset-top)+0.5rem)] z-20 flex flex-wrap items-center gap-2 rounded-xl border border-stone-200 dark:border-stone-800 bg-white dark:bg-stone-950 px-4 py-3 shadow-sm mb-4">
      <h1 class="text-base font-semibold tracking-tight text-stone-900 dark:text-stone-100">Menu de la semaine</h1>
      <span class="text-xs font-medium text-stone-500 dark:text-stone-400 bg-stone-100 dark:bg-stone-800 border border-stone-200 dark:border-stone-700 rounded-full px-2.5 py-0.5">{{ weekRangeLabel() }}</span>

      <!-- Navigation + progression : ligne dédiée pleine largeur -->
      <div class="w-full flex items-center gap-4">
        <!-- Moitié gauche : navigation semaine -->
        <div class="flex-1 flex items-stretch rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 overflow-hidden">
          <button
            (click)="prevWeek()"
            class="flex-1 h-10 flex items-center justify-center text-stone-500 dark:text-stone-400 hover:bg-stone-100 dark:hover:bg-stone-800 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500"
            aria-label="Semaine précédente"
          >
            <svg class="h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <div class="w-px bg-stone-200 dark:bg-stone-700 self-stretch"></div>
          <button
            (click)="goToToday()"
            class="flex-1 h-10 flex items-center justify-center text-sm font-medium text-stone-600 dark:text-stone-300 hover:bg-stone-100 dark:hover:bg-stone-800 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500 whitespace-nowrap"
            aria-label="Revenir à la semaine courante"
          >
            <span class="sm:hidden">Auj.</span>
            <span class="hidden sm:inline">Aujourd'hui</span>
          </button>
          <div class="w-px bg-stone-200 dark:bg-stone-700 self-stretch"></div>
          <button
            (click)="nextWeek()"
            class="flex-1 h-10 flex items-center justify-center text-stone-500 dark:text-stone-400 hover:bg-stone-100 dark:hover:bg-stone-800 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500"
            aria-label="Semaine suivante"
          >
            <svg class="h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>

        <!-- Moitié droite : barre de progression -->
        <div class="flex-1 flex items-center gap-3">
          <div class="flex-1 h-2 rounded-full bg-stone-200 dark:bg-stone-700 overflow-hidden">
            <div
              class="h-full bg-forest-500 rounded-full transition-all duration-300"
              [style.width.%]="(filledSlots() / totalSlots()) * 100"
            ></div>
          </div>
          <span class="text-sm font-medium tabular-nums text-stone-500 dark:text-stone-400 shrink-0">{{ filledSlots() }}/{{ totalSlots() }}</span>
        </div>
      </div>

      <div class="w-full sm:w-auto sm:ml-auto flex flex-wrap sm:flex-nowrap items-center gap-2">
        <button
          (click)="openWeeklyDrawer()"
          [class.ring-2]="weeklyDrawerOpen()"
          [class.ring-forest-500]="weeklyDrawerOpen()"
          class="flex-1 sm:flex-none inline-flex items-center justify-center gap-1.5 rounded-lg bg-forest-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-forest-700 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-600"
          aria-label="Planifier la semaine avec l'IA"
        >
          <ng-icon name="heroSparkles" class="h-3.5 w-3.5" aria-hidden="true" />
          Planifier
        </button>

        @if (syncDropdownOpen()) {
          <div class="fixed inset-0 z-40" (click)="syncDropdownOpen.set(false)" aria-hidden="true"></div>
        }
        <!-- Split button Cookidoo sync -->
        <div class="flex-1 sm:flex-none relative flex">
          <button
            (click)="syncToCookidoo(false)"
            [disabled]="syncing()"
            class="flex-1 inline-flex items-center justify-center gap-1.5 rounded-l-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 px-3 py-1.5 text-xs font-medium text-stone-600 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline-2 focus-visible:outline-forest-500"
            aria-label="Synchroniser la semaine avec Cookidoo"
          >
            @if (syncing()) {
              <svg class="animate-spin h-3.5 w-3.5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-hidden="true">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
              </svg>
            } @else {
              <ng-icon name="heroArrowUpOnSquare" class="h-3.5 w-3.5" aria-hidden="true" />
            }
            Cookidoo
          </button>
          <button
            (click)="syncDropdownOpen.set(!syncDropdownOpen())"
            [disabled]="syncing()"
            class="shrink-0 inline-flex items-center rounded-r-lg border-t border-r border-b border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 px-1.5 py-1.5 text-stone-500 dark:text-stone-400 hover:bg-stone-50 dark:hover:bg-stone-800 transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline-2 focus-visible:outline-forest-500"
            aria-label="Options de synchronisation Cookidoo"
            aria-haspopup="true"
            [attr.aria-expanded]="syncDropdownOpen()"
          >
            <ng-icon name="heroChevronDown" class="h-3 w-3" aria-hidden="true" />
          </button>
          @if (syncDropdownOpen()) {
            <div
              class="absolute right-0 top-full mt-1 z-50 min-w-max rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 shadow-lg py-1"
              role="menu"
            >
              <button
                (click)="syncToCookidoo(true)"
                class="w-full text-left px-3 py-2 text-xs text-stone-700 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 transition-colors cursor-pointer"
                role="menuitem"
              >
                Écraser le planning existant
              </button>
              <div class="my-1 border-t border-stone-200 dark:border-stone-700"></div>
              <button
                (click)="pullFromCookidoo()"
                class="w-full text-left px-3 py-2 text-xs text-stone-700 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 transition-colors cursor-pointer"
                role="menuitem"
              >
                Récupérer depuis Cookidoo
              </button>
            </div>
          }
        </div>

        <button
          (click)="suggestWeek()"
          [disabled]="loading()"
          class="flex-1 sm:flex-none inline-flex items-center justify-center gap-1.5 rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 px-3 py-1.5 text-xs font-medium text-stone-600 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline-2 focus-visible:outline-forest-500"
          aria-label="Suggérer des recettes aléatoires pour la semaine"
        >
          <ng-icon name="heroPlay" class="h-3.5 w-3.5" aria-hidden="true" />
          Suggérer
        </button>

        <button
          (click)="openWeeklyReview()"
          class="flex-1 sm:flex-none inline-flex items-center justify-center gap-1.5 rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 px-3 py-1.5 text-xs font-medium text-stone-600 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500"
          aria-label="Faire le bilan hebdomadaire de la semaine affichée"
        >
          <ng-icon name="heroCalendarDays" class="h-3.5 w-3.5" aria-hidden="true" />
          Bilan
        </button>
      </div>
    </div>

    <!-- Bandeau mode déplacement -->
    @if (heldMeal(); as held) {
      <div class="flex flex-wrap items-center gap-3 rounded-xl border border-amber-200 dark:border-amber-800 bg-amber-50 dark:bg-amber-950/20 px-4 py-2.5 mb-4">
        <span class="inline-flex items-center gap-2 rounded-full border border-amber-300 dark:border-amber-700 bg-white dark:bg-stone-900 pl-1.5 pr-3 py-1">
          <ng-icon name="heroArrowsRightLeft" class="h-3.5 w-3.5 text-amber-500 shrink-0" aria-hidden="true" />
          <span class="text-xs font-medium text-stone-800 dark:text-stone-200 max-w-[220px] truncate">{{ held.recipeName }}</span>
        </span>
        <span class="text-xs text-amber-700 dark:text-amber-400 flex-1">Naviguez entre les semaines puis cliquez le créneau cible</span>
        <button
          (click)="cancelHold()"
          class="inline-flex items-center gap-1 rounded-lg border border-amber-300 dark:border-amber-700 px-2.5 py-1 text-xs font-medium text-amber-700 dark:text-amber-400 hover:bg-amber-100 dark:hover:bg-amber-900/30 transition-colors cursor-pointer"
        >
          <ng-icon name="heroXMark" class="h-3 w-3" aria-hidden="true" />
          Annuler
        </button>
      </div>
    }

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
      <div class="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 mb-4" role="alert">{{ error() }}</div>
    }

    <!-- Grille calendrier -->
    @if (!loading() && weekPlan()) {
      <!-- Vue mobile : cartes verticales par jour (swipe pour changer de semaine) -->
      <div
        class="sm:hidden space-y-3"
        data-testid="mobile-calendar"
        (touchstart)="onTouchStart($event)"
        (touchend)="onTouchEnd($event)"
      >
        @for (day of weekDays(); track day.date) {
          <div
            class="rounded-xl border bg-white dark:bg-stone-950 shadow-sm overflow-hidden"
            data-testid="day-card"
            [ngClass]="day.isToday ? 'border-amber-300 dark:border-amber-700' : 'border-stone-200 dark:border-stone-800'"
          >
            <!-- En-tête du jour -->
            <div
              class="flex items-center gap-2 px-3 py-2 border-b border-stone-200 dark:border-stone-800"
              [ngClass]="day.isToday ? 'bg-amber-50 dark:bg-amber-950/20' : 'bg-stone-50 dark:bg-stone-900'"
            >
              <span
                class="text-xs font-bold uppercase tracking-wide"
                [ngClass]="day.isToday ? 'text-amber-500' : 'text-stone-500 dark:text-stone-400'"
              >{{ day.abbr }}</span>
              <span
                class="text-lg font-bold leading-none"
                [ngClass]="day.isToday ? 'text-amber-500' : 'text-stone-800 dark:text-stone-200'"
              >{{ day.num }}</span>
              <span
                class="text-xs"
                [ngClass]="day.isToday ? 'text-amber-400' : 'text-stone-400 dark:text-stone-500'"
              >{{ day.month }}</span>
              @if (day.isToday) {
                <span class="ml-auto text-[10px] font-semibold uppercase tracking-wider text-amber-600 dark:text-amber-400 bg-amber-100 dark:bg-amber-950/40 px-2 py-0.5 rounded-full">Aujourd'hui</span>
              }
            </div>
            <!-- Créneaux déjeuner + dîner côte à côte -->
            <div class="grid grid-cols-2 gap-2 p-2">
              <div>
                <p class="text-[10px] font-semibold uppercase tracking-wider text-stone-400 dark:text-stone-500 mb-1.5 px-0.5">Déj.</p>
                <app-meal-slot
                  [entry]="day.lunch"
                  [date]="day.date"
                  mealType="LUNCH"
                  label="Déjeuner"
                  [weekStart]="toISODateStr(weekStart())"
                  [inSelection]="day.lunch?.recipeId ? selectionIds().has(day.lunch!.recipeId!) : false"
                  [heldMode]="heldModeFor(day.date, 'LUNCH', !!day.lunch)"
                  (addRequested)="openPicker($event)"
                  (removeRequested)="handleRemove($event)"
                  (chatRequested)="openChatForSlot($event)"
                  (addToShoppingRequested)="onAddToShopping($event)"
                  (moveRequested)="handleMoveRequested($event)"
                  (placeRequested)="handlePlaceRequested($event)"
                />
              </div>
              <div>
                <p class="text-[10px] font-semibold uppercase tracking-wider text-stone-400 dark:text-stone-500 mb-1.5 px-0.5">Dîner</p>
                <app-meal-slot
                  [entry]="day.dinner"
                  [date]="day.date"
                  mealType="DINNER"
                  label="Dîner"
                  [weekStart]="toISODateStr(weekStart())"
                  [inSelection]="day.dinner?.recipeId ? selectionIds().has(day.dinner!.recipeId!) : false"
                  [heldMode]="heldModeFor(day.date, 'DINNER', !!day.dinner)"
                  (addRequested)="openPicker($event)"
                  (removeRequested)="handleRemove($event)"
                  (chatRequested)="openChatForSlot($event)"
                  (addToShoppingRequested)="onAddToShopping($event)"
                  (moveRequested)="handleMoveRequested($event)"
                  (placeRequested)="handlePlaceRequested($event)"
                />
              </div>
            </div>
            @if (day.undefinedMeals.length > 0) {
              <div class="px-2 pb-2">
                <p class="text-[10px] font-semibold uppercase tracking-wider text-stone-400 dark:text-stone-500 mb-1.5 px-0.5">Indéfini</p>
                <ng-container *ngTemplateOutlet="undefinedChips; context: { $implicit: day }" />
              </div>
            }
          </div>
        }
        <p class="text-center text-xs text-stone-400 dark:text-stone-500 py-2 select-none">← Glissez pour changer de semaine →</p>
      </div>

      <!-- Vue desktop : grille 7 colonnes -->
      <div class="hidden sm:block rounded-xl border border-stone-200 dark:border-stone-800 bg-white dark:bg-stone-950 shadow-sm overflow-hidden" data-testid="desktop-calendar">
        <div class="overflow-x-auto">
          <div class="grid min-w-[880px]" style="grid-template-columns: 72px repeat(7, minmax(130px, 1fr))">

            <!-- Ligne 1 : coin vide + en-têtes de jours -->
            <div></div>
            @for (day of weekDays(); track day.date) {
              <div
                class="text-center py-3 px-2 border-b border-stone-200 dark:border-stone-800"
                [ngClass]="day.isToday ? 'bg-amber-50 dark:bg-amber-950/20' : ''"
              >
                <p
                  class="text-[10px] font-semibold uppercase tracking-widest"
                  [ngClass]="day.isToday ? 'text-amber-500' : 'text-stone-400 dark:text-stone-500'"
                >{{ day.abbr }}</p>
                <p
                  class="text-2xl font-bold tracking-tight leading-none mt-0.5"
                  [ngClass]="day.isToday ? 'text-amber-500' : 'text-stone-800 dark:text-stone-200'"
                >{{ day.num }}</p>
                <p
                  class="text-[11px] mt-0.5"
                  [ngClass]="day.isToday ? 'text-amber-400' : 'text-stone-400 dark:text-stone-600'"
                >{{ day.month }}</p>
              </div>
            }

            <!-- Ligne 2 : label Déjeuner + cellules -->
            <div class="flex items-center px-3 py-3 border-b border-stone-200 dark:border-stone-800">
              <span class="text-[10px] font-semibold uppercase tracking-widest text-stone-400 dark:text-stone-600 whitespace-nowrap select-none">Déj.</span>
            </div>
            @for (day of weekDays(); track day.date) {
              <div
                class="p-2.5 border-l border-b border-stone-200 dark:border-stone-800"
                [ngClass]="day.isToday ? 'bg-amber-50/50 dark:bg-amber-950/10' : ''"
              >
                <app-meal-slot
                  [entry]="day.lunch"
                  [date]="day.date"
                  mealType="LUNCH"
                  label="Déjeuner"
                  [weekStart]="toISODateStr(weekStart())"
                  [inSelection]="day.lunch?.recipeId ? selectionIds().has(day.lunch!.recipeId!) : false"
                  [heldMode]="heldModeFor(day.date, 'LUNCH', !!day.lunch)"
                  (addRequested)="openPicker($event)"
                  (removeRequested)="handleRemove($event)"
                  (chatRequested)="openChatForSlot($event)"
                  (addToShoppingRequested)="onAddToShopping($event)"
                  (moveRequested)="handleMoveRequested($event)"
                  (placeRequested)="handlePlaceRequested($event)"
                />
              </div>
            }

            <!-- Ligne 3 : label Dîner + cellules -->
            <div class="flex items-center px-3 py-3 pb-4">
              <span class="text-[10px] font-semibold uppercase tracking-widest text-stone-400 dark:text-stone-600 whitespace-nowrap select-none">Dîner</span>
            </div>
            @for (day of weekDays(); track day.date) {
              <div
                class="p-2.5 pb-4 border-l border-stone-200 dark:border-stone-800"
                [ngClass]="day.isToday ? 'bg-amber-50/50 dark:bg-amber-950/10' : ''"
              >
                <app-meal-slot
                  [entry]="day.dinner"
                  [date]="day.date"
                  mealType="DINNER"
                  label="Dîner"
                  [weekStart]="toISODateStr(weekStart())"
                  [inSelection]="day.dinner?.recipeId ? selectionIds().has(day.dinner!.recipeId!) : false"
                  [heldMode]="heldModeFor(day.date, 'DINNER', !!day.dinner)"
                  (addRequested)="openPicker($event)"
                  (removeRequested)="handleRemove($event)"
                  (chatRequested)="openChatForSlot($event)"
                  (addToShoppingRequested)="onAddToShopping($event)"
                  (moveRequested)="handleMoveRequested($event)"
                  (placeRequested)="handlePlaceRequested($event)"
                />
              </div>
            }

            <!-- Ligne 4 : label Non défini + cellules (uniquement si au moins un jour en contient) -->
            @if (hasUndefinedMeals()) {
              <div class="flex items-center px-3 py-3 pb-4 border-t border-stone-200 dark:border-stone-800">
                <span class="text-[10px] font-semibold uppercase tracking-widest text-stone-400 dark:text-stone-600 whitespace-nowrap select-none">Indéfini</span>
              </div>
              @for (day of weekDays(); track day.date) {
                <div
                  class="p-2.5 pb-4 border-l border-t border-stone-200 dark:border-stone-800 min-w-0"
                  [ngClass]="day.isToday ? 'bg-amber-50/50 dark:bg-amber-950/10' : ''"
                >
                  @if (day.undefinedMeals.length > 0) {
                    <ng-container *ngTemplateOutlet="undefinedChips; context: { $implicit: day }" />
                  }
                </div>
              }
            }

          </div>
        </div>
      </div>
    }

    <!-- Puces "Non défini" (partagé desktop + mobile) -->
    <ng-template #undefinedChips let-day>
      <div class="flex flex-wrap gap-1.5 min-w-0 w-full">
        @for (entry of day.undefinedMeals; track entry.id) {
          <div
            class="group relative flex items-start gap-1 rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 pl-1 pr-1 py-1 max-w-full transition-all sm:hover:-translate-y-0.5 sm:hover:border-stone-300 dark:sm:hover:border-stone-600 sm:hover:shadow-md"
            [class.opacity-30]="heldMeal()?.sourceId === entry.id"
            [class.pointer-events-none]="heldMeal()?.sourceId === entry.id"
          >
            <div class="w-5 h-5 rounded-full bg-stone-100 dark:bg-stone-800 overflow-hidden flex items-center justify-center shrink-0">
              @if (entry.recipeThumbnailUrl) {
                <img [src]="entry.recipeThumbnailUrl" [alt]="entry.recipeName" class="w-full h-full object-cover" loading="lazy" />
              } @else {
                <svg class="h-2.5 w-2.5 text-stone-300 dark:text-stone-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                </svg>
              }
            </div>
            @if (entry.recipeId) {
              <a
                [routerLink]="['/recipes', entry.recipeId]"
                class="text-[11px] font-medium text-stone-700 dark:text-stone-300 line-clamp-2 break-words hover:text-forest-600 dark:hover:text-forest-400 transition-colors focus-visible:outline-2 focus-visible:outline-forest-500 rounded"
              >{{ entry.recipeName }}</a>
            } @else {
              <span class="text-[11px] font-medium text-stone-400 dark:text-stone-500 italic line-clamp-2 break-words">{{ entry.recipeName }}</span>
            }
            <button
              (click)="handleMoveUndefinedRequested(day.date, entry)"
              class="w-4 h-4 shrink-0 rounded-full flex items-center justify-center text-stone-400 dark:text-stone-500 sm:opacity-0 sm:group-hover:opacity-100 hover:text-amber-600 dark:hover:text-amber-400 transition-all cursor-pointer"
              aria-label="Déplacer cette recette"
              title="Déplacer"
            >
              <ng-icon name="heroArrowsRightLeft" class="h-2.5 w-2.5" aria-hidden="true" />
            </button>
            <button
              (click)="handleRemoveUndefined(entry.id!)"
              class="w-4 h-4 shrink-0 rounded-full flex items-center justify-center text-stone-400 dark:text-stone-500 sm:opacity-0 sm:group-hover:opacity-100 hover:text-red-500 transition-all cursor-pointer"
              aria-label="Supprimer cette recette"
              title="Supprimer"
            >
              <ng-icon name="heroXMark" class="h-2.5 w-2.5" aria-hidden="true" />
            </button>
          </div>
        }
      </div>
    </ng-template>

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

    <!-- Modale de bilan hebdomadaire -->
    @if (weeklyReviewOpen()) {
      <app-weekly-review-modal
        [weekStart]="toISODateStr(weekStart())"
        (dismissed)="weeklyReviewOpen.set(false)"
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
  weeklyReviewOpen = signal(false);
  selectionIds = signal<Set<string>>(new Set());
  syncing = signal(false);
  syncDropdownOpen = signal(false);
  heldMeal = signal<HeldMeal | null>(null);
  pickerDate = '';
  pickerMealType = '';

  weekDays = computed<WeekDay[]>(() => {
    const plan = this.weekPlan();
    if (!plan) return [];
    const today = new Date();
    return plan.days.map(day => {
      const d = new Date(day.date + 'T00:00:00');
      const isToday =
        d.getDate() === today.getDate() &&
        d.getMonth() === today.getMonth() &&
        d.getFullYear() === today.getFullYear();
      return {
        ...day,
        isToday,
        abbr: d.toLocaleDateString('fr-FR', { weekday: 'short' }).toUpperCase().replace('.', '').slice(0, 3),
        num: String(d.getDate()),
        month: d.toLocaleDateString('fr-FR', { month: 'short' }).replace('.', ''),
      };
    });
  });

  filledSlots = computed(() =>
    this.weekPlan()?.days.reduce((acc, day) => acc + (day.lunch ? 1 : 0) + (day.dinner ? 1 : 0), 0) ?? 0
  );

  totalSlots = computed(() => (this.weekPlan()?.days.length ?? 7) * 2);

  hasUndefinedMeals = computed(() => this.weekDays().some(d => d.undefinedMeals.length > 0));

  private renderer = inject(Renderer2);
  private document = inject(DOCUMENT);
  private route = inject(ActivatedRoute);
  private toast = inject(ToastService);

  constructor(private menuPlanService: MenuPlanService, private shoppingListService: ShoppingListService) {
    effect(() => {
      if (this.weeklyDrawerOpen()) {
        this.renderer.addClass(this.document.body, 'drawer-open');
      } else {
        this.renderer.removeClass(this.document.body, 'drawer-open');
      }
    });
  }

  private touchStartX = 0;

  onTouchStart(event: TouchEvent): void {
    this.touchStartX = event.touches[0].clientX;
  }

  onTouchEnd(event: TouchEvent): void {
    const dx = event.changedTouches[0].clientX - this.touchStartX;
    if (Math.abs(dx) > 60) {
      if (dx < 0) this.nextWeek(); else this.prevWeek();
    }
  }

  ngOnDestroy(): void {
    this.renderer.removeClass(this.document.body, 'drawer-open');
  }

  ngOnInit(): void {
    const ws = this.route.snapshot.queryParamMap.get('weekStart');
    if (ws) this.weekStart.set(getMondayOf(new Date(ws + 'T00:00:00')));
    this.loadWeekPlan();
    this.loadSelectionIds();
  }

  private loadSelectionIds(): void {
    this.shoppingListService.getShoppingList().subscribe({
      next: list => {
        const ids = new Set(list.recipes
          .filter(r => r.recipeId != null)
          .map(r => r.recipeId!));
        this.selectionIds.set(ids);
      }
    });
  }

  onAddToShopping({ recipeId, recipeName }: { recipeId: string; recipeName: string }): void {
    if (this.selectionIds().has(recipeId)) return;
    this.shoppingListService.addRecipeToSelection(recipeId)
      .pipe(catchError(() => EMPTY))
      .subscribe(() => {
        this.selectionIds.update(s => new Set([...s, recipeId]));
      });
  }

  weekRangeLabel(): string {
    const start = this.weekStart();
    const end = new Date(start);
    end.setDate(end.getDate() + 6);
    const fmt = (d: Date, opts: Intl.DateTimeFormatOptions) =>
      d.toLocaleDateString('fr-FR', opts).replace(/\./g, '');
    return `${fmt(start, { day: 'numeric', month: 'short' })} – ${fmt(end, { day: 'numeric', month: 'short', year: 'numeric' })}`;
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

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    this.cancelHold();
  }

  heldModeFor(date: string, mealType: MealType, hasEntry: boolean): HeldMode {
    const held = this.heldMeal();
    if (!held) return 'none';
    if (held.date === date && held.mealType === mealType) return 'source';
    return hasEntry ? 'target-swap' : 'target-empty';
  }

  handleMoveRequested(event: { date: string; mealType: string }): void {
    const entry = this.entryAt(event.date, event.mealType as MealType);
    if (!entry?.recipeId) return;
    this.heldMeal.set({
      date: event.date,
      mealType: event.mealType as MealType,
      recipeId: entry.recipeId,
      recipeName: entry.recipeName ?? '',
    });
  }

  cancelHold(): void {
    this.heldMeal.set(null);
  }

  handleMoveUndefinedRequested(date: string, entry: MealPlanEntryDto): void {
    if (entry.id == null) return;
    this.heldMeal.set({
      date,
      mealType: MealType.UNDEFINED,
      recipeId: entry.recipeId ?? '',
      recipeName: entry.recipeName ?? '',
      sourceId: entry.id,
    });
  }

  handleRemoveUndefined(id: number): void {
    this.menuPlanService.removeUndefinedEntry(id)
      .pipe(catchError(() => {
        this.error.set('Impossible de supprimer cette recette.');
        return EMPTY;
      }))
      .subscribe(() => this.loadWeekPlan());
  }

  handlePlaceRequested(event: { date: string; mealType: string }): void {
    const held = this.heldMeal();
    if (!held) return;
    const targetMealType = event.mealType as MealType;
    const targetEntry = this.entryAt(event.date, targetMealType);

    this.heldMeal.set(null);

    const onError = () => {
      this.toast.show({ type: 'error', title: 'Échec du déplacement', message: 'Vérifiez le planning et réessayez.' });
      this.loadWeekPlan();
    };
    const onSuccess = (message: string) => {
      this.toast.show({ type: 'success', title: message });
      this.loadWeekPlan();
    };

    if (held.sourceId != null) {
      this.menuPlanService.relocateUndefinedEntry(held.sourceId, { date: event.date, mealType: targetMealType })
        .pipe(catchError(() => { onError(); return EMPTY; }))
        .subscribe(() => onSuccess(targetEntry?.recipeId ? 'Recettes échangées' : 'Recette déplacée'));
      return;
    }

    if (targetEntry?.recipeId) {
      this.menuPlanService.upsertBulkEntries({
        entries: [
          { date: event.date, mealType: targetMealType, recipeId: held.recipeId },
          { date: held.date, mealType: held.mealType, recipeId: targetEntry.recipeId },
        ],
      })
        .pipe(catchError(() => { onError(); return EMPTY; }))
        .subscribe(() => onSuccess('Recettes échangées'));
    } else {
      this.menuPlanService.relocateEntry(held.date, held.mealType, { date: event.date, mealType: targetMealType })
        .pipe(catchError(() => { onError(); return EMPTY; }))
        .subscribe(() => onSuccess('Recette déplacée'));
    }
  }

  private entryAt(date: string, mealType: MealType): MealPlanEntryDto | null | undefined {
    const day = this.weekPlan()?.days.find(d => d.date === date);
    return mealType === MealType.LUNCH ? day?.lunch : day?.dinner;
  }

  openWeeklyDrawer(): void {
    this.cancelHold();
    this.weeklyDrawerOpen.set(true);
  }

  openWeeklyReview(): void {
    this.cancelHold();
    this.weeklyReviewOpen.set(true);
  }

  openPicker(event: { date: string; mealType: string }): void {
    this.cancelHold();
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
    this.cancelHold();
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

  syncToCookidoo(replace: boolean): void {
    this.syncDropdownOpen.set(false);
    this.syncing.set(true);
    this.menuPlanService.syncWeekToCookidoo(toISODate(this.weekStart()), replace)
      .pipe(catchError((_err: HttpErrorResponse) => {
        this.syncing.set(false);
        this.toast.show({ type: 'error', title: 'Échec de la synchronisation', message: 'Vérifiez votre connexion à Cookidoo.' });
        return EMPTY;
      }))
      .subscribe(() => {
        this.syncing.set(false);
        this.toast.show({ type: 'success', title: 'Synchronisé avec Cookidoo', message: 'Le planning a bien été envoyé.' });
      });
  }

  pullFromCookidoo(): void {
    this.syncDropdownOpen.set(false);
    this.syncing.set(true);
    this.menuPlanService.pullWeekFromCookidoo(toISODate(this.weekStart()))
      .pipe(catchError((_err: HttpErrorResponse) => {
        this.syncing.set(false);
        this.toast.show({ type: 'error', title: 'Échec de la récupération', message: 'Vérifiez votre connexion à Cookidoo.' });
        return EMPTY;
      }))
      .subscribe(() => {
        this.syncing.set(false);
        this.toast.show({ type: 'success', title: 'Récupéré depuis Cookidoo', message: 'Le planning a été mis à jour.' });
        this.loadWeekPlan();
      });
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
