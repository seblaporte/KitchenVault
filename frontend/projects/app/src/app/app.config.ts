import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { BASE_PATH } from '@KitchenVault/api-client';
import { MARKED_OPTIONS, provideMarkdown } from 'ngx-markdown';

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withFetch()),
    { provide: BASE_PATH, useValue: '' },
    provideMarkdown({
      markedOptions: {
        provide: MARKED_OPTIONS,
        useValue: { breaks: true },
      },
    }),
  ],
};
