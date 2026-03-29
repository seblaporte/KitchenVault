import { Component } from '@angular/core';

@Component({
  selector: 'app-recipes',
  standalone: true,
  template: `
    <div class="space-y-4">
      <h1 class="text-2xl font-semibold tracking-tight text-zinc-900">Recettes</h1>
      <p class="text-sm text-zinc-500">
        La consultation des recettes sera disponible en Phase 2.
      </p>
    </div>
  `,
})
export class RecipesComponent {}
