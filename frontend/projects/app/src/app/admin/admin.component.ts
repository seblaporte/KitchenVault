import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { catchError, interval, of, Subscription, switchMap, startWith } from 'rxjs';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { heroArrowPath } from '@ng-icons/heroicons/outline';
import { environment } from '../../environments/environment';

interface SyncRun {
  id: string;
  startedAt: string;
  completedAt?: string;
  status: 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'FAILED';
  collectionsSynced?: number;
  recipesSynced?: number;
  errorMessage?: string;
}

interface AdminStats {
  recipeCount: number;
  collectionCount: number;
  lastSuccessfulSyncAt?: string;
}

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, NgIconComponent],
  providers: [provideIcons({ heroArrowPath })],
  template: `
    <div class="space-y-8">
      <h1 class="text-2xl font-semibold tracking-tight text-stone-900 dark:text-stone-100">Administration</h1>

      <!-- Stats cards -->
      <section aria-labelledby="stats-heading">
        <h2 id="stats-heading" class="sr-only">Statistiques de la base de données</h2>
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div class="rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 p-6">
            <p class="text-sm font-medium text-stone-500 dark:text-stone-400">Recettes en base</p>
            <p class="mt-2 text-3xl font-semibold text-stone-900 dark:text-stone-100" aria-live="polite">
              {{ stats()?.recipeCount ?? '—' }}
            </p>
          </div>
          <div class="rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 p-6">
            <p class="text-sm font-medium text-stone-500 dark:text-stone-400">Collections</p>
            <p class="mt-2 text-3xl font-semibold text-stone-900 dark:text-stone-100" aria-live="polite">
              {{ stats()?.collectionCount ?? '—' }}
            </p>
          </div>
          <div class="rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 p-6">
            <p class="text-sm font-medium text-stone-500 dark:text-stone-400">Dernière sync réussie</p>
            <p class="mt-2 text-sm font-medium text-stone-900 dark:text-stone-100" aria-live="polite">
              {{ formatDate(stats()?.lastSuccessfulSyncAt) }}
            </p>
          </div>
        </div>
      </section>

      <!-- Sync control -->
      <section
        class="rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 p-6"
        aria-labelledby="sync-heading"
      >
        <div class="flex items-start justify-between gap-4">
          <div>
            <h2 id="sync-heading" class="text-base font-semibold text-stone-900 dark:text-stone-100">
              Synchronisation Cookidoo
            </h2>
            <p class="mt-1 text-sm text-stone-500 dark:text-stone-400">
              Déclenche la récupération des recettes et collections depuis Cookidoo.
            </p>
          </div>
          <button
            type="button"
            (click)="triggerSync()"
            [disabled]="isSyncing()"
            class="inline-flex items-center gap-2 rounded-lg bg-forest-600 px-4 py-2 text-sm
                   font-medium text-white hover:bg-forest-700 cursor-pointer disabled:opacity-50
                   disabled:cursor-not-allowed transition-colors
                   focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-forest-600"
            [attr.aria-busy]="isSyncing()"
            aria-label="Lancer une synchronisation manuelle"
          >
            @if (isSyncing()) {
              <ng-icon name="heroArrowPath" class="h-4 w-4 animate-spin" aria-hidden="true" />
              Synchronisation…
            } @else {
              Synchroniser
            }
          </button>
        </div>

        <!-- Latest sync status -->
        @if (latestSync()) {
          <div class="mt-6 rounded-lg bg-stone-50 dark:bg-stone-800 p-4" role="status" aria-live="polite">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <span
                  class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
                  [ngClass]="statusClass(latestSync()!.status)"
                  [attr.aria-label]="'Statut : ' + latestSync()!.status"
                >
                  {{ latestSync()!.status }}
                </span>
                <span class="text-sm text-stone-700 dark:text-stone-300">
                  Démarrée {{ formatDate(latestSync()!.startedAt) }}
                </span>
              </div>
              @if (latestSync()!.completedAt) {
                <span class="text-sm text-stone-500 dark:text-stone-400">
                  Durée : {{ duration(latestSync()!.startedAt, latestSync()!.completedAt!) }}
                </span>
              }
            </div>

            @if (latestSync()!.status !== 'RUNNING' && latestSync()!.recipesSynced != null) {
              <div class="mt-3 flex gap-6 text-sm text-stone-600 dark:text-stone-400">
                <span>{{ latestSync()!.collectionsSynced }} collections</span>
                <span>{{ latestSync()!.recipesSynced }} recettes</span>
              </div>
            }

            @if (latestSync()!.errorMessage) {
              <p class="mt-3 text-sm text-red-600" role="alert">
                {{ latestSync()!.errorMessage }}
              </p>
            }
          </div>
        }
      </section>

      <!-- Embedding indexation -->
      <section
        class="rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 p-6"
        aria-labelledby="indexing-heading"
      >
        <div class="flex items-start justify-between gap-4">
          <div>
            <h2 id="indexing-heading" class="text-base font-semibold text-stone-900 dark:text-stone-100">
              Indexation des embeddings
            </h2>
            <p class="mt-1 text-sm text-stone-500 dark:text-stone-400">
              Réindexe les embeddings de toutes les recettes sans relancer une synchronisation Cookidoo.
            </p>
          </div>
          <button
            type="button"
            (click)="triggerIndexation()"
            [disabled]="isIndexing()"
            class="inline-flex items-center gap-2 rounded-lg bg-forest-600 px-4 py-2 text-sm
                   font-medium text-white hover:bg-forest-700 cursor-pointer disabled:opacity-50
                   disabled:cursor-not-allowed transition-colors
                   focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-forest-600"
            [attr.aria-busy]="isIndexing()"
            aria-label="Lancer une indexation manuelle des embeddings"
          >
            @if (isIndexing()) {
              <ng-icon name="heroArrowPath" class="h-4 w-4 animate-spin" aria-hidden="true" />
              Indexation…
            } @else {
              Indexer
            }
          </button>
        </div>

        @if (indexingMessage()) {
          <div
            class="mt-4 rounded-lg p-3 text-sm"
            [ngClass]="{
              'bg-emerald-50 text-emerald-800': indexingMessage()!.type === 'success',
              'bg-red-50 text-red-700': indexingMessage()!.type === 'error'
            }"
            role="status"
            aria-live="polite"
          >
            {{ indexingMessage()!.text }}
          </div>
        }
      </section>
    </div>
  `,
})
export class AdminComponent implements OnInit, OnDestroy {
  latestSync = signal<SyncRun | null>(null);
  stats = signal<AdminStats | null>(null);
  isSyncing = signal(false);
  isIndexing = signal(false);
  indexingMessage = signal<{ type: 'success' | 'error'; text: string } | null>(null);

  private pollSubscription?: Subscription;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadStats();
    this.startPolling();
  }

  ngOnDestroy(): void {
    this.pollSubscription?.unsubscribe();
  }

  triggerSync(): void {
    if (this.isSyncing()) return;

    this.http.post<SyncRun>(`${environment.apiUrl}/api/v1/sync`, {}).subscribe({
      next: run => {
        this.latestSync.set(run);
        this.isSyncing.set(true);
      },
      error: err => console.error('Sync trigger failed', err),
    });
  }

  private startPolling(): void {
    this.pollSubscription = interval(5000)
      .pipe(
        startWith(0),
        switchMap(() =>
          this.http.get<SyncRun>(`${environment.apiUrl}/api/v1/sync/latest`).pipe(
            catchError(err => {
              if (err.status === 404) return of(null);
              throw err;
            })
          )
        )
      )
      .subscribe({
        next: run => {
          this.latestSync.set(run);
          this.isSyncing.set(run?.status === 'RUNNING');
          if (run && run.status !== 'RUNNING') {
            this.loadStats();
          }
        },
        error: () => this.isSyncing.set(false),
      });
  }

  triggerIndexation(): void {
    if (this.isIndexing()) return;
    this.isIndexing.set(true);
    this.indexingMessage.set(null);
    this.http.post(`${environment.apiUrl}/api/v1/embeddings/index`, {}).subscribe({
      next: () => {
        this.isIndexing.set(false);
        this.indexingMessage.set({ type: 'success', text: 'Indexation démarrée.' });
      },
      error: err => {
        this.isIndexing.set(false);
        this.indexingMessage.set({ type: 'error', text: 'Erreur lors du déclenchement.' });
        console.error('Indexation trigger failed', err);
      },
    });
  }

  private loadStats(): void {
    this.http
      .get<AdminStats>(`${environment.apiUrl}/api/v1/admin/stats`)
      .subscribe({ next: stats => this.stats.set(stats) });
  }

  statusClass(status: string): Record<string, boolean> {
    return {
      'bg-amber-100 text-amber-800': status === 'RUNNING',
      'bg-emerald-100 text-emerald-800': status === 'SUCCESS',
      'bg-red-100 text-red-800': status === 'FAILED',
      'bg-orange-100 text-orange-800': status === 'PARTIAL',
    };
  }

  formatDate(iso?: string): string {
    if (!iso) return '—';
    return new Intl.DateTimeFormat('fr-FR', {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(iso));
  }

  duration(start: string, end: string): string {
    const secs = Math.round((new Date(end).getTime() - new Date(start).getTime()) / 1000);
    if (secs < 60) return `${secs}s`;
    return `${Math.floor(secs / 60)}m ${secs % 60}s`;
  }
}
