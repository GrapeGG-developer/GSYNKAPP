package com.gsynk.app.ui

import androidx.compose.runtime.Composable

/**
 * Прямой полноэкранный запуск функции из Пуска.
 *
 * Этот разборщик заменяет старые контейнеры «Инструменты» и «Лаборатория»:
 * маршрут открывается сразу своим экраном, а его onBack в MainScreen ведёт
 * обратно в Пуск, а не в промежуточное меню.
 */
@Composable
fun DirectFeatureScreen(
    route: String,
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenNfc: () -> Unit,
    onHostGame: () -> Unit = {},
    onJoinGame: (com.gsynk.app.core.NfcLink.GameInvite) -> Unit = {}
) {
    val (kind, name) = Router.split(route)
    when (kind) {
        "tool" -> when (name) {
            "password" -> PasswordToolScreen(onBack = onBack)
            "morse" -> MorseToolScreen(onBack = onBack)
            "metal" -> MetalDetectorScreen(onBack = onBack)
            "level" -> LevelToolScreen(onBack = onBack)
            "sound" -> SoundMeterScreen(onBack = onBack)
            "sysinfo" -> SysDashboardScreen(onBack = onBack)
            "coder" -> EncoderScreen(onBack = onBack)
            "subnet" -> SubnetScreen(onBack = onBack)
            "quick" -> QuickActionsScreen(vm = vm, onBack = onBack)
            "nearby" -> NearbyScreen(
                vm = vm,
                onBack = onBack,
                onOpenNfc = onOpenNfc,
                onHostGame = onHostGame,
                onJoinGame = onJoinGame
            )
            else -> UnknownFeatureScreen(route = route, onBack = onBack)
        }
        "flag" -> when (name) {
            "turbo" -> TurboScreen(vm = vm, onBack = onBack)
            "chronicle" -> ChronicleScreen(onBack = onBack)
            "chat" -> ChatScreen(onBack = onBack)
            "screensaver" -> ScreensaverScreen(onBack = onBack)
            "diagnostics" -> DiagnosticsScreen(vm = vm, onBack = onBack)
            "bench" -> BenchScreen(vm = vm, onBack = onBack)
            else -> UnknownFeatureScreen(route = route, onBack = onBack)
        }
        "lab" -> {
            if (!HardwareFeatureScreen(id = name, onBack = onBack)) {
                UnknownFeatureScreen(route = route, onBack = onBack)
            }
        }
        else -> UnknownFeatureScreen(route = route, onBack = onBack)
    }
}

@Composable
private fun UnknownFeatureScreen(route: String, onBack: () -> Unit) {
    GrapeScreen(title = "плитка", subtitle = "маршрут не найден", onBack = onBack) {
        SurfaceCard {
            androidx.compose.material3.Text(
                "Эта плитка ведёт в удалённый старый раздел: $route. Закройте приложение плитки кнопкой × и откройте функцию прямо из Пуска.",
                color = com.gsynk.app.ui.theme.TextPrimary,
                fontSize = Gioui.Type.body
            )
        }
    }
}
