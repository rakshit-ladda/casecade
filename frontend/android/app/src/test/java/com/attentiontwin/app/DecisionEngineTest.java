package com.attentiontwin.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class DecisionEngineTest {
    private final DecisionEngine engine = new DecisionEngine();

    @Test public void focusOffAlwaysAllows() {
        DecisionEngine.Input in = new DecisionEngine.Input(); in.focusActive = false; in.role = DecisionEngine.Role.DISTRACTING;
        assertEquals(DecisionEngine.Action.ALLOW, engine.evaluate(in).action);
    }
    @Test public void importantAppAlwaysAllows() {
        DecisionEngine.Input in = new DecisionEngine.Input(); in.focusActive = true; in.role = DecisionEngine.Role.IMPORTANT;
        assertEquals(DecisionEngine.Action.ALLOW, engine.evaluate(in).action);
    }
    @Test public void verificationCodeAlwaysAllows() {
        DecisionEngine.Input in = new DecisionEngine.Input(); in.focusActive = true; in.title = "Your verification code"; in.text = "Use 381902";
        DecisionEngine.Result result = engine.evaluate(in);
        assertEquals(DecisionEngine.Action.ALLOW, result.action); assertTrue(result.exception);
    }
    @Test public void urgentWordAlwaysAllows() {
        DecisionEngine.Input in = new DecisionEngine.Input(); in.focusActive = true; in.text = "Emergency: call now"; in.urgentWords = "emergency";
        assertEquals(DecisionEngine.Action.ALLOW, engine.evaluate(in).action);
    }
    @Test public void lowHistoryProtectsMeaningfulImportanceFromSuppression() {
        DecisionEngine.Input in = new DecisionEngine.Input(); in.focusActive = true; in.category = "communication"; in.role = DecisionEngine.Role.NORMAL;
        in.stats = new DecisionEngine.Stats(2, 0, 0, 0);
        assertNotEquals(DecisionEngine.Action.SUPPRESS, engine.evaluate(in).action);
    }
    @Test public void heldNotificationIsNotRejudgedUntilScheduledRelease() {
        assertTrue(DecisionEngine.shouldKeepHeldOnRepost(2000, 1000));
        assertFalse(DecisionEngine.shouldKeepHeldOnRepost(1000, 1000));
    }
    @Test public void openRateUsesEightObservationPrior() {
        DecisionEngine.Input in = new DecisionEngine.Input(); in.category = "social";
        in.stats = new DecisionEngine.Stats(0, 0, 0, 0);
        assertEquals(.62, engine.evaluate(in).openRateEstimate, .0001);
    }

    @Test public void blockInFocusHoldsEvenHighImportance() {
        DecisionEngine.Input in = new DecisionEngine.Input(); in.focusActive = true; in.category = "communication";
        in.role = DecisionEngine.Role.IMPORTANT; in.stats = new DecisionEngine.Stats(200, 200, 200, 0);
        DecisionEngine.Result result = engine.evaluateForAppRole(in, DecisionEngine.AppRole.BLOCK_IN_FOCUS);
        assertEquals(DecisionEngine.Action.DEFER, result.action);
        assertEquals("This app is set to Block in focus.", result.reason);
    }

    @Test public void focusOffAllowsBlockedApp() {
        DecisionEngine.Input in = new DecisionEngine.Input(); in.focusActive = false;
        assertEquals(DecisionEngine.Action.ALLOW, engine.evaluateForAppRole(in, DecisionEngine.AppRole.BLOCK_IN_FOCUS).action);
    }

    @Test public void focusOnAllowRoleLeavesNotificationAvailable() {
        DecisionEngine.Input in = new DecisionEngine.Input(); in.focusActive = true;
        assertEquals(DecisionEngine.Action.ALLOW, engine.evaluateForAppRole(in, DecisionEngine.AppRole.ALLOW).action);
    }

    @Test public void focusOnBlockRoleAlwaysHoldsRegardlessOfImportanceAndHistory() {
        DecisionEngine.Input in = new DecisionEngine.Input(); in.focusActive = true;
        in.role = DecisionEngine.Role.IMPORTANT;
        in.stats = new DecisionEngine.Stats(200, 200, 200, 0);
        assertEquals(DecisionEngine.Action.DEFER, engine.evaluateForAppRole(in, DecisionEngine.AppRole.BLOCK_IN_FOCUS).action);
    }

    @Test public void engineFailureFallsOpen() {
        DecisionEngine.Result result = engine.evaluateForAppRoleSafely(null, DecisionEngine.AppRole.BLOCK_IN_FOCUS);
        assertEquals(DecisionEngine.Action.ALLOW, result.action);
        assertTrue(result.reason.startsWith("Fail-open"));
    }

    @Test public void dndRestoresOnlyWhenAttentionTwinOwnsIt() {
        assertTrue(DndController.shouldRestore(true));
        assertFalse(DndController.shouldRestore(false));
    }

    @Test public void otpCallAndAlarmAreAlwaysAllowed() {
        DecisionEngine.Input otp = new DecisionEngine.Input(); otp.focusActive = true; otp.text = "verification code 483920";
        assertEquals(DecisionEngine.Action.ALLOW, engine.evaluateForAppRole(otp, DecisionEngine.AppRole.BLOCK_IN_FOCUS).action);
        DecisionEngine.Input call = new DecisionEngine.Input(); call.focusActive = true; call.notificationCategory = "call";
        assertEquals(DecisionEngine.Action.ALLOW, engine.evaluateForAppRole(call, DecisionEngine.AppRole.BLOCK_IN_FOCUS).action);
        DecisionEngine.Input alarm = new DecisionEngine.Input(); alarm.focusActive = true; alarm.notificationCategory = "alarm";
        assertEquals(DecisionEngine.Action.ALLOW, engine.evaluateForAppRole(alarm, DecisionEngine.AppRole.BLOCK_IN_FOCUS).action);
    }

    @Test public void smartUsesEstimateButNeverReturnsSuppressForLiveRouting() {
        DecisionEngine.Input in = new DecisionEngine.Input(); in.focusActive = true; in.category = "work";
        in.stats = new DecisionEngine.Stats(100, 100, 0, 0);
        DecisionEngine.Result result = engine.evaluateForAppRole(in, DecisionEngine.AppRole.SMART);
        assertTrue(result.reason.startsWith("SMART used the local estimate"));
        assertNotEquals(DecisionEngine.Action.SUPPRESS, result.action);
    }

    @Test public void snoozeReleaseIsAllowedExactlyWhenDue() {
        assertFalse(DecisionEngine.shouldAllowSnoozeReleaseOnce(2000, 1999));
        assertTrue(DecisionEngine.shouldAllowSnoozeReleaseOnce(2000, 2000));
    }
}
