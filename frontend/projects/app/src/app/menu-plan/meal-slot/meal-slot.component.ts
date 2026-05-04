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
    @if (entry) {
      <div class="group relative rounded-xl border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 overflow-hidden hover:-translate-y-0.5 hover:border-stone-300 dark:hover:border-stone-600 hover:shadow-md transition-all">
        <div class="w-full h-24 bg-stone-100 dark:bg-stone-800 overflow-hidden flex items-center justify-center">
          @if (entry.recipeThumbnailUrl) {
            <img [src]="entry.recipeThumbnailUrl" [alt]="entry.recipeName" class="w-full h-full object-cover" loading="lazy" />
          } @else {
            <svg class="h-8 w-8 text-stone-300 dark:text-stone-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
            </svg>
          }
        </div>
        <div class="p-2.5 flex flex-col gap-1">
          @if (entry.recipeId) {
            <a
              [routerLink]="['/recipes', entry.recipeId]"
              (click)="$event.stopPropagation()"
              class="block text-xs font-medium text-stone-800 dark:text-stone-200 hover:text-forest-600 dark:hover:text-forest-400 line-clamp-2 leading-snug transition-colors focus-visible:outline-2 focus-visible:outline-forest-500 rounded"
            >{{ entry.recipeName }}</a>
          } @else {
            <p class="text-xs font-medium text-stone-400 dark:text-stone-500 line-clamp-2 leading-snug italic">{{ entry.recipeName }}</p>
          }
          @if (entry.recipeTotalTimeMinutes) {
            <p class="flex items-center gap-1 text-xs text-stone-400 dark:text-stone-500">
              <svg class="h-3 w-3 flex-shrink-0" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 16 16" stroke="currentColor" aria-hidden="true">
                <circle cx="8" cy="8" r="6" stroke-width="1.3"/>
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.3" d="M8 5v3.5l2 2"/>
              </svg>
              {{ entry.recipeTotalTimeMinutes }} min
            </p>
          }
        </div>
        <button
          (click)="$event.stopPropagation(); onRemove()"
          class="absolute top-1.5 right-1.5 w-6 h-6 rounded-md bg-black/40 text-white/70 flex items-center justify-center opacity-0 group-hover:opacity-100 hover:bg-red-500 hover:text-white transition-all backdrop-blur-sm cursor-pointer"
          aria-label="Supprimer ce repas"
        >
          <svg class="h-3.5 w-3.5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
    } @else {
      <div class="group relative w-full min-h-[130px] rounded-xl border-2 border-dashed border-stone-200 dark:border-stone-700 transition-colors hover:border-forest-400 dark:hover:border-forest-600 hover:bg-forest-50 dark:hover:bg-forest-950/30">
        <button
          (click)="onAdd()"
          class="absolute inset-0 w-full h-full rounded-xl flex flex-col items-center justify-center gap-1.5 cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500"
          [attr.aria-label]="'Ajouter une recette pour ' + label"
        >
          <svg class="h-4 w-4 text-stone-300 dark:text-stone-600 group-hover:text-forest-500 dark:group-hover:text-forest-400 transition-colors" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          <span class="text-xs text-stone-400 dark:text-stone-600 group-hover:text-forest-500 dark:group-hover:text-forest-400 transition-colors">Ajouter</span>
        </button>
        <button
          (click)="$event.stopPropagation(); onChatRequested()"
          class="absolute bottom-1.5 right-1.5 z-10 w-6 h-6 rounded-md border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 text-stone-400 dark:text-stone-500 flex items-center justify-center opacity-0 group-hover:opacity-100 hover:border-forest-400 hover:text-forest-600 dark:hover:text-forest-400 transition-all cursor-pointer"
          [attr.aria-label]="'Suggérer une recette IA pour ' + label"
        >
          <ng-icon name="heroSparkles" class="h-3 w-3" aria-hidden="true" />
        </button>
      </div>
    }
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
