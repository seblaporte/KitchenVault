import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, of, Subject, switchMap, takeUntil } from 'rxjs';
import { environment } from '../../environments/environment';

interface CollectionRecipe {
  id: string;
  name: string;
  thumbnailUrl?: string;
}

interface Chapter {
  name: string;
  recipes: CollectionRecipe[];
}

interface Collection {
  id: string;
  name: string;
  description?: string;
  chapters: Chapter[];
}

interface RecipeSummary {
  id: string;
  name: string;
  thumbnailUrl?: string;
  totalTimeMinutes?: number;
  difficulty?: string;
}

interface RecipePage {
  content: RecipeSummary[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

interface Category {
  id: string;
  name: string;
}

const TIME_BUCKETS = [
  { label: '≤ 20 min', value: 20 },
  { label: '≤ 40 min', value: 40 },
  { label: '≤ 60 min', value: 60 },
  { label: '> 60 min', value: 999 },
];

@Component({
  selector: 'app-recipes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <h1 class="text-2xl font-semibold tracking-tight text-stone-900 dark:text-stone-100">Recettes</h1>

      <!-- Barre de recherche + filtres -->
      <div class="space-y-3">

        <!-- Champ de recherche -->
        <div class="relative">
          <svg class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-stone-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-4.35-4.35M17 11A6 6 0 111 11a6 6 0 0116 0z" />
          </svg>
          <input
            type="search"
            [(ngModel)]="searchText"
            (ngModelChange)="onSearchChange($event)"
            placeholder="Rechercher une recette…"
            class="w-full rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 py-2.5 pl-10 pr-10 text-sm text-stone-900 dark:text-stone-100 placeholder-stone-400 shadow-sm focus:border-forest-400 focus:outline-none focus:ring-2 focus:ring-forest-200 dark:focus:ring-forest-700"
            aria-label="Rechercher une recette"
          />
          @if (searchText) {
            <button
              (click)="clearSearch()"
              class="absolute right-3 top-1/2 -translate-y-1/2 text-stone-400 hover:text-stone-700 dark:hover:text-stone-200 cursor-pointer focus-visible:outline-none"
              aria-label="Effacer la recherche"
            >
              <svg class="h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          }
        </div>

        <!-- Filtres -->
        <div class="rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 p-4 shadow-sm space-y-3">

          <!-- Collections -->
          @if (collectionsWithRecipes().length > 0) {
            <div>
              <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-stone-500 dark:text-stone-400">Collections</p>
              <div class="flex flex-wrap gap-2" role="group" aria-label="Filtrer par collection">
                @for (col of collectionsWithRecipes(); track col.id) {
                  <button
                    (click)="toggleCollection(col.id)"
                    [class]="chipClass(selectedCollections.has(col.id))"
                    [attr.aria-pressed]="selectedCollections.has(col.id)"
                  >{{ col.name }}</button>
                }
              </div>
            </div>
          }

          <!-- Catégories -->
          @if (categories().length > 0) {
            <div>
              <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-stone-500 dark:text-stone-400">Catégories</p>
              <div class="flex flex-wrap gap-2" role="group" aria-label="Filtrer par catégorie">
                @for (cat of categories(); track cat.id) {
                  <button
                    (click)="toggleCategory(cat.id)"
                    [class]="chipClass(selectedCategories.has(cat.id))"
                    [attr.aria-pressed]="selectedCategories.has(cat.id)"
                  >{{ cat.name }}</button>
                }
              </div>
            </div>
          }

          <!-- Difficulté -->
          @if (difficulties().length > 0) {
            <div>
              <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-stone-500 dark:text-stone-400">Difficulté</p>
              <div class="flex flex-wrap gap-2" role="group" aria-label="Filtrer par difficulté">
                @for (d of difficulties(); track d) {
                  <button
                    (click)="toggleDifficulty(d)"
                    [class]="chipClass(selectedDifficulties.has(d))"
                    [attr.aria-pressed]="selectedDifficulties.has(d)"
                  >{{ d }}</button>
                }
              </div>
            </div>
          }

          <!-- Temps total -->
          <div>
            <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-stone-500 dark:text-stone-400">Temps total</p>
            <div class="flex flex-wrap gap-2" role="group" aria-label="Filtrer par temps total">
              @for (bucket of timeBuckets; track bucket.value) {
                <button
                  (click)="toggleTimeBucket(bucket.value)"
                  [class]="chipClass(selectedMaxTime === bucket.value)"
                  [attr.aria-pressed]="selectedMaxTime === bucket.value"
                >{{ bucket.label }}</button>
              }
            </div>
          </div>

          <!-- Ingrédients -->
          <div>
            <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-stone-500 dark:text-stone-400">Ingrédients</p>
            <div class="flex gap-2 mb-2">
              <input
                type="text"
                [(ngModel)]="ingredientInput"
                (keydown.enter)="addIngredient()"
                placeholder="Ex : poulet, farine…"
                class="flex-1 rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 px-3 py-1.5 text-sm text-stone-900 dark:text-stone-100 placeholder-stone-400 shadow-sm focus:border-forest-400 focus:outline-none focus:ring-2 focus:ring-forest-200 dark:focus:ring-forest-700"
                aria-label="Ajouter un ingrédient"
              />
              <button
                (click)="addIngredient()"
                [disabled]="!ingredientInput.trim()"
                class="rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 px-3 py-1.5 text-sm font-medium text-stone-700 dark:text-stone-300 shadow-sm hover:bg-stone-50 dark:hover:bg-stone-800 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                aria-label="Ajouter l'ingrédient"
              >+</button>
            </div>
            @if (selectedIngredients.length > 0) {
              <div class="flex flex-wrap gap-2" role="group" aria-label="Ingrédients sélectionnés">
                @for (name of selectedIngredients; track name; let i = $index) {
                  <span class="inline-flex items-center gap-1 rounded-full px-3 py-1 text-xs font-medium bg-forest-600 text-white">
                    {{ name }}
                    <button
                      (click)="removeIngredient(i)"
                      class="ml-0.5 rounded-full hover:text-forest-200 cursor-pointer focus-visible:outline-none"
                      [attr.aria-label]="'Retirer ' + name"
                    >
                      <svg class="h-3 w-3" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                      </svg>
                    </button>
                  </span>
                }
              </div>
            }
          </div>

          <!-- Indicateur filtres actifs -->
          @if (activeFilterCount > 0) {
            <div class="flex items-center justify-between border-t border-stone-100 dark:border-stone-800 pt-3">
              <span class="text-xs text-stone-500 dark:text-stone-400">{{ activeFilterCount }} filtre{{ activeFilterCount > 1 ? 's' : '' }} actif{{ activeFilterCount > 1 ? 's' : '' }}</span>
              <button
                (click)="clearAllFilters()"
                class="text-xs font-medium text-forest-600 dark:text-forest-400 hover:text-forest-800 dark:hover:text-forest-300 transition-colors cursor-pointer focus-visible:outline-none"
              >Tout effacer</button>
            </div>
          }
        </div>
      </div>

      <!-- Mode navigation (aucun filtre) -->
      @if (!isSearchMode()) {

        @if (collectionsLoading()) {
          <div class="flex items-center justify-center py-20">
            <svg class="animate-spin h-8 w-8 text-forest-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-label="Chargement">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
            </svg>
          </div>
        }

        @if (collectionsError()) {
          <div class="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">{{ collectionsError() }}</div>
        }

        @if (!collectionsLoading() && !collectionsError() && collections().length === 0) {
          <div class="rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 p-12 text-center text-sm text-stone-500 dark:text-stone-400">
            Aucune recette disponible. Lancez une synchronisation depuis l'Administration.
          </div>
        }

        @for (collection of collections(); track collection.id) {
          @if (hasRecipes(collection)) {
            <section [attr.aria-labelledby]="'collection-' + collection.id">
              <h2 [id]="'collection-' + collection.id" class="mb-4 text-lg font-semibold text-stone-800 dark:text-stone-200">{{ collection.name }}</h2>
              @for (chapter of collection.chapters; track chapter.name) {
                <div class="mb-6">
                  @if (collection.chapters.length > 1) {
                    <h3 class="mb-3 text-sm font-medium uppercase tracking-wide text-stone-500 dark:text-stone-400">{{ chapter.name }}</h3>
                  }
                  <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
                    @for (recipe of chapter.recipes; track recipe.id) {
                      <button (click)="openRecipe(recipe.id)" [class]="recipeTileClass" [attr.aria-label]="recipe.name">
                        <ng-container *ngTemplateOutlet="recipeThumbnail; context: { recipe: recipe }"></ng-container>
                        <div class="p-3"><p class="line-clamp-2 text-sm font-medium text-stone-900 dark:text-stone-100 leading-snug">{{ recipe.name }}</p></div>
                      </button>
                    }
                  </div>
                </div>
              }
            </section>
          }
        }
      }

      <!-- Mode recherche (filtres actifs) -->
      @if (isSearchMode()) {

        @if (searchLoading()) {
          <div class="flex items-center justify-center py-20">
            <svg class="animate-spin h-8 w-8 text-forest-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-label="Chargement">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
            </svg>
          </div>
        }

        @if (!searchLoading()) {
          <!-- Compteur de résultats -->
          <p class="text-sm text-stone-500 dark:text-stone-400">
            {{ searchResult().totalElements }} recette{{ searchResult().totalElements !== 1 ? 's' : '' }} trouvée{{ searchResult().totalElements !== 1 ? 's' : '' }}
          </p>

          <!-- Grille résultats -->
          @if (searchResult().content.length === 0) {
            <div class="rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 p-12 text-center text-sm text-stone-500 dark:text-stone-400">
              Aucune recette ne correspond à vos critères.
            </div>
          }

          @if (searchResult().content.length > 0) {
            <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
              @for (recipe of searchResult().content; track recipe.id) {
                <button (click)="openRecipe(recipe.id)" [class]="recipeTileClass" [attr.aria-label]="recipe.name">
                  <ng-container *ngTemplateOutlet="recipeThumbnail; context: { recipe: recipe }"></ng-container>
                  <div class="p-3">
                    <p class="line-clamp-2 text-sm font-medium text-stone-900 dark:text-stone-100 leading-snug">{{ recipe.name }}</p>
                    @if (recipe.totalTimeMinutes) {
                      <p class="mt-1 text-xs text-stone-400 dark:text-stone-500">{{ recipe.totalTimeMinutes }} min</p>
                    }
                  </div>
                </button>
              }
            </div>

            <!-- Pagination -->
            @if (searchResult().totalPages > 1) {
              <div class="flex items-center justify-between pt-2">
                <button
                  (click)="goToPage(currentPage() - 1)"
                  [disabled]="currentPage() === 0"
                  class="inline-flex items-center gap-1.5 rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 px-3 py-2 text-sm text-stone-700 dark:text-stone-300 shadow-sm hover:bg-stone-50 dark:hover:bg-stone-800 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed transition-colors focus-visible:outline-2 focus-visible:outline-forest-500"
                >
                  <svg class="h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
                  </svg>
                  Précédent
                </button>
                <span class="text-sm text-stone-500 dark:text-stone-400">Page {{ currentPage() + 1 }} / {{ searchResult().totalPages }}</span>
                <button
                  (click)="goToPage(currentPage() + 1)"
                  [disabled]="currentPage() >= searchResult().totalPages - 1"
                  class="inline-flex items-center gap-1.5 rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 px-3 py-2 text-sm text-stone-700 dark:text-stone-300 shadow-sm hover:bg-stone-50 dark:hover:bg-stone-800 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed transition-colors focus-visible:outline-2 focus-visible:outline-forest-500"
                >
                  Suivant
                  <svg class="h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                  </svg>
                </button>
              </div>
            }
          }
        }
      }
    </div>

    <!-- Template tuile recette -->
    <ng-template #recipeThumbnail let-recipe="recipe">
      <div class="aspect-square w-full overflow-hidden bg-stone-100 dark:bg-stone-800">
        @if (recipe.thumbnailUrl) {
          <img [src]="recipe.thumbnailUrl" [alt]="recipe.name" class="h-full w-full object-cover transition-transform group-hover:scale-105" loading="lazy" />
        } @else {
          <div class="flex h-full w-full items-center justify-center">
            <svg class="h-10 w-10 text-stone-300 dark:text-stone-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
            </svg>
          </div>
        }
      </div>
    </ng-template>
  `,
})
export class RecipesComponent implements OnInit, OnDestroy {
  readonly timeBuckets = TIME_BUCKETS;
  readonly recipeTileClass = 'group flex flex-col overflow-hidden rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 text-left shadow-sm transition-all hover:shadow-md hover:-translate-y-0.5 cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500';

  collections = signal<Collection[]>([]);
  collectionsLoading = signal(true);
  collectionsError = signal<string | null>(null);

  categories = signal<Category[]>([]);
  difficulties = signal<string[]>([]);

  searchResult = signal<RecipePage>({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 });
  searchLoading = signal(false);
  currentPage = signal(0);

  searchText = '';
  selectedCollections = new Set<string>();
  selectedCategories = new Set<string>();
  selectedDifficulties = new Set<string>();
  selectedMaxTime: number | null = null;
  ingredientInput = '';
  selectedIngredients: string[] = [];

  private readonly searchTrigger$ = new Subject<void>();
  private readonly destroy$ = new Subject<void>();

  constructor(
    private http: HttpClient,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.loadCollections();
    this.loadCategories();
    this.setupSearch();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  isSearchMode(): boolean {
    return !!(this.searchText.trim() || this.selectedCollections.size > 0
      || this.selectedCategories.size > 0 || this.selectedDifficulties.size > 0
      || this.selectedMaxTime !== null || this.selectedIngredients.length > 0);
  }

  get activeFilterCount(): number {
    return (this.searchText.trim() ? 1 : 0)
      + this.selectedCollections.size
      + this.selectedCategories.size
      + this.selectedDifficulties.size
      + (this.selectedMaxTime !== null ? 1 : 0)
      + this.selectedIngredients.length;
  }

  collectionsWithRecipes(): Collection[] {
    return this.collections().filter(c => this.hasRecipes(c));
  }

  toggleCollection(id: string): void {
    if (this.selectedCollections.has(id)) {
      this.selectedCollections.delete(id);
    } else {
      this.selectedCollections.add(id);
    }
    this.currentPage.set(0);
    this.searchTrigger$.next();
  }

  onSearchChange(_value: string): void {
    this.currentPage.set(0);
    this.searchTrigger$.next();
  }

  toggleCategory(id: string): void {
    if (this.selectedCategories.has(id)) {
      this.selectedCategories.delete(id);
    } else {
      this.selectedCategories.add(id);
    }
    this.currentPage.set(0);
    this.searchTrigger$.next();
  }

  toggleDifficulty(d: string): void {
    if (this.selectedDifficulties.has(d)) {
      this.selectedDifficulties.delete(d);
    } else {
      this.selectedDifficulties.add(d);
    }
    this.currentPage.set(0);
    this.searchTrigger$.next();
  }

  toggleTimeBucket(value: number): void {
    this.selectedMaxTime = this.selectedMaxTime === value ? null : value;
    this.currentPage.set(0);
    this.searchTrigger$.next();
  }

  clearSearch(): void {
    this.searchText = '';
    this.currentPage.set(0);
    this.searchTrigger$.next();
  }

  clearAllFilters(): void {
    this.searchText = '';
    this.selectedCollections.clear();
    this.selectedCategories.clear();
    this.selectedDifficulties.clear();
    this.selectedMaxTime = null;
    this.selectedIngredients = [];
    this.currentPage.set(0);
    this.searchTrigger$.next();
  }

  addIngredient(): void {
    const trimmed = this.ingredientInput.trim();
    if (trimmed && !this.selectedIngredients.includes(trimmed.toLowerCase())) {
      this.selectedIngredients.push(trimmed.toLowerCase());
      this.ingredientInput = '';
      this.currentPage.set(0);
      this.searchTrigger$.next();
    }
  }

  removeIngredient(index: number): void {
    this.selectedIngredients.splice(index, 1);
    this.currentPage.set(0);
    this.searchTrigger$.next();
  }

  goToPage(page: number): void {
    this.currentPage.set(page);
    this.searchTrigger$.next();
  }

  openRecipe(id: string): void {
    this.router.navigate(['/recipes', id]);
  }

  hasRecipes(collection: Collection): boolean {
    return collection.chapters.some(chapter => chapter.recipes.length > 0);
  }

  chipClass(active: boolean): string {
    return active
      ? 'rounded-full px-3 py-1 text-xs font-medium bg-forest-600 text-white transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500'
      : 'rounded-full px-3 py-1 text-xs font-medium bg-stone-100 dark:bg-stone-800 text-stone-700 dark:text-stone-300 hover:bg-stone-200 dark:hover:bg-stone-700 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500';
  }

  private setupSearch(): void {
    this.searchTrigger$.pipe(
      debounceTime(300),
      distinctUntilChanged(() => !this.isSearchMode()),
      switchMap(() => {
        if (!this.isSearchMode()) {
          return of(null);
        }
        this.searchLoading.set(true);
        return this.http.get<RecipePage>(`${environment.apiUrl}/api/v1/recipes`, {
          params: this.buildSearchParams(),
        }).pipe(
          catchError(() => of({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 } as RecipePage)),
        );
      }),
      takeUntil(this.destroy$),
    ).subscribe(result => {
      if (result !== null) {
        this.searchResult.set(result);
      }
      this.searchLoading.set(false);
    });
  }

  private buildSearchParams(): HttpParams {
    let params = new HttpParams()
      .set('page', this.currentPage().toString())
      .set('size', '20');

    if (this.searchText.trim()) {
      params = params.set('search', this.searchText.trim());
    }
    this.selectedCollections.forEach(id => {
      params = params.append('collectionIds', id);
    });
    this.selectedCategories.forEach(id => {
      params = params.append('categoryIds', id);
    });
    this.selectedDifficulties.forEach(d => {
      params = params.append('difficulties', d);
    });
    if (this.selectedMaxTime !== null && this.selectedMaxTime < 999) {
      params = params.set('maxTotalTimeMinutes', this.selectedMaxTime.toString());
    }
    this.selectedIngredients.forEach(name => {
      params = params.append('ingredientNames', name);
    });
    return params;
  }

  private loadCollections(): void {
    this.http.get<Collection[]>(`${environment.apiUrl}/api/v1/collections`).pipe(
      catchError(() => {
        this.collectionsError.set('Impossible de charger les recettes. Vérifiez que le serveur est démarré.');
        this.collectionsLoading.set(false);
        return of([]);
      }),
    ).subscribe(collections => {
      this.collections.set(collections);
      this.collectionsLoading.set(false);
    });
  }

  private loadCategories(): void {
    this.http.get<Category[]>(`${environment.apiUrl}/api/v1/categories`).pipe(
      catchError(() => of([])),
    ).subscribe(categories => {
      this.categories.set(categories);
    });

    // Charger les valeurs de difficulté depuis la première page de recettes
    this.http.get<RecipePage>(`${environment.apiUrl}/api/v1/recipes`, {
      params: new HttpParams().set('size', '100'),
    }).pipe(
      catchError(() => of(null)),
    ).subscribe(page => {
      if (page) {
        const diffs = [...new Set(page.content
          .map(r => r.difficulty)
          .filter((d): d is string => !!d)
        )].sort();
        this.difficulties.set(diffs);
      }
    });
  }
}
