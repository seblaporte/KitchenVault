describe('Administration', () => {
  beforeEach(() => {
    cy.intercept('GET', '**/api/v1/admin/stats', { fixture: 'admin-stats.json' }).as('stats');
    cy.intercept('GET', '**/api/v1/admin/recipe-lists', { fixture: 'recipe-list-settings.json' }).as(
      'recipeListSettings',
    );
    cy.intercept('GET', '**/api/v1/collections', { fixture: 'collections.json' });
  });

  it('affiche les statistiques de la base', () => {
    cy.intercept('GET', '**/api/v1/sync/latest', { statusCode: 404 });

    cy.visit('/admin');
    cy.wait('@stats');

    cy.contains('142').should('be.visible');
    cy.contains('5').should('be.visible');
    cy.contains('Dernière sync réussie').parent().contains('20/04/2026').should('be.visible');
  });

  it('affiche "—" quand aucune sync réussie', () => {
    cy.intercept('GET', '**/api/v1/sync/latest', { statusCode: 404 });
    cy.intercept('GET', '**/api/v1/admin/stats', {
      body: { recipeCount: 0, collectionCount: 0 },
    });

    cy.visit('/admin');

    cy.contains('Dernière sync réussie').parent().contains('—').should('be.visible');
  });

  it('déclenche une synchronisation et affiche le statut RUNNING', () => {
    // Poll initial → pas de sync en cours (permet de cliquer le bouton)
    cy.intercept('GET', '**/api/v1/sync/latest', { statusCode: 404 }).as('latestInitial');
    cy.intercept('POST', '**/api/v1/sync', { fixture: 'sync-running.json' }).as('triggerSync');

    cy.visit('/admin');
    cy.wait('@latestInitial');

    // Après le clic, les polls suivants retournent RUNNING
    cy.intercept('GET', '**/api/v1/sync/latest', { fixture: 'sync-running.json' });

    cy.get('[aria-label="Lancer une synchronisation manuelle"]').click();
    cy.wait('@triggerSync');

    // La réponse POST met isSyncing=true et latestSync=RUNNING
    cy.get('[aria-label="Lancer une synchronisation manuelle"]')
      .should('have.attr', 'aria-busy', 'true')
      .and('contain.text', 'Synchronisation');

    cy.get('[role="status"]').within(() => {
      cy.get('[aria-label*="RUNNING"]').should('be.visible');
    });
  });

  it('affiche le statut SUCCESS après une sync réussie', () => {
    // Le poll initial (startWith(0)) retourne SUCCESS directement
    cy.intercept('GET', '**/api/v1/sync/latest', { fixture: 'sync-success.json' }).as('latestSync');

    cy.visit('/admin');
    cy.wait('@latestSync');

    cy.get('[role="status"]').within(() => {
      cy.get('[aria-label*="SUCCESS"]').should('be.visible');
      cy.contains('142 recettes').should('be.visible');
      cy.contains('5 collections').should('be.visible');
    });
  });

  describe('Réglages des listes de recettes', () => {
    beforeEach(() => {
      cy.intercept('GET', '**/api/v1/sync/latest', { statusCode: 404 });
    });

    it('affiche les 3 rôles avec leurs libellés et collections rattachées', () => {
      cy.visit('/admin');
      cy.wait('@recipeListSettings');

      cy.contains('Listes de recettes').should('be.visible');
      cy.get('[aria-label="Libellé de la liste Recettes favorites"]').should('have.value', 'Favoris');
      cy.get('[aria-label="Libellé de la liste Recettes à découvrir"]').should('have.value', 'Miam');
      cy.get('[aria-label="Libellé de la liste Recettes à exclure"]').should('have.value', 'Pas fou');
    });

    it('modifie le libellé d\'un rôle', () => {
      cy.intercept('PATCH', '**/api/v1/admin/recipe-lists/FAVORITES', {
        body: { role: 'FAVORITES', displayLabel: 'Mes classiques', collection: { id: 'col-1', name: 'Plats du quotidien' } },
      }).as('updateLabel');

      cy.visit('/admin');
      cy.wait('@recipeListSettings');

      cy.get('[aria-label="Libellé de la liste Recettes favorites"]')
        .clear()
        .type('Mes classiques')
        .blur();

      cy.wait('@updateLabel').its('request.body').should('deep.equal', { displayLabel: 'Mes classiques' });
    });

    it('rattache une collection Cookidoo existante à un rôle', () => {
      cy.intercept('PATCH', '**/api/v1/admin/recipe-lists/DISCOVERY', {
        body: { role: 'DISCOVERY', displayLabel: 'Miam', collection: { id: 'col-2', name: 'Desserts & Pâtisseries' } },
      }).as('bindCollection');

      cy.visit('/admin');
      cy.wait('@recipeListSettings');

      cy.get('[aria-label="Collection rattachée à Recettes à découvrir"]').select('col-2', { force: true });

      cy.wait('@bindCollection').its('request.body').should('deep.equal', { collectionId: 'col-2' });
      cy.get('[role="status"]').should('contain.text', 'Collection rattachée');
    });

    it('crée une nouvelle collection Cookidoo pour un rôle sans collection', () => {
      cy.intercept('POST', '**/api/v1/admin/recipe-lists/DISCOVERY/collection', {
        body: { role: 'DISCOVERY', displayLabel: 'Miam', collection: { id: 'col-new', name: 'Nouvelle collection' } },
      }).as('createCollection');
      cy.intercept('GET', '**/api/v1/collections', { fixture: 'collections.json' });

      cy.visit('/admin');
      cy.wait('@recipeListSettings');

      cy.contains('button', '+ Créer une nouvelle collection').click();
      cy.get('[aria-label="Nom de la nouvelle collection pour Recettes à découvrir"]').type('Nouvelle collection');
      cy.contains('button', 'Créer').click();

      cy.wait('@createCollection').its('request.body').should('deep.equal', { name: 'Nouvelle collection' });
      cy.get('[role="status"]').should('contain.text', 'Collection créée');
    });
  });
});
