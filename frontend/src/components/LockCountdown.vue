<template>
  <el-alert class="lock-countdown" type="warning" :closable="false" show-icon>
    <template #title>
      <span class="lock-title">{{ message }}</span>
      <span class="lock-time">{{ formattedTime }}</span>
      <span class="lock-suffix">后可重新登录</span>
    </template>
  </el-alert>
</template>

<script setup>
import { computed, onUnmounted, ref, watch } from 'vue';

const props = defineProps({
  unlockAt: { type: Number, required: true },
  message: { type: String, default: '账号已锁定' }
});
const emit = defineEmits(['expired']);

const now = ref(Date.now());
let timer = null;
let expiredEmitted = false;

const remainingSeconds = computed(() => Math.max(0, Math.ceil((props.unlockAt - now.value) / 1000)));
const formattedTime = computed(() => {
  const total = remainingSeconds.value;
  const minutes = String(Math.floor(total / 60)).padStart(2, '0');
  const seconds = String(total % 60).padStart(2, '0');
  return `${minutes}:${seconds}`;
});

function tick() {
  now.value = Date.now();
  if (remainingSeconds.value <= 0 && !expiredEmitted) {
    expiredEmitted = true;
    stop();
    emit('expired');
  }
}

function stop() {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
}

watch(() => props.unlockAt, () => {
  expiredEmitted = false;
  now.value = Date.now();
  stop();
  if (props.unlockAt > Date.now()) timer = setInterval(tick, 1000);
}, { immediate: true });

onUnmounted(stop);
</script>

<style scoped>
.lock-countdown {
  margin-bottom: 16px;
  border-radius: 10px;
}
.lock-title { font-weight: 600; }
.lock-time {
  margin: 0 6px;
  font-family: 'DIN Alternate', Consolas, monospace;
  font-size: 18px;
  font-weight: 700;
  color: #d48806;
}
.lock-suffix { color: #8a6d3b; }
</style>