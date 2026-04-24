import { Component, input, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MenuPlanService } from '@KitchenVault/api-client';

interface Ingredient {
  id: string;
  name: string;
  description?: string;
}

interface IngredientGroup {
  name?: string;
  ingredients: Ingredient[];
}

interface NutritionEntry {
  type: string;
  number: number;
  unitType: string;
}

interface NutritionGroup {
  name: string;
  quantity?: number;
  unitNotation?: string;
  nutritions: NutritionEntry[];
}

interface Category {
  id: string;
  name: string;
}

interface RecipeDetail {
  id: string;
  name: string;
  thumbnailUrl?: string;
  imageUrl?: string;
  url?: string;
  difficulty?: string;
  servingSize?: number;
  activeTimeMinutes?: number;
  totalTimeMinutes?: number;
  notes: string[];
  utensils: string[];
  ingredientGroups: IngredientGroup[];
  nutritionGroups: NutritionGroup[];
  categories: Category[];
  lastSyncedAt?: string;
}

@Component({
  selector: 'app-recipe-detail',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="space-y-6">
      <!-- Retour -->
      <button
        (click)="goBack()"
        class="inline-flex items-center gap-1.5 text-sm text-zinc-500 hover:text-zinc-900 transition-colors focus-visible:outline-2 focus-visible:outline-indigo-500 rounded"
        aria-label="Retour à la liste des recettes"
      >
        <svg class="h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
        </svg>
        Recettes
      </button>

      <!-- Chargement -->
      @if (loading()) {
        <div class="flex items-center justify-center py-20">
          <svg class="animate-spin h-8 w-8 text-indigo-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-label="Chargement">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
          </svg>
        </div>
      }

      <!-- Erreur -->
      @if (error()) {
        <div class="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">
          {{ error() }}
        </div>
      }

      @if (recipe(); as r) {
        <!-- En-tête -->
        <div class="overflow-hidden rounded-2xl border border-zinc-200 bg-white shadow-sm">
          <div class="flex flex-col md:flex-row">
            <!-- Image -->
            <div class="w-full md:w-64 lg:w-80 flex-shrink-0 bg-zinc-100">
              @if (r.imageUrl || r.thumbnailUrl) {
                <img
                  [src]="r.imageUrl ?? r.thumbnailUrl"
                  [alt]="r.name"
                  class="h-56 w-full object-cover md:h-full"
                />
              } @else {
                <div class="flex h-56 w-full items-center justify-center md:h-full">
                  <svg class="h-16 w-16 text-zinc-300" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                  </svg>
                </div>
              }
            </div>

            <!-- Infos principales -->
            <div class="flex flex-col gap-4 p-6">
              <h1 class="text-2xl font-semibold tracking-tight text-zinc-900">{{ r.name }}</h1>

              <!-- Stats -->
              <div class="flex flex-wrap gap-4">
                @if (r.totalTimeMinutes) {
                  <div class="flex items-center gap-1.5 text-sm text-zinc-600">
                    <svg class="h-4 w-4 text-zinc-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    <span>{{ r.totalTimeMinutes }} min au total</span>
                  </div>
                }
                @if (r.activeTimeMinutes) {
                  <div class="flex items-center gap-1.5 text-sm text-zinc-600">
                    <svg class="h-4 w-4 text-zinc-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
                    </svg>
                    <span>{{ r.activeTimeMinutes }} min actives</span>
                  </div>
                }
                @if (r.servingSize) {
                  <div class="flex items-center gap-1.5 text-sm text-zinc-600">
                    <svg class="h-4 w-4 text-zinc-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
                    </svg>
                    <span>{{ r.servingSize }} personne{{ r.servingSize > 1 ? 's' : '' }}</span>
                  </div>
                }
                @if (r.difficulty) {
                  <div class="flex items-center gap-1.5 text-sm text-zinc-600">
                    <svg class="h-4 w-4 text-zinc-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                    </svg>
                    <span>{{ r.difficulty }}</span>
                  </div>
                }
              </div>

              <!-- Catégories -->
              @if (r.categories.length > 0) {
                <div class="flex flex-wrap gap-2" aria-label="Catégories">
                  @for (cat of r.categories; track cat.id) {
                    <span class="rounded-full bg-indigo-50 px-3 py-1 text-xs font-medium text-indigo-700">
                      {{ cat.name }}
                    </span>
                  }
                </div>
              }

              <!-- Lien Cookidoo -->
              @if (r.url) {
                <a
                  [href]="r.url"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="inline-flex w-fit items-center gap-1.5 text-sm text-indigo-600 hover:text-indigo-800 transition-colors focus-visible:outline-2 focus-visible:outline-indigo-500 rounded"
                >
                  Voir sur Cookidoo
                  <svg class="h-3.5 w-3.5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                  </svg>
                </a>
              }
            </div>
          </div>
        </div>

        <!-- Corps -->
        <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">

          <!-- Ingrédients -->
          @if (r.ingredientGroups.length > 0) {
            <section class="rounded-xl border border-zinc-200 bg-white p-6 shadow-sm" aria-labelledby="ingredients-title">
              <h2 id="ingredients-title" class="mb-4 text-base font-semibold text-zinc-900">Ingrédients</h2>
              <div class="space-y-4">
                @for (group of r.ingredientGroups; track $index) {
                  <div>
                    @if (group.name) {
                      <h3 class="mb-2 text-xs font-semibold uppercase tracking-wide text-zinc-500">{{ group.name }}</h3>
                    }
                    <ul class="space-y-2" role="list">
                      @for (ingredient of group.ingredients; track ingredient.id) {
                        <li class="flex items-baseline gap-1.5">
                          @if (ingredient.description) {
                            <span class="text-sm text-zinc-500">{{ ingredient.description }}</span>
                          }
                          <span class="text-sm text-zinc-900">{{ ingredient.name }}</span>
                        </li>
                      }
                    </ul>
                  </div>
                }
              </div>
            </section>
          }

          <!-- Notes / Étapes -->
          @if (r.notes.length > 0) {
            <section class="rounded-xl border border-zinc-200 bg-white p-6 shadow-sm" aria-labelledby="notes-title">
              <h2 id="notes-title" class="mb-4 text-base font-semibold text-zinc-900">Préparation</h2>
              <ol class="space-y-3 list-none" role="list">
                @for (note of r.notes; track $index) {
                  <li class="flex gap-3">
                    <span class="flex-shrink-0 flex h-6 w-6 items-center justify-center rounded-full bg-indigo-100 text-xs font-semibold text-indigo-700">
                      {{ $index + 1 }}
                    </span>
                    <span class="text-sm text-zinc-700 leading-relaxed pt-0.5">{{ note }}</span>
                  </li>
                }
              </ol>
            </section>
          }

          <!-- Ustensiles -->
          @if (r.utensils.length > 0) {
            <section class="rounded-xl border border-zinc-200 bg-white p-6 shadow-sm" aria-labelledby="utensils-title">
              <h2 id="utensils-title" class="mb-4 text-base font-semibold text-zinc-900">Ustensiles</h2>
              <ul class="space-y-1.5" role="list">
                @for (utensil of r.utensils; track $index) {
                  <li class="flex items-center gap-2 text-sm text-zinc-700">
                    <span class="h-1.5 w-1.5 flex-shrink-0 rounded-full bg-indigo-400" aria-hidden="true"></span>
                    {{ utensil }}
                  </li>
                }
              </ul>
            </section>
          }

          <!-- Historique des menus -->
          @if (historyDates().length > 0) {
            <section class="rounded-xl border border-zinc-200 bg-white p-6 shadow-sm" aria-labelledby="history-title">
              <h2 id="history-title" class="mb-4 text-base font-semibold text-zinc-900">Historique des menus</h2>
              <p class="mb-3 text-sm text-zinc-500">
                Dernière fois : {{ formatHistoryDate(historyDates()[0]) }}
              </p>
              @if (historyDates().length > 1) {
                <ul class="space-y-1" role="list">
                  @for (date of historyDates(); track date) {
                    <li class="flex items-center gap-2 text-sm text-zinc-600">
                      <span class="h-1.5 w-1.5 flex-shrink-0 rounded-full bg-indigo-400" aria-hidden="true"></span>
                      {{ formatHistoryDate(date) }}
                    </li>
                  }
                </ul>
              }
            </section>
          }

          <!-- Valeurs nutritionnelles -->
          @if (r.nutritionGroups.length > 0) {
            <section class="rounded-xl border border-zinc-200 bg-white p-6 shadow-sm" aria-labelledby="nutrition-title">
              <h2 id="nutrition-title" class="mb-4 text-base font-semibold text-zinc-900">Valeurs nutritionnelles</h2>
              <div class="space-y-4">
                @for (group of r.nutritionGroups; track $index) {
                  <div>
                    <div class="mb-2 flex items-baseline gap-2">
                      <h3 class="text-xs font-semibold uppercase tracking-wide text-zinc-500">{{ group.name }}</h3>
                      @if (group.quantity && group.unitNotation) {
                        <span class="text-xs text-zinc-400">pour {{ group.quantity }} {{ group.unitNotation }}</span>
                      }
                    </div>
                    <table class="w-full text-sm" aria-label="Valeurs nutritionnelles pour {{ group.name }}">
                      <tbody class="divide-y divide-zinc-100">
                        @for (entry of group.nutritions; track $index) {
                          <tr>
                            <td class="py-1.5 text-zinc-600">{{ getNutritionLabel(entry.type) }}</td>
                            <td class="py-1.5 text-right font-medium text-zinc-900">{{ entry.number }} {{ entry.unitType }}</td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                }
              </div>
            </section>
          }
        </div>
      }
    </div>
  `,
})
export class RecipeDetailComponent implements OnInit {
  readonly id = input.required<string>();

  recipe = signal<RecipeDetail | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);
  historyDates = signal<string[]>([]);

  private readonly nutritionLabels: Record<string, string> = {
    protein: 'Protéines',
    fat: 'Lipides',
    kcal: 'Calories',
    dietaryFibre: 'Fibres',
    carb2: 'Glucides',
    saturatedFat: 'Graisses saturées',
    sodium: 'Sodium',
  };

  getNutritionLabel(type: string): string {
    return this.nutritionLabels[type] ?? type;
  }

  constructor(
    private http: HttpClient,
    private router: Router,
    private menuPlanService: MenuPlanService,
  ) {}

  ngOnInit(): void {
    this.http
      .get<RecipeDetail>(`${environment.apiUrl}/api/v1/recipes/${this.id()}`)
      .pipe(
        catchError(err => {
          if (err.status === 404) {
            this.error.set('Recette introuvable.');
          } else {
            this.error.set('Impossible de charger la recette. Vérifiez que le serveur est démarré.');
          }
          this.loading.set(false);
          return of(null);
        }),
      )
      .subscribe(recipe => {
        this.recipe.set(recipe);
        this.loading.set(false);
      });

    this.menuPlanService.getRecipeHistory(this.id(), 52)
      .pipe(catchError(() => of(null)))
      .subscribe(history => {
        if (history) {
          this.historyDates.set(history.dates);
        }
      });
  }

  formatHistoryDate(date: string): string {
    return new Date(date + 'T00:00:00').toLocaleDateString('fr-FR', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    });
  }

  goBack(): void {
    this.router.navigate(['/recipes']);
  }
}
