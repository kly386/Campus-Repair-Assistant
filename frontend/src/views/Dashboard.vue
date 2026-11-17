<template>
  <div class="dashboard">
    <div class="dashboard-header">
      <h2>运营数据大屏</h2>
      <div class="header-right">
        <span class="refresh-time">更新于 {{ refreshTime }}</span>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <!-- 指标卡 -->
    <el-row :gutter="16">
      <el-col :span="4" v-for="card in statCards" :key="card.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="12">
        <el-card shadow="hover" header="近 14 天工单趋势">
          <div ref="trendRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" header="工单状态分布">
          <div ref="statusRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" header="紧急度分布">
          <div ref="emergencyRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="16" class="chart-row">
      <el-col :span="8">
        <el-card shadow="hover" header="高频故障 Top5">
          <div ref="categoryRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" header="报修楼栋 Top5">
          <div ref="buildingRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" header="师傅接单 Top5">
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

const EMERGENCY_LABEL = { LOW: '低', MEDIUM: '中', HIGH: '高' };
const STATUS_LABEL = {
  SUBMITTED: '已提交', ASSIGNED: '已派单', ON_THE_WAY: '上门中',
  PROCESSING: '维修中', PENDING_ACCEPT: '待验收', COMPLETED: '已完成',
  CANCELLED: '已取消', RESCHEDULED: '已改期'
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
  { label: '工单总量', value: 0, color: '#409eff' },
  { label: '今日新增', value: 0, color: '#67c23a' },
  { label: '本月新增', value: 0, color: '#e6a23c' },
  { label: '处理中', value: 0, color: '#f56c6c' },
  { label: '完成率', value: '0%', color: '#409eff' },
  { label: '平均满意度', value: '-', color: '#67c23a' }
]);

/** 拉取总览数据并渲染全部图表 */
async function load() {
  loading.value = true;
  try {
    const resp = await getOverview();
    const data = resp.data || {};
    summary.value = data.summary || {};
    refreshTime.value = new Date().toLocaleTimeString();
    renderStatCards();
    renderTrend(data.trend || []);
    renderPie(statusRef.value, translateNames(data.statusDistribution, STATUS_LABEL), '状态');
    renderPie(emergencyRef.value, translateNames(data.emergencyDistribution, EMERGENCY_LABEL), '紧急度');
    renderBar(categoryRef.value, data.categoryTop || [], '故障类别');
    renderBar(buildingRef.value, data.buildingTop || [], '报修量');
    renderBar(workerRef.value, data.workerTop || [], '接单量');
  } catch (err) {
    ElMessage.error('大屏数据加载失败：' + formatApiError(err));
  } finally {
    loading.value = false;
  }
}

/** 顶部指标卡数值 */
function renderStatCards() {
  const s = summary.value;
  statCards.value = [
    { label: '工单总量', value: s.total ?? 0, color: '#409eff' },
    { label: '今日新增', value: s.todayNew ?? 0, color: '#67c23a' },
    { label: '本月新增', value: s.monthNew ?? 0, color: '#e6a23c' },
    { label: '处理中', value: s.inProgress ?? 0, color: '#f56c6c' },
    { label: '完成率', value: `${s.completedRate ?? 0}%`, color: '#409eff' },
    { label: '平均满意度', value: s.avgScore ? `${s.avgScore} 分` : '-', color: '#67c23a' }
  ];
}

/** 状态/紧急度中文映射 */
function translateNames(list, labelMap) {
  return (list || []).map((it) => ({ ...it, name: labelMap[it.name] || it.name }));
}

/** 折线图：近 14 天趋势 */
function renderTrend(list) {
  const chart = getChart(trendRef.value);
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: list.map((it) => it.date.slice(5)) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      name: '新增工单', type: 'line', smooth: true, data: list.map((it) => it.value),
      areaStyle: { opacity: 0.15 }, itemStyle: { color: '#409eff' }
    }]
  });
}

/** 饼图：状态 / 紧急度 */
function renderPie(el, list, name) {
  if (!el) return;
  const chart = getChart(el);
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 单 ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      name, type: 'pie', radius: ['38%', '65%'], center: ['50%', '44%'],
      data: list, label: { formatter: '{b} {d}%' }
    }]
  });
}

/** 横向条形图：类别 / 楼栋 / 师傅 Top5 */
function renderBar(el, list, name) {
  if (!el) return;
  const chart = getChart(el);
  const data = [...list].reverse();
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 90, right: 30, top: 10, bottom: 30 },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: data.map((it) => it.name) },
    series: [{
      name, type: 'bar', barMaxWidth: 18, data: data.map((it) => it.value),
      itemStyle: { color: '#67c23a' }, label: { show: true, position: 'right' }
    }]
  });
}

/** 复用/懒创建 ECharts 实例 */
function getChart(el) {
  if (!charts[el.__key]) {
    charts[el.__key] = echarts.init(el);
  }
  return charts[el.__key];
}

/** 30 秒自动刷新 */
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
