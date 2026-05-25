import { Component, OnInit, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { heroBookOpen, heroCalendarDays, heroCog6Tooth, heroSun, heroMoon, heroShoppingCart } from '@ng-icons/heroicons/outline';
import { ToastComponent } from './shared/toast/toast.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgIconComponent, NgClass, ToastComponent],
  providers: [provideIcons({ heroBookOpen, heroCalendarDays, heroCog6Tooth, heroSun, heroMoon, heroShoppingCart })],
  template: `
    <div class="min-h-screen bg-stone-50 dark:bg-stone-950 text-stone-900 dark:text-stone-100">
      <!-- Navigation desktop -->
      <nav class="sticky top-0 z-30 border-b border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900" style="padding-top: env(safe-area-inset-top)" aria-label="Navigation principale">
        <div class="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
          <div class="flex h-14 sm:h-16 items-center justify-between">
            <div class="flex items-center">
              <span class="text-xl sm:text-3xl font-bold tracking-tight select-none">
                <span class="text-stone-800 dark:text-stone-100">Kitchen</span><span class="text-forest-600">Vault</span>
              </span>
            </div>
            <!-- Liens desktop uniquement -->
            <ul class="hidden sm:flex gap-2" role="list">
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
                  routerLink="/shopping"
                  routerLinkActive="bg-stone-100 dark:bg-stone-800 text-forest-600 dark:text-forest-400"
                  class="flex items-center gap-2 px-4 py-2 rounded-xl text-base font-medium text-stone-600 dark:text-stone-300 hover:bg-stone-100 dark:hover:bg-stone-800 transition-colors focus-visible:outline-2 focus-visible:outline-forest-500"
                  aria-label="Liste de courses"
                >
                  <ng-icon name="heroShoppingCart" class="h-5 w-5" aria-hidden="true" />
                  Courses
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

      <!-- Contenu principal -->
      <main class="mx-auto max-w-6xl px-4 pt-4 pb-24 sm:py-8 sm:px-6 lg:px-8">
        <router-outlet />
      </main>

      <!-- Barre de navigation mobile (bottom tab bar) -->
      <nav
        class="sm:hidden fixed bottom-0 inset-x-0 z-30 bg-white dark:bg-stone-900 border-t border-stone-200 dark:border-stone-700"
        aria-label="Navigation mobile"
        style="padding-bottom: env(safe-area-inset-bottom)"
      >
        <div class="flex items-stretch">
          <a
            routerLink="/recipes"
            routerLinkActive
            #rlaRecipes="routerLinkActive"
            [ngClass]="rlaRecipes.isActive ? 'text-forest-600 dark:text-forest-400' : 'text-stone-400 dark:text-stone-500'"
            class="flex-1 flex flex-col items-center gap-0.5 py-2.5 transition-colors active:bg-stone-50 dark:active:bg-stone-800"
            aria-label="Recettes"
          >
            <ng-icon name="heroBookOpen" class="h-6 w-6" aria-hidden="true" />
            <span class="text-[10px] font-medium">Recettes</span>
          </a>
          <a
            routerLink="/menu"
            routerLinkActive
            #rlaMenu="routerLinkActive"
            [ngClass]="rlaMenu.isActive ? 'text-forest-600 dark:text-forest-400' : 'text-stone-400 dark:text-stone-500'"
            class="flex-1 flex flex-col items-center gap-0.5 py-2.5 transition-colors active:bg-stone-50 dark:active:bg-stone-800"
            aria-label="Menu"
          >
            <ng-icon name="heroCalendarDays" class="h-6 w-6" aria-hidden="true" />
            <span class="text-[10px] font-medium">Menu</span>
          </a>
          <a
            routerLink="/shopping"
            routerLinkActive
            #rlaShopping="routerLinkActive"
            [ngClass]="rlaShopping.isActive ? 'text-forest-600 dark:text-forest-400' : 'text-stone-400 dark:text-stone-500'"
            class="flex-1 flex flex-col items-center gap-0.5 py-2.5 transition-colors active:bg-stone-50 dark:active:bg-stone-800"
            aria-label="Liste de courses"
          >
            <ng-icon name="heroShoppingCart" class="h-6 w-6" aria-hidden="true" />
            <span class="text-[10px] font-medium">Courses</span>
          </a>
          <a
            routerLink="/admin"
            routerLinkActive
            #rlaAdmin="routerLinkActive"
            [ngClass]="rlaAdmin.isActive ? 'text-forest-600 dark:text-forest-400' : 'text-stone-400 dark:text-stone-500'"
            class="flex-1 flex flex-col items-center gap-0.5 py-2.5 transition-colors active:bg-stone-50 dark:active:bg-stone-800"
            aria-label="Administration"
          >
            <ng-icon name="heroCog6Tooth" class="h-6 w-6" aria-hidden="true" />
            <span class="text-[10px] font-medium">Admin</span>
          </a>
        </div>
      </nav>
    </div>
    <app-toast />
  `,
})
export class AppComponent implements OnInit {
  darkMode = signal(false);

  ngOnInit(): void {
    const stored = localStorage.getItem('theme');
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    if (stored === 'dark' || (!stored && prefersDark)) {
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
