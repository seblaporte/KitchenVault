import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'admin',
    pathMatch: 'full',
  },
  {
    path: 'admin',
    loadComponent: () =>
      import('./admin/admin.component').then(m => m.AdminComponent),
  },
  {
    path: 'recipes',
    loadComponent: () =>
      import('./recipes/recipes.component').then(m => m.RecipesComponent),
  },
  {
    path: 'recipes/:id',
    loadComponent: () =>
      import('./recipes/recipe-detail/recipe-detail.component').then(
        m => m.RecipeDetailComponent,
      ),
  },
  {
    path: 'menu',
    loadComponent: () =>
      import('./menu-plan/menu-plan.component').then(m => m.MenuPlanComponent),
  },
  {
    path: 'shopping',
    loadComponent: () =>
      import('./shopping-list/shopping-list.component').then(m => m.ShoppingListComponent),
  },
];
