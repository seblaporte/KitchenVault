import { ApplicationConfig, APP_INITIALIZER, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { BASE_PATH } from '@KitchenVault/api-client';
import { MARKED_OPTIONS, provideMarkdown } from 'ngx-markdown';

import { routes } from './app.routes';

let resolvedApiUrl = '';

function loadConfig(): () => Promise<void> {
  return () =>
    fetch('/assets/config.json')
      .then(r => r.json())
      .then((config: { apiUrl: string }) => {
        resolvedApiUrl = config.apiUrl ?? '';
      });
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withFetch()),
    {
      provide: APP_INITIALIZER,
      useFactory: loadConfig,
      multi: true,
    },
    {
      provide: BASE_PATH,
      useFactory: () => resolvedApiUrl,
    },
    provideMarkdown({
      markedOptions: {
        provide: MARKED_OPTIONS,
        useValue: { breaks: true },
      },
    }),
  ],
};
