package id.dompetku.ui

import id.dompetku.core.t

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import id.dompetku.ui.accounts.AccountDetailScreen
import id.dompetku.ui.accounts.AccountsScreen
import id.dompetku.ui.budget.BudgetRoutes
import id.dompetku.ui.budget.BudgetScreen
import id.dompetku.ui.budget.BudgetSetupScreen
import id.dompetku.ui.goals.GOALS_ROUTE
import id.dompetku.ui.goals.GoalsScreen
import id.dompetku.ui.home.HomeScreen
import id.dompetku.ui.inbox.InboxScreen
import id.dompetku.ui.capture.CaptureSetupScreen
import id.dompetku.ui.home.HomeOrderScreen
import id.dompetku.ui.importer.ImportScreen
import id.dompetku.ui.insights.InsightsScreen
import id.dompetku.ui.settings.CategoriesScreen
import id.dompetku.ui.settings.SettingsScreen
import id.dompetku.ui.theme.LocalDompetkuColors
import id.dompetku.ui.transactions.CategoryTransactionsScreen
import id.dompetku.ui.transactions.TransactionsScreen
import id.dompetku.ui.tx.EditTxScreen

object Routes {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val ACCOUNTS = "accounts"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val CATEGORIES = "categories"
    const val IMPORT = "import"
    const val HOME_ORDER = "home-order"
    const val INBOX = "inbox"
    const val CAPTURE_SETUP = "capture-setup"
    fun editTx(id: Long = 0) = "tx/edit/$id"
    fun account(id: Long) = "account/$id"
    fun categoryTx(cat: Long, from: Long, to: Long) = "cattx/$cat/$from/$to"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home),
    Tab(Routes.TRANSACTIONS, "Transaksi", Icons.Outlined.ReceiptLong),
    Tab(Routes.ACCOUNTS, "Akun", Icons.Outlined.AccountBalanceWallet),
    Tab(Routes.INSIGHTS, "Insights", Icons.Outlined.Insights),
)

@Composable
fun AppNav(openInbox: Boolean = false, onInboxOpened: () -> Unit = {}) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }
    androidx.compose.runtime.LaunchedEffect(openInbox) { if (openInbox) { nav.navigate(Routes.INBOX); onInboxOpened() } }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) { HomeScreen(nav) }
                composable(Routes.TRANSACTIONS) { TransactionsScreen(nav) }
                composable(Routes.ACCOUNTS) { AccountsScreen(nav) }
                composable(Routes.INSIGHTS) {
                    InsightsScreen(nav) { cat, p -> nav.navigate(Routes.categoryTx(cat, p.start.toEpochMilli(), p.endExclusive.toEpochMilli())) }
                }
                composable(
                    "cattx/{cat}/{from}/{to}",
                    arguments = listOf(
                        navArgument("cat") { type = NavType.LongType },
                        navArgument("from") { type = NavType.LongType },
                        navArgument("to") { type = NavType.LongType },
                    ),
                ) {
                    val a = it.arguments!!
                    CategoryTransactionsScreen(nav, a.getLong("cat"), a.getLong("from"), a.getLong("to"))
                }
                composable(Routes.SETTINGS) { SettingsScreen(nav) }
                composable(Routes.CATEGORIES) { CategoriesScreen(nav) }
                composable(Routes.IMPORT) { ImportScreen(nav) }
                composable(Routes.HOME_ORDER) { HomeOrderScreen(nav) }
                composable(Routes.INBOX) { InboxScreen(nav) }
                composable(Routes.CAPTURE_SETUP) { CaptureSetupScreen(nav) }
                composable(BudgetRoutes.BUDGET) { BudgetScreen(nav) }
                composable("budget/setup/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    BudgetSetupScreen(nav, it.arguments?.getLong("id") ?: 0L)
                }
                composable(GOALS_ROUTE) { GoalsScreen(nav) }
                composable("tx/edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    EditTxScreen(nav, it.arguments?.getLong("id") ?: 0L)
                }
                composable("account/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    AccountDetailScreen(nav, it.arguments?.getLong("id") ?: 0L)
                }
            }
        }
        if (showBar) BottomBar(nav, route)
    }
}

@Composable
private fun BottomBar(nav: NavHostController, current: String?) {
    val c = LocalDompetkuColors.current
    fun go(r: String) = nav.navigate(r) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    Row(
        Modifier.fillMaxWidth().background(c.background).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.take(2).forEach { TabItem(it, it.route == current) { go(it.route) } }
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(c.fab).clickable { nav.navigate(Routes.editTx()) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Add, t("Tambah transaksi"), tint = c.onFab, modifier = Modifier.size(28.dp)) }
        tabs.drop(2).forEach { TabItem(it, it.route == current) { go(it.route) } }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val c = LocalDompetkuColors.current
    Column(
        Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(tab.icon, t(tab.label), tint = if (selected) c.text else c.muted)
        Text(t(tab.label), style = MaterialTheme.typography.labelSmall, color = if (selected) c.text else c.muted)
    }
}
