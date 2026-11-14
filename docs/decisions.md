# 技术决策

| 需求 | 实现 | 限制 |
| --- | --- | --- |
| 只记录 App 和时间 | PACKAGE_USAGE_STATS + queryEvents；白名单消费 resume / pause / 屏幕 / 锁屏 / 重启事件 | 不读取类名、交互事件、通知或屏幕内容；显示名称受包可见性 / 卸载影响 |
| 5 秒合并 | 持久化会话 + 前台子区间；同包间隔 ≤ 5000 ms 合并一次 | 流向独立保存；避免合并抹去 A→B→A、避免把间隔算为前台时长 |
| 仅可交互时采集 | UserManager + PowerManager + KeyguardManager + DisplayManager；历史事件再门控 | 系统仍产生事件；解锁后批量拉取本身不排除历史熄屏区间 |
| 低功耗 | 无定时轮询、无前台服务；唯一 24h WorkManager + UI/解锁机会触发 | Worker 可能熄屏启动，状态检查不为零功耗；动态广播不能唤醒已死亡进程 |
| 7 天滚动 | 私有 SQLite，事务重写当前有效会话与报表；边界会话截断 | 停止执行期间无法准点物理删除；SQLite freelist 复用，不按历史线性增长 |
| 日报、周报 | 本地自然日 / 周一边界，每次刷新重建仍有源数据的日期和周 | 不保证午夜执行；当前日/周、首次授权日、过期周和有缺失区间的报表明确不完整 |
| 数据缺失 | >24h 未采集、撤权、时间回拨等保守标记；结束未知不计未知段时长 | 系统无公开逐事件覆盖水位，无法证明完全不漏；不把空结果当作完整零使用 |
| 后台禁止内容采集 | Manifest 无 INTERNET / Accessibility / 前台服务，备份关闭 | WorkManager 自带 WAKE_LOCK 和重启恢复基础设施，无法宣称完全无唤醒开销 |

## 事务与隐私

SQLite 一次事务写入会话、前台时间子区间、App 流向、名称快照、游标和必要恢复状态。中途锁屏 / 撤权会丢弃本次查询变更，避免提交半个批次。系统事件不保存原始对象，也不读取 `getClassName()`、`USER_INTERACTION` 或通知事件。工作日志不打印 App 使用记录。

增量区间为 `[cursor, now - 5 seconds)`，游标仅在查询成功后推进。5 秒尾部延迟减少刚发生事件的不确定性，但不能保证处理厂商迟到事件；因此所有报表注明系统尽力提供。

关闭记录会断开当前未知结束会话。再次开启从当前时间开始，不补录暂停区间。清空操作同时关闭记录并 VACUUM，不保留可查询使用记录。备份和设备迁移禁用；无导出或上传功能。

## 官方资料

- [UsageStatsManager：授权与事件保留](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
- [UsageEvents.Event：Activity、屏幕、锁屏、关机语义](https://developer.android.com/reference/android/app/usage/UsageEvents.Event)
- [UserManager.isUserUnlocked：凭据存储状态](https://developer.android.com/reference/android/os/UserManager#isUserUnlocked())
- [PowerManager.isInteractive：设备可交互状态](https://developer.android.com/reference/android/os/PowerManager#isInteractive())
- [WorkManager：非精确周期任务](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
- [后台广播限制](https://developer.android.com/about/versions/oreo/background#broadcasts)
