import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { heroSparkles } from '@ng-icons/heroicons/outline';
import { MealPlanEntryDto } from '@KitchenVault/api-client';

@Component({
  selector: 'app-meal-slot',
  standalone: true,
  imports: [CommonModule, RouterLink, NgIconComponent],
  providers: [provideIcons({ heroSparkles })],
  template: `
    <div class="rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 p-4 shadow-sm min-h-[100px]">
      <p class="mb-3 text-xs font-semibold uppercase tracking-wide text-stone-500 dark:text-stone-400">{{ label }}</p>

      @if (entry) {
        <!-- Recette planifiée -->
        <div class="flex items-start gap-3">
          <div class="shrink-0 w-14 h-14 overflow-hidden rounded-lg bg-stone-100 dark:bg-stone-800">
            @if (entry.recipeThumbnailUrl) {
              <img [src]="entry.recipeThumbnailUrl" [alt]="entry.recipeName" class="h-full w-full object-cover" loading="lazy" />
            } @else {
              <div class="flex h-full w-full items-center justify-center">
                <svg class="h-6 w-6 text-stone-300 dark:text-stone-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                </svg>
              </div>
            }
          </div>
          <div class="flex-1 min-w-0">
            @if (entry.recipeId) {
              <a [routerLink]="['/recipes', entry.recipeId]" class="block text-sm font-medium text-stone-900 dark:text-stone-100 hover:text-forest-600 dark:hover:text-forest-400 truncate transition-colors focus-visible:outline-2 focus-visible:outline-forest-500 rounded">
                {{ entry.recipeName }}
              </a>
            } @else {
              <p class="text-sm font-medium text-stone-400 dark:text-stone-500 truncate italic">{{ entry.recipeName }}</p>
            }
            @if (entry.recipeTotalTimeMinutes) {
              <p class="mt-0.5 text-xs text-stone-400 dark:text-stone-500">{{ entry.recipeTotalTimeMinutes }} min</p>
            }
          </div>
          <button
            (click)="onRemove()"
            class="flex-shrink-0 rounded-lg p-1.5 text-stone-400 dark:text-stone-500 hover:bg-red-50 dark:hover:bg-red-950 hover:text-red-500 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500"
            aria-label="Supprimer ce repas"
          >
            <svg class="h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

      } @else {
        <!-- Créneau vide -->
        <div class="flex items-center gap-2">
          <button
            (click)="onAdd()"
            class="inline-flex items-center gap-1.5 rounded-lg border border-stone-300 dark:border-stone-600 px-3 py-1.5 text-sm text-stone-500 dark:text-stone-400 hover:border-forest-400 hover:text-forest-600 dark:hover:text-forest-400 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500"
            aria-label="Ajouter une recette"
          >
            <svg class="h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
            Ajouter
          </button>
          <button
            (click)="onChatRequested()"
            class="inline-flex items-center gap-1.5 rounded-lg border border-stone-200 dark:border-stone-700 px-3 py-1.5 text-sm text-stone-600 dark:text-stone-400 hover:bg-stone-50 dark:hover:bg-stone-800 cursor-pointer transition-colors focus-visible:outline-2 focus-visible:outline-forest-500"
            aria-label="Suggérer une recette via l'assistant IA"
          >
            <ng-icon name="heroSparkles" class="h-3.5 w-3.5" aria-hidden="true" />
            Suggérer
          </button>
        </div>
      }
    </div>
  `,
})
export class MealSlotComponent {
  @Input() entry: MealPlanEntryDto | null | undefined;
  @Input({ required: true }) date!: string;
  @Input({ required: true }) mealType!: string;
  @Input({ required: true }) label!: string;

  @Output() addRequested = new EventEmitter<{ date: string; mealType: string }>();
  @Output() removeRequested = new EventEmitter<{ date: string; mealType: string }>();
  @Output() chatRequested = new EventEmitter<{ date: string; mealType: string; label: string }>();

  onAdd(): void {
    this.addRequested.emit({ date: this.date, mealType: this.mealType });
  }

  onRemove(): void {
    this.removeRequested.emit({ date: this.date, mealType: this.mealType });
  }

  onChatRequested(): void {
    this.chatRequested.emit({ date: this.date, mealType: this.mealType, label: this.label });
  }
}
