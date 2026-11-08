package com.smarttrader.app

fun main() {
    var passed = 0
    fun test(name: String, body: () -> Unit) { body(); println("PASS $name"); passed++ }
    val engine = PaperTradingEngine()
    test("opens valid BTC spot position with 1% target risk") {
        val r = engine.open(PaperOrderRequest("BTCUSDT", MarketType.SPOT, TradeSide.LONG, 100.0, 99.0, 102.0), "2026-11-09")
        check(r.accepted && r.position != null)
        check(kotlin.math.abs(r.position!!.riskUsdt - 0.1) < 0.02)
    }
    test("take profit closes position and records net PnL") {
        val trades = engine.mark("BTCUSDT", 102.0)
        check(trades.size == 1 && trades[0].reason == "TAKE_PROFIT" && trades[0].netPnl > 0)
        check(engine.snapshot().openPositions.isEmpty())
    }
    test("rejects spot short") {
        val r = engine.open(PaperOrderRequest("BTCUSDT", MarketType.SPOT, TradeSide.SHORT, 100.0, 101.0, 98.0), "2026-11-09")
        check(!r.accepted && r.message == "SPOT_SHORT_FORBIDDEN")
    }
    test("rejects cross margin") {
        val r = engine.open(PaperOrderRequest("BTCUSDT", MarketType.FUTURES, TradeSide.LONG, 100.0, 99.0, 102.0, 2.0, false), "2026-11-09")
        check(!r.accepted && r.message == "CROSS_MARGIN_FORBIDDEN")
    }
    test("rejects leverage above 2x") {
        val r = engine.open(PaperOrderRequest("BTCUSDT", MarketType.FUTURES, TradeSide.LONG, 100.0, 99.0, 102.0, 3.0, true), "2026-11-09")
        check(!r.accepted && r.message == "LEVERAGE_LIMIT_EXCEEDED")
    }
    test("rejects reward risk below 1:1.5") {
        val r = engine.open(PaperOrderRequest("BTCUSDT", MarketType.SPOT, TradeSide.LONG, 100.0, 99.0, 101.0), "2026-11-09")
        check(!r.accepted && r.message == "REWARD_RISK_BELOW_MINIMUM")
    }
    test("rejects missing protection through invalid direction") {
        val r = engine.open(PaperOrderRequest("BTCUSDT", MarketType.SPOT, TradeSide.LONG, 100.0, 100.0, 102.0), "2026-11-09")
        check(!r.accepted)
    }
    test("stop loss closes position and records negative PnL") {
        val r = engine.open(PaperOrderRequest("ETHUSDT", MarketType.SPOT, TradeSide.LONG, 100.0, 99.0, 102.0), "2026-11-09")
        check(r.accepted)
        val closed = engine.mark("ETHUSDT", 98.0)
        check(closed.size == 1 && closed[0].reason == "STOP_LOSS" && closed[0].netPnl < 0)
    }
    test("allows isolated futures short at 2x") {
        val e = PaperTradingEngine()
        val r = e.open(PaperOrderRequest("BTCUSDT", MarketType.FUTURES, TradeSide.SHORT, 100.0, 101.0, 98.0, 2.0, true), "2026-11-09")
        check(r.accepted && r.position!!.leverage == 2.0)
    }
    test("daily loss cap blocks new paper entries at 2 percent") {
        val cfg = PaperConfig(feeRate = 0.0, slippageRate = 0.0, maxOpenRiskPct = 10.0, reservePct = 0.0)
        val e = PaperTradingEngine(cfg)
        check(e.open(PaperOrderRequest("BTCUSDT", MarketType.FUTURES, TradeSide.LONG, 100.0, 99.0, 102.0, 2.0, true), "d1").accepted)
        check(e.open(PaperOrderRequest("ETHUSDT", MarketType.FUTURES, TradeSide.LONG, 100.0, 99.0, 102.0, 2.0, true), "d1").accepted)
        e.mark("BTCUSDT", 99.0); e.mark("ETHUSDT", 99.0)
        check(kotlin.math.abs(e.snapshot().dailyPnlUsdt + 0.2) < 1e-8)
        check(e.open(PaperOrderRequest("SOLUSDT", MarketType.FUTURES, TradeSide.LONG, 100.0, 99.0, 102.0, 2.0, true), "d1").message == "DAILY_LOSS_LIMIT")
    }
    test("daily loss baseline resets on next day") {
        val cfg = PaperConfig(feeRate = 0.0, slippageRate = 0.0, maxOpenRiskPct = 10.0, reservePct = 0.0)
        val e = PaperTradingEngine(cfg)
        e.open(PaperOrderRequest("BTCUSDT", MarketType.FUTURES, TradeSide.LONG, 100.0, 99.0, 102.0, 2.0, true), "d1")
        e.open(PaperOrderRequest("ETHUSDT", MarketType.FUTURES, TradeSide.LONG, 100.0, 99.0, 102.0, 2.0, true), "d1")
        e.mark("BTCUSDT", 99.0); e.mark("ETHUSDT", 99.0)
        val nextDay = e.open(PaperOrderRequest("SOLUSDT", MarketType.FUTURES, TradeSide.LONG, 100.0, 99.0, 102.0, 2.0, true), "d2")
        check(nextDay.accepted)
    }
    test("rejects unknown or non-positive market mark") {
        val e = PaperTradingEngine()
        check(e.mark("BTCUSDT", Double.NaN).isEmpty())
        check(e.mark("BTCUSDT", 0.0).isEmpty())
    }
    test("equity includes unrealized mark-to-market") {
        val e = PaperTradingEngine()
        val r = e.open(PaperOrderRequest("BTCUSDT", MarketType.FUTURES, TradeSide.LONG, 100.0, 99.0, 102.0, 2.0, true), "2026-11-09")
        check(r.accepted)
        val before = e.snapshot().equityUsdt
        e.mark("BTCUSDT", 101.0)
        check(e.snapshot().equityUsdt > before)
    }
    test("live gate refuses to arm without explicit user confirmation") {
        val gate = LiveTradingGate()
        val ready = LivePreflight(false, true, true, true, true, true, true, true)
        check(!gate.arm(ready) && gate.state == LiveGateState.DISABLED)
    }
    test("live gate arms only after all preflight checks") {
        val gate = LiveTradingGate()
        val ready = LivePreflight(true, true, true, true, true, true, true, true)
        check(gate.arm(ready) && gate.state == LiveGateState.ARMED)
    }
    test("live gate blocks duplicate client order IDs") {
        val gate = LiveTradingGate()
        check(gate.arm(LivePreflight(true, true, true, true, true, true, true, true)))
        val intent = LiveOrderIntent("client-1", "BTCUSDT", 0.10)
        check(gate.begin(intent, true, true))
        check(gate.onExchangeResult(ExchangeOrderState.ACCEPTED))
        check(!gate.begin(intent, true, true))
    }
    test("unknown order response blocks new orders until reconciliation") {
        val gate = LiveTradingGate()
        gate.arm(LivePreflight(true, true, true, true, true, true, true, true))
        check(gate.begin(LiveOrderIntent("client-2", "BTCUSDT", 0.10), true, true))
        gate.onExchangeResult(ExchangeOrderState.UNKNOWN)
        check(gate.state == LiveGateState.UNKNOWN_NEEDS_RECONCILIATION)
        check(!gate.begin(LiveOrderIntent("client-3", "ETHUSDT", 0.10), true, true))
        check(!gate.reconcile(ReconciliationState.UNKNOWN))
        check(gate.reconcile(ReconciliationState.DEFINITIVELY_NOT_FOUND))
        check(gate.state == LiveGateState.ARMED)
    }
    test("live gate enforces total risk and kill switch") {
        val gate = LiveTradingGate()
        gate.arm(LivePreflight(true, true, true, true, true, true, true, true))
        check(gate.begin(LiveOrderIntent("client-4", "BTCUSDT", 0.20), true, true))
        gate.onExchangeResult(ExchangeOrderState.ACCEPTED)
        check(!gate.begin(LiveOrderIntent("client-5", "ETHUSDT", 0.11), true, true))
        gate.killSwitch()
        check(gate.state == LiveGateState.PAUSED)
    }
    test("Futures sizing uses MEXC contract size, isolated margin and 2x cap") {
        val spec = FuturesContractSpec("BTC_USDT", 0.0001, 1.0, 1000.0, 1.0, 1, 100, 50, 3, 0.1, 0.0001, true, 0)
        val size = FuturesOrderSizer.calculate(spec, 100000.0, 99000.0, 2, 10.0, 0.0)
        check(size.accepted && size.contracts == 1.0 && size.marginUsdt < 10.0 && size.estimatedRiskUsdt <= 0.20)
    }
    test("Futures sizing rejects contracts not enabled for API") {
        val spec = FuturesContractSpec("BTC_USDT", 0.0001, 1.0, 1000.0, 1.0, 1, 100, 50, 3, 0.1, 0.0001, false, 0)
        check(FuturesOrderSizer.calculate(spec, 100000.0, 99000.0, 2, 10.0, 0.0).reason == "CONTRACT_NOT_API_TRADABLE")
    }
    test("Futures sizing respects country leverage ceiling") {
        val spec = FuturesContractSpec("BTC_USDT", 0.0001, 1.0, 1000.0, 1.0, 1, 100, 1, 3, 0.1, 0.0001, true, 0)
        check(FuturesOrderSizer.calculate(spec, 100000.0, 99000.0, 2, 10.0, 0.0).reason == "LEVERAGE_LIMIT_EXCEEDED")
    }
    test("Futures sizing rejects cross-only contract") {
        val spec = FuturesContractSpec("BTC_USDT", 0.0001, 1.0, 1000.0, 1.0, 1, 100, 50, 2, 0.1, 0.0001, true, 0)
        check(FuturesOrderSizer.calculate(spec, 100000.0, 99000.0, 2, 10.0, 0.0).reason == "ISOLATED_MARGIN_NOT_SUPPORTED")
    }
    test("Futures signature uses HMAC SHA-256 and 64 lowercase hex characters") {
        val sig = MexcFuturesSigner.signature("demoKey", "demoSecret", "1700000000000", "{\"symbol\":\"BTC_USDT\"}")
        check(sig.matches(Regex("[0-9a-f]{64}")))
        check(sig == MexcFuturesSigner.signature("demoKey", "demoSecret", "1700000000000", "{\"symbol\":\"BTC_USDT\"}"))
    }
    test("Futures GET query parameters are sorted deterministically") {
        check(MexcFuturesSigner.sortedQuery(mapOf("z" to "last", "a" to "first")) == "a=first&z=last")
    }
    println("$passed SMART TRADER core checks passed")
}
