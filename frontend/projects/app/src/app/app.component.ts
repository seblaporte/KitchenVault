import { Component, OnInit, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="min-h-screen bg-stone-50 dark:bg-stone-950 text-stone-900 dark:text-stone-100">
      <!-- Navigation -->
      <nav class="border-b border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900" aria-label="Navigation principale">
        <div class="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
          <div class="flex h-32 items-center justify-between">
            <div class="flex items-center">
              <img src="/logo.png" alt="KitchenVault" class="h-[120px] w-auto" />
            </div>
            <ul class="flex gap-6" role="list">
              <li>
                <a
                  routerLink="/admin"
                  routerLinkActive="text-forest-600 dark:text-forest-400 font-medium"
                  class="text-sm text-stone-500 dark:text-stone-400 hover:text-stone-900 dark:hover:text-stone-100 transition-colors focus-visible:outline-2 focus-visible:outline-forest-500 rounded"
                  aria-label="Administration"
                >
                  Administration
                </a>
              </li>
              <li>
                <a
                  routerLink="/recipes"
                  routerLinkActive="text-forest-600 dark:text-forest-400 font-medium"
                  class="text-sm text-stone-500 dark:text-stone-400 hover:text-stone-900 dark:hover:text-stone-100 transition-colors focus-visible:outline-2 focus-visible:outline-forest-500 rounded"
                  aria-label="Recettes"
                >
                  Recettes
                </a>
              </li>
              <li>
                <a
                  routerLink="/menu"
                  routerLinkActive="text-forest-600 dark:text-forest-400 font-medium"
                  class="text-sm text-stone-500 dark:text-stone-400 hover:text-stone-900 dark:hover:text-stone-100 transition-colors focus-visible:outline-2 focus-visible:outline-forest-500 rounded"
                  aria-label="Menu"
                >
                  Menu
                </a>
              </li>
            </ul>
            <button
              type="button"
              (click)="toggleDark()"
              class="rounded-lg p-2 text-stone-500 dark:text-stone-400 hover:bg-stone-100 dark:hover:bg-stone-800 transition-colors focus-visible:outline-2 focus-visible:outline-forest-500"
              [attr.aria-label]="darkMode() ? 'Activer le mode clair' : 'Activer le mode sombre'"
            >
              @if (darkMode()) {
                <svg class="h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
                </svg>
              } @else {
                <svg class="h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
                </svg>
              }
            </button>
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
export class AppComponent implements OnInit {
  darkMode = signal(false);

  ngOnInit(): void {
    if (localStorage.getItem('theme') === 'dark') {
      this.darkMode.set(true);
      document.documentElement.classList.add('dark');
    }
  }

  toggleDark(): void {
    const next = !this.darkMode();
    this.darkMode.set(next);
    if (next) {
      document.documentElement.classList.add('dark');
      localStorage.setItem('theme', 'dark');
    } else {
      document.documentElement.classList.remove('dark');
      localStorage.setItem('theme', 'light');
    }
  }
}
