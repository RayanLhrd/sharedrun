package dev.sharedrun.state;

public final class SharedRunState {
    public static final float DEFAULT_MAX_HEALTH = 20.0f;
    public static final int DEFAULT_TIMER_SECONDS = 5400;
    public static final int DEFAULT_SWAP_ACTIVATION_REMAINING_SECONDS = 4500;

    public static volatile float sharedHealth = DEFAULT_MAX_HEALTH;
    public static volatile boolean sharedHealthInitialized = false;

    public static volatile int totalSeconds = DEFAULT_TIMER_SECONDS;
    public static volatile int remainingTicks = DEFAULT_TIMER_SECONDS * 20;
    public static volatile boolean timerRunning = false;

    public static volatile boolean hpSyncEnabled = true;
    public static volatile boolean hungerSyncEnabled = false;
    public static volatile boolean effectsSyncEnabled = true;

    public static volatile int sharedFoodLevel = 20;
    public static volatile float sharedSaturation = 5f;
    public static volatile boolean hungerInitialized = false;

    public static volatile boolean swapEnabled = true;
    public static volatile int swapMinMinutes = 4;
    public static volatile int swapMaxMinutes = 7;
    public static volatile long nextSwapTick = -1;
    public static volatile int swapCountdownTicks = -1;
    public static volatile int swapActivationRemainingSeconds = DEFAULT_SWAP_ACTIVATION_REMAINING_SECONDS;
    public static volatile boolean swapActivationNotified = false;
    public static volatile boolean swapIntervalOverride = false;
    public static volatile boolean anyPlayerWasInEnd = false;

    public static volatile boolean knockbackEnabled = false;
    public static volatile double knockbackHorizontal = 0.2;
    public static volatile double knockbackVertical = 0.2;
    public static volatile int knockbackCooldownTicks = 30;

    public static volatile int checkpointMask = 0;
    public static volatile boolean victoryTriggered = false;

    public static volatile boolean preGame = true;
    public static volatile int startCountdownTicks = 0;
    public static volatile double pregameRadius = 18.0;

    public static volatile int swapCount = 0;
    public static volatile boolean runEnded = false;

    // Positions clés pour le mode debrief (Long.MIN_VALUE = pas encore connue)
    public static volatile long strongholdPos = Long.MIN_VALUE;
    public static volatile long netherEntryPos = Long.MIN_VALUE;
    public static volatile long endEntryPos = Long.MIN_VALUE;
    public static volatile long fortressPos = Long.MIN_VALUE;

    private SharedRunState() {}

    public static void resetToDefaults() {
        sharedHealth = DEFAULT_MAX_HEALTH;
        sharedHealthInitialized = false;

        totalSeconds = DEFAULT_TIMER_SECONDS;
        remainingTicks = DEFAULT_TIMER_SECONDS * 20;
        timerRunning = false;

        hpSyncEnabled = true;
        hungerSyncEnabled = false;
        effectsSyncEnabled = true;

        sharedFoodLevel = 20;
        sharedSaturation = 5f;
        hungerInitialized = false;

        swapEnabled = true;
        swapMinMinutes = 4;
        swapMaxMinutes = 7;
        nextSwapTick = -1;
        swapCountdownTicks = -1;
        swapActivationRemainingSeconds = DEFAULT_SWAP_ACTIVATION_REMAINING_SECONDS;
        swapActivationNotified = false;
        swapIntervalOverride = false;
        anyPlayerWasInEnd = false;

        knockbackEnabled = false;
        knockbackHorizontal = 0.2;
        knockbackVertical = 0.2;
        knockbackCooldownTicks = 30;

        checkpointMask = 0;
        victoryTriggered = false;

        preGame = true;
        startCountdownTicks = 0;
        pregameRadius = 18.0;

        swapCount = 0;
        runEnded = false;
        strongholdPos = Long.MIN_VALUE;
        netherEntryPos = Long.MIN_VALUE;
        endEntryPos = Long.MIN_VALUE;
        fortressPos = Long.MIN_VALUE;
    }
}
