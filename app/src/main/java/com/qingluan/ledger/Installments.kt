package com.qingluan.ledger

import androidx.room.*
import java.time.*
import org.json.JSONObject

@Entity(
    tableName = "installment_plans",
    foreignKeys =
        [
            ForeignKey(
                entity = Category::class,
                parentColumns = ["id"],
                childColumns = ["categoryId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index("categoryId")],
)
data class InstallmentPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long,
    val currencyCode: String = "CNY",
    val principal: Long,
    val fee: Long = 0,
    val terms: Int,
    val paidTerms: Int = 0,
    val nextDate: String = LocalDate.now().toString(),
    val anchorDay: Int = LocalDate.parse(nextDate).dayOfMonth,
    val enabled: Boolean = true,
    val note: String = "",
)

/** Earlier terms receive one extra minor unit; all terms add up to principal plus fee. */
fun installmentTermAmount(plan: InstallmentPlan, index: Int = plan.paidTerms): Long {
    require(plan.terms in 1..120 && index in 0 until plan.terms) { "分期期数无效" }
    val total = Math.addExact(plan.principal, plan.fee)
    require(plan.principal > 0 && plan.fee >= 0 && total >= plan.terms) { "分期金额无效" }
    return total / plan.terms + if (index < total % plan.terms) 1 else 0
}

fun nextInstallmentDate(plan: InstallmentPlan): LocalDate =
    YearMonth.from(LocalDate.parse(plan.nextDate))
        .plusMonths(1)
        .let { it.atDay(minOf(plan.anchorDay, it.lengthOfMonth())) }
        .also { require(it.year in 1970..2200) { "分期日期超出支持范围" } }

object Installments {
    fun validate(plan: InstallmentPlan, categories: List<Category>) {
        require(
            plan.id >= 0 &&
                plan.name.isNotBlank() &&
                plan.name.length <= 50 &&
                plan.note.length <= 900
        ) {
            "分期名称或备注无效"
        }
        require(Currencies.contains(plan.currencyCode)) { "分期币种无效" }
        require(plan.terms in 1..120 && plan.paidTerms in 0..plan.terms) { "分期期数无效" }
        installmentTermAmount(plan, 0)
        require(categories.any { it.id == plan.categoryId && it.type == "EXPENSE" }) { "请选择支出分类" }
        require(plan.anchorDay in 1..31) { "分期日期无效" }
        val date = LocalDate.parse(plan.nextDate)
        require(
            date.year in 1970..2200 &&
                date.dayOfMonth == minOf(plan.anchorDay, date.lengthOfMonth())
        ) {
            "分期日期与还款日不符"
        }
        // Ensure the complete remaining schedule is representable before saving or restoring.
        val finalMonth =
            YearMonth.from(date)
                .plusMonths((plan.terms - plan.paidTerms - 1).coerceAtLeast(0).toLong())
        require(finalMonth.year in 1970..2200) { "分期结束日期超出支持范围" }
    }

    fun encode(plan: InstallmentPlan): JSONObject =
        JSONObject()
            .put("id", plan.id)
            .put("name", plan.name)
            .put("categoryId", plan.categoryId)
            .put("currencyCode", plan.currencyCode)
            .put("principal", plan.principal)
            .put("fee", plan.fee)
            .put("terms", plan.terms)
            .put("paidTerms", plan.paidTerms)
            .put("nextDate", plan.nextDate)
            .put("anchorDay", plan.anchorDay)
            .put("enabled", plan.enabled)
            .put("note", plan.note)

    private fun number(j: JSONObject, key: String): Long {
        val value = j.get(key)
        require(value is Number) { "分期数值无效" }
        return java.math.BigDecimal(value.toString()).longValueExact()
    }

    fun decode(j: JSONObject): InstallmentPlan =
        InstallmentPlan(
            id = number(j, "id"),
            name = j.getString("name"),
            categoryId = number(j, "categoryId"),
            currencyCode = j.getString("currencyCode"),
            principal = number(j, "principal"),
            fee = number(j, "fee"),
            terms = Math.toIntExact(number(j, "terms")),
            paidTerms = Math.toIntExact(number(j, "paidTerms")),
            nextDate = j.getString("nextDate"),
            anchorDay = Math.toIntExact(number(j, "anchorDay")),
            enabled = j.getBoolean("enabled"),
            note = j.getString("note"),
        )
}

suspend fun Repository.saveInstallment(plan: InstallmentPlan) =
    db.withTransaction {
        Installments.validate(plan, dao.allCategories())
        if (plan.id == 0L) {
            require(plan.paidTerms == 0) { "新分期不能带有已还期数" }
            require(dao.allInstallments().size < 1000) { "分期数量已达上限" }
            dao.addInstallment(plan)
        } else {
            val old = dao.installment(plan.id) ?: error("分期已不存在")
            require(plan.paidTerms == old.paidTerms) { "分期进度已变化，请刷新" }
            if (old.paidTerms > 0)
                require(
                    plan.currencyCode == old.currencyCode &&
                        plan.principal == old.principal &&
                        plan.fee == old.fee &&
                        plan.terms == old.terms &&
                        plan.nextDate == old.nextDate &&
                        plan.anchorDay == old.anchorDay
                ) {
                    "已入账分期不能修改金额、币种、期数或日期"
                }
            dao.updateInstallment(plan)
        }
    }

suspend fun Repository.confirmInstallment(id: Long, expectedPaid: Int) =
    db.withTransaction {
        val plan = dao.installment(id) ?: error("分期已不存在")
        require(
            plan.enabled &&
                plan.paidTerms == expectedPaid &&
                plan.paidTerms < plan.terms &&
                !LocalDate.parse(plan.nextDate).isAfter(LocalDate.now())
        ) {
            "分期状态已变化或尚未到期，请刷新"
        }
        Installments.validate(plan, dao.allCategories())
        val paid = plan.paidTerms + 1
        val next = if (paid == plan.terms) plan.nextDate else nextInstallmentDate(plan).toString()
        save(
            Entry(
                type = "EXPENSE",
                amount = installmentTermAmount(plan),
                categoryId = plan.categoryId,
                currencyCode = plan.currencyCode,
                occurredAt =
                    LocalDate.parse(plan.nextDate)
                        .atTime(LocalTime.now())
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli(),
                note =
                    "${plan.name}（第${paid}/${plan.terms}期）" +
                        if (plan.note.isBlank()) "" else " ${plan.note}",
            )
        )
        dao.updateInstallment(
            plan.copy(paidTerms = paid, nextDate = next, enabled = paid < plan.terms)
        )
    }

suspend fun Repository.setInstallmentEnabled(id: Long, expectedPaid: Int, enabled: Boolean) =
    db.withTransaction {
        val plan = dao.installment(id) ?: error("分期已不存在")
        require(plan.paidTerms == expectedPaid && plan.paidTerms < plan.terms) { "分期进度已变化或已完成" }
        dao.updateInstallment(plan.copy(enabled = enabled))
    }

suspend fun Repository.deleteInstallment(id: Long, expectedPaid: Int) =
    db.withTransaction {
        val plan = dao.installment(id) ?: error("分期已不存在")
        require(plan.paidTerms == expectedPaid) { "分期进度已变化，请刷新" }
        dao.deleteInstallment(id)
    }
