import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { catchError, debounceTime, distinctUntilChanged, of, Subject, switchMap, takeUntil } from 'rxjs';
import { RecipesService, RecipeSummaryDto } from '@KitchenVault/api-client';

@Component({
  selector: 'app-recipe-picker-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      role="dialog"
      aria-modal="true"
      aria-label="Sélectionner une recette"
      (click)="onBackdropClick($event)"
    >
      <div class="w-full max-w-lg rounded-2xl bg-white dark:bg-stone-900 shadow-xl flex flex-col max-h-[80vh]" (click)="$event.stopPropagation()">

        <!-- Header -->
        <div class="flex items-center justify-between border-b border-stone-100 dark:border-stone-800 px-6 py-4">
          <h2 class="text-base font-semibold text-stone-900 dark:text-stone-100">Choisir une recette</h2>
          <button
            (click)="dismiss()"
            class="rounded-lg p-1.5 text-stone-400 dark:text-stone-500 hover:bg-stone-100 dark:hover:bg-stone-800 hover:text-stone-700 dark:hover:text-stone-300 transition-colors focus-visible:outline-2 focus-visible:outline-forest-500"
            aria-label="Fermer"
          >
            <svg class="h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <!-- Search -->
        <div class="border-b border-stone-100 dark:border-stone-800 px-6 py-3">
          <div class="relative">
            <svg class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-stone-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-4.35-4.35M17 11A6 6 0 111 11a6 6 0 0116 0z" />
            </svg>
            <input
              #searchInput
              type="search"
              [(ngModel)]="searchText"
              (ngModelChange)="onSearchChange($event)"
              placeholder="Rechercher une recette…"
              class="w-full rounded-xl border border-stone-200 dark:border-stone-700 bg-stone-50 dark:bg-stone-800 py-2 pl-10 pr-4 text-sm text-stone-900 dark:text-stone-100 placeholder-stone-400 focus:border-forest-400 focus:bg-white dark:focus:bg-stone-800 focus:outline-none focus:ring-2 focus:ring-forest-200"
              aria-label="Rechercher une recette"
            />
          </div>
        </div>

        <!-- Results -->
        <div class="flex-1 overflow-y-auto px-6 py-3">
          @if (loading()) {
            <div class="flex items-center justify-center py-8">
              <svg class="animate-spin h-6 w-6 text-forest-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-label="Chargement">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
              </svg>
            </div>
          }

          @if (!loading() && recipes().length === 0) {
            <p class="py-8 text-center text-sm text-stone-500 dark:text-stone-400">Aucune recette trouvée.</p>
          }

          @if (!loading() && recipes().length > 0) {
            <ul class="space-y-1.5" role="list">
              @for (recipe of recipes(); track recipe.id) {
                <li>
                  <button
                    (click)="selectRecipe(recipe.id)"
                    class="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left hover:bg-forest-50 dark:hover:bg-forest-950 transition-colors focus-visible:outline-2 focus-visible:outline-forest-500"
                    [attr.aria-label]="'Sélectionner ' + recipe.name"
                  >
                    <div class="flex-shrink-0 w-10 h-10 overflow-hidden rounded-lg bg-stone-100 dark:bg-stone-800">
                      @if (recipe.thumbnailUrl) {
                        <img [src]="recipe.thumbnailUrl" [alt]="recipe.name" class="h-full w-full object-cover" loading="lazy" />
                      } @else {
                        <div class="flex h-full w-full items-center justify-center">
                          <svg class="h-5 w-5 text-stone-300 dark:text-stone-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                          </svg>
                        </div>
                      }
                    </div>
                    <div class="min-w-0 flex-1">
                      <p class="text-sm font-medium text-stone-900 dark:text-stone-100 truncate">{{ recipe.name }}</p>
                      @if (recipe.totalTimeMinutes) {
                        <p class="text-xs text-stone-400 dark:text-stone-500">{{ recipe.totalTimeMinutes }} min</p>
                      }
                    </div>
                    <svg class="h-4 w-4 flex-shrink-0 text-stone-300 dark:text-stone-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                    </svg>
                  </button>
                </li>
              }
            </ul>
          }
        </div>
      </div>
    </div>
  `,
})
export class RecipePickerDialogComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) date!: string;
  @Input({ required: true }) mealType!: string;

  @Output() recipeSelected = new EventEmitter<string>();
  @Output() dismissed = new EventEmitter<void>();

  recipes = signal<RecipeSummaryDto[]>([]);
  loading = signal(false);
  searchText = '';

  private readonly searchTrigger$ = new Subject<string>();
  private readonly destroy$ = new Subject<void>();

  constructor(private recipesService: RecipesService) {
    this.searchTrigger$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(search => {
        this.loading.set(true);
        return this.recipesService.listRecipes(0, 20, search || undefined).pipe(
          catchError(() => of({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 })),
        );
      }),
      takeUntil(this.destroy$),
    ).subscribe(page => {
      this.recipes.set(page.content ?? []);
      this.loading.set(false);
    });
  }

  ngOnChanges(): void {
    this.searchText = '';
    this.onSearchChange('');
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSearchChange(value: string): void {
    this.searchTrigger$.next(value.trim());
  }

  selectRecipe(id: string): void {
    this.recipeSelected.emit(id);
  }

  dismiss(): void {
    this.dismissed.emit();
  }

  onBackdropClick(event: Event): void {
    if (event.target === event.currentTarget) {
      this.dismissed.emit();
    }
  }
}
