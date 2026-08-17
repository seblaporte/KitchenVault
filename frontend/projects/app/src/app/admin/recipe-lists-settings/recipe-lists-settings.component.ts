import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import {
  AdminService,
  CollectionsService,
  CollectionDto,
  RecipeListRole,
  RecipeListSettingsDto,
} from '@KitchenVault/api-client';
import { ToastService } from '../../shared/toast/toast.service';

interface RoleRow {
  role: RecipeListRole;
  roleTitle: string;
  displayLabel: string;
  collectionId: string | null;
  collectionName: string | null;
  creatingName: string;
  creating: boolean;
  saving: boolean;
}

const ROLE_TITLES: Record<RecipeListRole, string> = {
  FAVORITES: 'Recettes favorites',
  DISCOVERY: 'Recettes à découvrir',
  REJECTED: 'Recettes à exclure',
};

@Component({
  selector: 'app-recipe-lists-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <p class="text-sm text-stone-500 dark:text-stone-400">
        Chaque liste est rattachée à une collection Cookidoo réelle. Le libellé affiché dans KitchenVault
        peut être personnalisé indépendamment du nom de la collection sur Cookidoo.
      </p>

      @for (row of rows(); track row.role) {
        <div class="rounded-lg border border-stone-200 dark:border-stone-700 p-4 space-y-3">
          <h3 class="text-sm font-semibold text-stone-900 dark:text-stone-100">{{ row.roleTitle }}</h3>

          <div class="flex flex-wrap items-end gap-3">
            <label class="flex-1 min-w-[10rem]">
              <span class="mb-1 block text-xs font-medium text-stone-500 dark:text-stone-400">Libellé affiché</span>
              <input
                type="text"
                [(ngModel)]="row.displayLabel"
                (blur)="saveLabel(row)"
                class="w-full rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 px-3 py-2 text-sm text-stone-900 dark:text-stone-100 focus-visible:outline-2 focus-visible:outline-forest-500"
                [attr.aria-label]="'Libellé de la liste ' + row.roleTitle"
              />
            </label>

            <label class="flex-1 min-w-[12rem]">
              <span class="mb-1 block text-xs font-medium text-stone-500 dark:text-stone-400">Collection Cookidoo rattachée</span>
              <select
                [value]="row.collectionId ?? ''"
                (change)="onCollectionChange(row, $event)"
                class="w-full rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 px-3 py-2 text-sm text-stone-900 dark:text-stone-100 cursor-pointer focus-visible:outline-2 focus-visible:outline-forest-500"
                [attr.aria-label]="'Collection rattachée à ' + row.roleTitle"
              >
                <option value="">Aucune</option>
                @for (collection of collections(); track collection.id) {
                  <option [value]="collection.id">{{ collection.name }}</option>
                }
              </select>
            </label>
          </div>

          @if (!row.collectionId) {
            <div class="flex flex-wrap items-center gap-2">
              @if (row.creating) {
                <input
                  type="text"
                  [(ngModel)]="row.creatingName"
                  placeholder="Nom de la nouvelle collection"
                  class="rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 px-3 py-2 text-sm text-stone-900 dark:text-stone-100 focus-visible:outline-2 focus-visible:outline-forest-500"
                  [attr.aria-label]="'Nom de la nouvelle collection pour ' + row.roleTitle"
                />
                <button
                  type="button"
                  (click)="createCollection(row)"
                  [disabled]="!row.creatingName.trim() || row.saving"
                  class="rounded-lg bg-forest-600 px-3 py-2 text-sm font-medium text-white hover:bg-forest-700 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  Créer
                </button>
                <button
                  type="button"
                  (click)="row.creating = false"
                  class="rounded-lg px-3 py-2 text-sm text-stone-500 dark:text-stone-400 hover:bg-stone-100 dark:hover:bg-stone-800 cursor-pointer transition-colors"
                >
                  Annuler
                </button>
              } @else {
                <button
                  type="button"
                  (click)="row.creating = true"
                  class="text-sm font-medium text-forest-600 dark:text-forest-400 hover:text-forest-800 dark:hover:text-forest-300 cursor-pointer"
                >
                  + Créer une nouvelle collection
                </button>
              }
            </div>
          }
        </div>
      }
    </div>
  `,
})
export class RecipeListsSettingsComponent implements OnInit {
  rows = signal<RoleRow[]>([]);
  collections = signal<CollectionDto[]>([]);

  constructor(
    private adminService: AdminService,
    private collectionsService: CollectionsService,
    private toast: ToastService,
  ) {}

  ngOnInit(): void {
    forkJoin({
      settings: this.adminService.getRecipeListSettings(),
      collections: this.collectionsService.listCollections(),
    }).subscribe(({ settings, collections }) => {
      this.collections.set(collections);
      this.rows.set(settings.map(s => this.toRow(s)));
    });
  }

  private toRow(dto: RecipeListSettingsDto): RoleRow {
    return {
      role: dto.role,
      roleTitle: ROLE_TITLES[dto.role],
      displayLabel: dto.displayLabel,
      collectionId: dto.collection?.id ?? null,
      collectionName: dto.collection?.name ?? null,
      creatingName: '',
      creating: false,
      saving: false,
    };
  }

  saveLabel(row: RoleRow): void {
    const label = row.displayLabel.trim();
    if (!label) return;
    this.adminService.updateRecipeListSettings(row.role, { displayLabel: label }).subscribe({
      next: dto => this.replaceRow(dto),
      error: () => this.toast.show({ type: 'error', title: 'Échec de la mise à jour du libellé' }),
    });
  }

  onCollectionChange(row: RoleRow, event: Event): void {
    const collectionId = (event.target as HTMLSelectElement).value;
    this.bindCollection(row, collectionId || null);
  }

  bindCollection(row: RoleRow, collectionId: string | null): void {
    if (!collectionId) return;
    this.adminService.updateRecipeListSettings(row.role, { collectionId }).subscribe({
      next: dto => {
        this.replaceRow(dto);
        this.toast.show({ type: 'success', title: 'Collection rattachée', message: dto.collection?.name });
      },
      error: () =>
        this.toast.show({
          type: 'error',
          title: 'Échec du rattachement',
          message: 'Cette collection est peut-être déjà rattachée à une autre liste.',
        }),
    });
  }

  createCollection(row: RoleRow): void {
    const name = row.creatingName.trim();
    if (!name) return;
    row.saving = true;
    this.adminService.createRecipeListCollection(row.role, { name }).subscribe({
      next: dto => {
        row.saving = false;
        row.creating = false;
        row.creatingName = '';
        this.replaceRow(dto);
        this.collectionsService.listCollections().subscribe(collections => this.collections.set(collections));
        this.toast.show({ type: 'success', title: 'Collection créée', message: dto.collection?.name });
      },
      error: () => {
        row.saving = false;
        this.toast.show({ type: 'error', title: 'Échec de la création de la collection' });
      },
    });
  }

  private replaceRow(dto: RecipeListSettingsDto): void {
    this.rows.update(rows => rows.map(r => (r.role === dto.role ? this.toRow(dto) : r)));
  }
}
