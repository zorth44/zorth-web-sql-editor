<script setup lang="ts">
import { computed, ref } from 'vue'
import { isOptimizationReport } from '@/sql-optimizer/report-model'
import { copyText } from '@/clipboard'
const props = defineProps<{ report: unknown; schemaVersion: number | null }>()
const report = computed(() =>
  props.schemaVersion === 1 && isOptimizationReport(props.report) ? props.report : null,
)
const root = ref<HTMLElement | null>(null)
const copied = ref('')
function locate(id: string) {
  const element = Array.from(
    root.value?.querySelectorAll<HTMLElement>('[data-issue-id]') || [],
  ).find((el) => el.dataset.issueId === id)
  element?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  element?.focus({ preventScroll: true })
}
async function copy(detail: string) {
  copied.value = (await copyText(detail)) ? '建议已复制' : '复制失败，请手动选择文字复制'
}
</script>
<template>
  <div ref="root" class="space-y-5">
    <p v-if="!report" class="panel p-5 text-danger" role="alert">
      报告格式异常或版本不受支持，请联系管理员核对报告。
    </p>
    <template v-else>
      <section class="panel p-5">
        <h2 class="text-lg font-semibold">总体结论</h2>
        <p class="mt-3 whitespace-pre-wrap">{{ report.verdict }}</p>
        <p v-if="!report.issues.length" class="mt-3 text-sm text-muted">
          本次未发现有证据支持的问题。{{
            report.limitations.length
              ? '存在证据缺口，不能据此判定全部规则通过。'
              : '结论仅覆盖本次取证范围。'
          }}
        </p>
      </section>
      <section class="panel p-5">
        <h2 class="text-lg font-semibold">问题（{{ report.issues.length }}）</h2>
        <article
          v-for="issue in report.issues"
          :key="issue.id"
          :data-issue-id="issue.id"
          tabindex="-1"
          class="mt-4 rounded-lg border border-line p-4 focus:outline-brand"
        >
          <div class="flex flex-wrap gap-3 text-sm">
            <strong>{{ issue.category === 'POLICY' ? '规范问题' : '性能问题' }}</strong
            ><span>组织规则 #{{ issue.ruleId }}</span
            ><span>组织重要性：{{ issue.priority }}</span
            ><span v-if="issue.category === 'PERFORMANCE' && issue.severity"
              >性能严重度：{{ issue.severity }}</span
            >
          </div>
          <p class="mt-3 whitespace-pre-wrap">{{ issue.detail }}</p>
          <div
            v-for="(evidence, index) in issue.evidence"
            :key="index"
            class="mt-3 rounded-md bg-subtle p-3"
          >
            <p class="text-xs text-muted">
              {{ evidence.kind === 'sql' ? 'SQL 片段' : '工具事实' }}
            </p>
            <pre class="mt-2 whitespace-pre-wrap break-all text-xs">{{ evidence.content }}</pre>
          </div>
        </article>
      </section>
      <section class="panel p-5">
        <h2 class="text-lg font-semibold">建议（{{ report.suggestions.length }}）</h2>
        <article
          v-for="suggestion in report.suggestions"
          :key="suggestion.id"
          class="mt-4 border-t border-line pt-4"
        >
          <p class="whitespace-pre-wrap">{{ suggestion.detail }}</p>
          <div class="mt-3 flex flex-wrap gap-2">
            <button
              v-for="id in suggestion.issueIds"
              :key="id"
              class="btn text-xs"
              type="button"
              @click="locate(id)"
            >
              定位问题 {{ id }}</button
            ><button class="btn text-xs" type="button" @click="copy(suggestion.detail)">
              复制文字建议
            </button>
          </div>
        </article>
        <p v-if="copied" role="status" class="mt-3 text-sm">{{ copied }}</p>
      </section>
      <section class="panel border-warning p-5">
        <h2 class="text-lg font-semibold">
          证据缺口 / 未作结论（{{ report.limitations.length }}）
        </h2>
        <p v-if="!report.limitations.length" class="mt-3 text-sm text-muted">
          本报告未列出证据缺口；结论仍仅限本次取证。
        </p>
        <ul v-else class="mt-3 space-y-3">
          <li
            v-for="(limitation, index) in report.limitations"
            :key="index"
            class="whitespace-pre-wrap"
          >
            <span v-if="limitation.ruleId !== null" class="mr-2 text-muted"
              >组织规则 #{{ limitation.ruleId }}</span
            >{{ limitation.reason }}
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>
