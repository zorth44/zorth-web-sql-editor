<script setup lang="ts">
import mysqlLogo from '@/assets/engines/mysql.svg'
import postgresqlLogo from '@/assets/engines/postgresql.svg'

withDefaults(defineProps<{ engine: string; size?: number }>(), { size: 48 })
</script>
<template>
  <span class="engine-type-icon" aria-hidden="true">
    <img v-if="engine === 'MYSQL'" :src="mysqlLogo" width="88" height="56" alt="" />
    <img v-else-if="engine === 'POSTGRESQL'" :src="postgresqlLogo" width="56" height="56" alt="" />
    <svg v-else viewBox="0 0 48 48" :width="size" :height="size">
      <rect width="48" height="48" rx="12" fill="#0F766E" />
      <ellipse cx="24" cy="16.5" rx="12" ry="5" fill="#5EEAD4" />
      <path
        fill="#14B8A6"
        d="M12 16.5v15c0 2.8 5.4 5 12 5s12-2.2 12-5v-15c0 2.8-5.4 5-12 5s-12-2.2-12-5Z"
      />
      <ellipse cx="24" cy="16.5" rx="12" ry="5" fill="#99F6E4" />
    </svg>
  </span>
</template>
