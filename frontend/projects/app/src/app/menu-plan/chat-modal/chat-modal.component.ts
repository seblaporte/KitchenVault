import { Component, ElementRef, EventEmitter, Input, OnInit, Output, ViewChild, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';
import { ChatService } from '@KitchenVault/api-client';

interface ChatMessage {
  role: 'user' | 'ai';
  content: string;
  isApplyable: boolean;
  applyUsed: boolean;
}

@Component({
  selector: 'app-chat-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-label="Assistant IA culinaire"
      (click)="onBackdropClick($event)"
    >
      <div class="absolute inset-0 bg-black/50" aria-hidden="true"></div>

      <div
        #modalCard
        class="relative flex flex-col w-full max-w-lg h-[80vh] rounded-2xl bg-white dark:bg-stone-900 shadow-xl overflow-hidden"
        (click)="$event.stopPropagation()"
      >
        <!-- En-tête -->
        <div class="flex items-center justify-between px-5 py-4 border-b border-stone-200 dark:border-stone-700 shrink-0">
          <div class="flex items-center gap-2">
            <svg class="h-5 w-5 text-forest-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
            </svg>
            <h2 class="text-base font-semibold text-stone-900 dark:text-stone-100">Assistant IA</h2>
          </div>
          <button
            (click)="close()"
            class="rounded-lg p-1.5 text-stone-400 dark:text-stone-500 hover:bg-stone-100 dark:hover:bg-stone-800 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500"
            aria-label="Fermer"
          >
            <svg class="h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <!-- Zone de messages -->
        <div #messagesContainer class="flex-1 overflow-y-auto px-4 py-4 space-y-4">
          @if (messages().length === 0) {
            <p class="text-center text-sm text-stone-400 dark:text-stone-500 mt-8">
              Posez votre question à l'assistant IA culinaire.
            </p>
          }

          @for (msg of messages(); track $index) {
            <div [class]="msg.role === 'user' ? 'flex justify-end' : 'flex justify-start'">
              <div [class]="msg.role === 'user'
                ? 'max-w-[80%] rounded-2xl rounded-tr-sm bg-forest-600 px-4 py-2.5 text-sm text-white'
                : 'max-w-[80%] space-y-2'">
                @if (msg.role === 'ai') {
                  <div class="rounded-2xl rounded-tl-sm bg-stone-100 dark:bg-stone-800 px-4 py-2.5 text-sm text-stone-800 dark:text-stone-200 whitespace-pre-wrap">{{ msg.content }}</div>
                  @if (msg.isApplyable && !msg.applyUsed) {
                    <button
                      (click)="applyPlan(msg)"
                      class="inline-flex items-center gap-1.5 rounded-lg bg-forest-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-forest-700 transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-600"
                    >
                      <svg class="h-3.5 w-3.5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                      </svg>
                      Appliquer ces suggestions
                    </button>
                  }
                } @else {
                  {{ msg.content }}
                }
              </div>
            </div>
          }

          <!-- Indicateur de frappe -->
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
            <div class="rounded-xl border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700" role="alert">{{ error() }}</div>
          }
        </div>

        <!-- Pied de page : saisie -->
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
              <svg class="h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
              </svg>
            </button>
          </form>
        </div>
      </div>
    </div>
  `,
})
export class ChatModalComponent implements OnInit {
  @Input({ required: true }) context!: 'week' | 'slot';
  @Input({ required: true }) weekStart!: string;
  @Input() date?: string;
  @Input() mealType?: string;
  @Input() slotLabel?: string;

  @Output() dismissed = new EventEmitter<boolean>();

  @ViewChild('messagesContainer') messagesContainer?: ElementRef<HTMLDivElement>;
  @ViewChild('inputField') inputField?: ElementRef<HTMLTextAreaElement>;

  messages = signal<ChatMessage[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  inputText = '';

  private sessionId = crypto.randomUUID();
  private planModified = false;

  constructor(private chatService: ChatService) {}

  ngOnInit(): void {
    this.inputText = this.buildInitialText();
  }

  onBackdropClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.close();
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

  sendMessage(): void {
    const text = this.inputText.trim();
    if (!text || this.loading()) return;

    this.inputText = '';
    this.error.set(null);

    this.messages.update(msgs => [...msgs, { role: 'user', content: text, isApplyable: false, applyUsed: false }]);
    this.loading.set(true);
    this.scrollToBottom();

    this.callChatEndpoint(text)
      .pipe(catchError(() => {
        this.error.set('Impossible de contacter l\'assistant IA. Veuillez réessayer.');
        return of(null);
      }))
      .subscribe(response => {
        this.loading.set(false);
        if (response?.reply) {
          this.messages.update(msgs => [...msgs, {
            role: 'ai',
            content: response.reply,
            isApplyable: true,
            applyUsed: false,
          }]);
          this.scrollToBottom();
        }
      });
  }

  applyPlan(msg: ChatMessage): void {
    msg.applyUsed = true;
    this.planModified = true;
    this.inputText = '';
    this.sendApplyMessage();
  }

  private sendApplyMessage(): void {
    const text = 'Oui, applique ces suggestions au menu.';
    this.error.set(null);
    this.messages.update(msgs => [...msgs, { role: 'user', content: text, isApplyable: false, applyUsed: false }]);
    this.loading.set(true);
    this.scrollToBottom();

    this.callChatEndpoint(text)
      .pipe(catchError(() => {
        this.error.set('Impossible de contacter l\'assistant IA. Veuillez réessayer.');
        return of(null);
      }))
      .subscribe(response => {
        this.loading.set(false);
        if (response?.reply) {
          this.messages.update(msgs => [...msgs, {
            role: 'ai',
            content: response.reply,
            isApplyable: false,
            applyUsed: false,
          }]);
          this.scrollToBottom();
        }
      });
  }

  private callChatEndpoint(message: string) {
    const dto = { sessionId: this.sessionId, message };
    return this.chatService.chatRecipe(dto);
  }

  private buildInitialText(): string {
    if (this.context === 'week') {
      const date = new Date(this.weekStart + 'T00:00:00');
      const formatted = date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
      return `Planifie-moi la semaine du ${formatted}`;
    }
    const date = new Date((this.date ?? '') + 'T00:00:00');
    const formatted = date.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
    return `Suggère-moi une recette pour le ${this.slotLabel?.toLowerCase() ?? 'repas'} du ${formatted}`;
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
