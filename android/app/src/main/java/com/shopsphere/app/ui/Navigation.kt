@file:OptIn(ExperimentalMaterial3Api::class)

package com.shopsphere.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("shop", "Shop", Icons.Default.Home),
    Tab("wishlist", "Wishlist", Icons.Default.Favorite),
    Tab("cart", "Cart", Icons.Default.ShoppingCart),
    Tab("orders", "Orders", Icons.AutoMirrored.Filled.List),
    Tab("account", "Account", Icons.Default.Person),
)

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun ShopRoot(vm: ShopViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val cart by vm.cart.collectAsState()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.switchTab(tab.route) },
                            label = { Text(tab.label) },
                            icon = {
                                if (tab.route == "cart" && cart.isNotEmpty()) {
                                    BadgedBox(badge = { Badge { Text("${cart.sumOf { it.quantity }}") } }) {
                                        Icon(tab.icon, contentDescription = tab.label)
                                    }
                                } else Icon(tab.icon, contentDescription = tab.label)
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "shop", modifier = Modifier.padding(padding)) {
            composable("shop") { ShopScreen(vm, onOpenProduct = { nav.navigate("product/$it") }) }

            composable(
                "product/{id}",
                arguments = listOf(navArgument("id") { type = NavType.IntType }),
            ) { entry ->
                ProductScreen(
                    vm, productId = entry.arguments?.getInt("id") ?: 0,
                    onBack = { nav.popBackStack() },
                    onSignIn = { nav.navigate("login") },
                )
            }

            composable("wishlist") {
                WishlistScreen(vm, onOpenProduct = { nav.navigate("product/$it") }, onSignIn = { nav.navigate("login") }, onShop = { nav.switchTab("shop") })
            }

            composable("cart") {
                val user by vm.user.collectAsState()
                CartScreen(
                    vm,
                    onOpenProduct = { nav.navigate("product/$it") },
                    onCheckout = { if (user == null) nav.navigate("login") else nav.navigate("checkout") },
                    onShop = { nav.switchTab("shop") },
                )
            }

            composable("checkout") {
                CheckoutScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onPlaced = {
                        nav.popBackStack("cart", inclusive = false)
                        nav.switchTab("orders")
                    },
                )
            }

            composable("orders") { OrdersScreen(vm, onSignIn = { nav.navigate("login") }, onShop = { nav.switchTab("shop") }) }

            composable("account") {
                AccountScreen(vm, onSignIn = { nav.navigate("login") }, onRegister = { nav.navigate("register") })
            }

            composable("login") {
                AuthScreen(
                    vm, register = false,
                    onBack = { nav.popBackStack() },
                    onSwitch = { nav.navigate("register") { popUpTo("login") { inclusive = true } } },
                    onDone = { nav.popBackStack() },
                )
            }
            composable("register") {
                AuthScreen(
                    vm, register = true,
                    onBack = { nav.popBackStack() },
                    onSwitch = { nav.navigate("login") { popUpTo("register") { inclusive = true } } },
                    onDone = { nav.popBackStack() },
                )
            }
        }
    }
}
