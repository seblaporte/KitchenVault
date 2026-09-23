<template>
  <div class="absolute bottom-4 left-6 text-sm opacity-50">
    REX dev agentique · Chapter dev - 23/09/2026
  </div>
  <div
    v-if="$nav.currentPage > 1"
    class="absolute bottom-4 right-6 text-sm opacity-50 font-mono"
  >
    {{ $nav.currentPage }} / {{ $nav.total }}
  </div>
</template>
