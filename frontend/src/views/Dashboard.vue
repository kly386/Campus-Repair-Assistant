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
      <el-col :span="24">
        <el-card shadow="hover" header="近 14 天工单趋势">
          <div ref="trendRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="16" class="chart-row">
      <el-col :span="12">
        <el-card shadow="hover" header="工单状态分布">
          <div ref="statusRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
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
import * as echarts from 'echarts/core';
import { BarChart, LineChart, PieChart } from 'echarts/charts';
import {
  AxisPointerComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { Refresh } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { getOverview } from '../api/dashboard';
import { formatApiError } from '../api/http';

// 按需注册：只打包大屏实际用到的 3 种图表、4 个组件和 Canvas 渲染器，
// 避免整包引入 ECharts 造成的首屏 JS 体积与解析开销。
echarts.use([
  BarChart, LineChart, PieChart,
  AxisPointerComponent, GridComponent, LegendComponent, TooltipComponent,
  CanvasRenderer
]);

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
const charts = new Map();

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
  if (loading.value) return;
  loading.value = true;
  try {
    const resp = await getOverview();
    const data = resp.data || {};
    summary.value = data.summary || {};
    refreshTime.value = new Date().toLocaleTimeString();
    renderStatCards();
    renderTrend(data.trend || []);
    renderPie('status', statusRef.value, translateNames(data.statusDistribution, STATUS_LABEL), '状态');
    renderPie('emergency', emergencyRef.value, translateNames(data.emergencyDistribution, EMERGENCY_LABEL), '紧急度');
    renderBar('category', categoryRef.value, data.categoryTop || [], '故障类别');
    renderBar('building', buildingRef.value, data.buildingTop || [], '报修量');
    renderBar('worker', workerRef.value, data.workerTop || [], '接单量');
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
  const chart = getChart('trend', trendRef.value);
  if (!chart) return;
  chart.setOption({
    tooltip: { trigger: 'axis', confine: true },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: list.map((it) => it.date.slice(5)) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      name: '新增工单', type: 'line', smooth: true, data: list.map((it) => it.value),
      areaStyle: { opacity: 0.15 }, itemStyle: { color: '#409eff' }
    }]
  });
}

/** 饼图：状态 / 紧急度（恢复原样式：外标签+底部图例，半屏宽度足够展示） */
function renderPie(key, el, list, name) {
  const chart = getChart(key, el);
  if (!chart) return;
  chart.setOption({
    tooltip: { trigger: 'item', confine: true, formatter: '{b}: {c} 单 ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      name, type: 'pie', radius: ['38%', '62%'], center: ['50%', '44%'],
      data: list, label: { formatter: '{b} {d}%', fontSize: 12 }
    }]
  });
}

/** 横向条形图：类别 / 楼栋 / 师傅 Top5 */
function renderBar(key, el, list, name) {
  const chart = getChart(key, el);
  if (!chart) return;
  const data = [...list].reverse();
  chart.setOption({
    tooltip: { trigger: 'axis', confine: true, axisPointer: { type: 'shadow' } },
    grid: { left: 90, right: 30, top: 10, bottom: 30 },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: data.map((it) => it.name) },
    series: [{
      name, type: 'bar', barMaxWidth: 18, data: data.map((it) => it.value),
      itemStyle: { color: '#67c23a' }, label: { show: true, position: 'right' }
    }]
  });
}

/** 复用/懒创建 ECharts 实例：按固定 key 管理，避免依赖 DOM 上的临时属性 */
function getChart(key, el) {
  if (!el) return null;
  let chart = charts.get(key);
  if (!chart) {
    chart = echarts.init(el);
    charts.set(key, chart);
  }
  return chart;
}

/** 30 秒自动刷新：页面不可见时暂停，避免后台标签页空跑请求 */
let timer;
let resizeTimer;
onMounted(() => {
  load();
  timer = setInterval(() => {
    if (!document.hidden) load();
  }, 30000);
  document.addEventListener('visibilitychange', handleVisibilityChange);
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  clearInterval(timer);
  clearTimeout(resizeTimer);
  document.removeEventListener('visibilitychange', handleVisibilityChange);
  window.removeEventListener('resize', handleResize);
  charts.forEach((chart) => chart.dispose());
  charts.clear();
});

/** 从后台标签页切回时立即补一次数据，避免等待下一个轮询周期 */
function handleVisibilityChange() {
  if (!document.hidden) load();
}

/** 窗口尺寸变化时节流 resize，避免拖动窗口时高频重绘 */
function handleResize() {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => {
    charts.forEach((chart) => chart.resize());
  }, 150);
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
