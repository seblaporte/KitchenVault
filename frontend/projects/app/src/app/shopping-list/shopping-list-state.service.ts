import { Injectable, computed, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import {
  AddShoppingListItemRequest,
  ShoppingListDto,
  ShoppingListItemDto,
  ShoppingListService,
} from '@KitchenVault/api-client';

@Injectable({ providedIn: 'root' })
export class ShoppingListStateService {
  private shoppingList = signal<ShoppingListDto | null>(null);
  private recipeNameMap = new Map<string, string>();

  loading = signal(false);
  error = signal<string | null>(null);

  addedRecipeIds = computed(() => this.shoppingList()?.addedRecipeIds ?? []);
  uncheckedCount = computed(
    () => this.shoppingList()?.items.filter(i => !i.checked).length ?? 0
  );
  items = computed(() => this.shoppingList()?.items ?? []);

  constructor(private api: ShoppingListService) {}

  getRecipeName(id: string): string {
    return this.recipeNameMap.get(id) ?? id;
  }

  load(): void {
    this.loading.set(true);
    this.api.getShoppingList().subscribe({
      next: list => {
        this.shoppingList.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      },
    });
  }

  addRecipe(recipeId: string, recipeName: string): Observable<ShoppingListDto> {
    this.recipeNameMap.set(recipeId, recipeName);
    return this.api.addRecipeToShoppingList(recipeId).pipe(
      tap(list => this.shoppingList.set(list))
    );
  }

  removeRecipe(recipeId: string): Observable<ShoppingListDto> {
    return this.api.removeRecipeFromShoppingList(recipeId).pipe(
      tap(list => this.shoppingList.set(list))
    );
  }

  toggleItem(itemId: string): Observable<ShoppingListItemDto> {
    return this.api.toggleShoppingListItem(itemId).pipe(
      tap(updated => {
        const current = this.shoppingList();
        if (!current) return;
        this.shoppingList.set({
          ...current,
          items: current.items.map(i => (i.id === itemId ? updated : i)),
        });
      })
    );
  }

  deleteItem(itemId: string): Observable<object> {
    return this.api.deleteShoppingListItem(itemId).pipe(
      tap(() => {
        const current = this.shoppingList();
        if (!current) return;
        const items = current.items.filter(i => i.id !== itemId);
        const addedRecipeIds = [...new Set(
          items.filter(i => !i.custom).flatMap(i => i.sourceRecipeIds)
        )];
        this.shoppingList.set({ items, addedRecipeIds });
      })
    );
  }

  addCustomItem(name: string, quantity: string | null): Observable<ShoppingListItemDto> {
    const req: AddShoppingListItemRequest = { name, ...(quantity ? { quantity } : {}) };
    return this.api.addCustomShoppingListItem(req).pipe(
      tap(item => {
        const current = this.shoppingList();
        if (!current) return;
        this.shoppingList.set({ ...current, items: [...current.items, item] });
      })
    );
  }

  clearChecked(): Observable<object> {
    return this.api.clearCheckedItems().pipe(
      tap(() => {
        const current = this.shoppingList();
        if (!current) return;
        const items = current.items.filter(i => !i.checked);
        const addedRecipeIds = [...new Set(
          items.filter(i => !i.custom).flatMap(i => i.sourceRecipeIds)
        )];
        this.shoppingList.set({ items, addedRecipeIds });
      })
    );
  }

  clearAll(): Observable<object> {
    return this.api.clearShoppingList().pipe(
      tap(() => this.shoppingList.set({ items: [], addedRecipeIds: [] }))
    );
  }
}
