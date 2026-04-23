import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MealSlotComponent } from '../meal-slot/meal-slot.component';
import { DayPlanDto } from '@KitchenVault/api-client';

@Component({
  selector: 'app-menu-day',
  standalone: true,
  imports: [CommonModule, MealSlotComponent],
  template: `
    <div class="rounded-2xl border border-zinc-200 bg-zinc-50 p-4 shadow-sm">
      <h2 class="mb-4 text-sm font-semibold capitalize text-zinc-700">{{ dayLabel }}</h2>
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <app-meal-slot
          [entry]="dayPlan.lunch"
          [date]="dayPlan.date"
          mealType="LUNCH"
          label="Déjeuner"
          (addRequested)="addRequested.emit($event)"
          (removeRequested)="removeRequested.emit($event)"
          (addSuggestion)="addSuggestion.emit($event)"
        />
        <app-meal-slot
          [entry]="dayPlan.dinner"
          [date]="dayPlan.date"
          mealType="DINNER"
          label="Dîner"
          (addRequested)="addRequested.emit($event)"
          (removeRequested)="removeRequested.emit($event)"
          (addSuggestion)="addSuggestion.emit($event)"
        />
      </div>
    </div>
  `,
})
export class MenuDayComponent {
  @Input({ required: true }) dayPlan!: DayPlanDto;

  @Output() addRequested = new EventEmitter<{ date: string; mealType: string }>();
  @Output() removeRequested = new EventEmitter<{ date: string; mealType: string }>();
  @Output() addSuggestion = new EventEmitter<{ date: string; mealType: string; recipeId: string }>();

  get dayLabel(): string {
    return new Date(this.dayPlan.date + 'T00:00:00').toLocaleDateString('fr-FR', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    });
  }
}
