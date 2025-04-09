package edu.cit.audioscholar.ui.onboarding

import android.Manifest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import edu.cit.audioscholar.R
import kotlinx.coroutines.launch

private const val ONBOARDING_PAGE_COUNT = 4

@OptIn(ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    onOnboardingComplete: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGE_COUNT })
    val scope = rememberCoroutineScope()

    val microphonePermissionState = rememberPermissionState(
        Manifest.permission.RECORD_AUDIO
    )

    val handleFinish: () -> Unit = {
        scope.launch {
            if (!microphonePermissionState.status.isGranted) {
                microphonePermissionState.launchPermissionRequest()
            }
            onOnboardingComplete()
        }
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            OnboardingBottomBar(
                pagerState = pagerState,
                onSkip = onOnboardingComplete,
                onFinishClick = handleFinish
            )
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { pageIndex ->
            OnboardingPageContent(
                pageIndex = pageIndex,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun OnboardingPageContent(pageIndex: Int, modifier: Modifier = Modifier) {
    val imageResId = when (pageIndex) {
        0 -> R.drawable.ic_onboarding1
        1 -> R.drawable.ic_onboarding2
        2 -> R.drawable.ic_onboarding3
        3 -> R.drawable.ic_onboarding4
        else -> R.drawable.ic_audioscholar_light
    }
    val imageContentDescription = when (pageIndex) {
        0 -> stringResource(R.string.onboarding_image_desc_1)
        1 -> stringResource(R.string.onboarding_image_desc_2)
        2 -> stringResource(R.string.onboarding_image_desc_3)
        3 -> stringResource(R.string.onboarding_image_desc_4)
        else -> null
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = imageResId),
            contentDescription = imageContentDescription,
            modifier = Modifier
                .height(400.dp)
                .fillMaxWidth()
                .padding(bottom = 48.dp),
            contentScale = ContentScale.Fit
        )

        when (pageIndex) {
            0 -> OnboardingTextContent1()
            1 -> OnboardingTextContent2()
            2 -> OnboardingTextContent3()
            3 -> OnboardingTextContent4()
        }
    }
}

@Composable
fun OnboardingTextContent1() {
    Text(
        text = stringResource(R.string.onboarding_title_1),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.onboarding_body_1),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun OnboardingTextContent2() {
    Text(
        text = stringResource(R.string.onboarding_title_2),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.onboarding_body_2),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun OnboardingTextContent3() {
    Text(
        text = stringResource(R.string.onboarding_title_3),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.onboarding_body_3),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun OnboardingTextContent4() {
    Text(
        text = stringResource(R.string.onboarding_title_4),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.onboarding_body_4),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = stringResource(R.string.onboarding_permission_note),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingBottomBar(
    pagerState: PagerState,
    onSkip: () -> Unit,
    onFinishClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (pagerState.currentPage < ONBOARDING_PAGE_COUNT - 1) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                } else {
                    Spacer(Modifier.width(64.dp))
                }
            }

            PageIndicator(
                pageCount = ONBOARDING_PAGE_COUNT,
                currentPage = pagerState.currentPage,
                modifier = Modifier.wrapContentWidth()
            )

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                val scope = rememberCoroutineScope()
                if (pagerState.currentPage == ONBOARDING_PAGE_COUNT - 1) {
                    Button(onClick = onFinishClick) {
                        Text(stringResource(R.string.onboarding_finish))
                    }
                } else {
                    Spacer(Modifier.width(64.dp))
                }
            }
        }
    }
}

@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val size = 8.dp
            val color = if (index == currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(size)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

