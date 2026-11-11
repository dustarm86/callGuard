<p align="center">
  <img src="assets/icon.png" width="128" alt="callGuard icon">
</p>

<h1 align="center">callGuard</h1>

personal android call screener. every incoming call goes through a `CallScreeningService`:

- contact or allowlisted number: rings normally
- anything else (including hidden numbers): silenced, no ring, no missed-call notification, still in your call log
- every silenced call is saved in the app's log, with a one-tap "allow" to promote real callers to the allowlist

## build and install

1. open this folder in android studio (ladybug or newer). let it sync. it will pull gradle 8.9.
2. enable developer options and usb debugging on the razr fold, plug it in, hit run.
   or from a terminal: `./gradlew assembleDebug`, then `adb install app/build/outputs/apk/debug/app-debug.apk`
3. open callGuard and tap **grant contacts access**, then **make default screener**.

heads up: the screener role is the same slot as "caller id & spam app". callGuard replaces google's spam filter there, which is the tradeoff for it seeing every call.

## unit tests

```
./gradlew testDebugUnitTest
```

covers `NumberNormalizer` and the `ScreeningEngine` decision table.

## manual test checklist

- call from a number that isn't in your contacts: phone stays silent, entry appears in the log
- call from a contact: rings
- tap **allow** on a logged number, call again: rings
- hidden/private number: silent
- toggle work hours only and call outside the schedule: rings
- emergency calls are never screened by android

## how it behaves when things go wrong

- contacts permission missing: nothing is screened, every call rings
- screening takes longer than 3 seconds or throws: call rings
- app is not the default screener: android never calls it, so the stock behavior applies

## layout

```
app/src/main/java/com/dustin/callguard/
  screening/  CallScreenerService, ScreeningEngine, ContactLookup, NumberNormalizer
  data/       Room entities, daos, AppDatabase, ScreenerSettings
  ui/         compose screens (log, allowlist, settings) and MainViewModel
```
