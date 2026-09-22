// 前端 ESLint 配置（依据《前后台项目代码规范监测组件配置说明》）
// 使用方法：npm run lint      # 自动修复基础错误
//           npm run lint:check # 只检查不修改
module.exports = {
  root: true,
  env: {
    browser: true,
    es2021: true
  },
  extends: [
    'eslint:recommended',
    'plugin:vue/vue3-recommended'
  ],
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module'
  },
  plugins: ['vue'],
  rules: {
    // ---------- 基础规则（对照老师附录选取，可后续补充） ----------
    'quotes': ['error', 'single'],                 // 字符串使用单引号
    'semi': ['error', 'always'],                   // 语句必须分号结尾
    'eqeqeq': ['error', 'always'],                 // 必须使用全等
    'curly': ['error', 'all'],                     // if/for/while 必须使用 {}
    'camelcase': 'error',                          // 强制驼峰命名
    'no-console': 'warn',                          // 禁止 console，保留警告便于排查
    'no-debugger': 'error',                        // 禁止 debugger
    'no-unused-vars': ['error', { 'vars': 'all', 'args': 'after-used' }],
    'no-throw-literal': 'error',                   // 禁止抛出字面量异常
    'no-multiple-empty-lines': ['warn', { 'max': 2, 'maxEOF': 1 }],
    'no-trailing-spaces': 'warn',                  // 行尾不留空格

    // Vue 前端统一 2 空格缩进（后端 Java 按阿里规约为 4 空格）
    'indent': 'off'
  }
}