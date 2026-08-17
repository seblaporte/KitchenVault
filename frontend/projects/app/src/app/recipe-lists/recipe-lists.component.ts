import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { heroCalendarDays, heroQueueList } from '@ng-icons/heroicons/outline';
import {
  RecipeListsService,
  RecipeListOverviewDto,
  RecipeListRole,
  RecipeSummaryDto,
} from '@KitchenVault/api-client';
import { ToastService } from '../shared/toast/toast.service';

@Component({
  selector: 'app-recipe-lists',
  standalone: true,
  imports: [CommonModule, RouterLink, NgIconComponent],
  providers: [provideIcons({ heroCalendarDays, heroQueueList })],
  template: `
    <div class="space-y-8">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 class="text-2xl font-semibold tracking-tight text-stone-900 dark:text-stone-100">Mes listes</h1>
          <p class="mt-1 text-sm text-stone-500 dark:text-stone-400">
            Organisez vos recettes et laissez le planificateur IA s'appuyer dessus.
          </p>
        </div>
        <a
          routerLink="/lists/weekly-review"
          class="inline-flex items-center gap-2 rounded-lg bg-forest-600 px-4 py-2 text-sm font-medium text-white hover:bg-forest-700 transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-forest-600"
        >
          <ng-icon name="heroCalendarDays" class="h-4 w-4" aria-hidden="true" />
          Faire le bilan hebdomadaire
        </a>
      </div>

      @if (loading()) {
        <div class="flex items-center justify-center py-20">
          <svg class="animate-spin h-8 w-8 text-forest-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-label="Chargement">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
          </svg>
        </div>
      }

      @if (error()) {
        <div class="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">
          {{ error() }}
        </div>
      }

      @for (section of overview(); track section.role) {
        <section
          class="rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 p-6 shadow-sm"
          [attr.aria-labelledby]="'section-' + section.role"
        >
          <div class="mb-4 flex items-center gap-2">
            <ng-icon name="heroQueueList" class="h-5 w-5 text-forest-600" aria-hidden="true" />
            <h2 [id]="'section-' + section.role" class="text-base font-semibold text-stone-900 dark:text-stone-100">
              {{ section.displayLabel }}
            </h2>
            <span class="text-sm text-stone-400 dark:text-stone-500">({{ (section.recipes ?? []).length }})</span>
          </div>

          @if ((section.recipes ?? []).length === 0) {
            <p class="text-sm text-stone-500 dark:text-stone-400">Aucune recette dans cette liste pour le moment.</p>
          } @else {
            <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
              @for (recipe of section.recipes ?? []; track recipe.id) {
                <div class="flex flex-col overflow-hidden rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 shadow-sm">
                  <a
                    [routerLink]="['/recipes', recipe.id]"
                    class="aspect-square w-full overflow-hidden bg-stone-100 dark:bg-stone-800 block"
                    [attr.aria-label]="recipe.name"
                  >
                    @if (recipe.thumbnailUrl) {
                      <img [src]="recipe.thumbnailUrl" [alt]="recipe.name" class="h-full w-full object-cover" loading="lazy" />
                    } @else {
                      <div class="flex h-full w-full items-center justify-center">
                        <svg class="h-8 w-8 text-stone-300 dark:text-stone-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                        </svg>
                      </div>
                    }
                  </a>
                  <div class="flex flex-1 flex-col gap-2 p-3">
                    <p class="text-sm font-medium text-stone-900 dark:text-stone-100 line-clamp-2">{{ recipe.name }}</p>
                    <label class="mt-auto">
                      <span class="sr-only">Déplacer « {{ recipe.name }} » vers...</span>
                      <select
                        (change)="onMove(recipe, section.role, $event)"
                        class="w-full rounded-lg border border-stone-200 dark:border-stone-700 bg-stone-50 dark:bg-stone-800 px-2 py-1.5 text-xs text-stone-700 dark:text-stone-300 cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500"
                        [attr.aria-label]="'Déplacer ' + recipe.name + ' vers'"
                      >
                        <option value="" selected>Déplacer vers...</option>
                        @for (target of otherRoles(section.role); track target) {
                          <option [value]="target">{{ labelFor(target) }}</option>
                        }
                      </select>
                    </label>
                  </div>
                </div>
              }
            </div>
          }
        </section>
      }
    </div>
  `,
})
export class RecipeListsComponent implements OnInit {
  overview = signal<RecipeListOverviewDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  private readonly allRoles: RecipeListRole[] = ['FAVORITES', 'DISCOVERY', 'REJECTED'];

  constructor(
    private recipeListsService: RecipeListsService,
    private toast: ToastService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.recipeListsService
      .getRecipeListsOverview()
      .pipe(
        catchError(() => {
          this.error.set('Impossible de charger vos listes. Vérifiez que le serveur est démarré.');
          this.loading.set(false);
          return of([]);
        }),
      )
      .subscribe(overview => {
        this.overview.set(overview);
        this.loading.set(false);
      });
  }

  otherRoles(current: RecipeListRole): RecipeListRole[] {
    return this.allRoles.filter(role => role !== current);
  }

  labelFor(role: RecipeListRole): string {
    return this.overview().find(section => section.role === role)?.displayLabel ?? role;
  }

  onMove(recipe: RecipeSummaryDto, currentRole: RecipeListRole, event: Event): void {
    const target = (event.target as HTMLSelectElement).value as RecipeListRole | '';
    (event.target as HTMLSelectElement).value = '';
    if (!target) return;

    this.recipeListsService.updateRecipeListMembership(recipe.id, { role: target }).subscribe({
      next: () => {
        this.toast.show({
          type: 'success',
          title: 'Recette déplacée',
          message: `« ${recipe.name} » a rejoint « ${this.labelFor(target)} ».`,
        });
        this.load();
      },
      error: () => {
        this.toast.show({
          type: 'error',
          title: 'Échec du déplacement',
          message: `« ${recipe.name} » n'a pas pu être déplacée. Vérifiez qu'une collection Cookidoo est rattachée à cette liste dans l'administration.`,
        });
      },
    });
  }
}
