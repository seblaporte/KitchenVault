import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { heroHandThumbDown, heroHandThumbUp } from '@ng-icons/heroicons/outline';
import {
  WeeklyReviewService,
  WeeklyReviewItemDto,
  WeeklyReviewVote,
} from '@KitchenVault/api-client';
import { ToastService } from '../../shared/toast/toast.service';

@Component({
  selector: 'app-weekly-review',
  standalone: true,
  imports: [CommonModule, RouterLink, NgIconComponent],
  providers: [provideIcons({ heroHandThumbUp, heroHandThumbDown })],
  template: `
    <div class="space-y-6">
      <div>
        <a
          routerLink="/lists"
          class="inline-flex items-center gap-1.5 text-sm text-stone-500 dark:text-stone-400 hover:text-stone-900 dark:hover:text-stone-100 transition-colors focus-visible:outline-2 focus-visible:outline-forest-500 rounded"
        >
          ← Retour à mes listes
        </a>
        <h1 class="mt-2 text-2xl font-semibold tracking-tight text-stone-900 dark:text-stone-100">
          Bilan de la semaine
        </h1>
        <p class="mt-1 text-sm text-stone-500 dark:text-stone-400">
          Notez les recettes à découvrir que vous avez essayées la semaine dernière (du {{ formatWeekStart() }}).
        </p>
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

      @if (!loading() && !error() && items().length === 0) {
        <div class="rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 p-6 text-sm text-stone-500 dark:text-stone-400">
          Aucune recette à découvrir n'était planifiée la semaine dernière.
        </div>
      }

      @if (items().length > 0) {
        <ul class="space-y-3" role="list">
          @for (item of items(); track item.recipeId) {
            <li class="flex items-center gap-4 rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 p-4 shadow-sm">
              <div class="h-16 w-16 flex-shrink-0 overflow-hidden rounded-lg bg-stone-100 dark:bg-stone-800">
                @if (item.thumbnailUrl) {
                  <img [src]="item.thumbnailUrl" [alt]="item.recipeName" class="h-full w-full object-cover" loading="lazy" />
                }
              </div>
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-medium text-stone-900 dark:text-stone-100">{{ item.recipeName }}</p>
                <p class="text-xs text-stone-500 dark:text-stone-400">
                  Planifiée {{ item.plannedDates.length }} fois cette semaine-là
                </p>
              </div>
              <div class="flex flex-shrink-0 gap-2">
                <button
                  type="button"
                  (click)="setVote(item.recipeId, 'UP')"
                  [attr.aria-pressed]="votes()[item.recipeId] === 'UP'"
                  [class.bg-emerald-100]="votes()[item.recipeId] === 'UP'"
                  [class.text-emerald-700]="votes()[item.recipeId] === 'UP'"
                  class="flex h-11 w-11 items-center justify-center rounded-full border border-stone-200 dark:border-stone-700 text-stone-400 dark:text-stone-500 hover:bg-emerald-50 hover:text-emerald-600 transition-colors focus-visible:outline-2 focus-visible:outline-forest-500"
                  [attr.aria-label]="'Pouce vers le haut pour ' + item.recipeName"
                >
                  <ng-icon name="heroHandThumbUp" class="h-5 w-5" aria-hidden="true" />
                </button>
                <button
                  type="button"
                  (click)="setVote(item.recipeId, 'DOWN')"
                  [attr.aria-pressed]="votes()[item.recipeId] === 'DOWN'"
                  [class.bg-red-100]="votes()[item.recipeId] === 'DOWN'"
                  [class.text-red-700]="votes()[item.recipeId] === 'DOWN'"
                  class="flex h-11 w-11 items-center justify-center rounded-full border border-stone-200 dark:border-stone-700 text-stone-400 dark:text-stone-500 hover:bg-red-50 hover:text-red-600 transition-colors focus-visible:outline-2 focus-visible:outline-forest-500"
                  [attr.aria-label]="'Pouce vers le bas pour ' + item.recipeName"
                >
                  <ng-icon name="heroHandThumbDown" class="h-5 w-5" aria-hidden="true" />
                </button>
              </div>
            </li>
          }
        </ul>

        <div class="sticky bottom-20 sm:bottom-4 flex justify-end">
          <button
            type="button"
            (click)="submit()"
            [disabled]="voteCount() === 0 || submitting()"
            class="inline-flex items-center gap-2 rounded-lg bg-forest-600 px-6 py-3 text-sm font-medium text-white shadow-lg hover:bg-forest-700 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-forest-600"
            [attr.aria-busy]="submitting()"
          >
            Valider mes votes ({{ voteCount() }})
          </button>
        </div>
      }
    </div>
  `,
})
export class WeeklyReviewComponent implements OnInit {
  items = signal<WeeklyReviewItemDto[]>([]);
  votes = signal<Record<string, WeeklyReviewVote>>({});
  loading = signal(true);
  submitting = signal(false);
  error = signal<string | null>(null);
  weekStart = this.computePreviousWeekStart();

  constructor(
    private weeklyReviewService: WeeklyReviewService,
    private router: Router,
    private toast: ToastService,
  ) {}

  ngOnInit(): void {
    this.weeklyReviewService
      .getWeeklyReview(this.weekStart)
      .pipe(
        catchError(() => {
          this.error.set('Impossible de charger le bilan. Vérifiez que le serveur est démarré.');
          this.loading.set(false);
          return of(null);
        }),
      )
      .subscribe(review => {
        if (review) this.items.set(review.items);
        this.loading.set(false);
      });
  }

  voteCount(): number {
    return Object.keys(this.votes()).length;
  }

  setVote(recipeId: string, vote: WeeklyReviewVote): void {
    this.votes.update(current => {
      const next = { ...current };
      if (next[recipeId] === vote) {
        delete next[recipeId];
      } else {
        next[recipeId] = vote;
      }
      return next;
    });
  }

  submit(): void {
    const votes = Object.entries(this.votes()).map(([recipeId, vote]) => ({ recipeId, vote }));
    if (votes.length === 0) return;

    this.submitting.set(true);
    this.weeklyReviewService.submitWeeklyReviewVotes(this.weekStart, { votes }).subscribe({
      next: result => {
        this.submitting.set(false);
        if (result.failed.length === 0) {
          this.toast.show({
            type: 'success',
            title: 'Bilan enregistré',
            message: `${result.moved.length} recette(s) déplacée(s).`,
          });
        } else {
          this.toast.show({
            type: 'error',
            title: 'Bilan partiellement enregistré',
            message: `${result.moved.length} recette(s) déplacée(s), ${result.failed.length} échec(s). Réessayez plus tard.`,
          });
        }
        this.router.navigate(['/lists']);
      },
      error: () => {
        this.submitting.set(false);
        this.toast.show({ type: 'error', title: 'Échec de l\'envoi du bilan' });
      },
    });
  }

  formatWeekStart(): string {
    return new Date(this.weekStart + 'T00:00:00').toLocaleDateString('fr-FR', {
      day: 'numeric',
      month: 'long',
    });
  }

  private computePreviousWeekStart(): string {
    const today = new Date();
    const day = today.getDay();
    const diffToMonday = day === 0 ? -6 : 1 - day;
    const thisMonday = new Date(today);
    thisMonday.setDate(today.getDate() + diffToMonday);
    const prevMonday = new Date(thisMonday);
    prevMonday.setDate(thisMonday.getDate() - 7);

    const y = prevMonday.getFullYear();
    const m = String(prevMonday.getMonth() + 1).padStart(2, '0');
    const d = String(prevMonday.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
}
