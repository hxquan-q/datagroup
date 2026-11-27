---
name: ui-ux-design
description: Use when designing, building, auditing, or refining XIAOAI's product UI — pages, components, layout, spacing, typography, states, dark mode, responsive behavior, or design-system consistency. Distilled from the interface-design / atelier / ui-ux-pro-max agent skills, adapted to XIAOAI's enterprise data-console business scenario.
---

# UI/UX Design(源自 interface-design / atelier / ui-ux-pro-max,按 XIAOAI 业务场景裁剪)

本技能把公开的高质量 agent 设计技能(interface-design:dashboard/admin/数据界面;atelier:分轮次审查法;ui-ux-pro-max:优先级规则)蒸馏为 XIAOAI 可执行的工作方法。设计令牌与组件标准以 `.agents/rules/design-system.md` 为唯一事实来源,本文件不重复定义,只讲**如何判断好坏**。

## 业务场景锚点(Intent)

动手前先在脑中对齐三个问题,任何设计决策都要能回答"为什么":

- **用户是谁**:数据工程师 / 数据治理管理员,工作日上午坐在办公桌前,屏幕上同时开着数据库客户端和 IM。不是终端消费者,是专业人士。
- **要完成什么**:连接数据源 → 建模语义(主题/术语/指标)→ 发布 MCP 服务给 AI agent 用。高频动作是浏览列表、核对元数据、编辑表单、测试工具;低频动作是授权、删除。
- **应该是什么感觉**:冷静、精确、有把控感的控制台(calming console)——像 Linear/Stripe 后台,不像营销页。密度服务于扫描效率,留白服务于信心。

如果某个页面的排版让你想起"落地页/卡片堆叠的 demo",就是错了。

## 审查方法(按优先级逐轮过)

对任何页面做 UI/UX 审查时,按以下顺序逐轮检查。**必须实际渲染页面截图验证**(浅色+深色都要),不要只看代码猜测。代码审查只能发现不一致,视觉审查才能发现"对不对"。

### 第 1 轮:层级与对齐(Hierarchy)

- 每屏只有一个视觉主角:列表页是表格,详情页是当前 tab 内容,首页是 Hero。其他元素必须退后。
- 标题层级只用字重/字号/颜色区分,不靠加边框、加底色卡片套卡片。
- 对齐:同一列的控件左边缘对齐;工具栏控件同一基线;表格操作列右对齐。
- 页面标题(24px/720)→ 区块标题(18-20px)→ 卡片标题(15-17px)→ 正文(13-14px)→ 元信息(11-13px),跳级即缺陷。

### 第 2 轮:间距与密度(Spacing)

- 4px 基准网格:控件间距 8/12/16,卡片内边距 20-28,区块间距 20-24。出现 13px/17px 这类零碎值即缺陷。
- 同类页面同样式:所有列表页同一 padding(24px 28px),所有详情页同一结构。两页并排截图对比,间距不一致即缺陷。
- 检查"拥挤的工具栏"和"嵌套的框":表格外只许一层容器(DataTableShell),不允许页面再套卡片。

### 第 3 轮:排版与截断(Typography & Overflow)

- 用**最坏数据**压测:32 字符中文名、64 字符英文混合名、空描述、1284 行数据、endpoint 长路径。截断必须优雅(ellipsis + tooltip/title),不允许撑破容器、撑高行。
- 技术标识符(endpoint、ID、SQL、code)用 monospace,11-13px,secondary 色——它们是元信息不是正文。
- 数字(计数、版本号)是数据不是装饰:大号数字必须配 label,且是真实数据。

### 第 4 轮:状态完整性(States)

每个交互表面必须有全部状态,缺一即缺陷:

- loading(v-loading/skeleton,不用全屏 spinner 挡局部)、empty(el-empty + 场景文案)、error(notifyError + 保留原数据)。
- dirty/unsaved:表单编辑后必须有"未保存"指示 + 保存按钮禁用态反转,全产品同一模式(图标 + warning 色文字,见 MCP 服务详情页)。
- hover/focus/active:focus ring 两主题可见;可点击卡片必须有 cursor-pointer + hover 反馈 + 键盘可达。
- 状态语义一致:同一实体状态在表格、详情、徽章中颜色语义必须相同(如 DISABLED 在详情页是 amber/warning,列表就不能是 red/danger)。色彩语义对照:PUBLISHED=success、DRAFT=neutral/info、DISABLED=warning、删除/错误=danger。

### 第 5 轮:色彩与令牌(Tokens)

- 模板和 scoped style 中不允许出现裸 hex(除 styles/index.scss 令牌定义)。grep `#[0-9a-fA-F]{3,6}` 扫描 pages/components 即审计。
- 每页只有一种主色(--ep-color-primary),页面级不允许引入新强调色。
- 深色模式:not 反色,是重设计——画布近黑蓝、阴影更深更软、品牌面(侧边栏/signal card)保持稳定。

### 第 6 轮:交互与文案(Interaction & Copy)

- 一个表面一个主行动:heading-actions 里 primary 按钮只有一个。
- 按钮文案是动作("发布变更"/"添加别名"),不是名词;`+` 按钮文案不能直接用名词复数(反例:`+ 别名`)。
- 危险操作:confirm + danger 色 + 说明后果;不可逆操作文案要说清不可逆。
- 重复信息即噪音:同一时间戳/ID 在 header meta 和表单 footer 重复出现,保留决策性位置(footer,与保存动作相邻),删掉另一处。
- 键盘全流程:Tab 顺序合理、Enter 可触发卡片、方向键可切 tab(SegmentedTabs 已实现)、Esc 关弹窗。

### 第 7 轮:响应式(Responsive)

- 断点 768/1100px:侧边栏折叠、工具栏纵排、表格列精简(参考 McpServiceTable 的 compactColumns)。
- 网格用 `auto-fit minmax()` 自适应,不要写死列数导致行尾孤子(反例:4 个步骤用 repeat(3) 网格)。
- grid 子项数必须与列定义匹配,溢出项换行错位即缺陷。

## 结案清单(Ship Checklist)

提交 UI 改动前逐项确认:

1. 浅色 + 深色截图对比过关键页面。
2. 最坏数据(长名称/空态/多行)压测过至少一个表格页。
3. `pnpm build` 通过;相关 vitest 通过。
4. 新增用户可见文案双语(zh.ts/en.ts)齐全,键名按功能命名空间嵌套。
5. 没有引入裸 hex、没有新组件库、没有 emoji 图标。
6. 与 design-system.md 冲突时,要么遵从,要么修改规则文件并说明——不允许静默偏离。

## 常见缺陷速查(XIAOAI 实例)

| 缺陷模式 | 正确做法 | 参考 |
|---|---|---|
| 页面标题用 Tailwind 类覆盖(如 `text-2xl font-semibold`) | 用 `.page-heading h1` 全局样式,不加类 | settings/api-keys 修复前 |
| 表单页脚 unsaved 提示样式不一 | InfoFilled 图标 + `--ep-color-warning` 文字 | mcp-services/[id] |
| 只读元数据渲染不统一 | `font-mono text-xs secondary select-all` | mcp-services/[id] basic tab |
| 死 CSS(旧 hero/tabs 样式残留) | 删掉,scoped style 不允许留无引用的类 | — |
| 状态色跨页面不一致(Disabled 红/黄) | 统一语义对照表(见第 4 轮) | McpServiceTable |
