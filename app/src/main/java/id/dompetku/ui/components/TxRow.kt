package id.dompetku.ui.components

import id.dompetku.core.t

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Money
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.dompetku.data.db.CategoryEntity
import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import id.dompetku.domain.Tx
import id.dompetku.domain.TxType
import id.dompetku.ui.theme.AmountStyle
import id.dompetku.ui.theme.LocalDompetkuColors

fun categoryIcon(name: String?): ImageVector = when (name) {
    "restaurant" -> Icons.Outlined.Restaurant
    "directions_car" -> Icons.Outlined.DirectionsCar
    "shopping_bag" -> Icons.Outlined.ShoppingBag
    "home" -> Icons.Outlined.Home
    "receipt" -> Icons.Outlined.Receipt
    "favorite" -> Icons.Outlined.Favorite
    "movie" -> Icons.Outlined.Movie
    "school" -> Icons.Outlined.School
    "family" -> Icons.Outlined.FamilyRestroom
    "volunteer" -> Icons.Outlined.VolunteerActivism
    "credit" -> Icons.Outlined.CreditCard
    "trending_up" -> Icons.Outlined.TrendingUp
    "work" -> Icons.Outlined.Work
    "star" -> Icons.Outlined.Star
    "laptop" -> Icons.Outlined.Laptop
    "add" -> Icons.Outlined.Add
    "undo" -> Icons.Outlined.Undo
    else -> Icons.Outlined.MoreHoriz
}

fun accountIcon(t: AccountType): ImageVector = when (t) {
    AccountType.CASH -> Icons.Outlined.Money
    AccountType.BANK -> Icons.Outlined.AccountBalance
    AccountType.EWALLET -> Icons.Outlined.AccountBalanceWallet
    AccountType.SAVINGS -> Icons.Outlined.Savings
    AccountType.DEPOSIT -> Icons.Outlined.Lock
    AccountType.INVESTMENT -> Icons.Outlined.TrendingUp
    AccountType.GOLD -> Icons.Outlined.Toll
    AccountType.OTHER_ASSET -> Icons.Outlined.Payments
}

@Composable
fun TxRow(
    tx: Tx,
    accounts: Map<Long, Account>,
    categories: Map<Long, CategoryEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalDompetkuColors.current
    val cat = tx.categoryId?.let { categories[it] }
    val src = accounts[tx.sourceAccountId]?.name ?: "?"
    val (title, sub, icon) = when (tx.type) {
        TxType.TRANSFER -> Triple(
            tx.description ?: "Transfer", "$src → ${accounts[tx.destinationAccountId]?.name ?: "?"}", Icons.Outlined.SwapHoriz,
        )
        TxType.ADJUSTMENT -> Triple(tx.description ?: t("Penyesuaian saldo"), src, Icons.Outlined.Tune)
        else -> Triple(
            tx.merchant ?: tx.description ?: cat?.name ?: tx.type.label,
            listOfNotNull(cat?.name.takeIf { tx.merchant != null || tx.description != null }, src,
                id.dompetku.ui.tx.Tags.display(tx.tags).ifEmpty { null }).joinToString(" · "),
            categoryIcon(cat?.icon),
        )
    }
    val amountColor: Color = when (tx.type) {
        TxType.INCOME -> c.income
        TxType.EXPENSE -> c.expense
        else -> c.transfer
    }
    val shown = when (tx.type) {
        TxType.EXPENSE -> -tx.amount
        else -> tx.amount
    }
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIcon(icon, c.background, c.text)
        HSpace(12)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        HSpace(8)
        AmountText(
            shown, style = AmountStyle.copy(fontSize = 15.sp), color = amountColor,
            signed = tx.type == TxType.INCOME || (tx.type == TxType.ADJUSTMENT && tx.amount > 0),
        )
    }
}
