package com.example.peppergptintegration

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.example.peppergptintegration.data.Auth
import com.example.peppergptintegration.robot.Robot
import com.example.peppergptintegration.ui.auth.LoginScreen
import com.example.peppergptintegration.ui.auth.RegisterScreen
import com.example.peppergptintegration.ui.participants.ParticipantFormScreen
import com.example.peppergptintegration.ui.participants.ParticipantScreen
import com.example.peppergptintegration.ui.participants.ParticipantsScreen
import com.example.peppergptintegration.ui.session.SessionScreen
import com.example.peppergptintegration.ui.theme.AppTheme

/**
 * The app's single activity. Everything visible is Compose; this class owns
 * only what has to belong to an activity: the robot's focus and the window.
 *
 * The same build runs on Pepper's tablet and on a phone. On a phone the robot
 * never grants focus, and [Robot] speaks through Android instead.
 */
class MainActivity : ComponentActivity(), RobotLifecycleCallbacks {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before anything can make a request: the saved server address and token.
        AppConfig.init(this)
        Auth.init(this)
        Robot.init(this)

        // A session is a participant looking at this screen for several
        // minutes without touching it while they listen and speak.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // On a phone this simply never gains focus. Guarded because a device
        // without the robot's services is not what the SDK expects to run on.
        try {
            QiSDK.register(this, this)
        } catch (e: Throwable) {
            Log.w(TAG, "QiSDK.register failed; continuing without a robot", e)
        }

        setContent { AppTheme { App() } }
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        Log.d(TAG, "robot focus gained")
        Robot.onFocusGained(qiContext)
    }

    override fun onRobotFocusLost() {
        Log.d(TAG, "robot focus lost")
        Robot.onFocusLost()
    }

    override fun onRobotFocusRefused(reason: String) {
        Log.w(TAG, "robot focus refused: $reason")
    }

    override fun onDestroy() {
        try {
            QiSDK.unregister(this, this)
        } catch (e: Throwable) {
            Log.w(TAG, "QiSDK.unregister failed", e)
        }
        if (isFinishing) Robot.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val PARTICIPANTS = "participants"
    const val NEW_PARTICIPANT = "enrol"
    fun participant(id: String) = "participants/$id"
    fun edit(id: String) = "participants/$id/edit"
    fun session(id: String, step: String, resume: String?) =
        "participants/$id/session/$step" + (resume?.let { "?resume=$it" } ?: "")
}

/**
 * Sign in, pick a participant, run the next step of their protocol.
 *
 *   login / register
 *     -> participants -> enrol | {id} -> {id}/edit
 *                                   -> {id}/session/{step}
 */
@Composable
private fun App() {
    val nav = rememberNavController()
    val signedIn by Auth.signedIn.collectAsStateWithLifecycle()

    // The token can be rejected from any screen. Wherever that happens, the
    // only place to go is back to sign-in, with nothing left behind it.
    LaunchedEffect(signedIn) {
        // Null until the graph is up, and at that point the start destination
        // has already been chosen from the same flag.
        val route = nav.currentDestination?.route ?: return@LaunchedEffect
        val onAuthPage = route == Routes.LOGIN || route == Routes.REGISTER
        if (!signedIn && !onAuthPage) nav.toRoot(Routes.LOGIN)
    }

    NavHost(nav, startDestination = if (Auth.signedIn.value) Routes.PARTICIPANTS else Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onSignedIn = { nav.toRoot(Routes.PARTICIPANTS) },
                onRegister = { nav.navigate(Routes.REGISTER) },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onDone = { nav.toRoot(Routes.PARTICIPANTS) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.PARTICIPANTS) {
            ParticipantsScreen(
                onOpen = { nav.navigate(Routes.participant(it)) },
                onCreate = { nav.navigate(Routes.NEW_PARTICIPANT) },
                onSignedOut = { nav.toRoot(Routes.LOGIN) },
            )
        }
        composable(Routes.NEW_PARTICIPANT) {
            ParticipantFormScreen(
                participantId = null,
                // Straight to the new participant's page, where they are
                // assigned to a group and their pre-test is started.
                onSaved = { id ->
                    nav.navigate(Routes.participant(id)) { popUpTo(Routes.PARTICIPANTS) }
                },
                onDeleted = { nav.popBackStack() },
                onBack = { nav.popBackStack() },
            )
        }
        composable("participants/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ParticipantScreen(
                participantId = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.edit(id)) },
                onStart = { step, resume -> nav.navigate(Routes.session(id, step, resume)) },
            )
        }
        composable("participants/{id}/edit") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ParticipantFormScreen(
                participantId = id,
                onSaved = { nav.popBackStack() },
                onDeleted = { nav.popBackStack(Routes.PARTICIPANTS, inclusive = false) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "participants/{id}/session/{step}?resume={resume}",
            arguments = listOf(navArgument("resume") { type = NavType.StringType; nullable = true }),
        ) { entry ->
            SessionScreen(
                childId = entry.arguments?.getString("id").orEmpty(),
                step = entry.arguments?.getString("step").orEmpty(),
                resumeSessionId = entry.arguments?.getString("resume"),
                onExit = { nav.popBackStack() },
            )
        }
    }
}

/** Go to [route] and leave nothing behind it. */
private fun NavHostController.toRoot(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
