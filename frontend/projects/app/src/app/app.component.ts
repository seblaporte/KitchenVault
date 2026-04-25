import { Component, OnInit, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { heroBookOpen, heroCalendarDays, heroCog6Tooth, heroSun, heroMoon } from '@ng-icons/heroicons/outline';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgIconComponent],
  providers: [provideIcons({ heroBookOpen, heroCalendarDays, heroCog6Tooth, heroSun, heroMoon })],
  template: `
    <div class="min-h-screen bg-stone-50 dark:bg-stone-950 text-stone-900 dark:text-stone-100">
      <!-- Navigation -->
      <nav class="border-b border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900" aria-label="Navigation principale">
        <div class="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
          <div class="flex h-16 items-center justify-between">
            <div class="flex items-center">
              <span class="text-3xl font-bold tracking-tight select-none">
                <span class="text-stone-800 dark:text-stone-100">Kitchen</span><span class="text-orange-500">Vault</span>
              </span>
            </div>
            <ul class="flex gap-2" role="list">
              <li>
                <a
                  routerLink="/recipes"
                  routerLinkActive="bg-stone-100 dark:bg-stone-800 text-forest-600 dark:text-forest-400"
                  class="flex items-center gap-2 px-4 py-2 rounded-xl text-base font-medium text-stone-600 dark:text-stone-300 hover:bg-stone-100 dark:hover:bg-stone-800 transition-colors focus-visible:outline-2 focus-visible:outline-forest-500"
                  aria-label="Recettes"
                >
                  <ng-icon name="heroBookOpen" class="h-5 w-5" aria-hidden="true" />
                  Recettes
                </a>
              </li>
              <li>
                <a
                  routerLink="/menu"
                  routerLinkActive="bg-stone-100 dark:bg-stone-800 text-forest-600 dark:text-forest-400"
                  class="flex items-center gap-2 px-4 py-2 rounded-xl text-base font-medium text-stone-600 dark:text-stone-300 hover:bg-stone-100 dark:hover:bg-stone-800 transition-colors focus-visible:outline-2 focus-visible:outline-forest-500"
                  aria-label="Menu"
                >
                  <ng-icon name="heroCalendarDays" class="h-5 w-5" aria-hidden="true" />
                  Menu
                </a>
              </li>
              <li>
                <a
                  routerLink="/admin"
                  routerLinkActive="bg-stone-100 dark:bg-stone-800 text-forest-600 dark:text-forest-400"
                  class="flex items-center gap-2 px-4 py-2 rounded-xl text-base font-medium text-stone-600 dark:text-stone-300 hover:bg-stone-100 dark:hover:bg-stone-800 transition-colors focus-visible:outline-2 focus-visible:outline-forest-500"
                  aria-label="Administration"
                >
                  <ng-icon name="heroCog6Tooth" class="h-5 w-5" aria-hidden="true" />
                  Administration
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
                <ng-icon name="heroSun" class="h-5 w-5" aria-hidden="true" />
              } @else {
                <ng-icon name="heroMoon" class="h-5 w-5" aria-hidden="true" />
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
