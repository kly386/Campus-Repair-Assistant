import http from './http';

/**
 * 数据大屏总览：一次拉取全部聚合指标与图表数据
 * 返回结构见后端 DashboardServiceImpl.overview()
 */
export function getOverview() {
  return http.get('/dashboard/overview').then((r) => r.data);
}
