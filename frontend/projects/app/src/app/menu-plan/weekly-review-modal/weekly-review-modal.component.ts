import { Component, EventEmitter, Input, OnInit, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
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
  selector: 'app-weekly-review-modal',
  standalone: true,
  imports: [CommonModule, NgIconComponent],
  viewProviders: [provideIcons({ heroHandThumbUp, heroHandThumbDown })],
  template: `
    <div
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-label="Bilan de la semaine"
      (click)="onBackdropClick($event)"
    >
      <div class="absolute inset-0 bg-black/70 backdrop-blur-sm" aria-hidden="true"></div>

      <div
        class="relative flex flex-col w-full max-w-lg max-h-[90dvh] rounded-2xl bg-white dark:bg-stone-900 border border-stone-200 dark:border-stone-800 shadow-2xl overflow-hidden"
        (click)="$event.stopPropagation()"
      >
        <!-- En-tête -->
        <div class="flex items-start justify-between px-5 py-4 border-b border-stone-200 dark:border-stone-700 shrink-0">
          <div>
            <h2 class="text-base font-semibold text-stone-900 dark:text-stone-100">Bilan de la semaine</h2>
            <p class="mt-0.5 text-xs text-stone-500 dark:text-stone-400">
              Recettes à découvrir planifiées la semaine du {{ formatWeekStart() }}
            </p>
          </div>
          <button
            (click)="close()"
            class="rounded-lg p-1.5 text-stone-400 dark:text-stone-500 hover:bg-stone-100 dark:hover:bg-stone-800 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500"
            aria-label="Fermer"
          >
            <svg class="h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <!-- Corps -->
        <div class="flex-1 overflow-y-auto px-5 py-4">
          @if (loading()) {
            <div class="flex items-center justify-center py-16">
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
            <div class="rounded-xl border border-stone-200 dark:border-stone-700 bg-stone-50 dark:bg-stone-800 p-6 text-sm text-stone-500 dark:text-stone-400">
              Aucune recette à découvrir n'était planifiée cette semaine-là.
            </div>
          }

          @if (items().length > 0) {
            <ul class="space-y-3" role="list">
              @for (item of items(); track item.recipeId) {
                <li class="flex items-center gap-4 rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 p-3 shadow-sm">
                  <div class="h-14 w-14 flex-shrink-0 overflow-hidden rounded-lg bg-stone-100 dark:bg-stone-800">
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
          }
        </div>

        @if (items().length > 0) {
          <div class="flex justify-end px-5 py-4 border-t border-stone-200 dark:border-stone-700 shrink-0">
            <button
              type="button"
              (click)="submit()"
              [disabled]="voteCount() === 0 || submitting()"
              class="inline-flex items-center gap-2 rounded-lg bg-forest-600 px-6 py-2.5 text-sm font-medium text-white shadow-sm hover:bg-forest-700 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-forest-600"
              [attr.aria-busy]="submitting()"
            >
              Valider mes votes ({{ voteCount() }})
            </button>
          </div>
        }
      </div>
    </div>
  `,
})
export class WeeklyReviewModalComponent implements OnInit {
  @Input({ required: true }) weekStart!: string;
  @Output() dismissed = new EventEmitter<void>();

  items = signal<WeeklyReviewItemDto[]>([]);
  votes = signal<Record<string, WeeklyReviewVote>>({});
  loading = signal(true);
  submitting = signal(false);
  error = signal<string | null>(null);

  constructor(
    private weeklyReviewService: WeeklyReviewService,
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

  onBackdropClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.close();
    }
  }

  close(): void {
    this.dismissed.emit();
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
        this.close();
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
}
