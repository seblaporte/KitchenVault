import { Component, ElementRef, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';
import {
  ChatService,
  PendingMealChangeDto,
  QuickActionDto,
  WeekConstraintsDto,
  WeeklyPlanChatRequest,
} from '@KitchenVault/api-client';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { heroSparkles, heroXMark, heroPaperAirplane } from '@ng-icons/heroicons/outline';
import { MarkdownComponent } from 'ngx-markdown';
import { WeekConstraintFormComponent } from './week-constraint-form/week-constraint-form.component';

interface DrawerMessage {
  role: 'user' | 'ai';
  content: string;
  pendingChanges?: PendingMealChangeDto[];
}

@Component({
  selector: 'app-weekly-plan-drawer',
  standalone: true,
  imports: [CommonModule, FormsModule, NgIconComponent, MarkdownComponent, WeekConstraintFormComponent],
  viewProviders: [provideIcons({ heroSparkles, heroXMark, heroPaperAirplane })],
  template: `
    <div
      class="fixed right-0 top-0 z-40 flex h-screen w-[380px] flex-col border-l border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 shadow-2xl animate-slide-in"
    >
      <!-- En-tête -->
      <div class="flex items-center justify-between px-5 py-4 border-b border-stone-200 dark:border-stone-700 shrink-0">
        <div class="flex items-center gap-2">
          <ng-icon name="heroSparkles" class="h-5 w-5 text-forest-600" aria-hidden="true" />
          <h2 class="text-base font-semibold text-stone-900 dark:text-stone-100">Planification de la semaine</h2>
        </div>
        <button
          (click)="close()"
          class="rounded-lg p-1.5 text-stone-400 dark:text-stone-500 hover:bg-stone-100 dark:hover:bg-stone-800 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500"
          aria-label="Fermer"
        >
          <ng-icon name="heroXMark" class="h-5 w-5" aria-hidden="true" />
        </button>
      </div>

      <!-- Corps scrollable -->
      <div #messagesContainer class="flex-1 overflow-y-auto px-4 py-4">
        @if (phase() === 'form') {
          <app-week-constraint-form (submitted)="onConstraintsSubmitted($event)" />
        } @else {
          <div class="space-y-4">
            @for (msg of messages(); track $index) {
              <div [class]="msg.role === 'user' ? 'flex justify-end' : 'flex justify-start'">
                <div [class]="msg.role === 'user'
                  ? 'max-w-[85%] rounded-2xl rounded-tr-sm bg-forest-600 px-4 py-2.5 text-sm text-white'
                  : 'max-w-[85%] space-y-2'">
                  @if (msg.role === 'ai') {
                    <markdown
                      [data]="msg.content"
                      class="block rounded-2xl rounded-tl-sm bg-stone-100 dark:bg-stone-800 px-4 py-2.5 text-sm text-stone-800 dark:text-stone-200 prose prose-sm dark:prose-invert max-w-none"
                    ></markdown>
                    @if (msg.pendingChanges && msg.pendingChanges.length > 0) {
                      <div class="mt-2 rounded-xl border border-amber-200 dark:border-amber-800 bg-amber-50 dark:bg-amber-950 p-3 space-y-1.5">
                        <p class="text-xs font-medium text-amber-800 dark:text-amber-200">Modifications proposées :</p>
                        @for (change of msg.pendingChanges; track change.date + change.mealType) {
                          <div class="flex items-center gap-2 text-xs text-amber-700 dark:text-amber-300 flex-wrap">
                            <span class="font-medium">{{ formatDayLabel(change.date) }} {{ change.mealType === 'LUNCH' ? 'Déj.' : 'Dîn.' }}</span>
                            <span aria-hidden="true">→</span>
                            <span>{{ change.recipeName }}</span>
                            @if (change.previousRecipeName) {
                              <span class="text-amber-500 dark:text-amber-400">(remplace {{ change.previousRecipeName }})</span>
                            }
                          </div>
                        }
                      </div>
                    }
                  } @else {
                    {{ msg.content }}
                  }
                </div>
              </div>
            }

            @if (loading()) {
              <div class="flex justify-start">
                <div class="rounded-2xl rounded-tl-sm bg-stone-100 dark:bg-stone-800 px-4 py-3 text-sm text-stone-600 dark:text-stone-400">
                  @if (messages().filter(m => m.role === 'ai').length === 0) {
                    Je compose votre menu…
                  }
                  <div class="flex items-center gap-1 mt-1.5" aria-label="L'IA réfléchit">
                    <span class="h-2 w-2 rounded-full bg-stone-400 dark:bg-stone-500 animate-bounce [animation-delay:-0.3s]"></span>
                    <span class="h-2 w-2 rounded-full bg-stone-400 dark:bg-stone-500 animate-bounce [animation-delay:-0.15s]"></span>
                    <span class="h-2 w-2 rounded-full bg-stone-400 dark:bg-stone-500 animate-bounce"></span>
                  </div>
                </div>
              </div>
            }

            @if (error()) {
              <div class="rounded-xl border border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-950 px-4 py-2.5 text-sm text-red-700 dark:text-red-300" role="alert">
                {{ error() }}
              </div>
            }
          </div>
        }
      </div>

      <!-- Chips d'actions rapides -->
      @if (phase() === 'chat' && quickActions().length > 0) {
        <div class="shrink-0 px-4 py-2 flex flex-wrap gap-2 border-t border-stone-200 dark:border-stone-700">
          @for (action of quickActions(); track action.action) {
            <button
              (click)="sendQuickAction(action)"
              [disabled]="loading()"
              class="rounded-full border border-stone-300 dark:border-stone-600 px-3 py-1 text-xs text-stone-700 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {{ action.label }}
            </button>
          }
        </div>
      }

      <!-- Saisie -->
      @if (phase() === 'chat') {
        <div class="shrink-0 border-t border-stone-200 dark:border-stone-700 px-4 py-3">
          <form (ngSubmit)="sendMessage()" class="flex items-end gap-2">
            <textarea
              #inputField
              [(ngModel)]="inputText"
              name="message"
              rows="2"
              [disabled]="loading()"
              (keydown.enter)="onEnterKey($event)"
              placeholder="Votre message…"
              class="flex-1 resize-none rounded-xl border border-stone-300 dark:border-stone-600 bg-white dark:bg-stone-800 px-3 py-2 text-sm text-stone-900 dark:text-stone-100 placeholder:text-stone-400 focus:outline-none focus:ring-2 focus:ring-forest-500 disabled:opacity-50"
              aria-label="Votre message"
            ></textarea>
            <button
              type="submit"
              [disabled]="loading() || !inputText.trim()"
              class="shrink-0 rounded-xl bg-forest-600 p-2.5 text-white hover:bg-forest-700 transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline-2 focus-visible:outline-forest-600"
              aria-label="Envoyer"
            >
              <ng-icon name="heroPaperAirplane" class="h-5 w-5" aria-hidden="true" />
            </button>
          </form>
        </div>
      }
    </div>
  `,
  styles: [`
    @keyframes slideIn {
      from { transform: translateX(100%); }
      to { transform: translateX(0); }
    }
    .animate-slide-in {
      animation: slideIn 0.3s ease-out;
    }
  `],
})
export class WeeklyPlanDrawerComponent implements OnChanges {
  @Input({ required: true }) weekStart!: string;

  @Output() dismissed = new EventEmitter<boolean>();
  @Output() planChanged = new EventEmitter<void>();

  @ViewChild('messagesContainer') messagesContainer?: ElementRef<HTMLDivElement>;
  @ViewChild('inputField') inputField?: ElementRef<HTMLTextAreaElement>;

  phase = signal<'form' | 'chat'>('form');
  messages = signal<DrawerMessage[]>([]);
  quickActions = signal<QuickActionDto[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  inputText = '';

  private sessionId = crypto.randomUUID();
  private planModified = false;

  constructor(private chatService: ChatService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['weekStart'] && !changes['weekStart'].firstChange) {
      this.resetSession();
    }
  }

  close(): void {
    this.dismissed.emit(this.planModified);
  }

  onEnterKey(event: Event): void {
    if (!(event as KeyboardEvent).shiftKey) {
      event.preventDefault();
      if (!this.loading() && this.inputText.trim()) {
        this.sendMessage();
      }
    }
  }

  onConstraintsSubmitted(constraints: WeekConstraintsDto | null): void {
    this.phase.set('chat');
    const message = constraints ? 'Génère mon menu avec ces contraintes.' : 'Génère mon menu.';
    const request: WeeklyPlanChatRequest = {
      sessionId: this.sessionId,
      weekStart: this.weekStart,
      message,
      constraints: constraints ?? undefined,
    };
    this.messages.update(msgs => [...msgs, { role: 'user', content: message }]);
    this.callApi(request);
  }

  sendMessage(): void {
    const text = this.inputText.trim();
    if (!text || this.loading()) return;
    this.inputText = '';
    this.sendMessageText(text);
  }

  sendQuickAction(action: QuickActionDto): void {
    this.sendMessageText(action.action, action.label);
  }

  formatDayLabel(dateStr: string): string {
    return new Date(dateStr + 'T00:00:00').toLocaleDateString('fr-FR', {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
    });
  }

  private sendMessageText(message: string, displayText?: string): void {
    this.error.set(null);
    this.messages.update(msgs => [...msgs, { role: 'user', content: displayText ?? message }]);
    this.quickActions.set([]);
    this.scrollToBottom();

    const request: WeeklyPlanChatRequest = {
      sessionId: this.sessionId,
      weekStart: this.weekStart,
      message,
    };
    this.callApi(request);
  }

  private callApi(request: WeeklyPlanChatRequest): void {
    this.loading.set(true);
    this.chatService.chatMealPlanWeek(request)
      .pipe(catchError((err: HttpErrorResponse) => {
        if (err.status === 422) {
          this.error.set('Vous n\'avez pas encore de recettes dans votre base. Ajoutez des recettes pour utiliser la planification IA.');
        } else {
          this.error.set('Impossible de contacter l\'assistant IA. Veuillez réessayer.');
        }
        return of(null);
      }))
      .subscribe(response => {
        this.loading.set(false);
        if (!response) return;

        this.messages.update(msgs => [...msgs, {
          role: 'ai',
          content: response.reply,
          pendingChanges: response.proposedChanges ?? undefined,
        }]);

        this.quickActions.set(response.quickActions ?? []);

        if (!response.proposedChanges || response.proposedChanges.length === 0) {
          this.planModified = true;
          this.planChanged.emit();
        }

        this.scrollToBottom();
      });
  }

  private resetSession(): void {
    this.sessionId = crypto.randomUUID();
    this.phase.set('form');
    this.messages.set([]);
    this.quickActions.set([]);
    this.error.set(null);
    this.planModified = false;
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      if (this.messagesContainer) {
        const el = this.messagesContainer.nativeElement;
        el.scrollTop = el.scrollHeight;
      }
    }, 0);
  }
}
