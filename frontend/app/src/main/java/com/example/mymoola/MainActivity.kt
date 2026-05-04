package com.example.mymoola

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.mymoola.ui.theme.MyMoolaTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyMoolaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "onboarding1",
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
                                onLoginClick = { navController.navigate("login") }
                            )
                        }
                        composable("login") {
                            LoginScreen(
                                onBackClick = { navController.popBackStack() },
                                onSignUpClick = { navController.navigate("signup") }
                            )
                        }
                    }
                }
            }
        }
    }
}
