import { Component, inject } from '@angular/core';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { heroCheckCircle, heroXCircle, heroXMark } from '@ng-icons/heroicons/outline';
import { ToastService } from './toast.service';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [NgIconComponent],
  viewProviders: [provideIcons({ heroCheckCircle, heroXCircle, heroXMark })],
  template: `
    @if (toast.current(); as current) {
      <div
        class="fixed top-4 right-4 z-[200] flex items-start gap-3 rounded-xl border px-4 py-3 shadow-lg max-w-xs animate-toast-in"
        [class]="current.type === 'success'
          ? 'bg-emerald-50 dark:bg-emerald-950 border-emerald-200 dark:border-emerald-800'
          : 'bg-red-50 dark:bg-red-950 border-red-200 dark:border-red-800'"
        role="status"
        aria-live="polite"
      >
        <ng-icon
          [name]="current.type === 'success' ? 'heroCheckCircle' : 'heroXCircle'"
          class="h-5 w-5 mt-0.5 shrink-0"
          [class]="current.type === 'success' ? 'text-emerald-500' : 'text-red-500'"
          aria-hidden="true"
        />
        <div class="flex-1 min-w-0">
          <p class="text-sm font-medium"
             [class]="current.type === 'success' ? 'text-emerald-800 dark:text-emerald-200' : 'text-red-800 dark:text-red-200'">
            {{ current.title }}
          </p>
          @if (current.message) {
            <p class="text-xs mt-0.5"
               [class]="current.type === 'success' ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400'">
              {{ current.message }}
            </p>
          }
        </div>
        <button
          (click)="toast.dismiss()"
          class="text-stone-400 hover:text-stone-600 dark:hover:text-stone-300 transition-colors cursor-pointer shrink-0"
          aria-label="Fermer"
        >
          <ng-icon name="heroXMark" class="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
    }
  `,
})
export class ToastComponent {
  toast = inject(ToastService);
}
