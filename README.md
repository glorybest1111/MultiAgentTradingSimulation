# MultiAgentTradingSimulation — 多智能体智能金融市场模拟平台

Java 课程项目。本项目不仅是股票交易模拟器，而是一个结合**订单驱动市场、多智能体 AI 决策与金融实验分析**的平台：通过不同的 AI 交易策略、市场机制与风险控制模型，研究智能体如何影响市场价格形成，以及不同交易策略在不同市场环境中的表现。

## 功能特性（对应项目创新点）

| 创新点 | 实现 |
| --- | --- |
| 核心撮合引擎 | 价格优先 + 时间优先的订单簿与撮合引擎，支持 **LIMIT / MARKET / STOP / IOC / ICEBERG** 五种订单类型 |
| 交易成本模型 | 手续费（0.1%）+ 滑点模型（订单簿层级行走 + 有限虚拟深度） |
| 多智能体 AI 交易比赛 | Momentum（趋势）、Value（价值）、HFT（高频抢帽子）、MarketMaker（做市商）同场竞技，控制台 Dashboard 实时排名 |
| AI 辩论决策机制 | BullAgent / BearAgent 各自陈述证据，JudgeAgent 仲裁后执行，提高决策可信度 |
| 新闻情绪分析 | NewsEngine 随机发布新闻，SentimentAnalyzer 关键词打分，情绪影响价格漂移与 Agent 决策 |
| 可解释 AI | 每个 AI 的每次决策记录动作、置信度与理由，落盘 `data/decision_log.csv`；每笔成交记录 `data/trade_log.csv` |
| 市场实验设计 | 实验 1 策略竞赛 / 实验 2 做市商前后对比 / 实验 3 辩论 vs 单一 AI |

## 项目结构

```
src/main/java/com/trading/
├── Main.java                  # 入口：三个实验 + 控制台 Dashboard（--human 交互模式）
├── model/                     # 金融对象
│   ├── Order.java             #   订单（五种类型，冰山单/止损单参数）
│   ├── Trade.java             #   成交记录（含成交原因）
│   ├── Decision.java          #   AI 决策记录（动作/数量/置信度/理由）
│   ├── Side.java              #   BUY / SELL
│   ├── OrderType.java         #   LIMIT / MARKET / STOP / IOC / ICEBERG
│   └── Action.java            #   BUY / SELL / HOLD
├── engine/                    # 交易逻辑
│   ├── OrderBook.java         #   订单簿：价格优先 + 时间优先
│   └── MatchingEngine.java    #   撮合引擎：双向撮合、止损触发、虚拟深度滑点
├── account/                   # 账户体系
│   ├── Account.java           #   现金/持仓/加权平均成本/已实现盈亏/手续费
│   └── Portfolio.java         #   账户汇总与排名
├── agent/                     # 交易者
│   ├── TraderAgent.java       #   交易者统一接口
│   ├── TradingApi.java        #   下单/撤单接口
│   ├── MarketSnapshot.java    #   行情快照
│   ├── HumanTrader.java       #   人类交易者（控制台交互）
│   ├── MomentumTrader.java    #   趋势交易（带保护性止损单）
│   ├── ValueTrader.java       #   价值投资（仓位上限）
│   ├── HighFrequencyTrader.java # 高频抢帽子
│   ├── MarketMakerAgent.java  #   做市商（双边报价 + 库存倾斜）
│   ├── DebateTrader.java      #   辩论交易者
│   └── DecisionLog.java       #   CSV 决策/成交日志
├── ai/                        # 多 Agent 辩论系统
│   ├── BullAgent.java / BearAgent.java / JudgeAgent.java
│   └── DebateSystem.java / Opinion.java
├── market/                    # 市场数据
│   ├── PriceHistory.java      #   均线/动量/波动率
│   ├── NewsEngine.java        #   新闻引擎
│   ├── SentimentAnalyzer.java #   情绪分析（关键词规则，可替换为 LLMClient）
│   └── NewsItem.java
├── risk/                      # 风险模型
│   ├── FeeCalculator.java     #   手续费（0.1%）
│   └── SlippageModel.java     #   滑点（簿内行走 + 虚拟深度）
├── simulation/                # 仿真框架
│   ├── MarketSimulation.java  #   主循环：新闻→价格→止损→Agent 决策→结算
│   ├── SimulationConfig.java  #   参数集中配置（可复现实验）
│   └── PerformanceTracker.java # 净值序列/收益率/最大回撤
└── test/                      # 测试
    ├── CoreEngineTest.java    #   核心引擎单元测试（53 个断言）
    ├── SimulationSmokeTest.java # 端到端冒烟测试
    └── SimulationMain.java    #   入门演示
```

## 快速开始

### 方式一：IntelliJ IDEA（推荐）

1. 打开 IDEA → Open → 选择本项目文件夹（配置好的 `.idea` 会自动加载，JDK 21）
2. 运行 `com.trading.Main` → 输出三个实验的完整报告
3. 运行 `com.trading.test.CoreEngineTest` → 单元测试
4. 运行 `com.trading.Main` 时加参数 `--human` → 人类交互交易模式

### 方式二：命令行

```bash
# 编译（JDK 21，-encoding UTF-8 保证中文正常）
javac -encoding UTF-8 -d out/production/main $(find src/main/java -name "*.java")

# 运行完整实验（三个实验 + 可解释 AI 样本）
java -Dstdout.encoding=UTF-8 -cp out/production/main com.trading.Main

# 单元测试
java -Dstdout.encoding=UTF-8 -cp out/production/main com.trading.test.CoreEngineTest

# 冒烟测试
java -Dstdout.encoding=UTF-8 -cp out/production/main com.trading.test.SimulationSmokeTest

# 人类交互模式（30 期，你 vs 做市商）
java -Dstdout.encoding=UTF-8 -cp out/production/main com.trading.Main --human
```

> Windows CMD 中文乱码时先执行 `chcp 65001`。

## 实验设计（随机种子固定，结果可复现）

### 实验 1：AI 策略竞赛（300 期，初始资金 $100,000，seed=42）

| Rank | Strategy | Return | MaxDrawdown | Trades | 分析 |
| --- | --- | --- | --- | --- | --- |
| 1 | MarketMaker | +3.13% | 6.07% | 519 | 价差收益稳定，成交量最大 |
| 2 | ValueTrader | -0.41% | 2.01% | 55 | 逆向投资回撤最小 |
| 3 | HFTrader | -1.53% | 2.25% | 70 | 高频小额，交易成本敏感 |
| 4 | DebateTrader | -3.44% | 4.32% | 297 | 辩论仲裁，决策全程可解释 |
| 5 | MomentumTrader | -10.20% | 16.56% | 258 | 单边下跌市中追涨受挫，止损控制损失 |

结论：本期价格路径为震荡下跌（150 → 135.45），做市商无论市况如何通过价差盈利；趋势策略在下跌市中表现最差，但其保护性止损单真实触发 8 次，将损失控制在 3% 以内。

### 实验 2：加入做市商前后对比（同一随机种子）

| 指标 | 无做市商 | 有做市商 | 变化 |
| --- | --- | --- | --- |
| 平均价差 | 0.20（滑点隐性成本） | 0.04 | 收窄 80% |
| 总成交量 | 3777 | 4404 | **+16.6%** |
| 主动方平均收益 | -4.98% | -3.90% | 执行成本下降 |

结论：做市商提供持续双边报价，显著降低价差与执行成本、提高成交量与流动性。

### 实验 3：AI 辩论 vs 单一 AI

辩论系统让 AI 不直接交易：Bull（看涨证据）与 Bear（看跌证据）分别陈述，Judge 综合仲裁。辩论全过程记录在决策日志，可追溯每次交易的完整推理链，相比单一 AI 黑盒决策显著提高可信度与可研究性。

## 测试

- `CoreEngineTest`：作业要求的 4 个场景（买卖成交 / 多订单排序 / 部分成交 / 无法成交）+ 卖单主动成交、市价单、IOC、止损、冰山、手续费、滑点、账户盈亏，共 53 个断言
- `SimulationSmokeTest`：端到端验证 5 个 Agent 完整仿真 + 做市商前后对比

## 设计说明

- **AI 无外部依赖**：三个 AI 策略、辩论系统与情绪分析均采用规则引擎实现，无 LLM API 依赖、离线可运行、结果可复现；`SentimentAnalyzer` 预留了替换为真实 LLMClient 的位置
- **价格过程**：随机游走 + 新闻冲击 + 情绪漂移，按 0.01 最小变动价位取整
- **冰山单简化**：切片成交后自动补充，不做队尾重排（真实交易所会重排，作为未来扩展点）
- **确定性**：全部实验使用固定随机种子，答辩演示结果与文档一致
