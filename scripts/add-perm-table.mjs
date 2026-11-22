#!/usr/bin/env node
/*
 * add-perm-table.mjs — 把权限表配置块安全合并进 config/permissions.json
 *
 * 用法:
 *   node scripts/add-perm-table.mjs [--config <主配置路径>] [--file <snippet.json>] [--dry-run]
 *   node scripts/add-perm-table.mjs --file snippet.json
 *   cat snippet.json | node scripts/add-perm-table.mjs --dry-run
 *
 * snippet 格式（四段，对应 项目→环境→库→表 层级）:
 *   {"project":     {"code","name"?},
 *    "environment": {"code","name"?},
 *    "database":    {"code","name"?,"jdbcUrl","username"?,"password"?,"driverClassName"?,"autoInit"?},
 *    "table":       {...permissionTables 元素的完整结构...}}
 *
 * 行为:
 *   - 按 code 查找/创建 project → environment → database 层级
 *   - table 追加到该 database.permissionTables；同 code 则替换并打印提示
 *   - database.code 全局唯一校验：若已有同名 code 但位于其他 project/environment
 *     组合下、或同名 code 的 jdbcUrl 不同 → 判定为"不同库撞名"，报错 exit 1
 *   - 写回 JSON 固定 2 空格缩进 + 末尾换行；--dry-run 只把合并结果打印到 stdout，不落盘
 *   - 所有提示/诊断走 stderr，stdout 只输出合并后的 JSON（便于管道）
 */

import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const USAGE = `用法:
  node scripts/add-perm-table.mjs [--config <path>] [--file <snippet.json>] [--dry-run]

选项:
  --config <path>  主配置文件路径，默认 config/permissions.json
  --file <path>    snippet JSON 文件；省略时从 stdin 读取
  --dry-run        只打印合并结果到 stdout，不写回文件
  -h, --help       显示帮助

snippet 示例:
  {"project":{"code":"crm","name":"CRM系统"},
   "environment":{"code":"dev","name":"开发环境"},
   "database":{"code":"crm-dev-db","name":"CRM开发库",
               "jdbcUrl":"jdbc:mysql://localhost:3306/crm","username":"root","password":"***"},
   "table":{"code":"user_perm","name":"用户权限表","tableName":"t_perm",
            "fields":{"personId":"emp_no","personName":"emp_name","scope":"scope","permission":"perm"},
            "otherFields":{"create_by":"perm-tool","create_time":"\${now}"},
            "scopes":[{"value":"ALL","label":"全部数据"}],
            "permissions":[{"value":"R","label":"查询"}]}}
`;

const note = (msg) => console.error(`[add-perm-table] ${msg}`);
const die = (msg) => { console.error(`[add-perm-table] 错误: ${msg}`); process.exit(1); };

// ---------- 参数解析 ----------

function parseArgs(argv) {
  const opts = { config: 'config/permissions.json', file: null, dryRun: false };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--config') opts.config = argv[++i] ?? die('--config 缺少参数值');
    else if (a === '--file') opts.file = argv[++i] ?? die('--file 缺少参数值');
    else if (a === '--dry-run') opts.dryRun = true;
    else if (a === '-h' || a === '--help') { console.log(USAGE); process.exit(0); }
    else die(`未知参数 "${a}"\n\n${USAGE}`);
  }
  if (!opts.config) die('--config 缺少参数值');
  if (opts.file === undefined) die('--file 缺少参数值');
  return opts;
}

// ---------- JSON 读取 + 友好的语法错误定位 ----------

function locateJsonError(err, text) {
  const mLC = err.message.match(/line (\d+) column (\d+)/);
  const mPos = err.message.match(/position (\d+)/);
  let line = null, col = null;
  if (mLC) { line = +mLC[1]; col = +mLC[2]; }
  else if (mPos) {
    const pos = +mPos[1];
    const upto = text.slice(0, pos);
    line = upto.split('\n').length;
    col = pos - upto.lastIndexOf('\n');
  }
  let out = err.message;
  if (line != null) {
    const src = (text.split('\n')[line - 1] ?? '').trim();
    out += `\n  → 第 ${line} 行第 ${col} 列附近: ${src.slice(0, 120)}`;
  }
  return out;
}

function parseJson(text, sourceDesc) {
  try {
    return JSON.parse(text);
  } catch (e) {
    die(`${sourceDesc} JSON 语法错误，未做任何修改:\n  ${locateJsonError(e, text)}`);
  }
}

function readJsonFile(path, sourceDesc) {
  let text;
  try {
    text = readFileSync(path, 'utf8');
  } catch (e) {
    die(`${sourceDesc}读取失败 ${path}: ${e.message}`);
  }
  return parseJson(text, `${sourceDesc}(${path})`);
}

async function readStdin() {
  const chunks = [];
  for await (const c of process.stdin) chunks.push(c);
  return Buffer.concat(chunks).toString('utf8');
}

// ---------- 校验辅助 ----------

const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);

function need(obj, key, ctx) {
  const v = isObj(obj) ? obj[key] : undefined;
  if (typeof v !== 'string' || !v.trim()) die(`snippet 缺少必填字段 ${ctx}.${key}（非空字符串）`);
  return v;
}

function validateSnippet(snippet) {
  if (!isObj(snippet)) die('snippet 必须是 JSON 对象');
  for (const k of ['project', 'environment', 'database', 'table'])
    if (!isObj(snippet[k])) die(`snippet 缺少对象字段 "${k}"`);

  need(snippet.project, 'code', 'project');
  need(snippet.environment, 'code', 'environment');
  need(snippet.database, 'code', 'database');
  need(snippet.database, 'jdbcUrl', 'database');

  const t = snippet.table;
  need(t, 'code', 'table');
  need(t, 'tableName', 'table');

  const f = t.fields;
  if (!isObj(f)) die('table.fields 必须是对象 {"personId","personName","scope","permission"}');
  need(f, 'personId', 'table.fields');
  need(f, 'scope', 'table.fields');
  need(f, 'permission', 'table.fields');
  if (f.personName !== undefined && f.personName !== null && typeof f.personName !== 'string')
    die('table.fields.personName 必须是列名字符串或 null（姓名列可空时配 null）');

  if (t.otherFields !== undefined && !isObj(t.otherFields))
    die('table.otherFields 必须是对象 {列名: 填充值}，占位符支持 ${personId}/${personName}/${now}');

  for (const k of ['scopes', 'permissions']) {
    if (!Array.isArray(t[k]) || t[k].length === 0)
      die(`table.${k} 必须是非空数组 [{"value","label"}]`);
    t[k].forEach((item, i) => {
      if (!isObj(item) || typeof item.value !== 'string' || typeof item.label !== 'string')
        die(`table.${k}[${i}] 需要 {"value":"存储值","label":"显示名"}，value 一律写字符串（数字存储值也要加引号）`);
    });
  }
}

// ---------- 主流程 ----------

const opts = parseArgs(process.argv);
const configPath = resolve(opts.config);

const config = readJsonFile(configPath, '主配置文件');
if (!isObj(config)) die('主配置不是 JSON 对象');
config.projects ??= [];
if (!Array.isArray(config.projects)) die('主配置 projects 必须是数组');

let snippetText;
if (opts.file) {
  try {
    snippetText = readFileSync(resolve(opts.file), 'utf8');
  } catch (e) {
    die(`snippet 文件读取失败 ${opts.file}: ${e.message}`);
  }
} else {
  if (process.stdin.isTTY) die(`未提供 --file 且 stdin 无输入。\n\n${USAGE}`);
  snippetText = await readStdin();
}
if (!snippetText.trim()) die('snippet 内容为空');

const snippet = parseJson(snippetText, 'snippet');
validateSnippet(snippet);

const pCode = snippet.project.code;
const eCode = snippet.environment.code;
const dCode = snippet.database.code;
const dJdbc = snippet.database.jdbcUrl;
const tCode = snippet.table.code;

// project → environment 层级查找/创建
let project = config.projects.find((p) => p.code === pCode);
if (!project) {
  project = { code: pCode, name: snippet.project.name ?? pCode, environments: [] };
  config.projects.push(project);
  note(`新建 project "${pCode}"`);
}
project.environments ??= [];

let env = project.environments.find((e) => e.code === eCode);
if (!env) {
  env = { code: eCode, name: snippet.environment.name ?? eCode, databases: [] };
  project.environments.push(env);
  note(`新建 environment "${pCode}/${eCode}"`);
}
env.databases ??= [];

// database.code 全局唯一校验：同名 code 出现在其他 project/environment 组合下，
// 或同名 code 的 jdbcUrl 不同 → 视为不同库撞名，拒绝合并。
const conflicts = [];
for (const p of config.projects)
  for (const e of p.environments ?? [])
    for (const d of e.databases ?? [])
      if (d.code === dCode && (p.code !== pCode || e.code !== eCode || d.jdbcUrl !== dJdbc))
        conflicts.push(`"${p.code}/${e.code}/${d.code}" (jdbcUrl=${d.jdbcUrl})`);
if (conflicts.length)
  die(`database.code "${dCode}" 冲突：配置中已存在同名但不同的 database：\n  ${conflicts.join('\n  ')}\n` +
      `database.code 必须全局唯一（后端按 dbCode 定位数据源），请更换 code 或修正 jdbcUrl。`);

let db = env.databases.find((d) => d.code === dCode);
if (!db) {
  db = { ...snippet.database, name: snippet.database.name ?? dCode, permissionTables: [] };
  env.databases.push(db);
  note(`新建 database "${pCode}/${eCode}/${dCode}"`);
}
db.permissionTables ??= [];

const idx = db.permissionTables.findIndex((t) => t.code === tCode);
if (idx >= 0) {
  db.permissionTables[idx] = snippet.table;
  note(`table "${tCode}" 在 database "${dCode}" 中已存在 → 已按 code 替换`);
} else {
  db.permissionTables.push(snippet.table);
  note(`table "${tCode}" 已追加到 "${pCode}/${eCode}/${dCode}".permissionTables`);
}

const out = JSON.stringify(config, null, 2) + '\n';
if (opts.dryRun) {
  note('--dry-run：未写回文件，合并结果输出到 stdout');
  process.stdout.write(out);
} else {
  writeFileSync(configPath, out, 'utf8');
  note(`已写回 ${configPath}`);
}
