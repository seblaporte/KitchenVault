import { Component, ElementRef, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, ViewChild, signal } from '@angular/core';
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
import { heroSparkles, heroXMark, heroPaperAirplane, heroCheck } from '@ng-icons/heroicons/outline';
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
  viewProviders: [provideIcons({ heroSparkles, heroXMark, heroPaperAirplane, heroCheck })],
  template: `
    <div
      class="fixed right-0 top-0 z-40 flex h-screen w-[400px] flex-col border-l border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-900 shadow-2xl animate-slide-in"
    >
      <!-- En-tête commun à toutes les phases -->
      <div class="shrink-0 flex items-center gap-3 px-5 py-4 border-b border-stone-200 dark:border-stone-700">
        <div class="shrink-0 flex h-9 w-9 items-center justify-center rounded-xl border border-forest-200 dark:border-forest-800 bg-forest-50 dark:bg-forest-950">
          <ng-icon name="heroSparkles" class="h-4 w-4 text-forest-600 dark:text-forest-400" aria-hidden="true"></ng-icon>
        </div>
        <div class="flex-1 min-w-0">
          <h2 class="text-[15px] font-semibold leading-tight text-stone-900 dark:text-stone-100">Planification de la semaine</h2>
          <p class="mt-0.5 text-xs text-stone-400 dark:text-stone-500 leading-snug">
            @if (phase() === 'form') {
              Renseignez vos contraintes — tous les champs sont optionnels.
            } @else if (phase() === 'generating') {
              L'IA compose votre menu personnalisé…
            } @else {
              Votre menu est prêt. Continuez à affiner.
            }
          </p>
        </div>
        <button
          (click)="close()"
          class="shrink-0 flex h-8 w-8 items-center justify-center rounded-lg bg-stone-100 dark:bg-stone-800 text-stone-400 dark:text-stone-500 hover:bg-stone-200 dark:hover:bg-stone-700 hover:text-stone-700 dark:hover:text-stone-200 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500"
          aria-label="Fermer"
        >
          <ng-icon name="heroXMark" class="h-4 w-4" aria-hidden="true"></ng-icon>
        </button>
      </div>

      <!-- Corps scrollable -->
      <div #messagesContainer class="flex-1 overflow-y-auto min-h-0 px-5 py-6">

        @if (phase() === 'form') {
          <app-week-constraint-form #constraintForm></app-week-constraint-form>
        }

        @if (phase() === 'generating') {
          <div class="flex h-full min-h-[300px] flex-col items-center justify-center gap-8">
            <div class="h-20 w-20 rounded-full bg-gradient-to-br from-forest-400 to-forest-700 shadow-lg shadow-forest-600/30 dark:shadow-forest-900/50 animate-orb-pulse" aria-hidden="true"></div>
            <div class="w-full space-y-2">
              @for (step of GEN_STEPS; track step.label; let i = $index) {
                <div [ngClass]="stepRowClass(i)" class="flex items-center gap-3 rounded-xl p-3 transition-all duration-300">
                  <div [ngClass]="stepIconClass(i)" class="shrink-0 flex h-7 w-7 items-center justify-center rounded-lg text-sm">{{ step.emoji }}</div>
                  <div class="flex-1 min-w-0">
                    <p [ngClass]="stepLabelClass(i)" class="text-sm font-medium leading-tight">{{ step.label }}</p>
                    @if (getStepState(i) !== 'pending') {
                      <p class="mt-0.5 text-xs text-stone-400 dark:text-stone-500">{{ step.sub }}</p>
                    }
                  </div>
                  @if (getStepState(i) === 'done') {
                    <ng-icon name="heroCheck" class="shrink-0 h-4 w-4 text-forest-600 dark:text-forest-400" aria-hidden="true"></ng-icon>
                  }
                  @if (getStepState(i) === 'active') {
                    <svg class="shrink-0 h-4 w-4 animate-spin text-forest-600 dark:text-forest-400" viewBox="0 0 16 16" fill="none" aria-label="En cours">
                      <circle cx="8" cy="8" r="6" stroke="currentColor" stroke-width="1.5" stroke-dasharray="20 18" stroke-linecap="round"></circle>
                    </svg>
                  }
                </div>
              }
            </div>
          </div>
        }

        @if (phase() === 'chat') {
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
                      <div class="rounded-xl border border-amber-200 dark:border-amber-800 bg-amber-50 dark:bg-amber-950/50 p-3 space-y-2.5">
                        <p class="text-xs font-semibold uppercase tracking-wider text-amber-700 dark:text-amber-400">Modifications proposées</p>
                        <div class="space-y-1.5">
                          @for (change of msg.pendingChanges; track change.date + change.mealType) {
                            <div class="rounded-lg bg-white dark:bg-stone-900 border border-amber-100 dark:border-amber-900 px-3 py-2">
                              <p class="text-xs font-medium text-stone-700 dark:text-stone-300">
                                {{ formatDayLabel(change.date) }}
                                <span class="text-stone-400 dark:text-stone-500">·</span>
                                {{ change.mealType === 'LUNCH' ? 'Déjeuner' : 'Dîner' }}
                              </p>
                              <p class="text-xs text-stone-900 dark:text-stone-100 mt-0.5">{{ change.recipeName }}</p>
                              @if (change.previousRecipeName) {
                                <p class="text-xs text-stone-400 dark:text-stone-500 mt-0.5">Remplace : {{ change.previousRecipeName }}</p>
                              }
                            </div>
                          }
                        </div>
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
                <div class="rounded-2xl rounded-tl-sm bg-stone-100 dark:bg-stone-800 px-4 py-3">
                  <div class="flex items-center gap-1" aria-label="L'IA réfléchit">
                    <span class="h-2 w-2 rounded-full bg-stone-400 dark:bg-stone-500 animate-bounce [animation-delay:-0.3s]"></span>
                    <span class="h-2 w-2 rounded-full bg-stone-400 dark:bg-stone-500 animate-bounce [animation-delay:-0.15s]"></span>
                    <span class="h-2 w-2 rounded-full bg-stone-400 dark:bg-stone-500 animate-bounce"></span>
                  </div>
                </div>
              </div>
            }

            @if (error()) {
              <div class="rounded-xl border border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-950/50 px-4 py-2.5 text-sm text-red-700 dark:text-red-300" role="alert">
                {{ error() }}
              </div>
            }
          </div>
        }

      </div>

      <!-- Footer phase form -->
      @if (phase() === 'form') {
        <div class="shrink-0 border-t border-stone-200 dark:border-stone-700 px-5 py-4 flex flex-col gap-2.5">
          <button
            type="button"
            (click)="onGenerateClicked()"
            class="flex w-full items-center justify-center gap-2 rounded-xl bg-forest-600 py-3 text-sm font-semibold text-white hover:bg-forest-700 active:bg-forest-800 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-600"
          >
            <ng-icon name="heroSparkles" class="h-4 w-4" aria-hidden="true"></ng-icon>
            Générer mon menu
          </button>
          <button
            type="button"
            (click)="onSkipClicked()"
            class="w-full py-1 text-center text-xs text-stone-400 dark:text-stone-500 hover:text-stone-600 dark:hover:text-stone-300 transition-colors cursor-pointer"
          >
            Passer les contraintes
          </button>
        </div>
      }

      <!-- Footer phase chat: quick actions + input -->
      @if (phase() === 'chat') {
        @if (quickActions().length > 0) {
          <div class="shrink-0 border-t border-stone-200 dark:border-stone-700 px-4 py-2.5 flex flex-wrap gap-1.5">
            @for (action of quickActions(); track action.action) {
              <button
                (click)="sendQuickAction(action)"
                [disabled]="loading()"
                class="rounded-full border border-stone-300 dark:border-stone-600 px-3 py-1 text-xs text-stone-700 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              >{{ action.label }}</button>
            }
          </div>
        }
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
              class="flex-1 resize-none rounded-xl border border-stone-200 dark:border-stone-600 bg-stone-50 dark:bg-stone-800 px-3 py-2 text-sm text-stone-900 dark:text-stone-100 placeholder:text-stone-400 focus:outline-none focus:border-forest-400 dark:focus:border-forest-600 transition-colors disabled:opacity-50"
              aria-label="Votre message"
            ></textarea>
            <button
              type="submit"
              [disabled]="loading() || !inputText.trim()"
              class="shrink-0 rounded-xl bg-forest-600 p-2.5 text-white hover:bg-forest-700 transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline-2 focus-visible:outline-forest-600"
              aria-label="Envoyer"
            >
              <ng-icon name="heroPaperAirplane" class="h-5 w-5" aria-hidden="true"></ng-icon>
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
      animation: slideIn 0.3s cubic-bezier(0.32, 0.72, 0, 1);
    }
    @keyframes orbPulse {
      0%, 100% { transform: scale(1); }
      50% { transform: scale(1.06); }
    }
    .animate-orb-pulse {
      animation: orbPulse 2s ease-in-out infinite;
    }
  `],
})
export class WeeklyPlanDrawerComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) weekStart!: string;

  @Output() dismissed = new EventEmitter<boolean>();
  @Output() planChanged = new EventEmitter<void>();

  @ViewChild('messagesContainer') messagesContainer?: ElementRef<HTMLDivElement>;
  @ViewChild('inputField') inputField?: ElementRef<HTMLTextAreaElement>;
  @ViewChild('constraintForm') constraintForm?: WeekConstraintFormComponent;

  readonly GEN_STEPS = [
    { emoji: '🧠', label: 'Analyse de vos préférences', sub: 'Contraintes et disponibilités' },
    { emoji: '🥦', label: 'Équilibre nutritionnel', sub: 'Variété et apports de la semaine' },
    { emoji: '🔍', label: 'Sélection des recettes', sub: 'Depuis votre bibliothèque' },
    { emoji: '✨', label: 'Planification finalisée', sub: '14 repas générés' },
  ];

  phase = signal<'form' | 'generating' | 'chat'>('form');
  genStep = signal(0);
  messages = signal<DrawerMessage[]>([]);
  quickActions = signal<QuickActionDto[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  inputText = '';

  private sessionId = crypto.randomUUID();
  private planModified = false;
  private genTimer?: ReturnType<typeof setTimeout>;

  constructor(private chatService: ChatService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['weekStart'] && !changes['weekStart'].firstChange) {
      this.resetSession();
    }
  }

  ngOnDestroy(): void {
    clearTimeout(this.genTimer);
  }

  close(): void {
    this.dismissed.emit(this.planModified);
  }

  getStepState(index: number): 'done' | 'active' | 'pending' {
    const current = this.genStep();
    if (index + 1 < current) return 'done';
    if (index + 1 === current) return 'active';
    return 'pending';
  }

  stepRowClass(index: number): string {
    const s = this.getStepState(index);
    if (s === 'done') return 'bg-forest-50/60 dark:bg-forest-950/30';
    if (s === 'active') return 'bg-forest-50 dark:bg-forest-950/50 border border-forest-200 dark:border-forest-800';
    return 'opacity-35';
  }

  stepIconClass(index: number): string {
    const s = this.getStepState(index);
    if (s === 'done' || s === 'active') return 'bg-forest-100 dark:bg-forest-900 text-forest-600 dark:text-forest-400';
    return 'bg-stone-100 dark:bg-stone-800 text-stone-400';
  }

  stepLabelClass(index: number): string {
    const s = this.getStepState(index);
    if (s === 'done') return 'text-stone-500 dark:text-stone-400';
    if (s === 'active') return 'text-stone-900 dark:text-stone-100';
    return 'text-stone-400 dark:text-stone-500';
  }

  onGenerateClicked(): void {
    const constraints = this.constraintForm?.getConstraints() ?? null;
    this.startGeneration(constraints);
  }

  onSkipClicked(): void {
    this.startGeneration(null);
  }

  onEnterKey(event: Event): void {
    if (!(event as KeyboardEvent).shiftKey) {
      event.preventDefault();
      if (!this.loading() && this.inputText.trim()) {
        this.sendMessage();
      }
    }
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

  private startGeneration(constraints: WeekConstraintsDto | null): void {
    const message = constraints ? 'Génère mon menu avec ces contraintes.' : 'Génère mon menu.';
    this.phase.set('generating');
    this.startGenAnimation();
    this.messages.update(msgs => [...msgs, { role: 'user', content: message }]);

    this.callApi({
      sessionId: this.sessionId,
      weekStart: this.weekStart,
      message,
      constraints: constraints ?? undefined,
    });
  }

  private startGenAnimation(): void {
    clearTimeout(this.genTimer);
    this.genStep.set(0);
    let s = 0;
    const tick = () => {
      s++;
      this.genStep.set(s);
      if (s < 3) {
        this.genTimer = setTimeout(tick, 950);
      }
    };
    this.genTimer = setTimeout(tick, 950);
  }

  private sendMessageText(message: string, displayText?: string): void {
    this.error.set(null);
    this.messages.update(msgs => [...msgs, { role: 'user', content: displayText ?? message }]);
    this.quickActions.set([]);
    this.scrollToBottom();

    this.callApi({
      sessionId: this.sessionId,
      weekStart: this.weekStart,
      message,
    });
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

        if (!response) {
          if (this.phase() === 'generating') {
            this.phase.set('chat');
          }
          return;
        }

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

        if (this.phase() === 'generating') {
          clearTimeout(this.genTimer);
          this.genStep.set(4);
          setTimeout(() => {
            this.phase.set('chat');
            this.scrollToBottom();
          }, 400);
        } else {
          this.scrollToBottom();
        }
      });
  }

  private resetSession(): void {
    clearTimeout(this.genTimer);
    this.sessionId = crypto.randomUUID();
    this.phase.set('form');
    this.genStep.set(0);
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
