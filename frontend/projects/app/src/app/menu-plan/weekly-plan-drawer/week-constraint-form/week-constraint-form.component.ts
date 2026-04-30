import { Component, EventEmitter, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WeekConstraintsDto } from '@KitchenVault/api-client';

@Component({
  selector: 'app-week-constraint-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <form (ngSubmit)="submit()" class="space-y-5">
      <p class="text-sm text-stone-600 dark:text-stone-400">
        Renseignez vos contraintes pour la semaine (tous les champs sont optionnels).
      </p>

      <fieldset>
        <legend class="text-xs font-medium text-stone-700 dark:text-stone-300 mb-2">
          Jours d'absence (aucun repas planifié)
        </legend>
        <div class="flex flex-wrap gap-x-3 gap-y-2">
          @for (day of DAYS; track day.value) {
            <label class="flex items-center gap-1.5 cursor-pointer select-none">
              <input
                type="checkbox"
                [checked]="absenceDays().includes(day.value)"
                (change)="toggleDay('absence', day.value)"
                class="rounded border-stone-300 dark:border-stone-600 text-forest-600 focus:ring-forest-500"
              />
              <span class="text-xs text-stone-600 dark:text-stone-400">{{ day.label }}</span>
            </label>
          }
        </div>
      </fieldset>

      <div>
        <label for="ingredients" class="block text-xs font-medium text-stone-700 dark:text-stone-300 mb-1">
          Ingrédients à utiliser en priorité
        </label>
        <input
          id="ingredients"
          type="text"
          [(ngModel)]="priorityIngredients"
          name="ingredients"
          placeholder="Ex : crevettes à cuisiner dans 2 jours, steak haché à finir"
          class="w-full rounded-xl border border-stone-300 dark:border-stone-600 bg-white dark:bg-stone-800 px-3 py-2 text-sm text-stone-900 dark:text-stone-100 placeholder:text-stone-400 focus:outline-none focus:ring-2 focus:ring-forest-500"
        />
      </div>

      <fieldset>
        <legend class="text-xs font-medium text-stone-700 dark:text-stone-300 mb-2">
          Jours sans four (cuissons légères uniquement)
        </legend>
        <div class="flex flex-wrap gap-x-3 gap-y-2">
          @for (day of DAYS; track day.value) {
            <label class="flex items-center gap-1.5 cursor-pointer select-none">
              <input
                type="checkbox"
                [checked]="thermalConstraintDays().includes(day.value)"
                (change)="toggleDay('thermal', day.value)"
                class="rounded border-stone-300 dark:border-stone-600 text-forest-600 focus:ring-forest-500"
              />
              <span class="text-xs text-stone-600 dark:text-stone-400">{{ day.label }}</span>
            </label>
          }
        </div>
      </fieldset>

      <div>
        <label for="freeText" class="block text-xs font-medium text-stone-700 dark:text-stone-300 mb-1">
          Remarques libres
        </label>
        <textarea
          id="freeText"
          rows="2"
          [(ngModel)]="freeText"
          name="freeText"
          placeholder="Pas de plats trop lourds, préférence végétarienne ce soir…"
          class="w-full resize-none rounded-xl border border-stone-300 dark:border-stone-600 bg-white dark:bg-stone-800 px-3 py-2 text-sm text-stone-900 dark:text-stone-100 placeholder:text-stone-400 focus:outline-none focus:ring-2 focus:ring-forest-500"
        ></textarea>
      </div>

      <div class="flex items-center gap-3 pt-1">
        <button
          type="submit"
          class="flex-1 rounded-xl bg-forest-600 py-2 text-sm font-medium text-white hover:bg-forest-700 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-600"
        >
          Générer mon menu
        </button>
        <button
          type="button"
          (click)="skip()"
          class="text-sm text-stone-500 dark:text-stone-400 hover:text-stone-700 dark:hover:text-stone-200 transition-colors cursor-pointer underline"
        >
          Passer les contraintes
        </button>
      </div>
    </form>
  `,
})
export class WeekConstraintFormComponent {
  @Output() submitted = new EventEmitter<WeekConstraintsDto | null>();

  readonly DAYS = [
    { label: 'Lundi', value: 'MONDAY' },
    { label: 'Mardi', value: 'TUESDAY' },
    { label: 'Mercredi', value: 'WEDNESDAY' },
    { label: 'Jeudi', value: 'THURSDAY' },
    { label: 'Vendredi', value: 'FRIDAY' },
    { label: 'Samedi', value: 'SATURDAY' },
    { label: 'Dimanche', value: 'SUNDAY' },
  ];

  absenceDays = signal<string[]>([]);
  thermalConstraintDays = signal<string[]>([]);
  priorityIngredients = '';
  freeText = '';

  toggleDay(type: 'absence' | 'thermal', value: string): void {
    const sig = type === 'absence' ? this.absenceDays : this.thermalConstraintDays;
    sig.update(days =>
      days.includes(value) ? days.filter(d => d !== value) : [...days, value]
    );
  }

  submit(): void {
    const constraints: WeekConstraintsDto = {
      absenceDays: this.absenceDays().length > 0 ? this.absenceDays() : undefined,
      thermalConstraintDays: this.thermalConstraintDays().length > 0 ? this.thermalConstraintDays() : undefined,
      priorityIngredients: this.priorityIngredients.trim() || undefined,
      freeText: this.freeText.trim() || undefined,
    };
    const hasAny = Object.values(constraints).some(v => v !== undefined);
    this.submitted.emit(hasAny ? constraints : null);
  }

  skip(): void {
    this.submitted.emit(null);
  }
}
