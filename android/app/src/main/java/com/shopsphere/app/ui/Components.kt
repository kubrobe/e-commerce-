package com.shopsphere.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shopsphere.app.data.Product
import java.util.Locale

fun Double.money(): String = "$" + String.format(Locale.US, "%.2f", this)

fun stars(rating: Double): String {
    val full = Math.round(rating).toInt().coerceIn(0, 5)
    return "★".repeat(full) + "☆".repeat(5 - full)
}

@Composable
fun ProductImage(url: String?, name: String, modifier: Modifier = Modifier) {
    if (url.isNullOrBlank()) {
        Box(
            modifier.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
    } else {
        AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Crop, modifier = modifier)
    }
}

@Composable
fun ProductCard(product: Product, onClick: () -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            ProductImage(product.imageUrl, product.name, Modifier.fillMaxWidth().aspectRatio(1.5f))
            Column(Modifier.padding(12.dp)) {
                product.categoryName?.let {
                    Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                Text(
                    product.name, style = MaterialTheme.typography.titleSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(40.dp),
                )
                if (product.reviewCount > 0) {
                    Text("${stars(product.ratingAvg)} (${product.reviewCount})", style = MaterialTheme.typography.labelSmall)
                }
                Text(product.price.money(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Add to cart") }
            }
        }
    }
}

@Composable
fun CenterLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun MessageState(
    title: String,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) =
    MessageState("Something went wrong", message, "Try again", onRetry)

@Composable
fun SignInPrompt(reason: String, onSignIn: () -> Unit) =
    MessageState("Sign in required", reason, "Sign in", onSignIn)

@Composable
fun StatusChip(status: String) {
    val color = when (status) {
        "delivered", "paid" -> MaterialTheme.colorScheme.secondary
        "cancelled" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(status.replaceFirstChar { it.uppercase() }, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun LabeledRow(label: String, value: String, bold: Boolean = false, valueColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = valueColor)
    }
}

@Composable
fun SecondaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
