import { Component, EventEmitter, Input, OnChanges, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { MenuPlanService, MealPlanEntryDto, MealType } from '@KitchenVault/api-client';

@Component({
  selector: 'app-meal-slot',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm min-h-[100px]">
      <p class="mb-3 text-xs font-semibold uppercase tracking-wide text-zinc-500">{{ label }}</p>

      @if (entry) {
        <!-- Recette planifiée -->
        <div class="flex items-start gap-3">
          <div class="flex-shrink-0 w-14 h-14 overflow-hidden rounded-lg bg-zinc-100">
            @if (entry.recipeThumbnailUrl) {
              <img [src]="entry.recipeThumbnailUrl" [alt]="entry.recipeName" class="h-full w-full object-cover" loading="lazy" />
            } @else {
              <div class="flex h-full w-full items-center justify-center">
                <svg class="h-6 w-6 text-zinc-300" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                </svg>
              </div>
            }
          </div>
          <div class="flex-1 min-w-0">
            @if (entry.recipeId) {
              <a [routerLink]="['/recipes', entry.recipeId]" class="block text-sm font-medium text-zinc-900 hover:text-indigo-600 truncate transition-colors focus-visible:outline-2 focus-visible:outline-indigo-500 rounded">
                {{ entry.recipeName }}
              </a>
            } @else {
              <p class="text-sm font-medium text-zinc-400 truncate italic">{{ entry.recipeName }}</p>
            }
            @if (entry.recipeTotalTimeMinutes) {
              <p class="mt-0.5 text-xs text-zinc-400">{{ entry.recipeTotalTimeMinutes }} min</p>
            }
          </div>
          <button
            (click)="onRemove()"
            class="flex-shrink-0 rounded-lg p-1.5 text-zinc-400 hover:bg-red-50 hover:text-red-500 transition-colors focus-visible:outline-2 focus-visible:outline-indigo-500"
            aria-label="Supprimer ce repas"
          >
            <svg class="h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

      } @else {
        <!-- Créneau vide -->
        <div class="space-y-2">
          <button
            (click)="onAdd()"
            class="inline-flex items-center gap-1.5 rounded-lg border border-dashed border-zinc-300 px-3 py-1.5 text-sm text-zinc-500 hover:border-indigo-400 hover:text-indigo-600 transition-colors focus-visible:outline-2 focus-visible:outline-indigo-500"
            aria-label="Ajouter une recette"
          >
            <svg class="h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
            Ajouter
          </button>

          <!-- Suggérer -->
          <div class="flex items-center gap-2">
            <button
              (click)="loadSuggestions()"
              [disabled]="suggestionsLoading()"
              class="inline-flex items-center gap-1.5 rounded-lg border border-zinc-200 px-3 py-1.5 text-sm text-zinc-600 hover:bg-zinc-50 disabled:opacity-50 transition-colors focus-visible:outline-2 focus-visible:outline-indigo-500"
              aria-label="Suggérer des recettes"
            >
              @if (suggestionsLoading()) {
                <svg class="animate-spin h-3.5 w-3.5 text-indigo-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-hidden="true">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
                </svg>
              }
              Suggérer
            </button>
            <input
              type="number"
              [(ngModel)]="maxTimeInput"
              placeholder="Temps max (min)"
              min="1"
              class="w-36 rounded-lg border border-zinc-200 px-2 py-1.5 text-xs text-zinc-700 placeholder-zinc-400 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-200"
              aria-label="Temps de préparation maximum en minutes"
            />
          </div>

          <!-- Suggestions -->
          @if (suggestions().length > 0) {
            <div class="mt-2 space-y-1.5">
              @for (s of suggestions(); track s.recipeName) {
                <div class="flex items-center justify-between gap-2 rounded-lg border border-zinc-100 bg-zinc-50 px-3 py-2">
                  <div class="flex items-center gap-2 min-w-0">
                    <div class="flex-shrink-0 w-8 h-8 overflow-hidden rounded bg-zinc-200">
                      @if (s.recipeThumbnailUrl) {
                        <img [src]="s.recipeThumbnailUrl" [alt]="s.recipeName" class="h-full w-full object-cover" loading="lazy" />
                      }
                    </div>
                    <div class="min-w-0">
                      <p class="text-xs font-medium text-zinc-800 truncate">{{ s.recipeName }}</p>
                      @if (s.recipeTotalTimeMinutes) {
                        <p class="text-xs text-zinc-400">{{ s.recipeTotalTimeMinutes }} min</p>
                      }
                    </div>
                  </div>
                  <button
                    (click)="onAddSuggestion(s.recipeId!)"
                    class="flex-shrink-0 rounded-lg bg-indigo-600 px-2 py-1 text-xs font-medium text-white hover:bg-indigo-700 transition-colors focus-visible:outline-2 focus-visible:outline-indigo-500"
                    aria-label="Ajouter cette suggestion"
                  >
                    Ajouter
                  </button>
                </div>
              }
            </div>
          }

          @if (suggestionsError()) {
            <p class="text-xs text-red-600">{{ suggestionsError() }}</p>
          }
        </div>
      }
    </div>
  `,
})
export class MealSlotComponent implements OnChanges {
  @Input() entry: MealPlanEntryDto | null | undefined;
  @Input({ required: true }) date!: string;
  @Input({ required: true }) mealType!: string;
  @Input({ required: true }) label!: string;

  @Output() addRequested = new EventEmitter<{ date: string; mealType: string }>();
  @Output() removeRequested = new EventEmitter<{ date: string; mealType: string }>();
  @Output() addSuggestion = new EventEmitter<{ date: string; mealType: string; recipeId: string }>();

  suggestions = signal<MealPlanEntryDto[]>([]);
  suggestionsLoading = signal(false);
  suggestionsError = signal<string | null>(null);
  maxTimeInput = '';

  constructor(private menuPlanService: MenuPlanService) {}

  ngOnChanges(): void {
    this.suggestions.set([]);
    this.suggestionsError.set(null);
  }

  loadSuggestions(): void {
    this.suggestionsLoading.set(true);
    this.suggestionsError.set(null);
    this.suggestions.set([]);

    const maxTime = parseInt(this.maxTimeInput, 10);
    const maxTotalMinutes = !isNaN(maxTime) && maxTime > 0 ? maxTime : undefined;

    this.menuPlanService.getSuggestions(this.date, this.mealType as MealType, maxTotalMinutes, 3)
      .pipe(catchError(() => {
        this.suggestionsError.set('Impossible de charger les suggestions.');
        return of([]);
      }))
      .subscribe(result => {
        this.suggestions.set(result);
        if (result.length === 0 && !this.suggestionsError()) {
          this.suggestionsError.set('Aucune suggestion disponible.');
        }
        this.suggestionsLoading.set(false);
      });
  }

  onAdd(): void {
    this.addRequested.emit({ date: this.date, mealType: this.mealType });
  }

  onRemove(): void {
    this.removeRequested.emit({ date: this.date, mealType: this.mealType });
  }

  onAddSuggestion(recipeId: string): void {
    this.addSuggestion.emit({ date: this.date, mealType: this.mealType, recipeId });
    this.suggestions.set([]);
    this.suggestionsError.set(null);
  }
}
