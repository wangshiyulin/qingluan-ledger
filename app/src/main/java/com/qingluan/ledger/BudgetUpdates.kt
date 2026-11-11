package com.qingluan.ledger

/** Apply one monthly edit without changing a legacy recurring default or another month. */
fun updatedMonthlyBudgets(
    current: List<BudgetRule>,
    original: BudgetRule?,
    next: BudgetRule,
): List<BudgetRule> {
    require(next.month.isNotBlank()) { "请选择预算月份" }
    require(original == null || current.any { it == original }) { "预算已变化，请重新打开" }
    require(current.none { it.key == next.key && it.limit > 0 && it.key != original?.key }) {
        "本月该币种和分类已有预算"
    }
    if (original != null && original.month.isBlank()) {
        require(current.none { it.month == next.month && it.scopeKey == original.scopeKey }) {
            "本月预算已变化，请重新打开"
        }
    }
    val result =
        current
            .filterNot {
                it.key == next.key || (original?.month == next.month && it.key == original.key)
            }
            .toMutableList()
    if (original != null && original.scopeKey != next.scopeKey) {
        val stopped = original.copy(month = next.month, limit = 0)
        result.removeAll { it.key == stopped.key }
        result.add(stopped)
    }
    result.add(next)
    BudgetRules.encode(result)
    return result
}
