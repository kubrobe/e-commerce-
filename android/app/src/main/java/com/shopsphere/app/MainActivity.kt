package com.shopsphere.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shopsphere.app.ui.ShopRoot
import com.shopsphere.app.ui.ShopSphereTheme
import com.shopsphere.app.ui.ShopViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShopSphereTheme {
                val vm: ShopViewModel = viewModel()
                ShopRoot(vm)
            }
        }
    }
}
