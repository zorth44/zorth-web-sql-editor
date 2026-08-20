<script setup lang="ts">
import mysqlLogo from '@/assets/engines/mysql.svg'
import mysqlMark from '@/assets/engines/mysql-mark.svg'
import postgresqlLogo from '@/assets/engines/postgresql.svg'
import gbase8aLogo from '@/assets/engines/gbase-8a.svg'
import gbase8aMark from '@/assets/engines/gbase-8a-mark.svg'

withDefaults(defineProps<{ engine: string; size?: number; variant?: 'card' | 'tree' }>(), {
  size: 48,
  variant: 'card',
})
</script>
<template>
  <span
    :class="variant === 'tree' ? 'tree-engine-icon' : 'engine-type-icon'"
    :data-engine-icon="engine"
    :data-engine-variant="variant"
    aria-hidden="true"
  >
    <template v-if="variant === 'tree'">
      <img v-if="engine === 'MYSQL'" :src="mysqlMark" alt="" />
      <img v-else-if="engine === 'POSTGRESQL'" :src="postgresqlLogo" alt="" />
      <img v-else-if="engine === 'GBASE_8A'" :src="gbase8aMark" alt="" />
      <svg v-else viewBox="0 0 16 16" width="16" height="16">
        <ellipse cx="8" cy="4.2" rx="5.6" ry="2.1" fill="#0f766e" />
        <path
          fill="#0f766e"
          d="M2.4 4.2v7.4c0 1.16 2.5 2.1 5.6 2.1s5.6-.94 5.6-2.1V4.2c0 1.16-2.5 2.1-5.6 2.1S2.4 5.36 2.4 4.2Z"
        />
        <ellipse cx="8" cy="4.2" rx="5.6" ry="2.1" fill="#14b8a6" />
      </svg>
    </template>
    <template v-else>
      <img v-if="engine === 'MYSQL'" :src="mysqlLogo" width="88" height="56" alt="" />
      <img
        v-else-if="engine === 'POSTGRESQL'"
        :src="postgresqlLogo"
        width="56"
        height="56"
        alt=""
      />
      <img v-else-if="engine === 'GBASE_8A'" :src="gbase8aLogo" width="88" height="56" alt="" />
      <svg v-else viewBox="0 0 48 48" :width="size" :height="size">
        <rect width="48" height="48" rx="12" fill="#0F766E" />
        <ellipse cx="24" cy="16.5" rx="12" ry="5" fill="#5EEAD4" />
        <path
          fill="#14B8A6"
          d="M12 16.5v15c0 2.8 5.4 5 12 5s12-2.2 12-5v-15c0 2.8-5.4 5-12 5s-12-2.2-12-5Z"
        />
        <ellipse cx="24" cy="16.5" rx="12" ry="5" fill="#99F6E4" />
      </svg>
    </template>
  </span>
</template>
