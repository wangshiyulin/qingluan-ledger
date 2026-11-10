# 青鸾记账实现记录
用户已批准 V1 规格及方案。单 app 模块、API26、CNY、替换恢复、自然周期、Canvas、Haze。
工程位于独立 implementation 分支。Room 数据约束、MVVM、四主页面、备份、图表已实现，待构建验证。
Ruling: compileSdk/targetSdk 35，使用已知兼容稳定依赖组合；保持 API26 最低版本。
初始测试尝试被 Gradle 下载连接问题阻断，不宣称完成红绿测试循环。
Final review: external reviewer found integer coercion, duplicate save, unbounded main-thread restore. Fixed strict numeric decode, ViewModel in-flight guard, 16MiB bounded read and worker decode. Added backup regression tests.
Build environment: installed SDK35; discovered system Java21 is runtime-only, provisioning JDK17.
Verification stage: business tests and Android UI/database tests added. Source formatted with ktfmt. SDK35 and JDK17 available. First build downloads dependencies; emulator software image provisioning in progress (no /dev/kvm).
Final build: test + assembleDebug + assembleDebugAndroidTest successful; debug/release unit suites each 11/11. APK signature and merged permissions verified. Instrumentation runtime blocked by emulator disk minimum and no connected device; not marked passed. Details in verification.md.
1.0.1: Haze required background specified; Scaffold bottom safe area fixed after touch bounds evidence; category type mutation rejected after failing Room regression; migration list scrollable. 19 Debug tests green including SDK28/34/36 navigation+CRUD; lint 0 errors; APK signatures match original. No actual OnePlus hardware logs/GPU verification.
1.1.0: screenshot UI requirements implemented, product copy preference persisted in AGENTS.md. Popup category-first entry, note/amount draft restoration, separate settings destinations, compact detail/insight screens. New tests first failed against prior UI, then 19/19 green including SDK28/34/36 draft + CRUD flows. Final build, instrumentation APK compilation and lint succeeded.
1.2.0: compact single-row summary, bordered compact entries, drag-reorder category grid and bottom actions, insights structural update and removal of repeated page titles. 25/25 Debug regressions green including SDK28/34/36 touch drag persistence. APK, instrumentation APK and lint build successful.
1.3.0: screenshot UI requests implemented; 30/30 tests green including SDK28/34/36 grid layout, inline notes, backspace, donut navigation and date-clipping tests. APK, instrumentation APK, lint, APK signature and 16KB ZIP alignment passed. No physical OnePlus GPU claim.
1.4.0: all screenshot tasks and immediate navigation/FAB/liquid-bar requests implemented. 33/33 tests passed including SDK28/34/36 paused-clock rapid navigation and themed date dialog. Build, test APK, lint, signature and 16KB ZIP alignment succeeded. GPU/splash real-device claims excluded.
1.4.1: user-provided Qingluan bird/ledger branding adapted using image editing tool, integrated for launcher/round icon, startup and empty state; removed old leaf assets. Build+lint+signature+ZIP alignment passed. Prior pending1.4.0 features included; no new business tests for resource-only update.
