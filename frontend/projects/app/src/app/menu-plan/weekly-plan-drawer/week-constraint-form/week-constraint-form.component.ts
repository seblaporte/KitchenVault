import { Component, ElementRef, ViewChild, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import {
  heroCalendarDays,
  heroListBullet,
  heroFire,
  heroDocumentText,
  heroXCircle,
} from '@ng-icons/heroicons/outline';
import { WeekConstraintsDto } from '@KitchenVault/api-client';

const WEEK_DAYS_ALL = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

const INGREDIENT_SUGGESTIONS = ['Poulet', 'Saumon', 'Courgettes', 'Lentilles', 'Tomates', 'Oeufs', 'Riz', 'Pâtes'];

@Component({
  selector: 'app-week-constraint-form',
  standalone: true,
  imports: [CommonModule, FormsModule, NgIconComponent],
  viewProviders: [provideIcons({ heroCalendarDays, heroListBullet, heroFire, heroDocumentText, heroXCircle })],
  template: `
    <div class="space-y-6">

      <!-- Jours sans repas -->
      <div class="space-y-2.5">
        <div class="flex items-center gap-2">
          <ng-icon name="heroCalendarDays" class="h-3.5 w-3.5 text-stone-400" aria-hidden="true" />
          <span class="text-[10.5px] font-semibold uppercase tracking-widest text-stone-400 dark:text-stone-500">Jours sans repas planifié</span>
        </div>
        <div class="flex flex-wrap gap-1.5">
          @for (day of DAYS; track day.value) {
            <button
              type="button"
              (click)="toggleAbsence(day.value)"
              [ngClass]="absenceDays().includes(day.value)
                ? 'bg-forest-50 dark:bg-forest-950 border-forest-400 dark:border-forest-600 text-forest-700 dark:text-forest-300'
                : 'bg-stone-100 dark:bg-stone-800 border-stone-200 dark:border-stone-700 text-stone-500 dark:text-stone-400 hover:border-stone-300 dark:hover:border-stone-600 hover:text-stone-700 dark:hover:text-stone-200'"
              class="rounded-full border px-3 py-1.5 text-xs font-medium transition-all cursor-pointer select-none focus-visible:outline-2 focus-visible:outline-forest-500"
              [attr.aria-pressed]="absenceDays().includes(day.value)"
              [attr.aria-label]="day.label"
            >{{ day.abbr }}</button>
          }
        </div>
      </div>

      <!-- Ingrédients à écouler -->
      <div class="space-y-2.5">
        <div class="flex items-center gap-2">
          <ng-icon name="heroListBullet" class="h-3.5 w-3.5 text-stone-400" aria-hidden="true" />
          <span class="text-[10.5px] font-semibold uppercase tracking-widest text-stone-400 dark:text-stone-500">Ingrédients à écouler</span>
        </div>
        <div
          class="flex flex-wrap gap-1.5 min-h-[44px] rounded-xl border border-stone-200 dark:border-stone-700 bg-stone-50 dark:bg-stone-800 px-3 py-2.5 cursor-text transition-colors focus-within:border-forest-400 dark:focus-within:border-forest-600"
          (click)="tagInput.focus()"
        >
          @for (tag of ingredientTags(); track tag) {
            <span class="inline-flex items-center gap-1 rounded-full border border-forest-200 dark:border-forest-800 bg-forest-50 dark:bg-forest-950 px-2.5 py-0.5 text-xs text-forest-700 dark:text-forest-300">
              {{ tag }}
              <button
                type="button"
                (click)="$event.stopPropagation(); removeTag(tag)"
                class="ml-0.5 flex h-3.5 w-3.5 items-center justify-center rounded-full bg-forest-100 dark:bg-forest-900 text-forest-600 dark:text-forest-400 hover:bg-forest-200 dark:hover:bg-forest-800 transition-colors cursor-pointer"
                [attr.aria-label]="'Supprimer ' + tag"
              >×</button>
            </span>
          }
          <input
            #tagInput
            type="text"
            [(ngModel)]="ingredientInput"
            name="ingredientInput"
            (keydown)="onTagKeydown($event)"
            (blur)="addTag(ingredientInput)"
            [placeholder]="ingredientTags().length === 0 ? 'Ex : crevettes, steak haché…' : ''"
            class="flex-1 min-w-[120px] bg-transparent text-sm text-stone-900 dark:text-stone-100 placeholder:text-stone-400 outline-none"
            aria-label="Ajouter un ingrédient"
          />
        </div>
        <div class="flex flex-wrap gap-1.5">
          @for (suggestion of filteredSuggestions(); track suggestion) {
            <button
              type="button"
              (click)="addTag(suggestion)"
              class="rounded-full border border-stone-200 dark:border-stone-700 bg-stone-100 dark:bg-stone-800 px-2.5 py-1 text-xs text-stone-500 dark:text-stone-400 hover:text-stone-700 dark:hover:text-stone-200 hover:border-stone-300 dark:hover:border-stone-600 transition-colors cursor-pointer"
            >{{ suggestion }}</button>
          }
        </div>
      </div>

      <!-- Mode de cuisson -->
      <div class="space-y-2.5">
        <div class="flex items-center gap-2">
          <ng-icon name="heroFire" class="h-3.5 w-3.5 text-stone-400" aria-hidden="true" />
          <span class="text-[10.5px] font-semibold uppercase tracking-widest text-stone-400 dark:text-stone-500">Mode de cuisson</span>
        </div>
        <div class="flex overflow-hidden rounded-xl border border-stone-200 dark:border-stone-700 bg-stone-100 dark:bg-stone-800">
          <button
            type="button"
            (click)="ovenMode.set('all')"
            [ngClass]="ovenMode() === 'all'
              ? 'bg-white dark:bg-stone-700 text-stone-900 dark:text-stone-100 shadow-sm'
              : 'text-stone-500 dark:text-stone-400 hover:text-stone-700 dark:hover:text-stone-200'"
            class="flex-1 flex items-center justify-center gap-1.5 py-2.5 text-xs font-medium transition-all cursor-pointer"
            aria-pressed="ovenMode() === 'all'"
          >🔥 Toutes recettes</button>
          <button
            type="button"
            (click)="ovenMode.set('light')"
            [ngClass]="ovenMode() === 'light'
              ? 'bg-white dark:bg-stone-700 text-stone-900 dark:text-stone-100 shadow-sm'
              : 'text-stone-500 dark:text-stone-400 hover:text-stone-700 dark:hover:text-stone-200'"
            class="flex-1 flex items-center justify-center gap-1.5 py-2.5 text-xs font-medium transition-all cursor-pointer border-l border-stone-200 dark:border-stone-700"
            aria-pressed="ovenMode() === 'light'"
          >⚡ Cuissons légères</button>
        </div>
      </div>

      <!-- Remarques libres -->
      <div class="space-y-2.5">
        <div class="flex items-center gap-2">
          <ng-icon name="heroDocumentText" class="h-3.5 w-3.5 text-stone-400" aria-hidden="true" />
          <span class="text-[10.5px] font-semibold uppercase tracking-widest text-stone-400 dark:text-stone-500">Remarques libres</span>
        </div>
        <textarea
          id="freeText"
          rows="3"
          [(ngModel)]="freeText"
          name="freeText"
          placeholder="Préférence végétarienne ce soir, pas de plats trop lourds…"
          class="w-full resize-none rounded-xl border border-stone-200 dark:border-stone-700 bg-stone-50 dark:bg-stone-800 px-3 py-2.5 text-sm text-stone-900 dark:text-stone-100 placeholder:text-stone-400 focus:outline-none focus:border-forest-400 dark:focus:border-forest-600 transition-colors"
          aria-label="Remarques libres"
        ></textarea>
      </div>

    </div>
  `,
})
export class WeekConstraintFormComponent {
  @ViewChild('tagInput') tagInput?: ElementRef<HTMLInputElement>;

  readonly DAYS = [
    { label: 'Lundi', abbr: 'Lun', value: 'MONDAY' },
    { label: 'Mardi', abbr: 'Mar', value: 'TUESDAY' },
    { label: 'Mercredi', abbr: 'Mer', value: 'WEDNESDAY' },
    { label: 'Jeudi', abbr: 'Jeu', value: 'THURSDAY' },
    { label: 'Vendredi', abbr: 'Ven', value: 'FRIDAY' },
    { label: 'Samedi', abbr: 'Sam', value: 'SATURDAY' },
    { label: 'Dimanche', abbr: 'Dim', value: 'SUNDAY' },
  ];

  absenceDays = signal<string[]>([]);
  ovenMode = signal<'all' | 'light'>('all');
  ingredientTags = signal<string[]>([]);
  ingredientInput = '';
  freeText = '';

  filteredSuggestions() {
    const tags = this.ingredientTags();
    return INGREDIENT_SUGGESTIONS.filter(s => !tags.includes(s)).slice(0, 6);
  }

  toggleAbsence(value: string): void {
    this.absenceDays.update(days =>
      days.includes(value) ? days.filter(d => d !== value) : [...days, value]
    );
  }

  addTag(val: string): void {
    const v = val.trim();
    if (v && !this.ingredientTags().includes(v)) {
      this.ingredientTags.update(tags => [...tags, v]);
    }
    this.ingredientInput = '';
  }

  removeTag(tag: string): void {
    this.ingredientTags.update(tags => tags.filter(t => t !== tag));
  }

  onTagKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      this.addTag(this.ingredientInput);
    }
    if (event.key === 'Backspace' && !this.ingredientInput && this.ingredientTags().length > 0) {
      this.ingredientTags.update(tags => tags.slice(0, -1));
    }
  }

  getConstraints(): WeekConstraintsDto | null {
    const constraints: WeekConstraintsDto = {
      absenceDays: this.absenceDays().length > 0 ? this.absenceDays() : undefined,
      thermalConstraintDays: this.ovenMode() === 'light' ? WEEK_DAYS_ALL : undefined,
      priorityIngredients: this.ingredientTags().length > 0 ? this.ingredientTags().join(', ') : undefined,
      freeText: this.freeText.trim() || undefined,
    };
    const hasAny = Object.values(constraints).some(v => v !== undefined);
    return hasAny ? constraints : null;
  }
}
