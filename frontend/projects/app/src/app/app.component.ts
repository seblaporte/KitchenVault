import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="min-h-screen bg-zinc-50 text-zinc-900">
      <!-- Navigation -->
      <nav class="border-b border-zinc-200 bg-white" aria-label="Navigation principale">
        <div class="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
          <div class="flex h-16 items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="text-lg font-semibold tracking-tight">My Cookidoo</span>
            </div>
            <ul class="flex gap-6" role="list">
              <li>
                <a
                  routerLink="/admin"
                  routerLinkActive="text-indigo-600 font-medium"
                  class="text-sm text-zinc-500 hover:text-zinc-900 transition-colors focus-visible:outline-2 focus-visible:outline-indigo-500 rounded"
                  aria-label="Administration"
                >
                  Administration
                </a>
              </li>
              <li>
                <a
                  routerLink="/recipes"
                  routerLinkActive="text-indigo-600 font-medium"
                  class="text-sm text-zinc-500 hover:text-zinc-900 transition-colors focus-visible:outline-2 focus-visible:outline-indigo-500 rounded"
                  aria-label="Recettes"
                >
                  Recettes
                </a>
              </li>
              <li>
                <a
                  routerLink="/menu"
                  routerLinkActive="text-indigo-600 font-medium"
                  class="text-sm text-zinc-500 hover:text-zinc-900 transition-colors focus-visible:outline-2 focus-visible:outline-indigo-500 rounded"
                  aria-label="Menu"
                >
                  Menu
                </a>
              </li>
            </ul>
          </div>
        </div>
      </nav>

      <!-- Main content -->
      <main class="mx-auto max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
        <router-outlet />
      </main>
    </div>
  `,
})
export class AppComponent {}
