import { Injectable, signal } from '@angular/core';

export interface Toast {
  type: 'success' | 'error';
  title: string;
  message?: string;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  current = signal<Toast | null>(null);
  private timer: ReturnType<typeof setTimeout> | null = null;

  show(toast: Toast, duration = 4000): void {
    if (this.timer) clearTimeout(this.timer);
    this.current.set(toast);
    this.timer = setTimeout(() => this.dismiss(), duration);
  }

  dismiss(): void {
    if (this.timer) { clearTimeout(this.timer); this.timer = null; }
    this.current.set(null);
  }
}
