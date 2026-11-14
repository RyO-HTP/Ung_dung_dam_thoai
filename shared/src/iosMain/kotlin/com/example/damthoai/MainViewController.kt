package com.example.damthoai

import androidx.compose.ui.window.ComposeUIViewController
import com.example.damthoai.ios.IosNativeBridge
import com.example.damthoai.ios.IosPlatform
import com.example.damthoai.ios.IosPlatformUi
import platform.UIKit.UIViewController

/**
 * Điểm vào giao diện cho app iOS. Swift gọi: MainViewControllerKt.MainViewController(bridge: NativeBridge())
 */
fun MainViewController(bridge: IosNativeBridge): UIViewController {
    IosPlatform.install(bridge)
    val platformUi = IosPlatformUi(bridge)
    return ComposeUIViewController {
        App(platformUi = platformUi)
    }
}
