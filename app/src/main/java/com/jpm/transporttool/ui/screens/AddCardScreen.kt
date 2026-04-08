package com.jpm.transporttool.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jpm.transporttool.R
import com.jpm.transporttool.data.model.TransportCard
import com.jpm.transporttool.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCardScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val existingCard = viewModel.savedCards.find { it.uidPrefix == viewModel.prefilledPrefix }
    var name by remember { mutableStateOf(existingCard?.name ?: "") }
    var prefix by remember { mutableStateOf(viewModel.prefilledPrefix) }
    var keyB by remember { mutableStateOf(existingCard?.keyB ?: "") }
    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler { viewModel.currentScreen = "home" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.customize)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.currentScreen = "home" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.configure_card_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name_label)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = keyB,
                onValueChange = { keyB = it.uppercase() },
                label = { Text(stringResource(R.string.key_b_label)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.key_b_placeholder)) },
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(40.dp))
            
            Button(
                onClick = {
                    if (name.isNotEmpty() && prefix.isNotEmpty()) {
                        if (keyB.isNotEmpty() && keyB.length != 12) {
                            Toast.makeText(context, context.getString(R.string.error_key_b_length), Toast.LENGTH_SHORT).show()
                        } else {
                            keyboardController?.hide()
                            viewModel.saveNewCard(TransportCard(name, prefix, keyB, existingCard?.color ?: Color(0xFF1976D2).toArgb()))
                            viewModel.currentScreen = "home"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.save_changes), fontWeight = FontWeight.Bold)
            }
            if (existingCard != null) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = {
                        keyboardController?.hide()
                        viewModel.saveNewCardList(viewModel.savedCards.filter { it.uidPrefix != prefix })
                        viewModel.currentScreen = "home"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.delete_config), color = Color.Red)
                }
            }
        }
    }
}
