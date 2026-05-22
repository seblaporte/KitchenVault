import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import {
  heroSparkles, heroTrash, heroChevronDown, heroXMark, heroCheck, heroShoppingCart,
} from '@ng-icons/heroicons/outline';
import { catchError, EMPTY } from 'rxjs';
import {
  ShoppingListService, RecipesService,
  ShoppingListDto, ShoppingListItemDto, ShoppingListRecipeDto, ShoppingCategory,
  IngredientGroupDto,
} from '@KitchenVault/api-client';

interface CategoryMeta {
  label: string;
  emoji: string;
  order: number;
}

interface GroupedCategory {
  cat: string;
  label: string;
  emoji: string;
  items: ShoppingListItemDto[];
}

const CATEGORIES: Record<string, CategoryMeta> = {
  PRODUCE: { label: 'Fruits & Légumes',    emoji: '🥦', order: 0 },
  MEAT:    { label: 'Viandes & Poissons',  emoji: '🥩', order: 1 },
  DAIRY:   { label: 'Produits laitiers',   emoji: '🥛', order: 2 },
  BAKERY:  { label: 'Boulangerie',         emoji: '🍞', order: 3 },
  GROCERY: { label: 'Épicerie',            emoji: '🛒', order: 4 },
  FROZEN:  { label: 'Surgelés',           emoji: '🧊', order: 5 },
  SPICES:  { label: 'Épices & Condiments', emoji: '🧂', order: 6 },
  OTHER:   { label: 'Autres',             emoji: '📦', order: 7 },
};

const CONSOLIDATION_STEPS = [
  { icon: '📋', label: 'Analyse de la liste',      sub: 'Lecture des ingrédients' },
  { icon: '🔍', label: 'Détection des doublons',   sub: 'Regroupement par aliment' },
  { icon: '⚖️', label: 'Conversion des unités',    sub: 'Carottes en g, lait en ml…' },
  { icon: '✨', label: 'Fusion intelligente',       sub: 'Une ligne par aliment' },
];

@Component({
  selector: 'app-shopping-list',
  standalone: true,
  imports: [CommonModule, FormsModule, NgIconComponent],
  providers: [provideIcons({ heroSparkles, heroTrash, heroChevronDown, heroXMark, heroCheck, heroShoppingCart })],
  template: `
    <div class="flex flex-col min-h-0">
      <!-- ── Header sticky ─────────────────────────────────────────────── -->
      <div class="sticky top-0 z-10 bg-stone-50 dark:bg-stone-950 border-b border-stone-200 dark:border-stone-800 px-6 py-4 flex flex-wrap items-center gap-3">
        <h1 class="text-xl font-bold tracking-tight text-stone-900 dark:text-stone-100">Liste de courses</h1>

        <!-- Onglets segmentés -->
        <div class="inline-flex bg-stone-100 dark:bg-stone-800 border border-stone-200 dark:border-stone-700 rounded-xl p-1 gap-1" role="tablist">
          <button
            role="tab"
            [attr.aria-selected]="activeTab() === 'selection'"
            (click)="activeTab.set('selection')"
            class="px-3.5 py-1.5 rounded-lg text-[12.5px] font-medium flex items-center gap-1.5 transition-all"
            [ngClass]="activeTab() === 'selection'
              ? 'bg-white dark:bg-stone-900 text-stone-900 dark:text-stone-100 shadow-sm'
              : 'text-stone-500 dark:text-stone-400 hover:text-stone-700 dark:hover:text-stone-200'"
          >
            <ng-icon name="heroShoppingCart" class="h-3.5 w-3.5" aria-hidden="true" />
            Sélection
            @if ((shoppingList()?.recipes?.length ?? 0) > 0) {
              <span class="text-[10.5px] font-semibold px-1.5 py-0.5 rounded-full"
                [ngClass]="activeTab() === 'selection'
                  ? 'bg-stone-100 dark:bg-stone-800 text-stone-600 dark:text-stone-300'
                  : 'bg-stone-200 dark:bg-stone-700 text-stone-500'"
              >{{ shoppingList()!.recipes.length }}</span>
            }
          </button>
          <button
            role="tab"
            [attr.aria-selected]="activeTab() === 'consolidated'"
            (click)="activeTab.set('consolidated')"
            class="px-3.5 py-1.5 rounded-lg text-[12.5px] font-medium flex items-center gap-1.5 transition-all relative"
            [ngClass]="activeTab() === 'consolidated'
              ? 'bg-sky-500/10 dark:bg-sky-500/10 text-sky-600 dark:text-sky-400 border border-sky-400/30 shadow-sm'
              : 'text-stone-500 dark:text-stone-400 hover:text-stone-700 dark:hover:text-stone-200'"
          >
            <ng-icon name="heroSparkles" class="h-3.5 w-3.5" aria-hidden="true" />
            Consolidée IA
            @if ((shoppingList()?.items?.length ?? 0) > 0) {
              <span class="text-[10.5px] font-semibold px-1.5 py-0.5 rounded-full"
                [ngClass]="activeTab() === 'consolidated'
                  ? 'bg-sky-500/15 text-sky-600 dark:text-sky-400'
                  : 'bg-stone-200 dark:bg-stone-700 text-stone-500'"
              >{{ shoppingList()!.items.length }}</span>
            }
            @if (isStale() && (shoppingList()?.items?.length ?? 0) > 0) {
              <span class="absolute -top-1 -right-1 w-2 h-2 rounded-full bg-amber-400 shadow-sm" title="Sélection modifiée — relancer la consolidation"></span>
            }
          </button>
        </div>

        <!-- Stats contextuelles -->
        @if (activeTab() === 'selection' && (shoppingList()?.recipes?.length ?? 0) > 0) {
          <span class="text-[13px] text-stone-500 dark:text-stone-400 bg-stone-100 dark:bg-stone-800 border border-stone-200 dark:border-stone-700 px-3 py-1 rounded-full tabular-nums">
            <strong class="text-stone-800 dark:text-stone-200">{{ shoppingList()!.recipes.length }}</strong>
            recette{{ shoppingList()!.recipes.length > 1 ? 's' : '' }} collectée{{ shoppingList()!.recipes.length > 1 ? 's' : '' }}
          </span>
        }
        @if (activeTab() === 'consolidated' && (shoppingList()?.items?.length ?? 0) > 0) {
          <span class="text-[13px] text-stone-500 dark:text-stone-400 bg-stone-100 dark:bg-stone-800 border border-stone-200 dark:border-stone-700 px-3 py-1 rounded-full tabular-nums">
            <strong class="text-stone-800 dark:text-stone-200">{{ uncheckedCount() }}</strong> à acheter
            @if (savedLines() > 0) {
              <span class="text-sky-500 ml-1"> · {{ savedLines() }} ligne{{ savedLines() > 1 ? 's' : '' }} économisée{{ savedLines() > 1 ? 's' : '' }}</span>
            }
          </span>
        }

        <!-- Actions contextuelles -->
        <div class="ml-auto flex items-center gap-2">
          @if (activeTab() === 'selection' && (shoppingList()?.recipes?.length ?? 0) > 0) {
            <button
              (click)="toggleAllAccordions()"
              class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 text-[13px] font-medium text-stone-600 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 transition-colors cursor-pointer"
            >
              <ng-icon name="heroChevronDown" class="h-3.5 w-3.5 transition-transform duration-200"
                [ngClass]="anyExpanded() ? 'rotate-180' : ''" aria-hidden="true" />
              {{ anyExpanded() ? 'Tout replier' : 'Tout afficher' }}
            </button>
            <button
              (click)="clearAll()"
              class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 text-[13px] font-medium text-stone-600 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 transition-colors cursor-pointer"
            >
              <ng-icon name="heroTrash" class="h-3.5 w-3.5" aria-hidden="true" />
              Vider la sélection
            </button>
            @if (!consolidating()) {
              <button
                (click)="consolidate()"
                class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-sky-400/30 bg-sky-500/10 text-[13px] font-medium text-sky-600 dark:text-sky-400 hover:bg-sky-500/20 transition-colors cursor-pointer"
              >
                <ng-icon name="heroSparkles" class="h-3.5 w-3.5" aria-hidden="true" />
                {{ (shoppingList()?.items?.length ?? 0) > 0 ? 'Reconstruire la liste' : 'Construire la liste de courses' }}
              </button>
            }
          }
          @if (activeTab() === 'consolidated' && (shoppingList()?.items?.length ?? 0) > 0 && !consolidating()) {
            @if (checkedCount() > 0) {
              <button
                (click)="clearCheckedItems()"
                class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 text-[13px] font-medium text-stone-600 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 transition-colors cursor-pointer"
              >
                <ng-icon name="heroCheck" class="h-3.5 w-3.5" aria-hidden="true" />
                Retirer les cochés
              </button>
            }
            <button
              (click)="clearAll()"
              class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 text-[13px] font-medium text-stone-600 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 transition-colors cursor-pointer"
            >
              <ng-icon name="heroTrash" class="h-3.5 w-3.5" aria-hidden="true" />
              Tout effacer
            </button>
            @if (isStale()) {
              <button
                (click)="consolidate()"
                class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-sky-400/30 bg-sky-500/10 text-[13px] font-medium text-sky-600 dark:text-sky-400 hover:bg-sky-500/20 transition-colors cursor-pointer"
              >
                <ng-icon name="heroSparkles" class="h-3.5 w-3.5" aria-hidden="true" />
                Relancer
              </button>
            }
          }
        </div>
      </div>

      <!-- ── Loading global ─────────────────────────────────────────────── -->
      @if (loading()) {
        <div class="flex items-center justify-center py-20">
          <svg class="animate-spin h-8 w-8 text-forest-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-label="Chargement">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
          </svg>
        </div>
      }

      <!-- ── Body (2 colonnes) ───────────────────────────────────────────── -->
      @if (!loading()) {
        <div class="p-6 grid grid-cols-1 lg:grid-cols-[1fr_300px] gap-6 items-start">

          <!-- ════ Onglet Sélection ════ -->
          @if (activeTab() === 'selection') {
            <div>
              <!-- Empty state -->
              @if ((shoppingList()?.recipes?.length ?? 0) === 0) {
                <div class="flex flex-col items-center justify-center py-20 text-center text-stone-400 dark:text-stone-500">
                  <div class="w-16 h-16 rounded-full bg-stone-100 dark:bg-stone-800 border border-stone-200 dark:border-stone-700 flex items-center justify-center mb-5">
                    <ng-icon name="heroShoppingCart" class="h-7 w-7" aria-hidden="true" />
                  </div>
                  <p class="text-base font-semibold text-stone-700 dark:text-stone-300 mb-2">Aucune recette collectée</p>
                  <p class="text-sm max-w-sm leading-relaxed">
                    Depuis l'onglet <strong class="text-stone-700 dark:text-stone-300">Menu</strong>, survolez une recette planifiée et cliquez sur <strong class="text-stone-700 dark:text-stone-300">Liste</strong> pour la collecter ici.
                  </p>
                </div>
              }

              <!-- Liste des recettes (accordéon) -->
              @if ((shoppingList()?.recipes?.length ?? 0) > 0) {
                <div class="flex flex-col gap-3">
                  @for (recipe of shoppingList()!.recipes; track recipe.id) {
                    <section class="bg-white dark:bg-stone-900 rounded-xl border border-stone-200 dark:border-stone-700 overflow-hidden">
                      <!-- En-tête accordéon -->
                      <button
                        type="button"
                        class="w-full flex items-center gap-3 px-4 py-3.5 text-left hover:bg-stone-50 dark:hover:bg-stone-800/50 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500"
                        [attr.aria-expanded]="expandedRecipes().has(recipe.id)"
                        (click)="toggleAccordion(recipe)"
                      >
                        <span class="w-10 h-10 rounded-lg bg-stone-100 dark:bg-stone-800 overflow-hidden flex items-center justify-center flex-shrink-0">
                          @if (recipe.thumbnailUrl) {
                            <img [src]="recipe.thumbnailUrl" [alt]="recipe.recipeName"
                                 class="w-full h-full object-cover" loading="lazy" />
                          } @else {
                            <span class="text-base">🍽️</span>
                          }
                        </span>
                        <div class="flex-1 min-w-0">
                          <div class="text-sm font-semibold text-stone-900 dark:text-stone-100 truncate">{{ recipe.recipeName }}</div>
                          <div class="flex items-center gap-2 mt-0.5">
                            <span class="text-xs text-stone-400 dark:text-stone-500">
                              Ajouté le {{ formatDate(recipe.addedAt) }}
                            </span>
                            @if (recipe.consolidated) {
                              <span class="text-[10.5px] font-medium px-1.5 py-0.5 rounded-full bg-forest-100 dark:bg-forest-900/30 text-forest-700 dark:text-forest-400 border border-forest-200 dark:border-forest-800/50">
                                ✓ Dans la liste
                              </span>
                            } @else {
                              <span class="text-[10.5px] font-medium px-1.5 py-0.5 rounded-full bg-amber-100 dark:bg-amber-900/20 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-800/30">
                                Nouveau
                              </span>
                            }
                            @if (getIngredients(recipe.id).length > 0) {
                              <span class="text-xs text-stone-400">· {{ getIngredientCount(recipe.id) }} ingrédient{{ getIngredientCount(recipe.id) > 1 ? 's' : '' }}</span>
                            }
                          </div>
                        </div>
                        <button
                          type="button"
                          class="w-7 h-7 rounded-md flex items-center justify-center text-stone-400 hover:bg-stone-100 dark:hover:bg-stone-700 hover:text-red-500 transition-colors cursor-pointer flex-shrink-0"
                          [attr.aria-label]="'Retirer ' + recipe.recipeName + ' de la sélection'"
                          (click)="$event.stopPropagation(); removeRecipe(recipe)"
                        >
                          <ng-icon name="heroXMark" class="h-4 w-4" aria-hidden="true" />
                        </button>
                        <ng-icon
                          name="heroChevronDown"
                          class="h-4 w-4 text-stone-400 transition-transform duration-200 flex-shrink-0"
                          [ngClass]="expandedRecipes().has(recipe.id) ? 'rotate-180' : ''"
                          aria-hidden="true"
                        />
                      </button>

                      <!-- Corps accordéon (ingrédients) -->
                      @if (expandedRecipes().has(recipe.id)) {
                        <div class="border-t border-stone-100 dark:border-stone-800">
                          @if (loadingIngredients().has(recipe.id)) {
                            <div class="flex items-center justify-center py-6">
                              <svg class="animate-spin h-5 w-5 text-stone-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-label="Chargement des ingrédients">
                                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
                              </svg>
                            </div>
                          } @else if (getIngredients(recipe.id).length === 0) {
                            <p class="px-4 py-4 text-sm text-stone-400 italic">Ingrédients non disponibles</p>
                          } @else {
                            @for (group of getIngredients(recipe.id); track group.name) {
                              @if (group.name && getIngredients(recipe.id).length > 1) {
                                <div class="px-4 pt-3 pb-1 text-[10px] font-semibold uppercase tracking-widest text-stone-400">{{ group.name }}</div>
                              }
                              @for (ingredient of group.ingredients; track ingredient.id) {
                                <div class="flex items-center gap-3 px-4 py-2.5 border-b border-stone-50 dark:border-stone-800/50 last:border-0">
                                  <span class="w-1.5 h-1.5 rounded-full bg-stone-300 dark:bg-stone-600 flex-shrink-0"></span>
                                  <span class="text-sm text-stone-700 dark:text-stone-300">{{ ingredient.name }}</span>
                                  @if (ingredient.description) {
                                    <span class="text-xs text-stone-400 ml-auto font-mono">{{ ingredient.description }}</span>
                                  }
                                </div>
                              }
                            }
                          }
                        </div>
                      }
                    </section>
                  }
                </div>
              }
            </div>

            <!-- Sidebar Sélection -->
            <div class="flex flex-col gap-4 lg:sticky lg:top-[73px]">
              <div class="bg-white dark:bg-stone-900 rounded-xl border border-stone-200 dark:border-stone-700 p-4 flex flex-col gap-3">
                <div class="text-[10.5px] font-bold uppercase tracking-widest text-stone-400 dark:text-stone-500">Sélection · étape 1/2</div>
                <p class="text-[12.5px] text-stone-500 dark:text-stone-400 leading-relaxed">
                  Toutes les recettes ajoutées depuis le <strong class="text-stone-700 dark:text-stone-300">Menu</strong> arrivent ici.
                  C'est la <strong class="text-stone-700 dark:text-stone-300">matière première</strong> que l'agent IA utilisera pour construire votre liste de courses.
                </p>
                <p class="text-[11.5px] text-stone-400 dark:text-stone-500 leading-relaxed border-t border-dashed border-stone-200 dark:border-stone-700 pt-3">
                  Quand votre sélection est complète, lancez la consolidation → fusion des doublons, conversion des unités, regroupement par rayon.
                </p>
                @if ((shoppingList()?.recipes?.length ?? 0) > 0 && !consolidating()) {
                  <button
                    (click)="consolidate()"
                    class="w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-sky-500 text-sky-950 text-sm font-semibold hover:brightness-110 transition-all cursor-pointer mt-1"
                  >
                    <ng-icon name="heroSparkles" class="h-4 w-4" aria-hidden="true" />
                    {{ (shoppingList()?.items?.length ?? 0) > 0 ? "Reconstruire avec l'IA" : 'Construire la liste de courses' }}
                  </button>
                }
                @if ((shoppingList()?.items?.length ?? 0) > 0) {
                  <button
                    (click)="activeTab.set('consolidated')"
                    class="w-full flex items-center justify-center gap-2 px-4 py-2 rounded-xl bg-sky-500/10 border border-sky-400/30 text-sky-600 dark:text-sky-400 text-sm font-medium hover:bg-sky-500/20 transition-colors cursor-pointer"
                  >
                    <ng-icon name="heroSparkles" class="h-3.5 w-3.5" aria-hidden="true" />
                    Voir la liste de courses
                  </button>
                }
              </div>
            </div>
          }

          <!-- ════ Onglet Consolidée IA ════ -->
          @if (activeTab() === 'consolidated') {
            <div>
              <!-- State: en cours de consolidation -->
              @if (consolidating()) {
                <div class="bg-white dark:bg-stone-900 rounded-xl border border-sky-400/30 p-8 flex flex-col items-center gap-6 shadow-sm">
                  <div class="w-14 h-14 rounded-full bg-gradient-to-br from-sky-400 to-sky-600 flex items-center justify-center shadow-lg shadow-sky-500/20 animate-pulse">
                    <ng-icon name="heroSparkles" class="h-7 w-7 text-sky-950" aria-hidden="true" />
                  </div>
                  <div class="text-center">
                    <div class="text-base font-semibold text-stone-900 dark:text-stone-100 mb-1">L'agent IA consolide votre liste…</div>
                    <div class="text-sm text-stone-500">Fusion intelligente des ingrédients communs</div>
                  </div>
                  <div class="w-full max-w-sm flex flex-col gap-2.5">
                    @for (step of consolidationSteps; track step.label; let i = $index) {
                      <div class="flex items-center gap-3 px-4 py-2.5 rounded-lg transition-all"
                        [ngClass]="{
                          'bg-forest-50 dark:bg-forest-900/20 border border-forest-200 dark:border-forest-800/30': i < consolidationStep(),
                          'bg-sky-50 dark:bg-sky-900/20 border border-sky-200 dark:border-sky-800/30': i === consolidationStep(),
                          'bg-stone-50 dark:bg-stone-800/50 border border-stone-200 dark:border-stone-700': i > consolidationStep()
                        }"
                      >
                        <span class="w-8 h-8 rounded-lg flex items-center justify-center text-base flex-shrink-0"
                          [ngClass]="{
                            'bg-forest-100 dark:bg-forest-900/40': i < consolidationStep(),
                            'bg-sky-100 dark:bg-sky-900/40': i === consolidationStep(),
                            'bg-stone-100 dark:bg-stone-700': i > consolidationStep()
                          }"
                        >
                          @if (i < consolidationStep()) { ✓ }
                          @else { {{ step.icon }} }
                        </span>
                        <div>
                          <div class="text-sm font-medium"
                            [ngClass]="{
                              'text-forest-700 dark:text-forest-400': i < consolidationStep(),
                              'text-sky-700 dark:text-sky-300': i === consolidationStep(),
                              'text-stone-400 dark:text-stone-500': i > consolidationStep()
                            }"
                          >{{ step.label }}</div>
                          <div class="text-xs text-stone-400">{{ step.sub }}</div>
                        </div>
                      </div>
                    }
                  </div>
                </div>
              }

              <!-- State: pas encore consolidé (hero CTA) -->
              @if (!consolidating() && (shoppingList()?.items?.length ?? 0) === 0) {
                <div class="relative bg-white dark:bg-stone-900 rounded-xl border border-stone-200 dark:border-stone-700 p-10 flex flex-col items-center gap-5 text-center overflow-hidden">
                  <div class="absolute inset-0 bg-gradient-to-b from-sky-500/5 to-transparent pointer-events-none"></div>
                  <div class="relative w-16 h-16 rounded-full bg-gradient-to-br from-sky-400 to-sky-600 flex items-center justify-center shadow-xl shadow-sky-500/20">
                    <ng-icon name="heroSparkles" class="h-8 w-8 text-sky-950" aria-hidden="true" />
                  </div>
                  <div class="relative">
                    <h2 class="text-xl font-bold text-stone-900 dark:text-stone-100 tracking-tight mb-2">Construisez votre liste de courses</h2>
                    <p class="text-sm text-stone-500 dark:text-stone-400 leading-relaxed max-w-md">
                      Vos ingrédients viennent de plusieurs recettes, parfois avec des unités différentes.
                      L'agent IA fusionne les doublons en une seule ligne par aliment, en convertissant intelligemment les quantités.
                    </p>
                  </div>
                  <button
                    [disabled]="(shoppingList()?.recipes?.length ?? 0) === 0"
                    (click)="consolidate()"
                    class="relative flex items-center gap-2 px-6 py-3 rounded-xl bg-sky-500 text-sky-950 font-semibold text-sm hover:brightness-110 transition-all cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed mt-1"
                  >
                    <ng-icon name="heroSparkles" class="h-4 w-4" aria-hidden="true" />
                    Construire la liste de courses
                  </button>
                  <p class="relative text-xs text-stone-400">La sélection de recettes reste intacte.</p>
                </div>
              }

              <!-- State: liste consolidée -->
              @if (!consolidating() && (shoppingList()?.items?.length ?? 0) > 0) {
                <div class="flex flex-col gap-5">
                  <!-- Stale banner -->
                  @if (isStale()) {
                    <div class="flex items-center gap-3 p-3.5 rounded-xl bg-amber-50 dark:bg-amber-950/20 border border-amber-200 dark:border-amber-800/30">
                      <svg class="h-5 w-5 text-amber-500 flex-shrink-0" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 16 16" stroke="currentColor" aria-hidden="true">
                        <path d="M8 1.5L1 14h14L8 1.5z" stroke-width="1.4" stroke-linejoin="round"/>
                        <path d="M8 6.5v3M8 11.5v.01" stroke-width="1.4" stroke-linecap="round"/>
                      </svg>
                      <p class="text-sm text-stone-700 dark:text-stone-300 flex-1">
                        <strong class="text-amber-600 dark:text-amber-400">Sélection modifiée.</strong> Relancez la consolidation pour intégrer les changements.
                      </p>
                      <button
                        (click)="consolidate()"
                        class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-amber-500/10 border border-amber-400/30 text-amber-600 dark:text-amber-400 text-[12.5px] font-medium hover:bg-amber-500/20 transition-colors cursor-pointer flex-shrink-0"
                      >
                        <ng-icon name="heroSparkles" class="h-3 w-3" aria-hidden="true" />
                        Relancer
                      </button>
                    </div>
                  }

                  <!-- Résumé IA -->
                  <div class="flex items-center gap-3 px-4 py-3.5 rounded-xl bg-sky-500/8 border border-sky-400/20 dark:bg-sky-500/5">
                    <div class="w-8 h-8 rounded-full bg-sky-500/15 text-sky-500 flex items-center justify-center flex-shrink-0">
                      <ng-icon name="heroSparkles" class="h-4 w-4" aria-hidden="true" />
                    </div>
                    <div>
                      <div class="text-sm font-semibold text-stone-900 dark:text-stone-100">Liste consolidée par l'IA</div>
                      <div class="text-xs text-stone-500 mt-0.5">
                        <strong class="text-sky-600 dark:text-sky-400">{{ mergedCount() }}</strong> aliment{{ mergedCount() > 1 ? 's' : '' }} fusionné{{ mergedCount() > 1 ? 's' : '' }}
                        @if (savedLines() > 0) {
                          · <strong class="text-sky-600 dark:text-sky-400">{{ savedLines() }}</strong> ligne{{ savedLines() > 1 ? 's' : '' }} en moins
                        }
                      </div>
                    </div>
                  </div>

                  <!-- Catégories -->
                  @for (group of groupedItems(); track group.cat) {
                    <div class="bg-white dark:bg-stone-900 rounded-xl border border-stone-200 dark:border-stone-700 overflow-hidden">
                      <div class="flex items-center gap-2.5 px-4 py-3 border-b border-stone-100 dark:border-stone-800 bg-stone-50 dark:bg-stone-800/50">
                        <span class="text-lg">{{ group.emoji }}</span>
                        <span class="text-xs font-bold uppercase tracking-wider text-stone-700 dark:text-stone-200">{{ group.label }}</span>
                        <span class="ml-auto text-xs text-stone-400 bg-stone-100 dark:bg-stone-700 px-2 py-0.5 rounded-full">{{ group.items.length }}</span>
                      </div>
                      <div class="flex flex-col divide-y divide-stone-50 dark:divide-stone-800/50">
                        @for (item of group.items; track item.id) {
                          <div class="group/item flex items-center gap-3 px-4 py-3 hover:bg-stone-50 dark:hover:bg-stone-800/40 transition-colors"
                            [ngClass]="item.checked ? 'opacity-50' : ''">
                            <button
                              (click)="toggleItem(item)"
                              class="w-5 h-5 rounded-md border-[1.5px] flex items-center justify-center flex-shrink-0 transition-all cursor-pointer"
                              [ngClass]="item.checked
                                ? 'bg-forest-600 border-forest-600 text-white'
                                : 'border-stone-300 dark:border-stone-600 text-transparent hover:border-forest-500'"
                              [title]="item.checked ? 'Décocher' : 'Cocher'"
                            >
                              <ng-icon name="heroCheck" class="h-3 w-3" aria-hidden="true" />
                            </button>
                            <div class="flex-1 min-w-0">
                              <span class="text-sm font-medium text-stone-800 dark:text-stone-200"
                                [ngClass]="item.checked ? 'line-through text-stone-400 dark:text-stone-500' : ''">
                                {{ item.name }}
                              </span>
                              @if (item.sourceRecipeIds.length > 1) {
                                <div class="mt-0.5">
                                  <button
                                    type="button"
                                    (click)="$event.stopPropagation(); toggleFusion(item.id)"
                                    class="inline-flex items-center gap-1 text-[10.5px] font-medium px-1.5 py-0.5 rounded-full bg-sky-500/10 border border-sky-400/25 text-sky-600 dark:text-sky-400 hover:bg-sky-500/20 transition-colors cursor-pointer"
                                    [attr.aria-expanded]="expandedFusions().has(item.id)"
                                  >
                                    <ng-icon name="heroSparkles" class="h-2.5 w-2.5" aria-hidden="true" />
                                    Fusionné · {{ item.sourceRecipeIds.length }} recettes
                                    <ng-icon name="heroChevronDown" class="h-2.5 w-2.5 transition-transform duration-150"
                                      [ngClass]="expandedFusions().has(item.id) ? 'rotate-180' : ''" aria-hidden="true" />
                                  </button>
                                  @if (expandedFusions().has(item.id)) {
                                    <div class="mt-1.5 flex flex-col gap-0.5 pl-1">
                                      @for (recipeId of item.sourceRecipeIds; track recipeId) {
                                        <span class="inline-flex items-center gap-1 text-[10.5px] text-stone-500 dark:text-stone-400">
                                          <span class="w-1 h-1 rounded-full bg-sky-400/60 flex-shrink-0"></span>
                                          {{ getRecipeNameById(recipeId, item) }}
                                        </span>
                                      }
                                    </div>
                                  }
                                </div>
                              } @else if (item.sourceRecipeIds.length === 1) {
                                <div class="mt-0.5">
                                  <span class="inline-flex items-center gap-1 text-[10.5px] px-1.5 py-0.5 rounded-full bg-stone-100 dark:bg-stone-800 border border-stone-200 dark:border-stone-700 text-stone-500">
                                    {{ getRecipeNameById(item.sourceRecipeIds[0], item) }}
                                  </span>
                                </div>
                              }
                            </div>
                            @if (item.quantity) {
                              <span class="text-xs text-stone-500 dark:text-stone-400 font-mono bg-stone-100 dark:bg-stone-800 border border-stone-200 dark:border-stone-700 px-2 py-1 rounded-md flex-shrink-0"
                                [ngClass]="item.checked ? 'bg-transparent border-transparent' : ''">
                                {{ item.quantity }}
                              </span>
                            }
                            <button
                              (click)="deleteItem(item)"
                              class="w-7 h-7 rounded-md flex items-center justify-center text-stone-300 dark:text-stone-600 opacity-0 group-hover/item:opacity-100 hover:bg-stone-100 dark:hover:bg-stone-700 hover:text-red-500 transition-all cursor-pointer flex-shrink-0"
                              title="Supprimer"
                            >
                              <ng-icon name="heroTrash" class="h-3.5 w-3.5" aria-hidden="true" />
                            </button>
                          </div>
                        }
                      </div>
                    </div>
                  }
                </div>
              }
            </div>

            <!-- Sidebar Consolidée IA -->
            <div class="flex flex-col gap-4 lg:sticky lg:top-[73px]">
              @if ((shoppingList()?.recipes?.length ?? 0) > 0) {
                <div class="bg-white dark:bg-stone-900 rounded-xl border border-stone-200 dark:border-stone-700 p-4 flex flex-col gap-3">
                  <div class="text-[10.5px] font-bold uppercase tracking-widest text-stone-400 dark:text-stone-500">
                    Recettes intégrées · {{ shoppingList()!.recipes.length }}
                  </div>
                  @for (recipe of shoppingList()!.recipes; track recipe.id) {
                    <div class="flex items-center gap-2.5 px-3 py-2.5 rounded-lg bg-stone-50 dark:bg-stone-800 border border-stone-100 dark:border-stone-700">
                      <span class="w-7 h-7 rounded-md bg-stone-200 dark:bg-stone-700 overflow-hidden flex items-center justify-center flex-shrink-0 text-sm">
                        @if (recipe.thumbnailUrl) {
                          <img [src]="recipe.thumbnailUrl" [alt]="recipe.recipeName" class="w-full h-full object-cover" loading="lazy" />
                        } @else {
                          🍽️
                        }
                      </span>
                      <span class="flex-1 text-[12.5px] font-medium text-stone-800 dark:text-stone-200 truncate leading-snug">{{ recipe.recipeName }}</span>
                      <button
                        (click)="removeRecipe(recipe)"
                        class="w-6 h-6 rounded flex items-center justify-center text-stone-400 hover:bg-stone-100 dark:hover:bg-stone-700 hover:text-red-500 transition-colors cursor-pointer flex-shrink-0"
                        [title]="'Retirer ' + recipe.recipeName"
                      >
                        <ng-icon name="heroXMark" class="h-3.5 w-3.5" aria-hidden="true" />
                      </button>
                    </div>
                  }
                </div>
              }
            </div>
          }

        </div>
      }
    </div>
  `,
})
export class ShoppingListComponent implements OnInit {
  readonly consolidationSteps = CONSOLIDATION_STEPS;

  shoppingList = signal<ShoppingListDto | null>(null);
  loading = signal(true);
  consolidating = signal(false);
  consolidationStep = signal(0);
  activeTab = signal<'selection' | 'consolidated'>('selection');
  expandedRecipes = signal<Set<string>>(new Set());
  expandedFusions = signal<Set<string>>(new Set());
  loadingIngredients = signal<Set<string>>(new Set());
  ingredientsCache = signal<Map<string, IngredientGroupDto[]>>(new Map());

  isStale = computed(() => {
    const list = this.shoppingList();
    if (!list || list.items.length === 0) return false;
    if (list.recipes.some(r => !r.consolidated)) return true;
    const currentIds = new Set(list.recipes.map(r => r.recipeIdSnapshot));
    return list.items.some(item => item.sourceRecipeIds.some(id => !currentIds.has(id)));
  });

  groupedItems = computed((): GroupedCategory[] => {
    const items = this.shoppingList()?.items ?? [];
    const map = new Map<string, ShoppingListItemDto[]>();
    for (const item of items) {
      const cat = item.category ?? 'OTHER';
      if (!map.has(cat)) map.set(cat, []);
      map.get(cat)!.push(item);
    }
    return (Object.entries(CATEGORIES) as [string, CategoryMeta][])
      .filter(([cat]) => map.has(cat))
      .sort(([, a], [, b]) => a.order - b.order)
      .map(([cat, meta]) => ({ cat, label: meta.label, emoji: meta.emoji, items: map.get(cat)! }));
  });

  uncheckedCount = computed(() => (this.shoppingList()?.items ?? []).filter(i => !i.checked).length);
  checkedCount = computed(() => (this.shoppingList()?.items ?? []).filter(i => i.checked).length);
  mergedCount = computed(() => (this.shoppingList()?.items ?? []).filter(i => i.sourceRecipeIds.length > 1).length);
  savedLines = computed(() => {
    const list = this.shoppingList();
    if (!list) return 0;
    // Calcule le nombre de lignes d'ingrédients bruts chargés vs items consolidés
    const rawTotal = Array.from(this.ingredientsCache().values())
      .reduce((acc, groups) => acc + groups.reduce((a, g) => a + g.ingredients.length, 0), 0);
    return Math.max(0, rawTotal - list.items.length);
  });

  anyExpanded = computed(() => this.expandedRecipes().size > 0);

  constructor(
    private shoppingListService: ShoppingListService,
    private recipesService: RecipesService,
  ) {}

  ngOnInit(): void {
    this.loadShoppingList();
  }

  private loadShoppingList(): void {
    this.loading.set(true);
    this.shoppingListService.getShoppingList().subscribe({
      next: list => {
        this.shoppingList.set(list);
        this.loading.set(false);
        if (list.items.length > 0 && list.recipes.every(r => r.consolidated)) {
          this.activeTab.set('consolidated');
        }
      },
      error: () => this.loading.set(false),
    });
  }

  toggleAccordion(recipe: ShoppingListRecipeDto): void {
    const expanded = new Set(this.expandedRecipes());
    if (expanded.has(recipe.id)) {
      expanded.delete(recipe.id);
    } else {
      expanded.add(recipe.id);
      this.fetchIngredients(recipe);
    }
    this.expandedRecipes.set(expanded);
  }

  private fetchIngredients(recipe: ShoppingListRecipeDto): void {
    const recipeId = recipe.recipeId ?? recipe.recipeIdSnapshot;
    if (!recipeId || this.ingredientsCache().has(recipe.id)) return;

    const loading = new Set(this.loadingIngredients());
    loading.add(recipe.id);
    this.loadingIngredients.set(loading);

    this.recipesService.getRecipeById(recipeId).pipe(catchError(() => EMPTY)).subscribe(detail => {
      const cache = new Map(this.ingredientsCache());
      cache.set(recipe.id, detail.ingredientGroups ?? []);
      this.ingredientsCache.set(cache);

      const stillLoading = new Set(this.loadingIngredients());
      stillLoading.delete(recipe.id);
      this.loadingIngredients.set(stillLoading);
    });
  }

  getIngredients(recipeId: string): IngredientGroupDto[] {
    return this.ingredientsCache().get(recipeId) ?? [];
  }

  getIngredientCount(recipeId: string): number {
    return this.getIngredients(recipeId).reduce((acc, g) => acc + g.ingredients.length, 0);
  }

  toggleFusion(itemId: string): void {
    const s = new Set(this.expandedFusions());
    s.has(itemId) ? s.delete(itemId) : s.add(itemId);
    this.expandedFusions.set(s);
  }

  toggleAllAccordions(): void {
    if (this.anyExpanded()) {
      this.expandedRecipes.set(new Set());
    } else {
      const list = this.shoppingList();
      if (!list) return;
      const all = new Set(list.recipes.map(r => r.id));
      this.expandedRecipes.set(all);
      list.recipes.forEach(r => this.fetchIngredients(r));
    }
  }

  removeRecipe(recipe: ShoppingListRecipeDto): void {
    const recipeId = recipe.recipeId ?? recipe.recipeIdSnapshot;
    this.shoppingListService.removeRecipeFromSelection(recipeId)
      .pipe(catchError(() => EMPTY))
      .subscribe(() => this.loadShoppingList());
  }

  consolidate(): void {
    this.consolidating.set(true);
    this.consolidationStep.set(0);
    this.activeTab.set('consolidated');

    const stepTimer = setInterval(() => {
      const next = this.consolidationStep() + 1;
      if (next < CONSOLIDATION_STEPS.length) {
        this.consolidationStep.set(next);
      }
    }, 550);

    this.shoppingListService.consolidateShoppingList()
      .pipe(catchError(() => EMPTY))
      .subscribe(list => {
        clearInterval(stepTimer);
        this.shoppingList.set(list);
        this.consolidating.set(false);
      });
  }

  toggleItem(item: ShoppingListItemDto): void {
    this.shoppingListService.toggleShoppingListItem(item.id)
      .pipe(catchError(() => EMPTY))
      .subscribe(updated => {
        const list = this.shoppingList();
        if (!list) return;
        this.shoppingList.set({
          ...list,
          items: list.items.map(i => i.id === updated.id ? updated : i),
        });
      });
  }

  deleteItem(item: ShoppingListItemDto): void {
    this.shoppingListService.deleteShoppingListItem(item.id)
      .pipe(catchError(() => EMPTY))
      .subscribe(() => {
        const list = this.shoppingList();
        if (!list) return;
        this.shoppingList.set({ ...list, items: list.items.filter(i => i.id !== item.id) });
      });
  }

  clearCheckedItems(): void {
    const list = this.shoppingList();
    if (!list) return;
    const checkedItems = list.items.filter(i => i.checked);
    let remaining = checkedItems.length;
    if (remaining === 0) return;
    checkedItems.forEach(item => {
      this.shoppingListService.deleteShoppingListItem(item.id)
        .pipe(catchError(() => EMPTY))
        .subscribe(() => {
          remaining--;
          if (remaining === 0) this.loadShoppingList();
        });
    });
  }

  clearAll(): void {
    this.shoppingListService.clearShoppingList()
      .pipe(catchError(() => EMPTY))
      .subscribe(() => {
        this.shoppingList.set({ recipes: [], items: [] });
        this.activeTab.set('selection');
        this.expandedRecipes.set(new Set());
      });
  }

  getRecipeNameById(recipeId: string, item?: ShoppingListItemDto): string {
    const nameFromItem = (item?.sourceRecipeNames as Record<string, string> | undefined)?.[recipeId];
    if (nameFromItem) return nameFromItem;
    const recipe = this.shoppingList()?.recipes.find(r => r.recipeIdSnapshot === recipeId || r.recipeId === recipeId);
    return recipe?.recipeName ?? recipeId;
  }

  formatDate(isoDate: string): string {
    return new Date(isoDate).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long' });
  }
}
