package com.shopsphere.app

import android.app.Application
import com.shopsphere.app.data.Repository

class ShopApp : Application() {
    val repository: Repository by lazy { Repository(this) }
}
