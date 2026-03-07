package com.garemat.moonstone_companion.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garemat.moonstone_companion.CharacterViewModel
import com.garemat.moonstone_companion.RuleSection
import com.garemat.moonstone_companion.ui.theme.LocalAppThemeProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    viewModel: CharacterViewModel,
    onNavigateBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val rules by viewModel.rules.collectAsState()
    val theme = LocalAppThemeProperties.current
    val displayRules = remember(searchQuery, rules) {
        if (searchQuery.isEmpty()) rules
        else rules.filter { rule ->
            rule.searchable && (
                rule.title.contains(searchQuery, ignoreCase = true) ||
                rule.content.contains(searchQuery, ignoreCase = true) ||
                rule.keywords.any { it.contains(searchQuery, ignoreCase = true) } ||
                rule.category.contains(searchQuery, ignoreCase = true)
            )
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Rules Reference", style = theme.titleStyle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(theme.screenPadding),
                placeholder = { Text("Search keywords, combat, magic...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = theme.cardShape
            )

            if (displayRules.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isEmpty()) "No rules available." else "No matching rules found.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayRules) { rule ->
                        RuleItem(rule, searchQuery)
                    }
                }
            }
        }
    }
}

@Composable
fun RuleItem(rule: RuleSection, searchQuery: String) {
    var isExpanded by remember { mutableStateOf(false) }
    val theme = LocalAppThemeProperties.current
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            if (rule.title.contains(searchQuery, ignoreCase = true) ||
                rule.content.contains(searchQuery, ignoreCase = true)) {
                isExpanded = true
            }
        } else isExpanded = false
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).animateContentSize(),
        shape = theme.cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = theme.surfaceElevation),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.clickable { isExpanded = !isExpanded }.padding(theme.cardContentPadding)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = highlightText(rule.title, searchQuery),
                        style = theme.titleStyle.copy(fontSize = 18.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = parseRuleContent(rule.content, searchQuery, accentColor = MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                    inlineContent = getMoonstoneInlineContent()
                )
            }
        }
    }
}

@Composable
fun parseRuleContent(content: String, searchQuery: String, accentColor: Color) = buildAnnotatedString {
    val lines = content.split("\n")
    lines.forEachIndexed { index, line ->
        var currentLine = line
        if (currentLine.trim().startsWith("•")) append("  ")
        val dashIndex = currentLine.indexOf(" – ")
        if (dashIndex != -1) {
            val title = currentLine.substring(0, dashIndex)
            val rest = currentLine.substring(dashIndex)
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = accentColor)) {
                appendFormattedPart(title, searchQuery)
            }
            appendFormattedPart(rest, searchQuery)
        } else appendFormattedPart(currentLine, searchQuery)
        if (index < lines.size - 1) append("\n")
    }
}

private fun AnnotatedString.Builder.appendFormattedPart(text: String, searchQuery: String) {
    val regex = "(\\*\\*(.*?)\\*\\*)|(\\{[Nn]ull\\})".toRegex()
    var lastIndex = 0
    regex.findAll(text).forEach { match ->
        appendWithHighlight(text.substring(lastIndex, match.range.first), searchQuery)
        when {
            match.groupValues[1].isNotEmpty() -> {
                val boldText = match.groupValues[2]
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendWithHighlight(boldText, searchQuery)
                }
            }
            match.groupValues[3].isNotEmpty() -> appendInlineContent("nullSymbol", "{Null}")
        }
        lastIndex = match.range.last + 1
    }
    appendWithHighlight(text.substring(lastIndex), searchQuery)
}
