package com.qingluan.ledger

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/** AndroidX Material Icons, Apache-2.0. Keys are stable database/backup identifiers. */
object CategoryIcons {
    val labels =
        linkedMapOf(
            "food" to "餐饮",
            "bag" to "购物",
            "car" to "汽车",
            "home" to "居住",
            "circle" to "日用",
            "health" to "健康",
            "fun" to "音乐",
            "travel" to "旅行",
            "book" to "教育",
            "gift" to "礼物",
            "work" to "工作",
            "money" to "收款",
            "coffee" to "咖啡",
            "groceries" to "商超",
            "clothes" to "服饰",
            "phone" to "手机",
            "power" to "电费",
            "water" to "水费",
            "sports" to "运动",
            "pet" to "宠物",
            "child" to "育儿",
            "beauty" to "美容",
            "tools" to "维修",
            "insurance" to "保险",
            "tax" to "税费",
            "stock" to "投资",
            "refund" to "退款",
            "salary" to "工资",
            "bank" to "银行",
            "hotel" to "酒店",
            "train" to "火车",
            "bike" to "骑行",
            "bus" to "公交",
            "camera" to "摄影",
            "computer" to "电脑",
            "garden" to "园艺",
            "wallet" to "钱包",
            "savings" to "储蓄",
            "rent" to "租房",
            "delivery" to "快递",
        )

    fun vector(key: String): ImageVector =
        when (key) {
            "food" -> Icons.Outlined.Restaurant
            "bag" -> Icons.Outlined.ShoppingBag
            "car" -> Icons.Outlined.DirectionsCar
            "home" -> Icons.Outlined.Home
            "health" -> Icons.Outlined.FavoriteBorder
            "fun" -> Icons.Outlined.MusicNote
            "travel" -> Icons.Outlined.Flight
            "book" -> Icons.Outlined.MenuBook
            "gift" -> Icons.Outlined.CardGiftcard
            "work" -> Icons.Outlined.WorkOutline
            "money" -> Icons.Outlined.Payments
            "coffee" -> Icons.Outlined.LocalCafe
            "groceries" -> Icons.Outlined.LocalGroceryStore
            "clothes" -> Icons.Outlined.Checkroom
            "phone" -> Icons.Outlined.PhoneAndroid
            "power" -> Icons.Outlined.Bolt
            "water" -> Icons.Outlined.WaterDrop
            "sports" -> Icons.Outlined.SportsBasketball
            "pet" -> Icons.Outlined.Pets
            "child" -> Icons.Outlined.ChildCare
            "beauty" -> Icons.Outlined.Face
            "tools" -> Icons.Outlined.Build
            "insurance" -> Icons.Outlined.Shield
            "tax" -> Icons.Outlined.ReceiptLong
            "stock" -> Icons.Outlined.TrendingUp
            "refund" -> Icons.Outlined.Replay
            "salary" -> Icons.Outlined.Badge
            "bank" -> Icons.Outlined.AccountBalance
            "hotel" -> Icons.Outlined.Hotel
            "train" -> Icons.Outlined.Train
            "bike" -> Icons.Outlined.DirectionsBike
            "bus" -> Icons.Outlined.DirectionsBus
            "camera" -> Icons.Outlined.PhotoCamera
            "computer" -> Icons.Outlined.Computer
            "garden" -> Icons.Outlined.Park
            "wallet" -> Icons.Outlined.AccountBalanceWallet
            "savings" -> Icons.Outlined.Savings
            "rent" -> Icons.Outlined.Apartment
            "delivery" -> Icons.Outlined.LocalShipping
            else -> Icons.Outlined.Category
        }
}
