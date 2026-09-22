package com.veltravia.marketscopeai.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.TextMuted

/**
 * Stock-only result experience. Every analyzed stock keeps its complete result
 * card, while horizontal paging combines up to four independently saved and
 * independently quota-counted analyses into one batch result.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StockBatchResultScreen(
    analysisIds: List<String>,
    onBack: () -> Unit,
    onOpenBrokerInfo: (() -> Unit)? = null,
    continueCta: Pair<String, () -> Unit>? = null
) {
    val ids = analysisIds.filter { it.isNotBlank() }.distinct().take(4)
    if (ids.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("No stock results were found.", color = TextMuted)
        }
        return
    }

    val pagerState = rememberPagerState { ids.size }
    Column(modifier = Modifier.fillMaxSize()) {
        if (ids.size > 1) {
            Text(
                "${pagerState.currentPage + 1} of ${ids.size} stock results  ·  Swipe to compare",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
            )
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ids.indices.forEach { index ->
                    Spacer(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (index == pagerState.currentPage) AccentCyan else BorderSubtle)
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            key = { ids[it] }
        ) { page ->
            SignalCardScreen(
                analysisId = ids[page],
                onBack = onBack,
                continueCta = continueCta,
                onOpenBrokerInfo = onOpenBrokerInfo,
                title = if (ids.size > 1) "Stock ${page + 1} Analysis" else "Stock Analysis"
            )
        }
    }
}
