<template>
  <div class="dashboard">
    <div class="dashboard-header">
      <h2>??????</h2>
      <div class="header-right">
        <span class="refresh-time">???? {{ refreshTime }}</span>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">??</el-button>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="4" v-for="card in statCards" :key="card.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="chart-row">
      <el-col :span="24">
        <el-card shadow="hover" header="? 14 ?????">
          <div ref="trendRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="16" class="chart-row">
      <el-col :span="12">
        <el-card shadow="hover" header="??????">
          <div ref="statusRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover" header="?????">
          <div ref="emergencyRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="16" class="chart-row">
      <el-col :span="8">
        <el-card shadow="hover" header="???? Top5">
          <div ref="categoryRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" header="???? Top5">
          <div ref="buildingRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" header="???? Top5">
          <div ref="workerRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import * as echarts from 'echarts';
import { Refresh } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { getOverview } from '../api/dashboard';
import { formatApiError } from '../api/http';

const EMERGENCY_LABEL = { LOW: '?', MEDIUM: '?', HIGH: '?' };
const STATUS_LABEL = {
  SUBMITTED: '???', ASSIGNED: '???', ON_THE_WAY: '???',
  PROCESSING: '???', PENDING_ACCEPT: '???', COMPLETED: '???',
  CANCELLED: '???', RESCHEDULED: '???'
};

const loading = ref(false);
const refreshTime = ref('');
const summary = ref({});
const trendRef = ref();
const statusRef = ref();
const emergencyRef = ref();
const categoryRef = ref();
const buildingRef = ref();
const workerRef = ref();
const charts = {};

const statCards = ref([
  { label: '????', value: 0, color: '#409eff' },
  { label: '????', value: 0, color: '#67c23a' },
  { label: '????', value: 0, color: '#e6a23c' },
  { label: '???', value: 0, color: '#f56c6c' },
  { label: '???', value: '0%', color: '#409eff' },
  { label: '?????', value: '-', color: '#67c23a' }
]);

async function load() {
  loading.value = true;
  try {
    const resp = await getOverview();
    const data = resp.data || {};
    summary.value = data.summary || {};
    refreshTime.value = new Date().toLocaleTimeString();
    renderStatCards();
    renderTrend(data.trend || []);
    renderPie(statusRef.value, translateNames(data.statusDistribution, STATUS_LABEL), '??');
    renderPie(emergencyRef.value, translateNames(data.emergencyDistribution, EMERGENCY_LABEL), '???');
    renderBar(categoryRef.value, data.categoryTop || [], '????');
    renderBar(buildingRef.value, data.buildingTop || [], '????');
    renderBar(workerRef.value, data.workerTop || [], '????');
  } catch (err) {
    ElMessage.error('?????????' + formatApiError(err));
  } finally {
    loading.value = false;
  }
}

function renderStatCards() {
  const s = summary.value;
  const avg = Number(s.avgScore);
  statCards.value = [
    { label: '????', value: s.total ?? 0, color: '#409eff' },
    { label: '????', value: s.todayNew ?? 0, color: '#67c23a' },
    { label: '????', value: s.monthNew ?? 0, color: '#e6a23c' },
    { label: '???', value: s.inProgress ?? 0, color: '#f56c6c' },
    { label: '???', value: `${s.completedRate ?? 0}%`, color: '#409eff' },
    { label: '?????', value: Number.isFinite(avg) && avg > 0 ? `${avg.toFixed(2)} ?` : '-', color: '#67c23a' }
  ];
}

function translateNames(list, labelMap) {
  return (list || []).map((it) => ({ ...it, name: labelMap[it.name] || it.name || '??' }));
}

function renderTrend(list) {
  const chart = getChart(trendRef.value);
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: list.map((it) => String(it.date || '').slice(5)) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      name: '????', type: 'line', smooth: true, data: list.map((it) => it.value),
      areaStyle: { opacity: 0.15 }, itemStyle: { color: '#409eff' }
    }]
  });
}

function renderPie(el, list, name) {
  if (!el) return;
  const chart = getChart(el);
  const data = (list || []).filter((it) => Number(it.value) > 0);
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ? ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      name, type: 'pie', radius: ['38%', '62%'], center: ['50%', '44%'],
      data, label: { formatter: '{b} {d}%', fontSize: 12 }
    }]
  });
}

function renderBar(el, list, name) {
  if (!el) return;
  const chart = getChart(el);
  const data = [...(list || [])].reverse();
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 90, right: 30, top: 10, bottom: 30 },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: data.map((it) => it.name || '??') },
    series: [{
      name, type: 'bar', barMaxWidth: 18, data: data.map((it) => it.value),
      itemStyle: { color: '#67c23a' }, label: { show: true, position: 'right' }
    }]
  });
}

function getChart(el) {
  if (!charts[el.__key]) {
    charts[el.__key] = echarts.init(el);
  }
  return charts[el.__key];
}

let timer;
onMounted(() => {
  [trendRef, statusRef, emergencyRef, categoryRef, buildingRef, workerRef].forEach(
    (r, i) => { if (r.value) r.value.__key = `chart${i}`; }
  );
  load();
  timer = setInterval(load, 30000);
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  clearInterval(timer);
  window.removeEventListener('resize', handleResize);
  Object.values(charts).forEach((c) => c.dispose());
});

function handleResize() {
  Object.values(charts).forEach((c) => c.resize());
}
</script>

<style scoped>
.dashboard h2 {
  margin: 0;
}
.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.refresh-time {
  font-size: 12px;
  color: #909399;
}
.stat-card {
  text-align: center;
}
.stat-value {
  font-size: 26px;
  font-weight: bold;
}
.stat-label {
  margin-top: 8px;
  color: #666;
  font-size: 13px;
}
.chart-row {
  margin-top: 16px;
}
.chart {
  height: 300px;
}
</style>
