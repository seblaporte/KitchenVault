import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MealSlotComponent } from '../meal-slot/meal-slot.component';
import { DayPlanDto } from '@KitchenVault/api-client';

@Component({
  selector: 'app-menu-day',
  standalone: true,
  imports: [CommonModule, MealSlotComponent],
  template: `
    <div class="rounded-2xl border border-stone-200 dark:border-stone-700 bg-stone-50 dark:bg-stone-900 p-4 shadow-sm">
      <h2 class="mb-4 text-sm font-semibold capitalize text-stone-700 dark:text-stone-300">{{ dayLabel }}</h2>
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <app-meal-slot
          [entry]="dayPlan.lunch"
          [date]="dayPlan.date"
          mealType="LUNCH"
          label="Déjeuner"
          (addRequested)="addRequested.emit($event)"
          (removeRequested)="removeRequested.emit($event)"
          (chatRequested)="chatRequested.emit($event)"
        />
        <app-meal-slot
          [entry]="dayPlan.dinner"
          [date]="dayPlan.date"
          mealType="DINNER"
          label="Dîner"
          (addRequested)="addRequested.emit($event)"
          (removeRequested)="removeRequested.emit($event)"
          (chatRequested)="chatRequested.emit($event)"
        />
      </div>
    </div>
  `,
})
export class MenuDayComponent {
  @Input({ required: true }) dayPlan!: DayPlanDto;

  @Output() addRequested = new EventEmitter<{ date: string; mealType: string }>();
  @Output() removeRequested = new EventEmitter<{ date: string; mealType: string }>();
  @Output() chatRequested = new EventEmitter<{ date: string; mealType: string; label: string }>();

  get dayLabel(): string {
    return new Date(this.dayPlan.date + 'T00:00:00').toLocaleDateString('fr-FR', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    });
  }
}
