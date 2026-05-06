import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import {
  heroShoppingCart,
  heroTrash,
  heroClipboard,
  heroCheck,
  heroXMark,
} from '@ng-icons/heroicons/outline';
import { catchError, EMPTY } from 'rxjs';
import { ShoppingListStateService } from './shopping-list-state.service';
import { ShoppingCategory } from '@KitchenVault/api-client';

interface CategoryGroup {
  category: ShoppingCategory;
  label: string;
  emoji: string;
  items: ReturnType<ShoppingListStateService['items']> extends Array<infer T> ? T[] : never[];
}

const CATEGORY_META: Record<string, { label: string; emoji: string }> = {
  produce: { label: 'Fruits & légumes', emoji: '🥬' },
  meat:    { label: 'Viandes & poissons', emoji: '🥩' },
  dairy:   { label: 'Produits laitiers', emoji: '🧀' },
  bakery:  { label: 'Boulangerie', emoji: '🥖' },
  grocery: { label: 'Épicerie', emoji: '🛒' },
  frozen:  { label: 'Surgelés', emoji: '❄️' },
  spices:  { label: 'Épices & condiments', emoji: '🧂' },
  other:   { label: 'Autres', emoji: '📦' },
};
const CATEGORY_ORDER: ShoppingCategory[] = [
  'produce', 'meat', 'dairy', 'bakery', 'grocery', 'frozen', 'spices', 'other'
];

@Component({
  selector: 'app-shopping-list',
  standalone: true,
  imports: [CommonModule, RouterLink, NgIconComponent],
  providers: [provideIcons({ heroShoppingCart, heroTrash, heroClipboard, heroCheck, heroXMark })],
  template: `
    <!-- En-tête -->
    <div class="flex flex-wrap items-center gap-2 rounded-xl border border-stone-200 dark:border-stone-800 bg-white dark:bg-stone-950 px-4 py-3 shadow-sm mb-6">
      <h1 class="text-base font-semibold tracking-tight text-stone-900 dark:text-stone-100">Liste de courses</h1>

      @if (shoppingListState.items().length > 0) {
        <span class="text-xs text-stone-500 dark:text-stone-400">
          {{ shoppingListState.uncheckedCount() }} à acheter
          @if (checkedCount() > 0) { · {{ checkedCount() }} cochés }
        </span>
      }

      <div class="ml-auto flex items-center gap-2">
        @if (checkedCount() > 0) {
          <button
            (click)="clearChecked()"
            class="inline-flex items-center gap-1.5 rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 px-3 py-1.5 text-xs font-medium text-stone-600 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500"
          >
            <ng-icon name="heroCheck" class="h-3.5 w-3.5" aria-hidden="true" />
            Retirer les cochés
          </button>
        }
        @if (shoppingListState.items().length > 0) {
          <button
            (click)="copyToClipboard()"
            class="inline-flex items-center gap-1.5 rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 px-3 py-1.5 text-xs font-medium text-stone-600 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500"
            [attr.aria-label]="copied() ? 'Copié !' : 'Copier la liste'"
          >
            <ng-icon name="heroClipboard" class="h-3.5 w-3.5" aria-hidden="true" />
            {{ copied() ? 'Copié !' : 'Copier' }}
          </button>
          <button
            (click)="clearAll()"
            class="inline-flex items-center gap-1.5 rounded-lg border border-red-200 dark:border-red-800 bg-white dark:bg-stone-900 px-3 py-1.5 text-xs font-medium text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/20 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-red-500"
          >
            <ng-icon name="heroTrash" class="h-3.5 w-3.5" aria-hidden="true" />
            Tout effacer
          </button>
        }
      </div>
    </div>

    <!-- Erreur -->
    @if (shoppingListState.error()) {
      <div class="rounded-xl border border-red-200 bg-red-50 dark:bg-red-950/20 dark:border-red-800 p-4 text-sm text-red-700 dark:text-red-400 mb-4" role="alert">
        {{ shoppingListState.error() }}
      </div>
    }

    <!-- Chargement -->
    @if (shoppingListState.loading()) {
      <div class="flex items-center justify-center py-16">
        <svg class="animate-spin h-8 w-8 text-forest-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-label="Chargement">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
        </svg>
      </div>
    }

    <!-- Empty state -->
    @if (!shoppingListState.loading() && shoppingListState.items().length === 0) {
      <div class="flex flex-col items-center justify-center py-20 gap-4 text-center">
        <ng-icon name="heroShoppingCart" class="h-14 w-14 text-stone-300 dark:text-stone-600" aria-hidden="true" />
        <p class="text-stone-500 dark:text-stone-400 text-sm">Votre liste de courses est vide.</p>
        <a
          routerLink="/menu"
          class="inline-flex items-center gap-2 rounded-lg bg-forest-600 px-4 py-2 text-sm font-medium text-white hover:bg-forest-700 transition-colors focus-visible:outline-2 focus-visible:outline-forest-600"
        >
          Aller au menu de la semaine
        </a>
      </div>
    }

    <!-- Contenu principal -->
    @if (!shoppingListState.loading() && shoppingListState.items().length > 0) {
      <div class="grid grid-cols-1 lg:grid-cols-[1fr_280px] gap-6 items-start">

        <!-- Colonne gauche : items groupés par catégorie -->
        <div class="flex flex-col gap-4">
          @for (group of itemsByCategory(); track group.category) {
            <div class="rounded-xl border border-stone-200 dark:border-stone-800 bg-white dark:bg-stone-950 overflow-hidden shadow-sm">
              <div class="px-4 py-2.5 border-b border-stone-100 dark:border-stone-800 bg-stone-50 dark:bg-stone-900/50 flex items-center gap-2">
                <span class="text-base leading-none" aria-hidden="true">{{ group.emoji }}</span>
                <span class="text-xs font-semibold uppercase tracking-widest text-stone-500 dark:text-stone-400">{{ group.label }}</span>
                <span class="text-xs text-stone-400 dark:text-stone-600 ml-auto">{{ group.items.length }}</span>
              </div>
              <ul role="list" class="divide-y divide-stone-100 dark:divide-stone-800">
                @for (item of group.items; track item.id) {
                  <li class="group flex items-center gap-3 px-4 py-3 hover:bg-stone-50 dark:hover:bg-stone-900/30 transition-colors">
                    <input
                      type="checkbox"
                      [id]="'item-' + item.id"
                      [checked]="item.checked"
                      (change)="toggleItem(item.id)"
                      class="h-4 w-4 rounded border-stone-300 dark:border-stone-600 text-forest-600 focus:ring-forest-500 cursor-pointer flex-shrink-0"
                      [attr.aria-label]="'Cocher ' + item.name"
                    />
                    <label
                      [for]="'item-' + item.id"
                      class="flex-1 flex items-baseline gap-2 cursor-pointer min-w-0"
                    >
                      <span
                        class="text-sm font-medium text-stone-800 dark:text-stone-200 transition-colors"
                        [class.line-through]="item.checked"
                        [class.text-stone-400]="item.checked"
                        [class.dark:text-stone-600]="item.checked"
                      >{{ item.name }}</span>
                      @if (item.quantity) {
                        <span
                          class="text-xs text-stone-400 dark:text-stone-500 flex-shrink-0"
                          [class.line-through]="item.checked"
                        >{{ item.quantity }}</span>
                      }
                    </label>
                    <!-- Badges recettes sources -->
                    @if (!item.custom && item.sourceRecipeIds.length > 0) {
                      <div class="hidden sm:flex items-center gap-1 flex-shrink-0">
                        @for (rid of item.sourceRecipeIds; track rid) {
                          <span class="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium bg-forest-50 dark:bg-forest-950/40 text-forest-700 dark:text-forest-400 border border-forest-100 dark:border-forest-800 max-w-[80px] truncate" [title]="shoppingListState.getRecipeName(rid)">
                            {{ shoppingListState.getRecipeName(rid) }}
                          </span>
                        }
                      </div>
                    }
                    <!-- Bouton supprimer -->
                    <button
                      (click)="deleteItem(item.id)"
                      class="w-6 h-6 rounded-md text-stone-300 dark:text-stone-700 flex items-center justify-center opacity-0 group-hover:opacity-100 hover:text-red-500 dark:hover:text-red-400 transition-all cursor-pointer flex-shrink-0"
                      [attr.aria-label]="'Supprimer ' + item.name"
                    >
                      <ng-icon name="heroTrash" class="h-3.5 w-3.5" aria-hidden="true" />
                    </button>
                  </li>
                }
              </ul>
            </div>
          }
        </div>

        <!-- Colonne droite : sidebar -->
        <div class="flex flex-col gap-4">

          <!-- Recettes ajoutées -->
          @if (shoppingListState.addedRecipeIds().length > 0) {
            <div class="rounded-xl border border-stone-200 dark:border-stone-800 bg-white dark:bg-stone-950 overflow-hidden shadow-sm">
              <div class="px-4 py-2.5 border-b border-stone-100 dark:border-stone-800 bg-stone-50 dark:bg-stone-900/50">
                <span class="text-xs font-semibold uppercase tracking-widest text-stone-500 dark:text-stone-400">Recettes</span>
              </div>
              <ul role="list" class="divide-y divide-stone-100 dark:divide-stone-800">
                @for (rid of shoppingListState.addedRecipeIds(); track rid) {
                  <li class="group flex items-center gap-2 px-4 py-2.5">
                    <span class="flex-1 text-sm text-stone-700 dark:text-stone-300 truncate" [title]="shoppingListState.getRecipeName(rid)">
                      {{ shoppingListState.getRecipeName(rid) }}
                    </span>
                    <button
                      (click)="removeRecipe(rid)"
                      class="w-5 h-5 rounded flex items-center justify-center text-stone-300 dark:text-stone-700 hover:text-red-500 dark:hover:text-red-400 opacity-0 group-hover:opacity-100 transition-all cursor-pointer flex-shrink-0"
                      [attr.aria-label]="'Retirer ' + shoppingListState.getRecipeName(rid)"
                    >
                      <ng-icon name="heroXMark" class="h-3.5 w-3.5" aria-hidden="true" />
                    </button>
                  </li>
                }
              </ul>
            </div>
          }

          <!-- Ajout libre -->
          <div class="rounded-xl border border-stone-200 dark:border-stone-800 bg-white dark:bg-stone-950 overflow-hidden shadow-sm">
            <div class="px-4 py-2.5 border-b border-stone-100 dark:border-stone-800 bg-stone-50 dark:bg-stone-900/50">
              <span class="text-xs font-semibold uppercase tracking-widest text-stone-500 dark:text-stone-400">Ajout libre</span>
            </div>
            <div class="p-4 flex flex-col gap-2">
              <input
                type="text"
                [value]="newItemName()"
                (input)="newItemName.set($any($event.target).value)"
                placeholder="Nom de l'article"
                class="w-full rounded-lg border border-stone-200 dark:border-stone-700 bg-stone-50 dark:bg-stone-900 px-3 py-2 text-sm text-stone-800 dark:text-stone-200 placeholder-stone-400 focus:outline-none focus:ring-2 focus:ring-forest-500 focus:border-transparent transition"
                (keyup.enter)="addCustomItem()"
              />
              <input
                type="text"
                [value]="newItemQty()"
                (input)="newItemQty.set($any($event.target).value)"
                placeholder="Quantité (optionnel)"
                class="w-full rounded-lg border border-stone-200 dark:border-stone-700 bg-stone-50 dark:bg-stone-900 px-3 py-2 text-sm text-stone-800 dark:text-stone-200 placeholder-stone-400 focus:outline-none focus:ring-2 focus:ring-forest-500 focus:border-transparent transition"
                (keyup.enter)="addCustomItem()"
              />
              <button
                (click)="addCustomItem()"
                [disabled]="!newItemName().trim() || addingItem()"
                class="w-full rounded-lg bg-forest-600 px-3 py-2 text-sm font-medium text-white hover:bg-forest-700 transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline-2 focus-visible:outline-forest-600"
              >
                {{ addingItem() ? 'Ajout...' : 'Ajouter' }}
              </button>
            </div>
          </div>

        </div>
      </div>
    }
  `,
})
export class ShoppingListComponent implements OnInit {
  shoppingListState = inject(ShoppingListStateService);

  newItemName = signal('');
  newItemQty = signal('');
  addingItem = signal(false);
  copied = signal(false);

  checkedCount = computed(() =>
    this.shoppingListState.items().filter(i => i.checked).length
  );

  itemsByCategory = computed<CategoryGroup[]>(() => {
    const items = this.shoppingListState.items();
    const grouped = new Map<ShoppingCategory, typeof items>();
    for (const item of items) {
      const cat = item.category;
      if (!grouped.has(cat)) grouped.set(cat, []);
      grouped.get(cat)!.push(item);
    }
    return CATEGORY_ORDER
      .filter(cat => grouped.has(cat))
      .map(cat => ({
        category: cat,
        ...CATEGORY_META[cat],
        items: grouped.get(cat)!,
      }));
  });

  ngOnInit(): void {
    this.shoppingListState.load();
  }

  toggleItem(itemId: string): void {
    this.shoppingListState.toggleItem(itemId)
      .pipe(catchError(() => EMPTY))
      .subscribe();
  }

  deleteItem(itemId: string): void {
    this.shoppingListState.deleteItem(itemId)
      .pipe(catchError(() => EMPTY))
      .subscribe();
  }

  removeRecipe(recipeId: string): void {
    this.shoppingListState.removeRecipe(recipeId)
      .pipe(catchError(() => EMPTY))
      .subscribe();
  }

  clearChecked(): void {
    this.shoppingListState.clearChecked()
      .pipe(catchError(() => EMPTY))
      .subscribe();
  }

  clearAll(): void {
    if (!confirm('Vider entièrement la liste de courses ?')) return;
    this.shoppingListState.clearAll()
      .pipe(catchError(() => EMPTY))
      .subscribe();
  }

  addCustomItem(): void {
    const name = this.newItemName().trim();
    if (!name) return;
    this.addingItem.set(true);
    this.shoppingListState.addCustomItem(name, this.newItemQty().trim() || null)
      .pipe(catchError(() => EMPTY))
      .subscribe({
        complete: () => {
          this.newItemName.set('');
          this.newItemQty.set('');
          this.addingItem.set(false);
        },
      });
  }

  copyToClipboard(): void {
    const lines: string[] = [];
    for (const group of this.itemsByCategory()) {
      const unchecked = group.items.filter(i => !i.checked);
      if (unchecked.length === 0) continue;
      lines.push(`${group.emoji} ${group.label}`);
      for (const item of unchecked) {
        lines.push(`- ${item.name}${item.quantity ? ' ' + item.quantity : ''}`);
      }
      lines.push('');
    }
    navigator.clipboard.writeText(lines.join('\n').trim()).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    });
  }
}
