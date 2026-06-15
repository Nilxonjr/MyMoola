package com.example.mymoola

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.mymoola.ui.theme.MyMoolaTheme
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mymoola.features.auth.data.AuthApiClient
import com.example.mymoola.features.auth.data.AuthSession
import com.example.mymoola.features.auth.ui.LoginScreen
import com.example.mymoola.features.auth.ui.OtpScreen
import com.example.mymoola.features.auth.ui.SignUpScreen
import com.example.mymoola.features.home.data.WalletRealtimeClient
import com.example.mymoola.features.home.ui.BuyCryptoScreen
import com.example.mymoola.features.home.ui.ActivityDetailsScreen
import com.example.mymoola.features.home.ui.HomeScreen
import com.example.mymoola.features.home.ui.PayWithMpesaScreen
import com.example.mymoola.features.home.ui.SellCryptoScreen
import com.example.mymoola.features.home.ui.SendToUserChoiceScreen
import com.example.mymoola.features.home.ui.ReceiveCryptoScreen
import com.example.mymoola.features.home.ui.SendToUserScreen
import com.example.mymoola.features.home.ui.ViewRatesScreen
import com.example.mymoola.features.home.ui.ViewRecordsScreen
import com.example.mymoola.features.home.ui.WithdrawCryptoScreen
import com.example.mymoola.features.onboarding.ui.OnboardingFeature
import com.example.mymoola.features.onboarding.ui.OnboardingScreen
import com.example.mymoola.features.settings.ui.BiometricLoginScreen
import com.example.mymoola.features.settings.ui.ChangePinScreen
import com.example.mymoola.features.settings.ui.DefaultCurrencyScreen
import com.example.mymoola.features.settings.ui.DeleteAccountScreen
import com.example.mymoola.features.settings.ui.HelpSupportScreen
import com.example.mymoola.features.settings.ui.LogoutScreen
import com.example.mymoola.features.settings.ui.ProfileSummaryScreen
import com.example.mymoola.features.settings.ui.SettingsScreen
import com.example.mymoola.features.settings.ui.TransactionNotificationsScreen

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        if (!AuthSession.accessToken.isNullOrBlank()) {
            WalletRealtimeClient.connect()
        }
    }

    override fun onStop() {
        WalletRealtimeClient.disconnect()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthSession.initialize(applicationContext)
        enableEdgeToEdge()
        setContent {
            MyMoolaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val navController = rememberNavController()
                    val startDestination = if (
                        !AuthSession.accessToken.isNullOrBlank() ||
                        !AuthSession.refreshToken.isNullOrBlank()
                    ) {
                        "home"
                    } else {
                        "onboarding1"
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("onboarding1") {
                            OnboardingScreen(
                                title = "Welcome to MyMoola",
                                subtitle = "Buy, sell, send, and spend crypto with M-PESA support in Kenya.",
                                features = listOf(
                                    OnboardingFeature("onb_buy_mpesa", "↗", "Buy crypto using M-PESA"),
                                    OnboardingFeature("onb_sell_kes", "↘", "Sell crypto back to KES"),
                                    OnboardingFeature("onb_wallet_manage", "◎", "Manage everything from one wallet")
                                ),
                                currentPage = 0,
                                totalPages = 3,
                                onRegisterClick = { navController.navigate("onboarding2") }
                            )
                        }
                        composable("onboarding2") {
                            OnboardingScreen(
                                title = "Pay with M-PESA",
                                subtitle = "Use your wallet to pay Till Numbers, PayBills, and everyday services.",
                                features = listOf(
                                    OnboardingFeature("onb_pay_till", "₸", "Pay Till Numbers"),
                                    OnboardingFeature("onb_paybill", "¤", "Pay PayBills"),
                                    OnboardingFeature("onb_payment_records", "✓", "Keep payment records")
                                ),
                                currentPage = 1,
                                totalPages = 3,
                                showBackButton = true,
                                onBackClick = { navController.popBackStack() },
                                onRegisterClick = { navController.navigate("onboarding3") }
                            )
                        }
                        composable("onboarding3") {
                            OnboardingScreen(
                                title = "Send Crypto Easily",
                                subtitle = "Send crypto to friends, family, or supported wallet addresses quickly and securely.",
                                features = listOf(
                                    OnboardingFeature("onb_send_crypto", "➤", "Send crypto to other users"),
                                    OnboardingFeature("onb_receive_crypto", "⬇", "Receive crypto in your wallet"),
                                    OnboardingFeature("onb_tx_history", "🕘", "View your transaction history")
                                ),
                                currentPage = 2,
                                totalPages = 3,
                                buttonText = "Get Started",
                                showBackButton = true,
                                showSignInPrompt = true,
                                onLoginClick = { navController.navigate("login") },
                                onBackClick = { navController.popBackStack() },
                                onRegisterClick = { navController.navigate("signup") }
                            )
                        }
                        composable("signup") {
                            SignUpScreen(
                                onBackClick = { navController.popBackStack() },
                                onLoginClick = { navController.navigate("login") },
                                onRegisterSuccess = { phone ->
                                    navController.navigate("otp/${Uri.encode(phone)}/${AuthApiClient.OtpPurpose.Registration.name}")
                                }
                            )
                        }
                        composable("login") {
                            LoginScreen(
                                onBackClick = { navController.popBackStack() },
                                onSignUpClick = { navController.navigate("signup") },
                                onLoginSuccess = { phone ->
                                    navController.navigate("otp/${Uri.encode(phone)}/${AuthApiClient.OtpPurpose.Login.name}")
                                }
                            )
                        }
                        composable("home") {
                            val refreshNonce by navController.currentBackStackEntry
                                ?.savedStateHandle
                                ?.getStateFlow("home_force_refresh", 0L)
                                ?.collectAsState()
                                ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
                            HomeScreen(
                                refreshNonce = refreshNonce,
                                onSettingsClick = { navController.navigate("settings") },
                                onBuyClick = { navController.navigate("buy_crypto") },
                                onSellClick = { navController.navigate("sell_crypto") },
                                onWithdrawClick = { navController.navigate("withdraw_crypto") },
                                onReceiveCryptoClick = { navController.navigate("receive_crypto") },
                                onPayWithMpesaClick = { navController.navigate("pay_with_mpesa") },
                                onSendToUserClick = { navController.navigate("send_to_user") },
                                onViewRecordsClick = { navController.navigate("view_records") },
                                onViewRatesClick = { navController.navigate("view_rates") },
                                onActivityClick = { activity ->
                                    navController.currentBackStackEntry
                                        ?.savedStateHandle
                                        ?.apply {
                                            set("activity_type", activity.type)
                                            set("activity_status", activity.status)
                                            set("activity_detail", activity.detail)
                                            set("activity_amount", activity.amount)
                                            set("activity_market_rate_snapshot", activity.marketRateSnapshot)
                                            set("activity_on_chain_tx_hash", activity.onChainTxHash)
                                            set("activity_on_chain_confirmations", activity.onChainConfirmations)
                                            set("activity_mpesa_reference", activity.mpesaReference)
                                        }
                                    navController.navigate("activity_details")
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                onProfileSummaryClick = { navController.navigate("settings_profile_summary") },
                                onChangePinClick = { navController.navigate("settings_change_pin") },
                                onBiometricLoginClick = { navController.navigate("settings_biometric_login") },
                                onDefaultCurrencyClick = { navController.navigate("settings_default_currency") },
                                onTransactionNotificationsClick = { navController.navigate("settings_transaction_notifications") },
                                onHelpSupportClick = { navController.navigate("settings_help_support") },
                                onDeleteAccountClick = { navController.navigate("settings_delete_account") },
                                onLogoutClick = { navController.navigate("settings_logout") }
                            )
                        }
                        composable("settings_profile_summary") {
                            ProfileSummaryScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable("settings_change_pin") {
                            ChangePinScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable("settings_biometric_login") {
                            BiometricLoginScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable("settings_default_currency") {
                            DefaultCurrencyScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable("settings_transaction_notifications") {
                            TransactionNotificationsScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable("settings_help_support") {
                            HelpSupportScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable("settings_logout") {
                            LogoutScreen(
                                onBackClick = { navController.popBackStack() },
                                onConfirmLogout = {
                                    WalletRealtimeClient.disconnect()
                                    AuthSession.clear()
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable("settings_delete_account") {
                            DeleteAccountScreen(
                                onBackClick = { navController.popBackStack() },
                                onDeleted = {
                                    WalletRealtimeClient.disconnect()
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable("buy_crypto") {
                            BuyCryptoScreen(
                                onBackClick = { navController.popBackStack() },
                                onDoneClick = {
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("home_force_refresh", System.currentTimeMillis())
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("sell_crypto") {
                            SellCryptoScreen(
                                onBackClick = { navController.popBackStack() },
                                onDoneClick = {
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("home_force_refresh", System.currentTimeMillis())
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("withdraw_crypto") {
                            WithdrawCryptoScreen(
                                onBackClick = { navController.popBackStack() },
                                onDoneClick = {
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("home_force_refresh", System.currentTimeMillis())
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("receive_crypto") {
                            ReceiveCryptoScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable("pay_with_mpesa") {
                            PayWithMpesaScreen(
                                onBackClick = { navController.popBackStack() },
                                onDoneClick = {
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("home_force_refresh", System.currentTimeMillis())
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("send_to_user") {
                            SendToUserChoiceScreen(
                                onBackClick = { navController.popBackStack() },
                                onSendCryptoClick = { navController.navigate("send_crypto") },
                                onSendMpesaClick = { navController.navigate("send_mpesa") }
                            )
                        }
                        composable("send_crypto") {
                            SendToUserScreen(
                                initialMode = "crypto",
                                allowModeSwitch = false,
                                onBackClick = { navController.popBackStack() },
                                onGoHomeClick = {
                                    navController.getBackStackEntry("home")
                                        .savedStateHandle
                                        .set("home_force_refresh", System.currentTimeMillis())
                                    navController.popBackStack("home", false)
                                }
                            )
                        }
                        composable("send_mpesa") {
                            SendToUserScreen(
                                initialMode = "mpesa",
                                allowModeSwitch = false,
                                onBackClick = { navController.popBackStack() },
                                onGoHomeClick = {
                                    navController.getBackStackEntry("home")
                                        .savedStateHandle
                                        .set("home_force_refresh", System.currentTimeMillis())
                                    navController.popBackStack("home", false)
                                }
                            )
                        }
                        composable("view_records") {
                            ViewRecordsScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable("view_rates") {
                            ViewRatesScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable("activity_details") {
                            val state = navController.previousBackStackEntry?.savedStateHandle
                            val activityType = state?.get<String>("activity_type").orEmpty()
                            val activityStatus = state?.get<String>("activity_status").orEmpty()
                            val activityDetail = state?.get<String>("activity_detail").orEmpty()
                            val activityAmount = state?.get<String>("activity_amount").orEmpty()
                            val activityRate = state?.get<Double>("activity_market_rate_snapshot")
                            val activityOnChainTxHash = state?.get<String>("activity_on_chain_tx_hash")
                            val activityConfirmations = state?.get<Int>("activity_on_chain_confirmations") ?: 0
                            val activityMpesaRef = state?.get<String>("activity_mpesa_reference")
                            ActivityDetailsScreen(
                                type = activityType,
                                status = activityStatus,
                                detail = activityDetail,
                                amount = activityAmount,
                                marketRateSnapshot = activityRate,
                                onChainTxHash = activityOnChainTxHash,
                                onChainConfirmations = activityConfirmations,
                                mpesaReference = activityMpesaRef,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "otp/{phone}/{purpose}",
                            arguments = listOf(
                                navArgument("phone") { type = NavType.StringType },
                                navArgument("purpose") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val phone = backStackEntry.arguments?.getString("phone").orEmpty()
                            val purpose = backStackEntry.arguments?.getString("purpose")
                                ?.let { runCatching { AuthApiClient.OtpPurpose.valueOf(it) }.getOrNull() }
                                ?: AuthApiClient.OtpPurpose.Registration
                            OtpScreen(
                                phoneNumber = phone,
                                purpose = purpose,
                                onBackClick = { navController.popBackStack() },
                                onVerified = { tokenResponse ->
                                    AuthSession.setTokens(
                                        tokenResponse.accessToken,
                                        tokenResponse.refreshToken
                                    )
                                    WalletRealtimeClient.connect()
                                    navController.navigate("home") {
                                        popUpTo("onboarding1") { inclusive = false }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
