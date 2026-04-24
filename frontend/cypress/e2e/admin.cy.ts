describe('Administration', () => {
  beforeEach(() => {
    cy.intercept('GET', '**/api/v1/admin/stats', { fixture: 'admin-stats.json' }).as('stats');
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
});
