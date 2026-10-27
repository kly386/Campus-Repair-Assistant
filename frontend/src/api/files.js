import axios from 'axios';

const http = axios.create({ baseURL: '/api' });

/**
 * 小文件批量上传：一次请求提交多个文件
 * @param {File[]} files 文件数组
 * @param {number|null} orderId 关联工单
 */
export function batchUpload(files, orderId) {
  const form = new FormData();
  files.forEach((f) => form.append('files', f));
  return http.post('/files/batch-upload', form, {
    params: orderId == null ? {} : { orderId }
  }).then((r) => r.data);
}

/** 大文件分片第一步：初始化，拿 uploadId */
export function initMultipart(fileName, orderId) {
  return http
    .post('/files/multipart/init', null, {
      params: { fileName, ...(orderId == null ? {} : { orderId }) }
    })
    .then((r) => r.data);
}

/** 大文件分片第二步：传一支分片（index 从 1 起） */
export function uploadChunk(uploadId, index, chunk, onProgress) {
  const form = new FormData();
  form.append('chunk', chunk);
  return http.post(`/files/multipart/${uploadId}/${index}`, form, {
    onUploadProgress: onProgress
  }).then((r) => r.data);
}

/** 大文件分片第三步：合并 */
export function completeMultipart(uploadId, orderId) {
  return http
    .post(`/files/multipart/${uploadId}/complete`, null, {
      params: orderId == null ? {} : { orderId }
    })
    .then((r) => r.data);
}

/** 工单附件列表 */
export function listByOrder(orderId) {
  return http.get(`/files/order/${orderId}`).then((r) => r.data);
}

/** 单文件签名 URL */
export function getSignedUrl(fileId) {
  return http.get(`/files/${fileId}/url`).then((r) => r.data);
}

/** 批量下载 ZIP（浏览器直接流式下载） */
export function downloadZip(ids) {
  return http.get('/files/zip', { params: { ids: ids.join(',') }, responseType: 'blob' })
    .then((r) => {
      const url = URL.createObjectURL(r.data);
      const a = document.createElement('a');
      a.href = url;
      a.download = `campus-repair-files-${Date.now()}.zip`;
      a.click();
      URL.revokeObjectURL(url);
    });
}
