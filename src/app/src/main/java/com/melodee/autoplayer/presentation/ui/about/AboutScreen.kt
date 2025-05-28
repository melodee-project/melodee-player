package com.melodee.autoplayer.presentation.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.melodee.autoplayer.R
import android.content.pm.PackageManager

data class OpenSourcePackage(
    val name: String,
    val description: String,
    val url: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    
    // Get version info
    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
    val versionName = packageInfo?.versionName ?: "Unknown"
    
    // Open source packages list
    val openSourcePackages = remember {
        listOf(
            OpenSourcePackage(
                "Jetpack Compose",
                "Modern toolkit for building native Android UI",
                "https://github.com/androidx/androidx"
            ),
            OpenSourcePackage(
                "Material 3",
                "Material Design components for Android",
                "https://github.com/material-components/material-components-android"
            ),
            OpenSourcePackage(
                "Retrofit",
                "Type-safe HTTP client for Android and Java",
                "https://github.com/square/retrofit"
            ),
            OpenSourcePackage(
                "OkHttp",
                "HTTP client for Android and Java applications",
                "https://github.com/square/okhttp"
            ),
            OpenSourcePackage(
                "Gson",
                "Java library for JSON serialization/deserialization",
                "https://github.com/google/gson"
            ),
            OpenSourcePackage(
                "Coil",
                "Image loading library for Android backed by Kotlin Coroutines",
                "https://github.com/coil-kt/coil"
            ),
            OpenSourcePackage(
                "ExoPlayer (Media3)",
                "Extensible media player for Android",
                "https://github.com/androidx/media"
            ),
            OpenSourcePackage(
                "Navigation Compose",
                "Navigation component for Jetpack Compose",
                "https://github.com/androidx/androidx"
            ),
            OpenSourcePackage(
                "Kotlin Coroutines",
                "Asynchronous programming library for Kotlin",
                "https://github.com/Kotlin/kotlinx.coroutines"
            ),
            OpenSourcePackage(
                "AndroidX Core",
                "Core Android libraries with backward compatibility",
                "https://github.com/androidx/androidx"
            ),
            OpenSourcePackage(
                "AndroidX Lifecycle",
                "Lifecycle-aware components for Android",
                "https://github.com/androidx/androidx"
            ),
            OpenSourcePackage(
                "AndroidX Media",
                "Media framework for Android Auto compatibility",
                "https://github.com/androidx/androidx"
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Header with logo and app info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "Melodee Player Logo",
                            modifier = Modifier
                                .size(80.dp)
                                .padding(bottom = 16.dp),
                            contentScale = ContentScale.Fit
                        )
                        
                        Text(
                            text = "Melodee Player",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = "Version $versionName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            // Description
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Description",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Text(
                            text = "Melodee Player is a song player for the Melodee Music system focusing on a simple user experience.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                        )
                    }
                }
            }
            
            // Homepage
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Home Page",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "https://www.melodee.org/clients/melodeeplayer",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            
                            IconButton(
                                onClick = { 
                                    uriHandler.openUri("https://www.melodee.org/clients/melodeeplayer")
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open homepage",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            
            // Open Source Packages
            item {
                Text(
                    text = "Open Source Packages",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            items(openSourcePackages) { pkg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = pkg.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                Text(
                                    text = pkg.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            
                            IconButton(
                                onClick = { uriHandler.openUri(pkg.url) }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open ${pkg.name} repository",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            
            // Footer spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
} 